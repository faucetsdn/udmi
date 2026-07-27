#!/bin/bash -e

# Test script for bin/pull_save to verify null vs empty deviceId handling

UDMI_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$UDMI_ROOT"

# Clean up any existing test files in out/registries
rm -rf out/registries/test-registry

echo "Running pull_save tests..."

# Case 1: deviceId is null in JSON
echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": null, "subFolder": "update", "subType": "events", "publishTime": "2026-07-16T12:00:00Z", "projectId": "test-project"}, "payload": {"foo": "bar"}}' | bin/pull_save

# Case 2: deviceId is "" (empty string) in JSON
echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "", "subFolder": "update", "subType": "events", "publishTime": "2026-07-16T12:00:00Z", "projectId": "test-project"}, "payload": {"foo": "bar"}}' | bin/pull_save

# Case 3: deviceId is "test-device" in JSON
echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "test-device", "subFolder": "update", "subType": "events", "publishTime": "2026-07-16T12:00:00Z", "projectId": "test-project"}, "payload": {"foo": "bar"}}' | bin/pull_save

echo ""
echo "Verifying generated files..."

# We expect directories for null, empty, and test-device
if [[ -d out/registries/test-registry/devices/null ]]; then
    echo "PASS: 'null' directory created."
else
    echo "FAIL: 'null' directory NOT created."
    exit 1
fi

if [[ -d out/registries/test-registry/devices/empty ]]; then
    echo "PASS: 'empty' directory created for empty string."
else
    echo "FAIL: 'empty' directory NOT created for empty string."
    exit 1
fi

if [[ -d out/registries/test-registry/devices/test-device ]]; then
    echo "PASS: 'test-device' directory created."
else
    echo "FAIL: 'test-device' directory NOT created."
    exit 1
fi

# Case 4: explicit target 'disk'
echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "test-disk", "subFolder": "update", "subType": "events", "publishTime": "2026-07-16T12:00:00Z", "projectId": "test-project"}, "payload": {"foo": "bar"}}' | bin/pull_save disk

if [[ -d out/registries/test-registry/devices/test-disk ]]; then
    echo "PASS: 'test-disk' directory created for explicit target 'disk'."
else
    echo "FAIL: 'test-disk' directory NOT created for explicit target 'disk'."
    exit 1
fi

# Case 5: 'disc' should fail (no polymorphism allowed)
if echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "test-disc"}, "payload": {"foo": "bar"}}' | bin/pull_save disc 2>/dev/null; then
    echo "FAIL: pull_save with target 'disc' should fail."
    exit 1
else
    echo "PASS: pull_save with target 'disc' failed as expected."
fi

# Case 6: 'postgres' should fail (no polymorphism allowed, only 'db')
if echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "test-pg"}, "payload": {"foo": "bar"}}' | bin/pull_save postgres 2>/dev/null; then
    echo "FAIL: pull_save with target 'postgres' should fail."
    exit 1
else
    echo "PASS: pull_save with target 'postgres' failed as expected."
fi

# Case 7: invalid target should fail
if echo '{"envelope": {"deviceRegistryId": "test-registry", "deviceId": "test-invalid"}, "payload": {"foo": "bar"}}' | bin/pull_save invalid_target 2>/dev/null; then
    echo "FAIL: pull_save with invalid target should fail."
    exit 1
else
    echo "PASS: pull_save with invalid target failed as expected."
fi

# Clean up
rm -rf out/registries/test-registry
echo "All tests passed successfully!"
