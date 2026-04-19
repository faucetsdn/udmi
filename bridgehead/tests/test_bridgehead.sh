#!/bin/bash -e

# Navigate to bridgehead directory
cd $(dirname $0)/..

echo "Cloning default site model..."
# Remove existing udmi_site_model if it exists to avoid conflicts
sudo rm -rf udmi_site_model

git clone https://github.com/faucetsdn/udmi_site_model.git udmi_site_model

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

echo "Building Pubber container from source..."
# Run from workspace root to make bin/container work correctly
(cd .. && bin/container pubber build --no-check)

echo "Starting services with docker-compose..."
docker compose up -d --build

# Function to clean up on exit
function cleanup {
    echo "Cleaning up..."
    echo "Dumping UDMIS logs:"
    docker logs udmis || true
    echo "Dumping Validator logs:"
    docker logs validator || true
    
    echo "Stopping Pubber container..."
    docker stop pubber || true
    echo "Stopping docker-compose services..."
    docker compose down
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
    if docker logs udmis 2>&1 | grep -q "udmis running in the background"; then
        echo "UDMIS is ready."
        break
    fi
    echo "Waiting for UDMIS... ($i/60)"
    sleep 2
done

if ! docker logs udmis 2>&1 | grep -q "udmis running in the background"; then
    echo "UDMIS failed to become ready in time!"
    echo "Dumping UDMIS logs:"
    docker logs udmis || true
    exit 1
fi

echo "Executing discovery sequence..."

echo "Running registrar initial setup..."
docker exec validator bin/registrar site_model/ //mqtt/mosquitto -x -d
docker exec validator bin/registrar site_model/ //mqtt/mosquitto GAT-123

echo "Starting Pubber container in udminet network..."
# Use the locally built 'pubber' image and connect to 'udminet'
docker run -d --rm --name pubber --network udminet -v $(realpath udmi_site_model):/root/site_model pubber /bin/bash -c "tail -f /dev/null"

echo "Running Pubber inside container..."
docker exec -d pubber /bin/bash -c "bin/pubber site_model/ //mqtt/mosquitto GAT-123 852649"

# Wait a bit for pubber to connect and send some messages
sleep 10

echo "Running discovery script with GAT-123..."
docker exec validator /root/discovery.sh GAT-123

echo "Running registrar to check results..."
docker exec validator bin/registrar site_model/ //mqtt/mosquitto > registrar_output.txt

echo "Verifying output..."
cat registrar_output.txt

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
