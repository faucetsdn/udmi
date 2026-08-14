#!/bin/bash -e

# Test script for bin/pull_mqtt to verify topic parsing and null/empty handling

UDMI_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$UDMI_ROOT"

TMP_BIN=$(mktemp -d)
trap 'rm -rf "$TMP_BIN"' EXIT

# Create a mock mosquitto_sub in the temporary directory
cat <<'EOF' > "$TMP_BIN/mosquitto_sub"
#!/bin/bash
# Output simulated MQTT JSON lines

# Case 1: Standard UDMI topic with deviceId "test-device"
echo '{"topic": "/r/test-registry/d/test-device/events/update", "payload": "{\"foo\":\"bar\"}", "tst": "2026-07-16T12:00:00Z"}'

# Case 2: UDMI topic with empty deviceId (double slash)
echo '{"topic": "/r/test-registry/d//events/update", "payload": "{\"foo\":\"bar\"}", "tst": "2026-07-16T12:00:00Z"}'

# Case 3: UDMI topic with literal "null" deviceId
echo '{"topic": "/r/test-registry/d/null/events/update", "payload": "{\"foo\":\"bar\"}", "tst": "2026-07-16T12:00:00Z"}'
EOF
chmod +x "$TMP_BIN/mosquitto_sub"

# Prepend TMP_BIN to PATH so pull_mqtt executes our mock mosquitto_sub
export PATH="$TMP_BIN:$PATH"

echo "Running pull_mqtt tests..."
# We pass a fake spec so it runs
output=$(bin/pull_mqtt //mqtt/localhost%test-registry 2>/dev/null || true)

echo "Verifying stdout output lines..."

line1=$(echo "$output" | sed -n '1p')
line2=$(echo "$output" | sed -n '2p')
line3=$(echo "$output" | sed -n '3p')

echo "Line 1: $line1"
echo "Line 2: $line2"
echo "Line 3: $line3"

# Verify deviceId in line 1 is "test-device"
id1=$(echo "$line1" | jq -r '.envelope.deviceId')
if [[ $id1 == "test-device" ]]; then
    echo "PASS: Line 1 deviceId is 'test-device'"
else
    echo "FAIL: Line 1 deviceId is '$id1' (expected 'test-device')"
    exit 1
fi

# Verify deviceId in line 2 is "empty" (since it was empty in topic)
id2=$(echo "$line2" | jq -r '.envelope.deviceId')
if [[ $id2 == "empty" ]]; then
    echo "PASS: Line 2 deviceId is 'empty'"
else
    echo "FAIL: Line 2 deviceId is '$id2' (expected 'empty')"
    exit 1
fi

# Verify deviceId in line 3 is "null" (since it was null in topic)
id3=$(echo "$line3" | jq -r '.envelope.deviceId')
if [[ $id3 == "null" ]]; then
    echo "PASS: Line 3 deviceId is 'null'"
else
    echo "FAIL: Line 3 deviceId is '$id3' (expected 'null')"
    exit 1
fi

echo "All pull_mqtt tests passed successfully!"
