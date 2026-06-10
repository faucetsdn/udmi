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
        echo "UDMIS active. Executing initial registrar pass..."
        success=0
        for ((r=1; r<=3; r++)); do
            if bin/registrar site_model //mqtt/mosquitto; then
                success=1
                break
            fi
            echo "Registrar initial pass failed, retrying [$r/3] in 10s..."
            sleep 10
        done
        if [ "$success" -eq 0 ]; then
            echo "FATAL: Registrar failed all initial attempts. System indeterminate." >&2
            exit 1
        fi
        tail -f /dev/null
        exit 0
    fi

    if [ "$i" -lt "$MAX_ATTEMPTS" ]; then
        echo "udmis not started. Waiting $SLEEP_SECONDS seconds before next retry"
        sleep $SLEEP_SECONDS
    fi
done

echo Unable to run registrar, udmis not started 
tail -f /dev/null