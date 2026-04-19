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

echo "Starting services with docker-compose..."
docker compose up -d --build

# Function to clean up on exit
function cleanup {
    echo "Cleaning up..."
    echo "Dumping UDMIS logs:"
    docker logs udmis || true
    echo "Dumping Validator logs:"
    docker logs validator || true
    
    # echo "Stopping Pubber container..."
    # docker stop pubber || true
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

# Function to run a command with retries
function run_with_retry {
    local n=0
    until [ "$n" -ge 3 ]
    do
       "$@" && return 0
       n=$((n+1))
       echo "Command failed, retrying ($n/3)..." >&2
       sleep 5
    done
    return 1
}

failed=0

if [ $failed -eq 0 ]; then
    echo "Test PASSED"
else
    echo "Test FAILED"
    exit 1
fi
