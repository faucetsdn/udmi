#!/bin/bash -eu

UDMI_ROOT=$(realpath $(dirname $0)/..)
cd $UDMI_ROOT

echo "=================================================="
echo "Starting clean integration environment..."
echo "=================================================="

# 1. Cleanup existing processes and databases
echo "Stopping any running services..."
bin/start_udmis stop || true
kill $(pgrep -f '/tmp/etcd/etcd') || true
sudo systemctl stop mosquitto || true
killall mosquitto || true

echo "Flushing databases..."
rm -rf var/etcd
sudo rm -f /etc/mosquitto/dynamic_security.json
sudo rm -f /etc/mosquitto/mosquitto.passwd

# 2. Clean local site model repository
echo "Restoring clean site model state..."
(cd sites/udmi_site_model && git clean -fdx && git checkout .)

# 3. Startup fresh services
echo "Starting fresh local services..."
bin/start_local sites/udmi_site_model //mqtt/localhost

# 4. Run standalone registrar tests
echo "Running registrar tests..."
bin/test_registrar

# 5. Validate Mosquitto Dynamic Security
echo "Validating dynamic security configuration..."
source etc/mosquitto_ctrl.sh
clients=$($MOSQUITTO_CTRL listClients)
echo "Live Dynamic Security Clients:"
echo "$clients"

if [[ $clients =~ scrumptious ]]; then
    echo "Validation SUCCESS: Found scrumptious service client."
else
    echo "Validation FAILURE: Service client not found in Dynamic Security."
    exit 1
fi

# 6. Teardown
echo "Cleaning up services..."
bin/start_udmis stop || true
kill $(pgrep -f '/tmp/etcd/etcd') || true
sudo systemctl stop mosquitto || true

echo "=================================================="
echo "Integration test completed successfully!"
echo "=================================================="
