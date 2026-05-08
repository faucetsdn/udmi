#!/bin/bash -e

# Navigate to bridgehead directory
cd $(dirname $(realpath $0))/..

echo "Cloning default site model..."
# Remove existing udmi_site_model if it exists to avoid conflicts
sudo rm -rf udmi_site_model

git clone https://github.com/faucetsdn/udmi_site_model.git udmi_site_model
# Clean up extra keys in AHU-1 that cause validation errors
sudo rm -f udmi_site_model/devices/AHU-1/rsa_private.pem
sudo rm -f udmi_site_model/devices/AHU-1/rsa_private.pkcs8
sudo rm -f udmi_site_model/devices/AHU-1/rsa_public.pem

mkdir -p udmi_site_model/out
chmod a+rwx udmi_site_model/out

# Backup existing .env if it exists
if [ -f .env ]; then
    echo "Backing up existing .env"
    cp .env .env.bak
fi

echo "Generating .env file..."
HOST_IP=$(sudo hostname -I | awk '{print $1}')
cat <<EOF > .env
HOST_IP=$HOST_IP
AUTH_USER=scrumptious
AUTH_PASS=aardvark
SERV_USER=rocket
SERV_PASS=monkey
INFLUXDB_TOKEN=$(openssl rand -hex 32)
INFLUX_USER=bridgehead
INFLUX_PASSWORD=password
GRAFANA_USER=bridgehead
GRAFANA_PASSWORD=password
EOF

echo "Starting services with docker-compose..."
sudo docker compose up -d --build

# Function to clean up on exit
function cleanup {
    echo "Cleaning up..."
    echo "Dumping UDMIS logs:"
    sudo docker logs udmis || true
    echo "Dumping Validator logs:"
    sudo docker logs validator || true
    
    echo "Dumping Pubber logs:"
    sudo docker logs pubber || true
    
    if [ -f udmi_site_model/devices/AHU-1/out/exceptions.txt ]; then
        echo "Dumping AHU-1 exceptions.txt:"
        cat udmi_site_model/devices/AHU-1/out/exceptions.txt || true
    fi
    
    echo "Stopping Pubber container..."
    sudo docker stop pubber || true
    echo "Stopping docker-compose services..."
    sudo docker compose down
    sudo rm -rf udmi_site_model
    
    # Restore .env if backup exists
    if [ -f .env.bak ]; then
        echo "Restoring .env"
        mv .env.bak .env
    else
        rm -f .env
    fi
}
trap cleanup EXIT

echo "Waiting for services to be healthy..."
for i in {1..60}; do
    if sudo docker logs udmis 2>&1 | grep -q "udmis running in the background"; then
        echo "UDMIS is ready."
        break
    fi
    echo "Waiting for UDMIS... ($i/60)"
    sleep 5
done

if ! sudo docker logs udmis 2>&1 | grep -q "udmis running in the background"; then
    echo "UDMIS failed to become ready in time!"
    echo "Dumping UDMIS logs:"
    sudo docker logs udmis || true
    exit 1
fi

# Function to run a command with retries
function run_with_retry {
    local n=0
    until [ "$n" -ge 3 ]
    do
       "$@" && return 0
       n=$((n+1))
       echo "Command failed, retrying ($n/3)..." >&2
       sleep 15
    done
    return 1
}

echo "Executing discovery sequence..."
sleep 15

# 1. Registrar setup
run_with_retry sudo docker exec validator /bin/bash -c "bin/registrar site_model/ //mqtt/mosquitto -x -d"
echo "Registrar clean completed."
sleep 15
run_with_retry sudo docker exec validator /bin/bash -c "bin/registrar site_model/ //mqtt/mosquitto GAT-123"
echo "Step 1: Registrar initial setup completed."

# 2. Start Pubber container
sudo docker run -d --rm --name pubber --network udminet -v $(realpath udmi_site_model):/root/site_model ghcr.io/faucetsdn/udmi:pubber-latest /bin/bash -c "tail -f /dev/null"
echo "Step 2: Pubber container started."

# 3. Run Pubber inside container (using mosquitto service name)
sudo docker exec -d pubber /bin/bash -c "bin/pubber site_model/ //mqtt/mosquitto GAT-123 852649"
echo "Step 3: Pubber command executed inside container."

# Wait a bit for pubber to send messages
sleep 15
echo "Wait for Pubber messages completed."

# 4. Run discovery script
sudo docker exec validator /root/discovery.sh GAT-123
echo "Step 4: Discovery script executed."

# 5. Run registrar to check results
run_with_retry sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto > registrar_output.txt
echo "Step 5: Ran Registrar to check results."

echo "Verifying output..."
cat registrar_output.txt

# Verify expected summary
failed=0
grep -q "Device extra: 4" registrar_output.txt || failed=1
grep -q "Device status: 4" registrar_output.txt || failed=1

# Also verify specific processed lines as per sample output
grep -q "Processed AHU-22" registrar_output.txt || failed=1
grep -q "Processed SNS-4" registrar_output.txt || failed=1
grep -q "Processed GAT-123" registrar_output.txt || failed=1

if [ $failed -eq 0 ]; then
    echo "Test PASSED"
else
    echo "Test FAILED"
    exit 1
fi