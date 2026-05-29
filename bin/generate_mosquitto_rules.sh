#!/bin/sh
#
# Generate Mosquitto dynamic security rules from a UDMI site model.
# Replicates the logic in MosquittoBroker.java.
# POSIX sh compliant. Clean format, no headers or comments.

set -eu

# Check dependencies
if ! command -v jq > /dev/null 2>&1; then
    echo "Error: jq is required but not installed." >&2
    exit 1
fi

if [ $# -lt 1 ]; then
    echo "Usage: $0 <site_model_path> [registry_id]" >&2
    exit 1
fi

site_path="$1"
if [ ! -d "$site_path" ]; then
    echo "Error: Site path '$site_path' does not exist or is not a directory." >&2
    exit 1
fi

# Resolve devices directory
DEVICES_DIR=""
if [ -d "$site_path/devices" ]; then
    DEVICES_DIR="$site_path/devices"
elif [ -d "$site_path/udmi/devices" ]; then
    DEVICES_DIR="$site_path/udmi/devices"
else
    echo "Error: Could not find devices directory in $site_path" >&2
    exit 1
fi

# Resolve registry ID (site name)
registry_id=""
if [ -n "${2:-}" ]; then
    registry_id="$2"
else
    # Try cloud_iot_config.json
    if [ -f "$site_path/cloud_iot_config.json" ]; then
        registry_id=$(jq -r .registry_id "$site_path/cloud_iot_config.json" 2>/dev/null || true)
    fi
    # Try first metadata.json
    if [ -z "${registry_id}" ] || [ "$registry_id" = "null" ]; then
        first_metadata=""
        for f in "$DEVICES_DIR"/*/metadata.json; do
            if [ -f "$f" ]; then
                first_metadata="$f"
                break
            fi
        done
        if [ -n "$first_metadata" ]; then
            registry_id=$(jq -r '.system.location.site // .system.physical_tag.asset.site // empty' "$first_metadata" 2>/dev/null || true)
        fi
    fi
    # Fallback to directory name
    if [ -z "${registry_id}" ] || [ "$registry_id" = "null" ]; then
        registry_id=$(basename "$site_path")
        registry_id=$(echo "$registry_id" | xargs) # trim spaces
    fi
fi

if [ -z "${registry_id}" ]; then
    echo "Error: Could not resolve registry_id (site name)." >&2
    exit 1
fi

# Start generating the output script
cat << 'EOF'
#!/bin/sh
# Generated script to configure Mosquitto dynamic security rules.
# Timing and execution logging enabled. POSIX sh compliant.

set -u

# Default MOSQUITTO_CTRL command. Can be overridden by env var.
if [ -z "${MOSQUITTO_CTRL:-}" ]; then
    MOSQUITTO_CTRL="mosquitto_ctrl -h localhost -p 8883 --cafile /etc/mosquitto/certs/ca.crt --cert /etc/mosquitto/certs/server.crt --key /etc/mosquitto/certs/server.key --insecure dynsec"
fi

execute_and_time() {
    _sub_cmd="$*"
    _start_time=$(date +%s%N)
    
    eval "$MOSQUITTO_CTRL" '"$@"'
    _exit_code=$?
    
    _end_time=$(date +%s%N)
    
    # Calculate duration
    _duration=0
    case "$_end_time" in
        *[!0-9]*) _is_num=0 ;;
        *) [ -n "$_end_time" ] && _is_num=1 || _is_num=0 ;;
    esac
    
    case "$_start_time" in
        *[!0-9]*) _is_num_start=0 ;;
        *) [ -n "$_start_time" ] && _is_num_start=1 || _is_num_start=0 ;;
    esac
    
    if [ $_is_num -eq 1 ] && [ $_is_num_start -eq 1 ]; then
        _duration=$(( (_end_time - _start_time) / 1000000 ))
    fi
    
    echo "$_sub_cmd: $_exit_code (${_duration}ms)"
}
EOF

# Loop 1: Non-proxied devices (direct or gateway)
for metadata_file in "$DEVICES_DIR"/*/metadata.json; do
    [ -e "$metadata_file" ] || continue
    device_id=$(basename "$(dirname "$metadata_file")")
    
    # Check if proxied
    if ! gateway_id=$(jq -r '.gateway.gateway_id // empty' "$metadata_file" 2>/dev/null); then
        echo "Warning: Failed to parse metadata for device $device_id, skipping." >&2
        continue
    fi
    
    if [ -z "$gateway_id" ]; then
        # Non-proxied
        client_id="/r/$registry_id/d/$device_id"
        role_name="role__r_${registry_id}_d_${device_id}"
        
        cat << EOF
execute_and_time createClient "$client_id" -p unused -c "$client_id"
execute_and_time createRole "$role_name"
execute_and_time addClientRole "$client_id" "$role_name"
execute_and_time addRoleACL "$role_name" subscribePattern "$client_id/config" allow
execute_and_time addRoleACL "$role_name" subscribePattern "$client_id/commands" allow
execute_and_time addRoleACL "$role_name" subscribePattern "$client_id/errors" allow
execute_and_time addRoleACL "$role_name" publishClientSend "$client_id/events/#" allow
execute_and_time addRoleACL "$role_name" publishClientSend "$client_id/state" allow
EOF
    fi
done

# Loop 2: Proxied devices
for metadata_file in "$DEVICES_DIR"/*/metadata.json; do
    [ -e "$metadata_file" ] || continue
    device_id=$(basename "$(dirname "$metadata_file")")
    
    # Check if proxied
    if ! gateway_id=$(jq -r '.gateway.gateway_id // empty' "$metadata_file" 2>/dev/null); then
        echo "Warning: Failed to parse metadata for device $device_id, skipping." >&2
        continue
    fi
    
    if [ -n "$gateway_id" ]; then
        # Proxied
        client_id="/r/$registry_id/d/$device_id"
        gateway_role_name="role__r_${registry_id}_d_${gateway_id}"
        
        cat << EOF
execute_and_time addRoleACL "$gateway_role_name" subscribePattern "$client_id/config" allow
execute_and_time addRoleACL "$gateway_role_name" subscribePattern "$client_id/commands" allow
execute_and_time addRoleACL "$gateway_role_name" subscribePattern "$client_id/errors" allow
execute_and_time addRoleACL "$gateway_role_name" publishClientSend "$client_id/events/#" allow
execute_and_time addRoleACL "$gateway_role_name" publishClientSend "$client_id/state" allow
execute_and_time addRoleACL "$gateway_role_name" publishClientSend "$client_id/attach" allow
EOF
    fi
done
