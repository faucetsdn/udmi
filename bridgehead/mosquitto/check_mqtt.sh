#!/bin/sh

TOPIC="test/status"
MESSAGE="hello_world"
BROKER="mosquitto"
PORT="1883"

echo "--- Starting MQTT connectivity check ---"

mosquitto_sub -h $BROKER -p $PORT -t $TOPIC -C 1 > /tmp/received_message &
SUB_PID=$!

sleep 2

echo "Publishing '$MESSAGE' to topic '$TOPIC'..."
mosquitto_pub -h $BROKER -p $PORT -t $TOPIC -m $MESSAGE

wait $SUB_PID

RECEIVED=$(cat /tmp/received_message)

if [ "$RECEIVED" = "$MESSAGE" ]; then
    echo "✅ Success! Received message matches published message."
    exit 0
else
    echo "❌ Failure! Expected '$MESSAGE', but received '$RECEIVED'."
    exit 1
fi