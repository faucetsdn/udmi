# Common setup for running mosquitto_ctrl

ETC_DIR=/etc/mosquitto
CERT_DIR=$ETC_DIR/certs
CA_CERT=$CERT_DIR/ca.crt

AUTH_USER=scrumptious
AUTH_PASS=aardvark

CTRL_OPTS="-h ${MQTT_HOST:-localhost} -p ${MQTT_PORT:-8883} -u $AUTH_USER -P $AUTH_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"

NEED_SUDO=
md5sum $CERT_DIR/rsa_private.pem > /dev/null 2>&1 || NEED_SUDO=sudo

MOSQUITTO_CTRL="$NEED_SUDO mosquitto_ctrl $CTRL_OPTS dynsec"
MOSQUITTO_SUB="$NEED_SUDO mosquitto_sub"
MOSQUITTO_PUB="$NEED_SUDO mosquitto_pub"

if [[ -n ${registry_id:-} ]]; then
    SERV_USER=rocket
    SERV_PASS=monkey
    SERV_ID=$registry_id/server
    SERVER_OPTS="-i $SERV_ID -u $SERV_USER -P $SERV_PASS --cafile $CA_CERT --cert $CERT_DIR/rsa_private.crt --key $CERT_DIR/rsa_private.pem"
fi
