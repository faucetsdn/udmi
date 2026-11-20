#!/bin/bash
# Shared helpers for Bridgehead host wrapper scripts under bridgehead/bin/.
# Expects UDMI_ROOT, INVOCATION_DIR, and etc/shell_common.sh (fail) to be loaded.

require_bridgehead_ready() {
    local container
    for container in udmis validator; do
        if [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)" != "true" ]]; then
            fail "Container '$container' is not running. Start Bridgehead first with: bin/start_bridgehead [site_model_path]"
        fi
    done

    local started_at
    started_at=$(docker inspect -f '{{.State.StartedAt}}' udmis 2>/dev/null || true)
    if ! docker logs ${started_at:+--since "$started_at"} udmis 2>&1 | grep "udmis running in the background" >/dev/null; then
        fail "UDMIS container is running but not yet ready. Wait for startup to complete or check 'docker logs udmis'."
    fi
}

resolve_bridgehead_device() {
    local device_id="$1"

    if [[ -z "${SITE_MODEL_PATH:-}" ]]; then
        SITE_MODEL_PATH=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/root/site_model"}}{{.Source}}{{end}}{{end}}' validator 2>/dev/null || true)
    fi
    if [[ -z "${SITE_MODEL_PATH:-}" && -f "$UDMI_ROOT/bridgehead/.env" ]]; then
        SITE_MODEL_PATH=$(grep -E '^SITE_MODEL_PATH=' "$UDMI_ROOT/bridgehead/.env" | tail -n 1 | cut -d= -f2- || true)
    fi
    if [[ -z "${SITE_MODEL_PATH:-}" ]]; then
        SITE_MODEL_PATH="$UDMI_ROOT/sites/udmi_site_model"
    fi
    if [[ "$SITE_MODEL_PATH" != /* ]]; then
        SITE_MODEL_PATH="$INVOCATION_DIR/$SITE_MODEL_PATH"
    fi
    SITE_MODEL_PATH=$(realpath -m "$SITE_MODEL_PATH")

    if [[ ! -d "$SITE_MODEL_PATH/devices/$device_id" ]]; then
        fail "Device '$device_id' not found in site model directory '$SITE_MODEL_PATH/devices/$device_id'."
    fi

    GATEWAY_ID=$(jq -r '.gateway.gateway_id // empty' "$SITE_MODEL_PATH/devices/$device_id/metadata.json" 2>/dev/null || true)
    TARGET_ID="${GATEWAY_ID:-$device_id}"
    if [[ ! -d "$SITE_MODEL_PATH/devices/$TARGET_ID" ]]; then
        fail "Target gateway '$TARGET_ID' for device '$device_id' not found in '$SITE_MODEL_PATH/devices/$TARGET_ID'."
    fi
}

fix_bridgehead_permissions() {
    docker exec validator chown -RH "$(id -u):$(id -g)" /root/site_model/ 2>/dev/null || true
}
