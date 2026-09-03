#!/bin/bash
# Shared helper functions for UDMI tmux_* management scripts

UDMI_ROOT=$(dirname $(realpath ${BASH_SOURCE[0]}))/..
source $UDMI_ROOT/etc/shell_common.sh

function derive_port_from_namespace {
    local ns="$1"
    python3 -c "import hashlib; print(20000 + (int(hashlib.sha256(b'$ns').hexdigest(), 16) % 3500) * 10)" 2>/dev/null || {
        local hex=$(printf '%s' "$ns" | md5sum | awk '{print $1}')
        local slot=$(( 16#${hex: -6} % 3500 ))
        echo $(( 20000 + slot * 10 ))
    }
}

# Global parsed arguments
TMUX_ACTION=""
TMUX_SITE_MODEL="sites/udmi_site_model"
TMUX_PROJECT_SPEC="//mqtt/localhost:${MQTT_PORT:-8883}"
TMUX_TARGET_ID=""
TMUX_ONLY_LIST=()
TMUX_EXCLUDE_LIST=()
TMUX_OPTIONAL_LIST=()
TMUX_EXTRA_ARGS=()

TMUX_NAMESPACE=""

parse_tmux_args() {
    TMUX_ACTION=""
    TMUX_SITE_MODEL="sites/udmi_site_model"
    TMUX_PROJECT_SPEC=""
    TMUX_TARGET_ID=""
    TMUX_NAMESPACE="${UDMI_NAMESPACE:-}"
    TMUX_ONLY_LIST=()
    TMUX_EXCLUDE_LIST=()
    TMUX_OPTIONAL_LIST=()
    TMUX_EXTRA_ARGS=()

    local positional_args=()

    while [[ $# -gt 0 ]]; do
        local arg="$1"
        shift

        case "$arg" in
            start|stop|restart|clean|status|logs|attach|help)
                TMUX_ACTION="$arg"
                ;;
            -h|--help)
                TMUX_ACTION="help"
                ;;
            \+\+*)
                # Optional add-in modifier: ++service
                local svc="${arg#\+\+}"
                TMUX_OPTIONAL_LIST+=("$svc")
                ;;
            \+*)
                # Inclusion modifier: +service
                local svc="${arg#\+}"
                TMUX_ONLY_LIST+=("$svc")
                ;;
            \!*)
                # Exclusion modifier: !service
                local svc="${arg#\!}"
                TMUX_EXCLUDE_LIST+=("$svc")
                ;;
            *)
                positional_args+=("$arg")
                ;;
        esac
    done

    if [[ "$TMUX_ACTION" == "logs" || "$TMUX_ACTION" == "attach" ]]; then
        if [[ ${#positional_args[@]} -gt 0 ]]; then
            local first_arg="${positional_args[0]}"
            if [[ "$first_arg" =~ ^~ ]]; then
                echo "ERROR: Invalid target '$first_arg'. Leading tilde syntax is not supported; use a namespace (e.g. '${first_arg:1}') or 'session~${first_arg:1}'." >&2
                exit 1
            fi
            if [[ "$TMUX_ACTION" == "logs" && "$first_arg" =~ ^[0-9]+$ && ${#positional_args[@]} -eq 1 ]]; then
                TMUX_TARGET_ID=""
                TMUX_EXTRA_ARGS=("$first_arg")
            else
                TMUX_EXTRA_ARGS=("${positional_args[@]:1}")
                if [[ "$first_arg" =~ ^[0-9]+$ ]]; then
                    TMUX_TARGET_ID=""
                    TMUX_EXTRA_ARGS=("$first_arg")
                elif [[ "$first_arg" =~ ^: ]]; then
                    # Leading colon (:window) targets a window in the active/default session
                    TMUX_TARGET_ID="${first_arg#:}"
                elif [[ "$first_arg" == *:* ]]; then
                    # namespace:window or session~namespace:window
                    local target_ns="${first_arg%%:*}"
                    local target_win="${first_arg#*:}"
                    if [[ "$target_ns" == *~* ]]; then
                        local sess="${target_ns%%~*}"
                        TMUX_NAMESPACE="${target_ns#*~}"
                        if [[ -n "$SESSION_NAME" && "$SESSION_NAME" != "$sess" && "$SESSION_NAME" != "udmi_${sess}" ]]; then
                            SESSION_NAME="$sess"
                        fi
                    else
                        TMUX_NAMESPACE="$target_ns"
                    fi
                    TMUX_TARGET_ID="$target_win"
                elif [[ "$first_arg" == *~* ]]; then
                    # Explicit session~namespace format
                    local sess="${first_arg%%~*}"
                    TMUX_NAMESPACE="${first_arg#*~}"
                    TMUX_TARGET_ID=""
                    if [[ -n "$SESSION_NAME" && "$SESSION_NAME" != "$sess" && "$SESSION_NAME" != "udmi_${sess}" ]]; then
                        SESSION_NAME="$sess"
                    fi
                else
                    # Standalone name always matches a namespace
                    TMUX_NAMESPACE="$first_arg"
                    TMUX_TARGET_ID=""
                fi
            fi
        fi
    else
        case ${#positional_args[@]} in
            0)
                # No target provided: default to namespace 'default'
                TMUX_NAMESPACE="default"
                TMUX_SITE_MODEL="$UDMI_ROOT/sites/udmi~default"
                local port=$(derive_port_from_namespace "default")
                TMUX_PROJECT_SPEC="//mqtt/localhost:${port}"
                ;;
            1)
                local arg="${positional_args[0]}"
                if [[ -d "$arg" || -f "$arg/cloud_iot_config.json" || ( "$arg" == sites/* && -d "$arg" ) ]]; then
                    echo "ERROR: An explicit site model ('$arg') requires an explicit project spec (e.g. //mqtt/localhost:46432)." >&2
                    exit 1
                elif [[ -f "$arg" ]]; then
                    TMUX_SITE_MODEL=$(dirname "$arg")
                    TMUX_PROJECT_SPEC="$arg"
                    TMUX_NAMESPACE=""
                elif [[ "$arg" =~ ^// || "$arg" =~ ^(mqtt|mqtts|ssl):// || "$arg" =~ localhost: || "$arg" =~ :[0-9]+ ]]; then
                    TMUX_SITE_MODEL="sites/udmi_site_model"
                    TMUX_PROJECT_SPEC=$(normalize_conn_spec "$arg")
                    TMUX_NAMESPACE=""
                elif [[ "$arg" =~ ^[a-zA-Z0-9_-]+$ ]]; then
                    TMUX_NAMESPACE="$arg"
                    TMUX_SITE_MODEL="$UDMI_ROOT/sites/udmi~${arg}"
                    local port=$(derive_port_from_namespace "$arg")
                    TMUX_PROJECT_SPEC="//mqtt/localhost:${port}"
                else
                    echo "ERROR: Invalid target '$arg'. Expected a namespace, connection spec (//mqtt/localhost:...), or config file." >&2
                    exit 1
                fi
                ;;
            2)
                local arg1="${positional_args[0]}"
                local arg2="${positional_args[1]}"
                if [[ -d "$arg1" || -f "$arg1/cloud_iot_config.json" || ( "$arg1" == sites/* && -d "$arg1" ) ]]; then
                    TMUX_SITE_MODEL="$arg1"
                    if [[ -f "$arg2" ]]; then
                        TMUX_PROJECT_SPEC="$arg2"
                        TMUX_NAMESPACE=""
                    elif [[ "$arg2" =~ ^// || "$arg2" =~ ^(mqtt|mqtts|ssl):// || "$arg2" =~ localhost: || "$arg2" =~ :[0-9]+ ]]; then
                        TMUX_PROJECT_SPEC=$(normalize_conn_spec "$arg2")
                        TMUX_NAMESPACE=""
                    elif [[ "$arg2" =~ ^[a-zA-Z0-9_-]+$ ]]; then
                        echo "ERROR: Cannot combine explicit site model ('$arg1') with namespace ('$arg2'). Specify an explicit connection spec (e.g. //mqtt/localhost:46432) or use a solo namespace." >&2
                        exit 1
                    else
                        echo "ERROR: Invalid project spec '$arg2' for site model '$arg1'." >&2
                        exit 1
                    fi
                else
                    echo "ERROR: Unexpected argument pair: '$arg1' '$arg2'. Expected [site_model_dir] [project_spec]." >&2
                    exit 1
                fi
                ;;
            *)
                echo "ERROR: Too many positional arguments: ${positional_args[*]}" >&2
                exit 1
                ;;
        esac
    fi

    if [[ -d "$TMUX_SITE_MODEL" && -f "$TMUX_SITE_MODEL/cloud_iot_config.json" ]]; then
        TMUX_SITE_MODEL=$(realpath "$TMUX_SITE_MODEL")
    fi

    if [[ -n "$TMUX_PROJECT_SPEC" ]]; then
        if [[ $TMUX_PROJECT_SPEC =~ localhost:([0-9]+) ]]; then
            export MQTT_PORT="${BASH_REMATCH[1]}"
            if [[ $MQTT_PORT != 8883 ]]; then
                export ETCD_PORT=$((MQTT_PORT + 1))
                export INFLUX_PORT=$((MQTT_PORT + 2))
                export POSTGRES_PORT=$((MQTT_PORT + 3))
            fi
        fi
    fi

    if [[ -n "${TMUX_NAMESPACE:-}" && -n "${SESSION_NAME:-}" && ! "$SESSION_NAME" =~ ~ ]]; then
        SESSION_NAME="${SESSION_NAME}~${TMUX_NAMESPACE}"
    fi
}

tmux_validate_services() {
    local box_name="$1"
    shift
    local allowed_services=("$@")

    local all_requested=("${TMUX_ONLY_LIST[@]}" "${TMUX_EXCLUDE_LIST[@]}" "${TMUX_OPTIONAL_LIST[@]}")
    for req in "${all_requested[@]}"; do
        local matched=false
        for allowed in "${allowed_services[@]}"; do
            if [[ "$req" == "$allowed" ]]; then
                matched=true
                break
            fi
        done
        if [[ "$matched" == false ]]; then
            echo "ERROR: Unrecognized service '$req' for $box_name." >&2
            echo "Allowed canonical services for $box_name: [${allowed_services[*]}]" >&2
            exit 1
        fi
    done
}

tmux_show_help() {
    local box_name="$1"
    shift
    local allowed_services=("$@")
    local script_name="$(basename "$0")"

    echo "UDMI $box_name Controller ($script_name)"
    echo ""
    echo "Usage: $script_name <command> [site_model] [project_spec] [target_id] [+only | !exclude | ++optional]"
    echo ""
    echo "Available commands:"
    echo "  start   : Start all enabled services in this tmux session (preserves existing state)"
    echo "  stop    : Stop all running services and terminate this tmux session (preserves state for diagnosis)"
    echo "  clean   : Purge runtime state, cached certificates, databases, and artifacts"
    echo "  restart : Sequence { stop, clean, start } (clean-slate fresh restart)"
    echo "  status  : Show status of the tmux session and diagnostic probes for all services"
    echo "  logs    : View logs from tmux windows (usage: $script_name logs [window_name] [num_lines])"
    echo "  attach  : Attach to the tmux session (usage: $script_name attach [window_name])"
    echo "  help    : Show this help message"
    echo ""
    echo "Services managed by $script_name ($box_name):"
    echo "  ${allowed_services[*]}"
    echo ""
    echo "Service filters:"
    echo "  +service   : Include only the specified service"
    echo "  !service   : Exclude the specified service"
    echo "  ++service  : Enable an optional service"
}


tmux_should_run_service() {
    local service="$1"
    local is_optional_default="${2:-false}"

    # 1. Check explicit exclusion: !service
    for excluded in "${TMUX_EXCLUDE_LIST[@]}"; do
        if [[ "$excluded" == "$service" ]]; then
            return 1
        fi
    done

    # 2. Check explicit inclusion: +service
    if [[ ${#TMUX_ONLY_LIST[@]} -gt 0 ]]; then
        for included in "${TMUX_ONLY_LIST[@]}"; do
            if [[ "$included" == "$service" ]]; then
                return 0
            fi
        done
        return 1
    fi

    # 3. If optional by default, check if ++service was passed
    if [[ "$is_optional_default" == "true" ]]; then
        for opt in "${TMUX_OPTIONAL_LIST[@]}"; do
            if [[ "$opt" == "$service" ]]; then
                return 0
            fi
        done
        return 1
    fi

    # 4. Default: run service
    return 0
}

tmux_session_exists() {
    local session_name="$1"
    tmux has-session -t "$session_name" 2>/dev/null
}

tmux_init_or_add_window() {
    local session_name="$1"
    local window_name="$2"
    local cmd="$3"

    local env_exports="export UDMI_ROOT='$UDMI_ROOT' MQTT_PORT='${MQTT_PORT:-8883}' ETCD_PORT='${ETCD_PORT:-2379}' INFLUX_PORT='${INFLUX_PORT:-8086}' POSTGRES_PORT='${POSTGRES_PORT:-5432}' TARGET_PROJECT='${TARGET_PROJECT:-}'"
    local full_cmd="$env_exports; $cmd; ec=\$?; echo \"=== [$window_name] exited with code \$ec ===\"; while true; do read -r _ 2>/dev/null || sleep 3600; done"

    if ! tmux_session_exists "$session_name"; then
        echo "Creating tmux session '$session_name' [window: $window_name]..."
        tmux new-session -d -s "$session_name" -n "$window_name" "bash -c \"$full_cmd\""
        tmux set-option -t "$session_name" set-remain-on-exit on 2>/dev/null || true
        tmux set-window-option -t "$session_name" remain-on-exit on 2>/dev/null || true
    else
        echo "Adding window '$window_name' to session '$session_name'..."
        tmux new-window -t "$session_name:" -n "$window_name" "bash -c \"$full_cmd\""
        tmux set-window-option -t "$session_name:$window_name" remain-on-exit on 2>/dev/null || true
    fi
}

tmux_stop_session() {
    local session_name="$1"
    local stopped=false
    if tmux_session_exists "$session_name"; then
        echo "Terminating tmux session '$session_name'..."
        tmux kill-session -t "$session_name" 2>/dev/null || true
        echo "Session '$session_name' stopped."
        stopped=true
    fi
    if [[ "$session_name" =~ ~default$ ]]; then
        local base_sess="${session_name%~default}"
        if tmux_session_exists "$base_sess"; then
            echo "Terminating tmux session '$base_sess'..."
            tmux kill-session -t "$base_sess" 2>/dev/null || true
            echo "Session '$base_sess' stopped."
            stopped=true
        fi
    fi
    if [[ "$stopped" == false ]]; then
        echo "Session '$session_name' is not running."
    fi
}

tmux_probe_service() {
    local svc="$1"
    local site_model="${2:-${TMUX_SITE_MODEL:-sites/udmi_site_model}}"
    local project_spec="${3:-${TMUX_PROJECT_SPEC:-//mqtt/localhost:${MQTT_PORT:-8883}}}"

    case "$svc" in
        etcd)
            local port="${ETCD_PORT:-2379}"
            local pid_file="var/etcd/etcd.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null && (timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" 2>/dev/null || curl -sf "http://127.0.0.1:$port/health" >/dev/null 2>&1); then
                echo "RUNNING (PID $pid, port $port)"
            elif [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "STARTING (PID $pid, port $port not responding)"
            else
                echo "STOPPED"
            fi
            ;;
        mosquitto)
            local port="${MQTT_PORT:-8883}"
            local pid_file="${MOSQUITTO_ETC_DIR:-var/mosquitto}/mosquitto.pid"
            [[ -f "$pid_file" ]] || pid_file="/var/mosquitto/mosquitto.pid"
            [[ -f "$pid_file" ]] || pid_file="/etc/mosquitto/mosquitto.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" 2>/dev/null; then
                echo "RUNNING (port $port${pid:+, PID $pid})"
            elif [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "STARTING (PID $pid, port $port not responding)"
            else
                echo "STOPPED"
            fi
            ;;
        udmis)
            local pid_file="var/udmis.pid"
            local pod_ready="var/pod_ready.txt"
            [[ -f "$pod_ready" ]] || pod_ready="/tmp/pod_ready.txt"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                if [[ -f "$pod_ready" ]]; then
                    echo "RUNNING (PID $pid, pod_ready sentinel present)"
                else
                    echo "INITIALIZING (PID $pid, waiting for pod_ready)"
                fi
            else
                echo "STOPPED"
            fi
            ;;
        pubsub)
            local pid_file="out/pubsub.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "RUNNING (PID $pid)"
            else
                echo "STOPPED"
            fi
            ;;
        postgres)
            local port="${POSTGRES_PORT:-5432}"
            local pid_file="var/postgresql/postgresql.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" 2>/dev/null; then
                echo "RUNNING (port $port${pid:+, PID $pid})"
            elif [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "STARTING (PID $pid, port $port not responding)"
            else
                echo "STOPPED"
            fi
            ;;
        influxdb)
            local port="${INFLUX_PORT:-8086}"
            local pid_file="var/influx/influxd.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if timeout 1 bash -c "</dev/tcp/127.0.0.1/$port" 2>/dev/null || curl -sf "http://127.0.0.1:$port/health" >/dev/null 2>&1; then
                echo "RUNNING (port $port${pid:+, PID $pid})"
            elif [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "STARTING (PID $pid, port $port not responding)"
            else
                echo "STOPPED"
            fi
            ;;
        butler)
            local pid_file="var/butler.pid"
            local pid=$(cat "$pid_file" 2>/dev/null || true)
            if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                echo "RUNNING (PID $pid)"
            else
                echo "STOPPED"
            fi
            ;;
        validator)
            local pids=$(pgrep -f "com.google.daq.mqtt.validator.ValidatorRunner|com.google.bos.udmi.service.core.Validator" 2>/dev/null || true)
            if [[ -n "$pids" ]]; then
                echo "RUNNING (PID $(echo $pids | tr '\n' ' '))"
            else
                echo "STOPPED"
            fi
            ;;
        registrar)
            local pids=$(pgrep -f "registrar.*Registrar" 2>/dev/null || true)
            local summary="${site_model}/out/registration_summary.json"
            if [[ -n "$pids" ]]; then
                echo "RUNNING (PID $(echo $pids | tr '\n' ' '))"
            elif [[ -f "$summary" ]]; then
                echo "COMPLETED (registered in $site_model)"
            else
                echo "STOPPED"
            fi
            ;;
        certs)
            local ca_cert="${site_model}/reflector/ca.crt"
            local client_key="${site_model}/reflector/rsa_private.pem"
            if [[ -f "$ca_cert" && -f "$client_key" ]]; then
                echo "READY (CA & reflector keys present in $site_model)"
            else
                echo "NOT CONFIGURED (missing certs in $site_model)"
            fi
            ;;
        clone)
            if [[ -d "$site_model" && -f "$site_model/cloud_iot_config.json" ]]; then
                echo "READY ($site_model configured)"
            else
                echo "NOT CLONED (missing $site_model)"
            fi
            ;;
        pubber)
            local pids=$(pgrep -f "pubber.*jar|pubber\.serialNo|validator\.pubber\.Pubber|com\.google\.bos\.udmi\.service\.core\.Pubber" 2>/dev/null || true)
            if [[ -n "$pids" ]]; then
                echo "RUNNING (PID $(echo $pids | tr '\n' ' '))"
            else
                echo "STOPPED"
            fi
            ;;
        spotter)
            local pids=$(pgrep -f "spotter" 2>/dev/null || true)
            if [[ -n "$pids" ]]; then
                echo "RUNNING (PID $(echo $pids | tr '\n' ' '))"
            else
                echo "STOPPED"
            fi
            ;;
        server)
            local pids=$(pgrep -f "python3.*mcp/server.py" 2>/dev/null || true)
            if [[ -n "$pids" ]]; then
                echo "RUNNING (PID $(echo $pids | tr '\n' ' '))"
            else
                echo "STOPPED"
            fi
            ;;
        *)
            echo "UNKNOWN"
            ;;
    esac
}

tmux_session_status() {
    local session_name="$1"
    shift
    local services=("$@")

    if tmux_session_exists "$session_name"; then
        echo "Session '$session_name' is RUNNING."
        echo "Active windows:"
        tmux list-windows -t "$session_name" -F "  - Window #I: #{window_name} (#{pane_current_command})"
    else
        echo "Session '$session_name' is NOT RUNNING."
    fi

    if [[ ${#services[@]} -gt 0 ]]; then
        echo "Service Diagnostics:"
        for svc in "${services[@]}"; do
            local status_text=$(tmux_probe_service "$svc" "${TMUX_SITE_MODEL:-}" "${TMUX_PROJECT_SPEC:-}")
            printf "  %-12s: %s\n" "$svc" "$status_text"
        done
    fi
}

tmux_capture_logs() {
    local target="${1:-}"
    local window_name="${2:-}"
    local lines="${3:-50}"

    if [[ "$window_name" =~ ^[0-9]+$ && -z "${3:-}" ]]; then
        lines="$window_name"
        window_name=""
    fi

    # Reject leading tilde (~namespace) as an error
    if [[ "$target" =~ ^~ ]]; then
        echo "ERROR: Invalid target '$target'. Leading tilde syntax is not supported; use a namespace (e.g. '${target:1}') or 'session~${target:1}'." >&2
        return 1
    fi

    # Handle :window syntax (e.g. :mosquitto)
    if [[ "$target" =~ ^: ]]; then
        window_name="${target#:}"
        target=""
    elif [[ "$target" == *:* && -z "$window_name" ]]; then
        window_name="${target#*:}"
        target="${target%%:*}"
    fi

    local target_sessions=()

    # 1. Exact match
    if [[ -n "$target" ]] && tmux_session_exists "$target"; then
        target_sessions+=("$target")
    fi

    # 2. Match with udmi_ prefix if omitted (e.g. barbican -> udmi_barbican, barbican~ns -> udmi_barbican~ns)
    if [[ -n "$target" && ${#target_sessions[@]} -eq 0 && ! "$target" =~ ^udmi_ ]]; then
        if tmux_session_exists "udmi_${target}"; then
            target_sessions+=("udmi_${target}")
        fi
    fi

    # 3. Match with active or default namespace if no namespace delimiter present in target
    if [[ -n "$target" && ${#target_sessions[@]} -eq 0 && ! "$target" =~ ~ ]]; then
        local ns="${TMUX_NAMESPACE:-default}"
        if tmux_session_exists "${target}~${ns}"; then
            target_sessions+=("${target}~${ns}")
        elif [[ ! "$target" =~ ^udmi_ ]] && tmux_session_exists "udmi_${target}~${ns}"; then
            target_sessions+=("udmi_${target}~${ns}")
        fi
    fi

    # 4. If target is a standalone namespace name, find all sessions matching *~${target}
    if [[ -n "$target" && ${#target_sessions[@]} -eq 0 ]]; then
        local running_sessions=$(tmux list-sessions -F "#{session_name}" 2>/dev/null || true)
        for s in $running_sessions; do
            if [[ "$s" == *~"${target}" ]]; then
                target_sessions+=("$s")
            fi
        done
    fi

    # 5. If target is empty, discover active sessions for TMUX_NAMESPACE or all udmi_* sessions
    if [[ -z "$target" && ${#target_sessions[@]} -eq 0 ]]; then
        local running_sessions=$(tmux list-sessions -F "#{session_name}" 2>/dev/null || true)
        if [[ -n "${TMUX_NAMESPACE:-}" ]]; then
            for s in $running_sessions; do
                if [[ "$s" == *~"${TMUX_NAMESPACE}" ]]; then
                    target_sessions+=("$s")
                fi
            done
        fi
        if [[ ${#target_sessions[@]} -eq 0 ]]; then
            for s in $running_sessions; do
                if [[ "$s" =~ ^udmi_ ]]; then
                    target_sessions+=("$s")
                fi
            done
        fi
    fi

    if [[ ${#target_sessions[@]} -eq 0 ]]; then
        echo "Session '${target:-UDMI}' is not running."
        return 1
    fi

    for sess in "${target_sessions[@]}"; do
        if [[ -n "$window_name" ]]; then
            echo "=== Logs for $sess:$window_name (last $lines lines) ==="
            tmux capture-pane -t "$sess:$window_name" -p -S "-$lines" 2>/dev/null || echo "Window '$window_name' not found in session '$sess'."
        else
            echo "=== Windows in $sess ==="
            local windows=$(tmux list-windows -t "$sess" -F "#{window_name}" 2>/dev/null || true)
            for w in $windows; do
                echo "--- Window: $w ---"
                tmux capture-pane -t "$sess:$w" -p -S "-$lines" 2>/dev/null || true
            done
        fi
    done
}

tmux_attach_session() {
    local target="$1"
    local window_name="${2:-}"

    # Reject leading tilde (~namespace) as an error
    if [[ "$target" =~ ^~ ]]; then
        echo "ERROR: Invalid target '$target'. Leading tilde syntax is not supported; use a namespace (e.g. '${target:1}') or 'session~${target:1}'." >&2
        return 1
    fi

    # Handle :window syntax
    if [[ "$target" =~ ^: ]]; then
        window_name="${target#:}"
        target=""
    elif [[ "$target" == *:* && -z "$window_name" ]]; then
        window_name="${target#*:}"
        target="${target%%:*}"
    fi

    local session_name="$target"
    if [[ -z "$session_name" ]]; then
        session_name="udmi_barbican"
    fi

    if ! tmux_session_exists "$session_name"; then
        if [[ ! "$session_name" =~ ^udmi_ ]] && tmux_session_exists "udmi_${session_name}"; then
            session_name="udmi_${session_name}"
        elif [[ ! "$session_name" =~ ~ ]]; then
            local ns="${TMUX_NAMESPACE:-default}"
            if tmux_session_exists "${session_name}~${ns}"; then
                session_name="${session_name}~${ns}"
            elif [[ ! "$session_name" =~ ^udmi_ ]] && tmux_session_exists "udmi_${session_name}~${ns}"; then
                session_name="udmi_${session_name}~${ns}"
            fi
        fi
    fi

    if ! tmux_session_exists "$session_name"; then
        echo "Session '$target' is not running."
        return 1
    fi

    if [[ -n "$window_name" ]]; then
        tmux select-window -t "$session_name:$window_name" 2>/dev/null || true
    fi
    tmux attach-session -t "$session_name"
}
