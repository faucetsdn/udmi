#!/bin/bash
# Shared helper functions for UDMI tmux_* management scripts

UDMI_ROOT=$(dirname $(realpath ${BASH_SOURCE[0]}))/..
source $UDMI_ROOT/etc/shell_common.sh

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
    TMUX_PROJECT_SPEC="//mqtt/localhost:${MQTT_PORT:-8883}"
    TMUX_TARGET_ID=""
    TMUX_NAMESPACE="${UDMI_NAMESPACE:-}"
    TMUX_ONLY_LIST=()
    TMUX_EXCLUDE_LIST=()
    TMUX_OPTIONAL_LIST=()
    TMUX_EXTRA_ARGS=()

    local positional_count=0

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
                case $positional_count in
                    0)
                        if [[ -f "$arg/cloud_iot_config.json" || "$arg" == sites/* || ( -d "$arg" && "$arg" == */* ) ]]; then
                            TMUX_SITE_MODEL="$arg"
                            positional_count=1
                        elif [[ "$arg" =~ ^// || "$arg" =~ ^(mqtt|mqtts|ssl):// || "$arg" =~ localhost: ]]; then
                            TMUX_PROJECT_SPEC="$arg"
                            positional_count=2
                        elif [[ "$TMUX_ACTION" == "logs" || "$TMUX_ACTION" == "attach" ]]; then
                            if [[ "$arg" =~ ^[0-9]+$ ]]; then
                                TMUX_EXTRA_ARGS+=("$arg")
                                positional_count=3
                            elif [[ $# -gt 0 && ! "$arg" =~ ^(mosquitto|udmis|etcd|pubsub|postgres|influxdb|butler|validator|registrar|certs|clone|pubber|spotter|server|AHU-1)$ ]]; then
                                TMUX_PROJECT_SPEC="$arg"
                                positional_count=2
                            else
                                TMUX_TARGET_ID="$arg"
                                positional_count=3
                            fi
                        elif [[ "$arg" =~ ^[a-zA-Z0-9_-]+$ ]]; then
                            if [[ -d "$UDMI_ROOT/sites/udmi_site_model/devices/$arg" ]]; then
                                TMUX_TARGET_ID="$arg"
                                positional_count=3
                            else
                                TMUX_PROJECT_SPEC="$arg"
                                positional_count=2
                            fi
                        else
                            TMUX_TARGET_ID="$arg"
                            positional_count=3
                        fi
                        ;;
                    1)
                        TMUX_PROJECT_SPEC="$arg"
                        positional_count=2
                        ;;
                    2)
                        TMUX_TARGET_ID="$arg"
                        positional_count=3
                        ;;
                    *)
                        TMUX_EXTRA_ARGS+=("$arg")
                        ;;
                esac
                ;;
        esac
    done

    case $positional_count in
        0)
            # When no target is provided, namespace defaults to 'default'
            TMUX_PROJECT_SPEC="default"
            TMUX_SITE_MODEL="$UDMI_ROOT/sites/udmi~default"
            TMUX_NAMESPACE="default"
            ;;
        1)
            # Explicit site model provided without a project spec -> fail fast
            echo "ERROR: An explicit site model ('$TMUX_SITE_MODEL') requires an explicit project spec (e.g. //mqtt/localhost:46432 or a namespace)." >&2
            exit 1
            ;;
        3)
            # When target ID was specified without site model/project spec, default to default namespace
            if [[ "$TMUX_SITE_MODEL" == "sites/udmi_site_model" || "$TMUX_SITE_MODEL" == "$UDMI_ROOT/sites/udmi_site_model" ]]; then
                TMUX_PROJECT_SPEC="default"
                TMUX_SITE_MODEL="$UDMI_ROOT/sites/udmi~default"
                TMUX_NAMESPACE="default"
            fi
            ;;
        *)
            ;;
    esac

    if [[ -d "$TMUX_SITE_MODEL" && -f "$TMUX_SITE_MODEL/cloud_iot_config.json" ]]; then
        TMUX_SITE_MODEL=$(realpath "$TMUX_SITE_MODEL")
    fi

    if [[ -n "$TMUX_PROJECT_SPEC" ]]; then
        TMUX_PROJECT_SPEC=$(normalize_conn_spec "$TMUX_PROJECT_SPEC")
        if [[ $TMUX_PROJECT_SPEC =~ localhost:([0-9]+) ]]; then
            export MQTT_PORT="${BASH_REMATCH[1]}"
            if [[ $MQTT_PORT != 8883 ]]; then
                export ETCD_PORT=$((MQTT_PORT + 1))
                export INFLUX_PORT=$((MQTT_PORT + 2))
                export POSTGRES_PORT=$((MQTT_PORT + 3))
                export UDMI_NO_SUDO=true
            fi
        fi
        if [[ $TMUX_PROJECT_SPEC =~ localhost:[0-9]+/([a-zA-Z0-9_-]+) ]]; then
            export TMUX_NAMESPACE="${BASH_REMATCH[1]}"
        fi
    fi

    if [[ -n "${TMUX_NAMESPACE:-}" ]]; then
        if [[ "$TMUX_SITE_MODEL" == "sites/udmi_site_model" || "$TMUX_SITE_MODEL" == "$UDMI_ROOT/sites/udmi_site_model" ]]; then
            TMUX_SITE_MODEL="$UDMI_ROOT/sites/udmi~${TMUX_NAMESPACE}"
        fi
    fi

    if [[ -n "${TMUX_NAMESPACE:-}" && -n "${SESSION_NAME:-}" && ! "$SESSION_NAME" =~ ~ ]]; then
        SESSION_NAME="${SESSION_NAME}~${TMUX_NAMESPACE}"
    fi

    if [[ ("$TMUX_ACTION" == "start" || "$TMUX_ACTION" == "restart") && ${UDMI_NO_SUDO:-false} != true && $(id -u) != 0 ]]; then
        if command -v sudo >/dev/null 2>&1; then
            echo "Pre-authenticating sudo credentials in foreground..."
            sudo -v || true
        fi
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

    local env_exports="export UDMI_ROOT='$UDMI_ROOT' UDMI_NO_SUDO='${UDMI_NO_SUDO:-false}' MQTT_PORT='${MQTT_PORT:-8883}' ETCD_PORT='${ETCD_PORT:-2379}' INFLUX_PORT='${INFLUX_PORT:-8086}' POSTGRES_PORT='${POSTGRES_PORT:-5432}' TARGET_PROJECT='${TARGET_PROJECT:-}'"
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
    if tmux_session_exists "$session_name"; then
        echo "Terminating tmux session '$session_name'..."
        tmux kill-session -t "$session_name" 2>/dev/null || true
        echo "Session '$session_name' stopped."
    else
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
    local session_name="$1"
    local window_name="${2:-}"
    local lines="${3:-50}"

    if [[ "$window_name" =~ ^[0-9]+$ && -z "${3:-}" ]]; then
        lines="$window_name"
        window_name=""
    fi

    if ! tmux_session_exists "$session_name"; then
        echo "Session '$session_name' is not running."
        return 1
    fi

    if [[ -n "$window_name" ]]; then
        echo "=== Logs for $session_name:$window_name (last $lines lines) ==="
        tmux capture-pane -t "$session_name:$window_name" -p -S "-$lines" 2>/dev/null || echo "Window '$window_name' not found."
    else
        echo "=== Windows in $session_name ==="
        local windows=$(tmux list-windows -t "$session_name" -F "#{window_name}")
        for w in $windows; do
            echo "--- Window: $w ---"
            tmux capture-pane -t "$session_name:$w" -p -S "-$lines" 2>/dev/null || true
        done
    fi
}

tmux_attach_session() {
    local session_name="$1"
    local window_name="${2:-}"

    if ! tmux_session_exists "$session_name"; then
        echo "Session '$session_name' is not running."
        return 1
    fi

    if [[ -n "$window_name" ]]; then
        tmux select-window -t "$session_name:$window_name" 2>/dev/null || true
    fi
    tmux attach-session -t "$session_name"
}
