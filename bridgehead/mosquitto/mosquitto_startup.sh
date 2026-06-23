#!/bin/sh
UDMI_ROOT=/root/udmi

function fail {
    echo error: $*
    false
}

echo "persistence true
persistence_file mosquitto.db
persistence_location /mosquitto/data/ 
sys_interval 10

include_dir /etc/mosquitto/conf.d" > /etc/mosquitto/mosquitto.conf

cd $UDMI_ROOT

site_model=$(realpath /site_model)
site_config=$site_model/cloud_iot_config.json
registry_id=$(jq -r .registry_id $site_config)

source $UDMI_ROOT/etc/mosquitto_ctrl.sh
mkdir -p $CERT_DIR

if [[ ! -f $CERT_DIR/ca.crt ]]; then
    if [[ -n "$HOST_IP" ]]; then
        sed -i -e "s|IP:127.0.0.1,|IP:127.0.0.1, IP:${HOST_IP},|" bin/keygen
    fi
    bin/setup_ca $site_model mosquitto  
fi
bin/start_mosquitto

$MOSQUITTO_CTRL deleteClient $SERV_USER
$MOSQUITTO_CTRL createClient $SERV_USER -p $SERV_PASS
$MOSQUITTO_CTRL addClientRole $SERV_USER service

echo "Starting initializing site $site_model" | tee -a $UDMIS_LOG
bin/mosquctl_site $site_model
$MOSQUITTO_CTRL addClientRole $AUTH_USER service

MOSQUITTO_PID=$(pgrep -x mosquitto)
if [ -n "$MOSQUITTO_PID" ]; then
    echo "Found mosquitto PID: $MOSQUITTO_PID. Monitoring..."
    trap 'echo "Stopping mosquitto PID $MOSQUITTO_PID..."; kill -TERM "$MOSQUITTO_PID"; while kill -0 "$MOSQUITTO_PID" 2>/dev/null; do sleep 0.5; done; exit 0' SIGTERM SIGINT
    
    while kill -0 "$MOSQUITTO_PID" 2>/dev/null; do
        sleep 1
    done
    echo "Mosquitto process disappeared."
else
    echo "Mosquitto PID not found!"
    exit 1
fi