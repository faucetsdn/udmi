#!/bin/bash -e
mkdir -p /root/.ssh 
ssh-keygen -t ed25519 -N "" -f /root/.ssh/id_ed25519 && chmod 600 /root/.ssh/id_ed25519
echo starting up tomcat server
exec /usr/local/tomcat/bin/catalina.sh run > /tmp/tomcat.log 2>&1 &

UDMIS_LOG=/tmp/udmis.log
rm -rf $UDMIS_LOG

UDMI_ROOT=/root/udmi
source $UDMI_ROOT/etc/shell_common.sh
site_model=$(realpath /root/site_model)
site_config=$(realpath $site_model/cloud_iot_config.json)
project_spec=//mqtt/mosquitto

cd $UDMI_ROOT
mkdir -p out

echo Starting local services at $(sudo date -u -Is) | tee $UDMIS_LOG

iot_provider=$(jq -r .iot_provider $site_config)
if [[ -n ${project_spec:-} ]]; then
    project_target=${project_spec##*/}+
    iot_provider=${project_spec%/*}
    iot_provider=${iot_provider#//}
fi

registry_id=$(jq -r .registry_id $site_config)

echo Starting udmis proper... | tee -a $UDMIS_LOG


OLD_PID=$(ps ax | fgrep java | fgrep local_pod.json | awk '{print $1}') || true
if [[ -n $OLD_PID ]]; then
    echo Killing old udmis process $OLD_PID
    sudo kill $OLD_PID
    sleep 2
fi

POD_READY=/tmp/pod_ready.txt
rm -f $POD_READY

LOGFILE=/tmp/udmis.log
date > $LOGFILE

export SSL_SECRETS_DIR=/etc/mosquitto/certs

UDMIS_DIR=udmis
[[ -d $UDMIS_DIR ]] || UDMIS_DIR=..


jq --arg u "$SERV_USER" --arg p "$SERV_PASS" --arg host_var "$MQTT_HOST" \
'.flow_defaults.auth_provider.basic.username = $u | .flow_defaults.auth_provider.basic.password = $p | .flow_defaults.hostname=$host_var' \
/root/var/local_pod.json > /root/var/local_pod.tmp && mv /root/var/local_pod.tmp /root/var/local_pod.json

$UDMIS_DIR/bin/run /root/var/local_pod.json >> $LOGFILE 2>&1 &

PID=$!

WAITING=30
for i in `seq 1 $WAITING`; do
    if [[ -f $POD_READY || ! -d /proc/$PID ]]; then
        break
    fi
    echo Waiting for udmis startup $((WAITING - i))...
    sleep 1
done

echo ::::::::: tail $LOGFILE
tail -n 30 $LOGFILE

[[ -f $POD_READY ]] || fail pod_ready.txt not found.

echo udmis running in the background, pid $PID log in $(realpath $LOGFILE)

echo starting up telegraf

telegraf --config /usr/local/bin/startup/telegraf.conf > /tmp/telegraf.log 2>&1 &

(echo Blocking until termination. && tail -f /dev/null)