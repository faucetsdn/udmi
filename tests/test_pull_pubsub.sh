#!/bin/bash -e

# Test script for bin/pull_pubsub to verify PubSub payload parsing and null/empty handling

UDMI_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$UDMI_ROOT"

TMP_BIN=$(mktemp -d)
trap 'rm -rf "$TMP_BIN"' EXIT

# Create a mock gcloud in the temporary directory
cat <<'EOF' > "$TMP_BIN/gcloud"
#!/bin/bash
# Simulated gcloud output

# Case 1: Standard deviceId "test-device"
# Case 2: Empty string deviceId ""
# Case 3: null deviceId
echo '[
  {
    "message": {
      "attributes": {
        "deviceId": "test-device",
        "deviceRegistryId": "test-registry",
        "subFolder": "update",
        "subType": "events"
      },
      "publishTime": "2026-07-16T12:00:00Z",
      "data": "eyJmb28iOiJiYXIifQ=="
    }
  },
  {
    "message": {
      "attributes": {
        "deviceId": "",
        "deviceRegistryId": "test-registry",
        "subFolder": "update",
        "subType": "events"
      },
      "publishTime": "2026-07-16T12:00:00Z",
      "data": "eyJmb28iOiJiYXIifQ=="
    }
  },
  {
    "message": {
      "attributes": {
        "deviceId": null,
        "deviceRegistryId": "test-registry",
        "subFolder": "update",
        "subType": "events"
      },
      "publishTime": "2026-07-16T12:00:00Z",
      "data": "eyJmb28iOiJiYXIifQ=="
    }
  }
]'
EOF
chmod +x "$TMP_BIN/gcloud"

# Prepend TMP_BIN to PATH so pull_pubsub executes our mock gcloud
export PATH="$TMP_BIN:$PATH"

echo "Running pull_pubsub tests..."
output_file=$(mktemp)
bin/pull_pubsub //pubsub/test-project/test-namespace+test-suffix 2>/dev/null | head -n 3 > "$output_file"

echo "Verifying stdout output lines..."
output=$(cat "$output_file")
rm -f "$output_file"

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

# Verify deviceId in line 2 is "empty" (since it was empty in attributes)
id2=$(echo "$line2" | jq -r '.envelope.deviceId')
if [[ $id2 == "empty" ]]; then
    echo "PASS: Line 2 deviceId is 'empty'"
else
    echo "FAIL: Line 2 deviceId is '$id2' (expected 'empty')"
    exit 1
fi

# Verify deviceId in line 3 is "null" (since it was null in attributes)
id3=$(echo "$line3" | jq -r '.envelope.deviceId')
if [[ $id3 == "null" ]]; then
    echo "PASS: Line 3 deviceId is 'null'"
else
    echo "FAIL: Line 3 deviceId is '$id3' (expected 'null')"
    exit 1
fi

echo "All pull_pubsub tests passed successfully!"
