#!/bin/bash
# Shared helper functions for UDMI tmux_* management scripts

UDMI_ROOT=$(dirname $(realpath ${BASH_SOURCE[0]}))/..
source $UDMI_ROOT/etc/shell_common.sh

# Global parsed arguments
TMUX_ACTION="start"
TMUX_SITE_MODEL="sites/udmi_site_model"
TMUX_PROJECT_SPEC="//mqtt/localhost:${MQTT_PORT:-8883}"
TMUX_TARGET_ID=""
TMUX_ONLY_LIST=()
TMUX_EXCLUDE_LIST=()
TMUX_OPTIONAL_LIST=()
TMUX_EXTRA_ARGS=()

parse_tmux_args() {
    TMUX_ACTION="start"
    TMUX_SITE_MODEL="sites/udmi_site_model"
    TMUX_PROJECT_SPEC="//mqtt/localhost:${MQTT_PORT:-8883}"
    TMUX_TARGET_ID=""
    TMUX_ONLY_LIST=()
    TMUX_EXCLUDE_LIST=()
    TMUX_OPTIONAL_LIST=()
    TMUX_EXTRA_ARGS=()

    local positional_count=0

    while [[ $# -gt 0 ]]; do
        local arg="$1"
        shift

        case "$arg" in
            start|stop|status|logs|attach)
                TMUX_ACTION="$arg"
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
                        if [[ -d "$arg" || -f "$arg" || "$arg" == sites/* || "$arg" == */* ]]; then
                            TMUX_SITE_MODEL="$arg"
                            positional_count=1
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

    if [[ -d "$TMUX_SITE_MODEL" && -f "$TMUX_SITE_MODEL/cloud_iot_config.json" ]]; then
        TMUX_SITE_MODEL=$(realpath "$TMUX_SITE_MODEL")
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

    if ! tmux_session_exists "$session_name"; then
        echo "Creating tmux session '$session_name' [window: $window_name]..."
        tmux new-session -d -s "$session_name" -n "$window_name" "bash -c '$cmd; echo Process exited with code \$?; read -r'"
        tmux set-option -t "$session_name" remain-on-exit on 2>/dev/null || true
    else
        echo "Adding window '$window_name' to session '$session_name'..."
        tmux new-window -t "$session_name:" -n "$window_name" "bash -c '$cmd; echo Process exited with code \$?; read -r'"
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

tmux_session_status() {
    local session_name="$1"
    if tmux_session_exists "$session_name"; then
        echo "Session '$session_name' is RUNNING."
        echo "Active windows:"
        tmux list-windows -t "$session_name" -F "  - Window #I: #{window_name} (#{pane_current_command})"
    else
        echo "Session '$session_name' is NOT RUNNING."
    fi
}

tmux_capture_logs() {
    local session_name="$1"
    local window_name="${2:-}"
    local lines="${3:-50}"

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
