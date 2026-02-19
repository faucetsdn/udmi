#!/bin/bash -e
LOG_FILE="/usr/local/bin/udmis/udmis.log"
ACTIVATION_STRING="UdmiServicePod Finished activation of container components"
MAX_ATTEMPTS=9 
SLEEP_SECONDS=10

# Start sshd
ssh-keygen -A
mkdir -p /root/.ssh
cat /tmp/ssh_public_key/id_ed25519.pub >> /root/.ssh/authorized_keys
sed -i 's/#\?PermitRootLogin prohibit-password/PermitRootLogin yes/g' /etc/ssh/sshd_config 
sed -i 's/#\?PasswordAuthentication yes/PasswordAuthentication yes/g' /etc/ssh/sshd_config
/usr/sbin/sshd -D &

echo waiting for udmis...
sleep $SLEEP_SECONDS

for ((i=1; i<=$MAX_ATTEMPTS; i++)); do
    if [ -f "$LOG_FILE" ] && grep -q "$ACTIVATION_STRING" "$LOG_FILE"; then
        bin/registrar site_model //mqtt/mosquitto
        tail -f /dev/null
        exit 0
    fi

    if [ "$i" -lt "$MAX_ATTEMPTS" ]; then
        echo "udmis not started. Waiting $SLEEP_SECONDS seconds before next retry"
        sleep $SLEEP_SECONDS
    fi
done

echo Unable to run registrar, udmis not started 
exit 1