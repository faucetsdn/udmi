#!/bin/bash -euo pipefail
set -euo pipefail

# Ensure the script runs from the repository root
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR/.."

# Initialize test passed state
TEST_PASSED=false

# 2. Environment Check
echo "Checking environment..."
for cmd in java docker nc base64 openssl curl tar jq; do
  if ! command -v "$cmd" &> /dev/null; then
    echo "Error: $cmd is not installed or not in PATH." >&2
    exit 1
  fi
done

if ! docker compose version &>/dev/null; then
  echo "Error: docker compose plugin is not installed." >&2
  exit 1
fi

SITE_MODEL="$(pwd)/sites/udmi_site_model"
if [ ! -d "$SITE_MODEL" ]; then
  echo "Error: $SITE_MODEL directory does not exist." >&2
  exit 1
fi

REGISTRY_ID=$(jq -r .registry_id "$SITE_MODEL/cloud_iot_config.json")
DEVICE_ID=$(jq -r .device_id "$SITE_MODEL/cloud_iot_config.json")

# Cleanup Hook (Trap) and Process Helpers
PUBBER_PID=""
BRIDGE_EVENTS_PID=""
BRIDGE_STATE_PID=""
UDMIS_PID=""
UDMIS_MQTT_PID=""
CREATED_SYMLINK=false

kill_process() {
  local pid=$1
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "Killing process $pid and its children..."
    local children=$(pgrep -P "$pid" 2>/dev/null || true)
    kill "$pid" 2>/dev/null || true
    for child in $children; do
      kill "$child" 2>/dev/null || true
    done
    # Wait up to 5s
    for i in {1..10}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        return 0
      fi
      sleep 0.5
    done
    kill -9 "$pid" 2>/dev/null || true
  fi
}

cleanup() {
  trap - EXIT INT TERM
  
  if [ "$TEST_PASSED" = false ]; then
    echo "Test failed. Saving Docker logs to out/..." >&2
    # Make sure all files in bridgehead/var are readable before copying
    docker run --rm -v "$(pwd)/bridgehead/var:/var_dir" alpine chmod -R a+rX /var_dir || true
    if [ -f bridgehead/var/mosquitto/log/mosquitto.log ]; then
      echo "Saving Mosquitto broker log..." >&2
      cp bridgehead/var/mosquitto/log/mosquitto.log out/mosquitto.log
    fi
    if [ -f bridgehead/var/mosquitto/dynamic_security.json ]; then
      echo "Saving Mosquitto dynamic security config..." >&2
      cp bridgehead/var/mosquitto/dynamic_security.json out/dynamic_security.json
    fi
    docker compose -f bridgehead/docker-compose.yml logs mosquitto > out/mosquitto_docker.log 2>&1 || true
    docker compose -f bridgehead/docker-compose.yml logs etcd > out/etcd_docker.log 2>&1 || true
    docker logs pubsub-emulator > out/pubsub_emulator.log 2>&1 || true
    echo "All logs (Udmis, Bridges, Pubber, Mosquitto, Etcd, PubSub Emulator, Mosquitto Broker, Mosquitto DynSec) are available in the 'out/' directory." >&2
  fi

  echo "Cleaning up background processes and containers..."
  
  kill_process "$PUBBER_PID"
  kill_process "$BRIDGE_EVENTS_PID"
  kill_process "$BRIDGE_STATE_PID"
  kill_process "$UDMIS_PID"
  kill_process "$UDMIS_MQTT_PID"

  echo "Removing pubsub-emulator container..."
  docker rm -f pubsub-emulator >/dev/null 2>&1 || true

  echo "Stopping compose services..."
  docker compose -f bridgehead/docker-compose.yml --env-file bridgehead/.env down || true

  echo "Initializing and cleaning bridgehead/var..."
  mkdir -p bridgehead/var
  docker run --rm -v "$(pwd)/bridgehead/var:/var_dir" alpine sh -c "
    find /var_dir -mindepth 1 -maxdepth 1 ! -name 'tmp' -exec rm -rf {} +
    rm -rf /var_dir/tmp/* /var_dir/tmp/.* 2>/dev/null || true
    mkdir -p /var_dir/tmp
    chmod 777 /var_dir/tmp
  "

  if [ "$CREATED_SYMLINK" = true ]; then
    echo "Removing created symlink bridgehead/udmi_site_model..."
    rm -f bridgehead/udmi_site_model
  fi

  echo "Cleanup complete."
}

trap cleanup EXIT INT TERM

# 3. Clean Prior Runs
echo "Cleaning up prior runs..."
docker compose -f bridgehead/docker-compose.yml --env-file bridgehead/.env down || true
docker rm -f pubsub-emulator >/dev/null 2>&1 || true

echo "Initializing and cleaning bridgehead/var..."
mkdir -p bridgehead/var
docker run --rm -v "$(pwd)/bridgehead/var:/var_dir" alpine sh -c "
  find /var_dir -mindepth 1 -maxdepth 1 ! -name 'tmp' -exec rm -rf {} +
  rm -rf /var_dir/tmp/* /var_dir/tmp/.* 2>/dev/null || true
  mkdir -p /var_dir/tmp
  chmod 777 /var_dir/tmp
"

# 4. Port Check
echo "Checking ports..."
for port in 8883 2379 8085; do
  if nc -z 127.0.0.1 "$port" &>/dev/null; then
    echo "Error: Port $port is already in use." >&2
    exit 1
  fi
done

# 5. Site Model Setup
echo "Setting up site model link..."
if [ -L bridgehead/udmi_site_model ]; then
  echo "Symlink bridgehead/udmi_site_model already exists, ensuring it is correct..."
  ln -sf ../sites/udmi_site_model bridgehead/udmi_site_model
elif [ -e bridgehead/udmi_site_model ]; then
  echo "Error: bridgehead/udmi_site_model exists and is not a symlink." >&2
  exit 1
else
  ln -s ../sites/udmi_site_model bridgehead/udmi_site_model
  CREATED_SYMLINK=true
fi

# 7. Build Udmis and Validator
echo "Building Validator..."
validator/bin/build
echo "Building Udmis..."
udmis/bin/build
echo "Building Pubber..."
pubber/bin/build

# 8. Start Docker Services
echo "Configuring environment..."
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}') || HOST_IP=""
if [ -z "$HOST_IP" ]; then
  HOST_IP="127.0.0.1"
fi
export HOST_IP
export MOSQUITTO_LOG_PATH="$(pwd)/bridgehead/var/mosquitto/log/mosquitto.log"
export AUTH_USER=scrumptious
export AUTH_PASS=aardvark
export SERV_USER=rocket
export SERV_PASS=monkey
export INFLUXDB_TOKEN=$(openssl rand -hex 32)
export INFLUX_USER=bridgehead
export INFLUX_PASSWORD=password
export GRAFANA_USER=bridgehead
export GRAFANA_PASSWORD=password

mkdir -p out
cat << 'EOF' > out/docker-compose.override.yml
services:
  etcd:
    command: ["etcd", "-listen-client-urls=http://0.0.0.0:2379", "-advertise-client-urls=http://127.0.0.1:2379", "--data-dir", "/var/etcd"]
EOF

echo "Starting mosquitto and etcd via docker-compose..."
docker compose -f bridgehead/docker-compose.yml -f out/docker-compose.override.yml --env-file bridgehead/.env up -d --build mosquitto etcd

echo "Waiting for ports 8883 and 2379..."
timeout=30
while [ $timeout -gt 0 ]; do
  if nc -z 127.0.0.1 8883 && nc -z 127.0.0.1 2379; then
    echo "Ports 8883 and 2379 are open."
    break
  fi
  sleep 1
  timeout=$((timeout - 1))
done
if [ $timeout -le 0 ]; then
  echo "Error: Timeout waiting for ports 8883 and 2379." >&2
  exit 1
fi

echo "Waiting for Mosquitto initialization to complete..."
timeout=30
while [ $timeout -gt 0 ]; do
  if docker logs mosquitto 2>&1 | grep -q "Registered sites:"; then
    echo "Mosquitto initialization completed."
    break
  fi
  sleep 1
  timeout=$((timeout - 1))
done
if [ $timeout -le 0 ]; then
  echo "Error: Timeout waiting for Mosquitto initialization." >&2
  docker logs mosquitto >&2
  exit 1
fi

echo "Making mosquitto files readable..."
docker run --rm -v "$(pwd)/bridgehead/var:/var_dir" alpine chmod -R a+rX /var_dir

# 9. Start Pub/Sub Emulator
echo "Starting Pub/Sub Emulator..."
docker run -d --name pubsub-emulator -p 8085:8085 gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators gcloud beta emulators pubsub start --host-port=0.0.0.0:8085

echo "Waiting for Pub/Sub Emulator to be ready..."
timeout=30
while [ $timeout -gt 0 ]; do
  # Query topics list, if it returns 200 (even if empty) the emulator is ready
  if curl -s -o /dev/null "http://127.0.0.1:8085/v1/projects/udmis/topics"; then
    echo "Pub/Sub Emulator is ready."
    break
  fi
  sleep 1
  timeout=$((timeout - 1))
done
if [ $timeout -le 0 ]; then
  echo "Error: Timeout waiting for Pub/Sub Emulator to be ready." >&2
  exit 1
fi

# 10. Create Topics and Subscriptions
echo "Creating topics in Pub/Sub Emulator..."
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/topics/udmi_target"
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/topics/udmi_state"
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/topics/udmi_reflect"
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/topics/udmi_control"
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/topics/udmi_reply"

echo "Creating subscriptions in Pub/Sub Emulator..."
curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/subscriptions/udmi_target-udmis" \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/udmis/topics/udmi_target"}'

curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/subscriptions/udmi_target-provision" \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/udmis/topics/udmi_target"}'

curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/subscriptions/udmi_state-udmis" \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/udmis/topics/udmi_state"}'

curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/subscriptions/udmi_reflect-udmis" \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/udmis/topics/udmi_reflect"}'

curl -f -v -X PUT "http://127.0.0.1:8085/v1/projects/udmis/subscriptions/udmi_control-udmis" \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/udmis/topics/udmi_control"}'

echo "Verifying topics in Pub/Sub Emulator..."
curl -f -v "http://127.0.0.1:8085/v1/projects/udmis/topics"
echo
echo "Verifying subscriptions in Pub/Sub Emulator..."
curl -f -v "http://127.0.0.1:8085/v1/projects/udmis/subscriptions"
echo

# 11. Run Udmis in MQTT mode on Host and Register Devices
echo "Generating Udmis Host MQTT config..."
mkdir -p out
jq --arg u "$SERV_USER" --arg p "$SERV_PASS" --arg au "$AUTH_USER" --arg ap "$AUTH_PASS" --arg inc "$(pwd)/udmis/etc/prod_pod.json" --arg cert_dir "$(pwd)/bridgehead/var/mosquitto/certs" '
  .include = $inc |
  .flow_defaults.ca_file = ($cert_dir + "/ca.crt") |
  .flow_defaults.cert_file = ($cert_dir + "/rsa_private.crt") |
  .flow_defaults.key_file = ($cert_dir + "/rsa_private.pem") |
  .flow_defaults.auth_provider.basic.username = $u |
  .flow_defaults.auth_provider.basic.password = $p |
  .flow_defaults.hostname = "127.0.0.1" |
  .iot_access.implicit.endpoint.ca_file = ($cert_dir + "/ca.crt") |
  .iot_access.implicit.endpoint.cert_file = ($cert_dir + "/rsa_private.crt") |
  .iot_access.implicit.endpoint.key_file = ($cert_dir + "/rsa_private.pem") |
  .iot_access.implicit.endpoint.auth_provider.basic.username = $au |
  .iot_access.implicit.endpoint.auth_provider.basic.password = $ap |
  .iot_access.implicit.endpoint.hostname = "127.0.0.1"
' udmis/etc/local_pod.json > out/local_pod_host.json

echo "Starting Host Udmis (MQTT Mode)..."
export ETCD_CLUSTER=127.0.0.1
rm -f /tmp/pod_ready.txt
udmis/bin/run out/local_pod_host.json > out/udmis_mqtt_host.log 2>&1 &
UDMIS_MQTT_PID=$!

echo "Waiting for Host Udmis (MQTT Mode) to be ready..."
timeout=30
while [ $timeout -gt 0 ]; do
  if [ -f /tmp/pod_ready.txt ]; then
    echo "Host Udmis (MQTT Mode) is ready."
    break
  fi
  if ! kill -0 "$UDMIS_MQTT_PID" 2>/dev/null; then
    echo "Error: Host Udmis (MQTT Mode) process exited unexpectedly." >&2
    cat out/udmis_mqtt_host.log >&2
    exit 1
  fi
  sleep 1
  timeout=$((timeout - 1))
done
if [ $timeout -le 0 ]; then
  echo "Error: Timeout waiting for Host Udmis (MQTT Mode) to start." >&2
  cat out/udmis_mqtt_host.log >&2
  exit 1
fi

echo "Registering devices..."
bin/registrar "$SITE_MODEL" //mqtt/localhost

echo "Stopping Host Udmis (MQTT Mode)..."
kill_process "$UDMIS_MQTT_PID"
UDMIS_MQTT_PID=""

# 13. Start Host Udmis (Pub/Sub Mode)
echo "Starting Host Udmis (Pub/Sub Mode)..."
export GCP_PROJECT=udmis
export ETCD_CLUSTER=127.0.0.1
export PUBSUB_EMULATOR_HOST=127.0.0.1:8085
export UDMI_NAMESPACE=""

echo "Generating Udmis Host Pub/Sub config..."
jq '.flows.distributor.enabled = "false" |
    .iot_access."iot-access".provider = "implicit" |
    .iot_access."iot-access".project_id = "" |
    .iot_access."iot-access".options = "enabled=${ETCD_CLUSTER}" |
    .iot_access."iot-access".endpoint = {
      "protocol": "mqtt",
      "transport": "ssl",
      "hostname": "localhost",
      "port": 8883,
      "ca_file": "'$(pwd)'/bridgehead/var/mosquitto/certs/ca.crt",
      "cert_file": "'$(pwd)'/bridgehead/var/mosquitto/certs/rsa_private.crt",
      "key_file": "'$(pwd)'/bridgehead/var/mosquitto/certs/rsa_private.pem",
      "auth_provider": {
        "basic": {
          "username": "scrumptious",
          "password": "aardvark"
        }
      }
    }' udmis/etc/prod_pod.json > out/prod_pod_host.json

rm -f /tmp/pod_ready.txt

udmis/bin/run out/prod_pod_host.json > out/udmis_host.log 2>&1 &
UDMIS_PID=$!

echo "Waiting for Host Udmis (Pub/Sub Mode) to be ready..."
timeout=30
while [ $timeout -gt 0 ]; do
  if [ -f /tmp/pod_ready.txt ]; then
    echo "Host Udmis (Pub/Sub Mode) is ready."
    break
  fi
  if ! kill -0 "$UDMIS_PID" 2>/dev/null; then
    echo "Error: Host Udmis (Pub/Sub Mode) process exited unexpectedly." >&2
    cat out/udmis_host.log >&2
    exit 1
  fi
  sleep 1
  timeout=$((timeout - 1))
done
if [ $timeout -le 0 ]; then
  echo "Error: Timeout waiting for Host Udmis (Pub/Sub Mode) to start." >&2
  cat out/udmis_host.log >&2
  exit 1
fi

# 14. Start Bridges
echo "Starting Bridges..."
java -cp udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar \
  com.google.bos.udmi.service.bridge.MqttToPubSubBridge \
  --mqtt_broker_url=ssl://127.0.0.1:8883 \
  --mqtt_subscription_topic="/r/+/d/+/events/#" \
  --gcp_project_id=udmis \
  --pubsub_topic_id=udmi_target \
  --mqtt_tls \
  --mqtt_ca_path="$SITE_MODEL/reflector/ca.crt" \
  --mqtt_client_cert_path="$SITE_MODEL/reflector/rsa_private.crt" \
  --mqtt_client_key_path="$SITE_MODEL/reflector/rsa_private.pem" \
  --mqtt_username=rocket \
  --mqtt_password=monkey \
  --etcd_target=http://127.0.0.1:2379 \
  --etcd_options=enabled=true \
  --source_attribute=bridge \
  > out/bridge_events.log 2>&1 &
BRIDGE_EVENTS_PID=$!

java -cp udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar \
  com.google.bos.udmi.service.bridge.MqttToPubSubBridge \
  --mqtt_broker_url=ssl://127.0.0.1:8883 \
  --mqtt_subscription_topic="/r/+/d/+/state" \
  --gcp_project_id=udmis \
  --pubsub_topic_id=udmi_state \
  --mqtt_tls \
  --mqtt_ca_path="$SITE_MODEL/reflector/ca.crt" \
  --mqtt_client_cert_path="$SITE_MODEL/reflector/rsa_private.crt" \
  --mqtt_client_key_path="$SITE_MODEL/reflector/rsa_private.pem" \
  --mqtt_username=rocket \
  --mqtt_password=monkey \
  --etcd_target=http://127.0.0.1:2379 \
  --etcd_options=enabled=true \
  --source_attribute=bridge \
  > out/bridge_state.log 2>&1 &
BRIDGE_STATE_PID=$!

echo "Waiting for bridges to initialize..."
sleep 5

if ! kill -0 "$BRIDGE_EVENTS_PID" 2>/dev/null; then
  echo "Error: Events Bridge failed to start. Logs:" >&2
  cat out/bridge_events.log >&2
  exit 1
fi

if ! kill -0 "$BRIDGE_STATE_PID" 2>/dev/null; then
  echo "Error: State Bridge failed to start. Logs:" >&2
  cat out/bridge_state.log >&2
  exit 1
fi
echo "Bridges are running."

# 15. Run Pubber
echo "Starting Pubber..."
bin/pubber "$SITE_MODEL" //mqtt/127.0.0.1 "$DEVICE_ID" 852649 > out/pubber_host.log 2>&1 &
PUBBER_PID=$!

echo "Waiting for messages to propagate and populating etcd (up to 45s)..."
poll_timeout=45
STATE_VAL=""

for ((i = 1; i <= poll_timeout; i++)); do
  STATE_VAL=$(udmis/bin/etcdctl get --print-value-only "/r/$REGISTRY_ID/d/$DEVICE_ID:last_state" 2>/dev/null) || STATE_VAL=""
  if [[ -n "$STATE_VAL" && "$STATE_VAL" == *'"version"'* ]]; then
    break
  fi
  sleep 1
done

echo "Verifying etcd state..."
udmis/bin/etcdctl version

echo "All keys in etcd:"
udmis/bin/etcdctl get --prefix "" || true

echo "STATE_VAL output: $STATE_VAL"

if [[ -n "$STATE_VAL" && "$STATE_VAL" == *'"version"'* ]]; then
  echo "Verification PASSED"
  TEST_PASSED=true
else
  echo "Verification FAILED"
  exit 1
fi
