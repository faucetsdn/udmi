#!/bin/bash -e

# Navigate to bridgehead directory
cd $(dirname $0)/..

echo "Cloning default site model..."
# Remove existing udmi_site_model if it exists to avoid conflicts
sudo rm -rf udmi_site_model
sudo rm -rf site

git clone https://github.com/faucetsdn/udmi_site_model.git site
ln -s site udmi_site_model

# Backup existing .env if it exists
if [ -f .env ]; then
    echo "Backing up existing .env"
    cp .env .env.bak
fi

echo "Generating .env file..."
cat <<EOF > .env
HOST_IP=127.0.0.1
AUTH_USER=admin
AUTH_PASS=$(openssl rand -hex 16)
SERV_USER=service
SERV_PASS=$(openssl rand -hex 16)
INFLUXDB_TOKEN=$(openssl rand -hex 32)
INFLUX_USER=influx
INFLUX_PASSWORD=$(openssl rand -hex 16)
GRAFANA_USER=grafana
GRAFANA_PASSWORD=$(openssl rand -hex 16)
EOF

echo "Starting services with docker-compose..."
docker compose up -d --build

# Function to clean up on exit
function cleanup {
    echo "Cleaning up..."
    docker compose down
    sudo rm -rf site udmi_site_model
    
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
# Poll for UDMIS readiness. We can check logs for "Started UDMIS"
# For now, using a simple sleep as a fallback, but polling is better.
# Let's try to poll for 60 seconds.
for i in {1..60}; do
    if docker logs udmis 2>&1 | grep -q "Started UDMIS"; then
        echo "UDMIS is ready."
        break
    fi
    echo "Waiting for UDMIS... ($i/60)"
    sleep 2
done

# Also check if validator is running
if ! docker ps | grep -q validator; then
    echo "Validator container is not running!"
    exit 1
fi

echo "Executing discovery sequence..."

echo "Running registrar initial setup..."
docker exec validator bin/registrar site_model/ //mqtt/mosquitto -x -d
docker exec validator bin/registrar site_model/ //mqtt/mosquitto GAT-123

echo "Starting Pubber from source..."
# bin/pubber handles building if needed
# Run in background
../bin/pubber site //mqtt/localhost GAT-123 852649 &
PUBBER_PID=$!

echo "Pubber started with PID $PUBBER_PID"

# Wait a bit for pubber to connect and send some messages
sleep 10

echo "Running discovery script..."
docker exec validator /root/discovery.sh

echo "Running registrar to check results..."
docker exec validator bin/registrar site_model/ //mqtt/mosquitto > registrar_output.txt

echo "Stopping Pubber..."
kill $PUBBER_PID || true

echo "Verifying output..."
cat registrar_output.txt

# Expected summary:
# Summary:
#   Device envelope: 1
#   Device extra: 6
#   Device proxy: 2
#   Device status: 4
#   Device validation: 1
# Out of 4 total.

failed=0
grep -q "Device envelope: 1" registrar_output.txt || failed=1
grep -q "Device extra: 6" registrar_output.txt || failed=1
grep -q "Device proxy: 2" registrar_output.txt || failed=1
grep -q "Device status: 4" registrar_output.txt || failed=1
grep -q "Device validation: 1" registrar_output.txt || failed=1

if [ $failed -eq 0 ]; then
    echo "Test PASSED"
else
    echo "Test FAILED"
    exit 1
fi
