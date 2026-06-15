# Common setup for running mosquitto_ctrl

if [[ -z ${UDMI_ROOT:-} ]]; then
    UDMI_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fi

if [[ ${UDMI_NO_SUDO:-false} == true ]]; then
    ETC_DIR=var/mosquitto
    NEED_SUDO=""
else
    ETC_DIR=/etc/mosquitto
    NEED_SUDO=
    md5sum $ETC_DIR/certs/rsa_private.pem > /dev/null 2>&1 || NEED_SUDO=sudo
fi

CERT_DIR=$ETC_DIR/certs
CA_CERT=$CERT_DIR/ca.crt

AUTH_USER=scrumptious
AUTH_PASS=aardvark

CTRL_OPTS="-h ${MQTT_HOST:-localhost} -p ${MQTT_PORT:-8883} -u $AUTH_USER -P $AUTH_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"

MOSQUITTO_CTRL="$NEED_SUDO mosquitto_ctrl $CTRL_OPTS dynsec"
MOSQUITTO_SUB="$NEED_SUDO mosquitto_sub"
MOSQUITTO_PUB="$NEED_SUDO mosquitto_pub"

if [[ -n ${registry_id:-} ]]; then
    SERV_USER=rocket
    SERV_PASS=monkey
    SERV_ID=$registry_id/server
    SERVER_OPTS="-h ${MQTT_HOST:-localhost} -p ${MQTT_PORT:-8883} -i $SERV_ID -u $SERV_USER -P $SERV_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"
fi
