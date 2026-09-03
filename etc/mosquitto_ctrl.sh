# Common setup for running mosquitto_ctrl

if [ -z "${UDMI_ROOT:-}" ]; then
    _MOSQ_SCRIPT_DIR="${BASH_SOURCE:-$0}"
    UDMI_ROOT=$(cd "$(dirname "$_MOSQ_SCRIPT_DIR")/.." && pwd)
fi

if [[ -n "${TARGET_PROJECT:-}" && -z "${MQTT_PORT:-}" ]]; then
    if [[ $TARGET_PROJECT =~ localhost:([0-9]+) ]]; then
        export MQTT_PORT="${BASH_REMATCH[1]}"
    fi
fi

if [[ $(id -u) == 0 && ! -f /.dockerenv && -z ${UDMI_CONTAINER:-} && ${MQTT_PORT:-8883} == 8883 ]]; then
    ETC_DIR=/etc/mosquitto
elif [[ -f /.dockerenv || -n ${UDMI_CONTAINER:-} ]]; then
    ETC_DIR=/var/mosquitto_isolated
    if [[ ! -d /var/mosquitto_isolated ]]; then
        mkdir -p /var/mosquitto_isolated
        if [[ -d /etc/mosquitto ]]; then
            cp -r /etc/mosquitto/* /var/mosquitto_isolated/ 2>/dev/null || true
        fi
        chown -R mosquitto:mosquitto /var/mosquitto_isolated || true
        ln -sf /var/mosquitto_isolated /var/mosquitto
        mkdir -p var
        ln -sf /var/mosquitto_isolated var/mosquitto
    fi
else
    ETC_DIR=${MOSQUITTO_ETC_DIR:-var/mosquitto}
fi

CERT_DIR=$ETC_DIR/certs
CA_CERT=$CERT_DIR/ca.crt

AUTH_USER=scrumptious
AUTH_PASS=aardvark

CTRL_OPTS="-h ${MQTT_HOST:-localhost} -p ${MQTT_PORT:-8883} -u $AUTH_USER -P $AUTH_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"

MOSQUITTO_CTRL="mosquitto_ctrl $CTRL_OPTS dynsec"
MOSQUITTO_SUB="mosquitto_sub"
MOSQUITTO_PUB="mosquitto_pub"

if [[ -n ${registry_id:-} ]]; then
    SERV_USER=rocket
    SERV_PASS=monkey
    SERV_ID=$registry_id/server
    SERVER_OPTS="-h ${MQTT_HOST:-localhost} -p ${MQTT_PORT:-8883} -i $SERV_ID -u $SERV_USER -P $SERV_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"
fi
