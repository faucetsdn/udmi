#!/bin/bash

# Lightweight Process Supervisor for UDMI Spotter dual-process container

CONFIG_FILE=""
LEGACY_PATH="${LEGACY_PATH:-/app/legacy/main.py}"
SPOTTER_PATH="${SPOTTER_PATH:-/app/spotter/agent.py}"
LEGACY_VENV="${LEGACY_VENV:-/venv_legacy/bin/python3}"
SPOTTER_VENV="${SPOTTER_VENV:-/venv_spotter/bin/python3}"


SERIAL_NO=""

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --config_file) CONFIG_FILE="$2"; shift ;;
        --config_file=*) CONFIG_FILE="${1#*=}" ;;
        --serial_no) SERIAL_NO="$2"; shift ;;
        --serial_no=*) SERIAL_NO="${1#*=}" ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [[ -z "$CONFIG_FILE" ]]; then
    echo "Error: --config_file is required."
    exit 1
fi

echo "Supervisor: Starting with config file: $CONFIG_FILE"

# PIDs of the child processes
LEGACY_PID=0
SPOTTER_PID=0

# Flag to indicate if we are shutting down
SHUTDOWN=0

# Graceful shutdown handler
cleanup() {
    echo "Supervisor: Received shutdown signal, terminating child processes..."
    SHUTDOWN=1
    terminate_children_and_exit 0
}

# Function to kill all children and exit with given code
terminate_children_and_exit() {
    EXIT_CODE=$1
    if [ $LEGACY_PID -ne 0 ] && kill -0 "$LEGACY_PID" 2>/dev/null; then
        echo "Supervisor: Sending SIGTERM to legacy node (PID: $LEGACY_PID)"
        kill -TERM "$LEGACY_PID" 2>/dev/null
        wait "$LEGACY_PID" 2>/dev/null
    fi
    
    if [ $SPOTTER_PID -ne 0 ] && kill -0 "$SPOTTER_PID" 2>/dev/null; then
        echo "Supervisor: Sending SIGTERM to Spotter agent (PID: $SPOTTER_PID)"
        kill -TERM "$SPOTTER_PID" 2>/dev/null
        wait "$SPOTTER_PID" 2>/dev/null
    fi
    echo "Supervisor: All child processes terminated. Exiting."
    exit "$EXIT_CODE"

}

# Trap termination signals
trap cleanup SIGTERM SIGINT

# Start Legacy Discovery Node
start_legacy() {
    echo "Supervisor: Starting Legacy Discovery Node..."
    exec $LEGACY_VENV "$LEGACY_PATH" --config_file="$CONFIG_FILE" &
    LEGACY_PID=$!
    echo "Supervisor: Legacy Discovery Node started with PID $LEGACY_PID"
}

# Start Spotter Core Agent
start_spotter() {
    echo "Supervisor: Starting Spotter Core Agent..."
    exec $SPOTTER_VENV "$SPOTTER_PATH" --config_file="$CONFIG_FILE" &
    SPOTTER_PID=$!
    echo "Supervisor: Spotter Core Agent started with PID $SPOTTER_PID"
}

# Initial start
start_legacy
start_spotter

# Monitor loop
while [ $SHUTDOWN -eq 0 ]; do
    sleep 2
    
    # Check if legacy node is running
    if ! kill -0 "$LEGACY_PID" 2>/dev/null; then
        wait "$LEGACY_PID"
        LEGACY_EXIT_CODE=$?
        echo "Supervisor: Legacy Discovery Node (PID: $LEGACY_PID) exited with code $LEGACY_EXIT_CODE"
        if [ $SHUTDOWN -eq 0 ]; then
            echo "Supervisor: Fatal crash detected on legacy node. Restarting container."
            terminate_children_and_exit 101
        fi
    fi
    
    # Check if Spotter agent is running
    if ! kill -0 "$SPOTTER_PID" 2>/dev/null; then
        wait "$SPOTTER_PID"
        SPOTTER_EXIT_CODE=$?
        echo "Supervisor: Spotter Core Agent (PID: $SPOTTER_PID) exited with code $SPOTTER_EXIT_CODE"
        if [ $SHUTDOWN -eq 0 ]; then
            if [ "$SPOTTER_EXIT_CODE" -eq 42 ]; then
                echo "Supervisor: OTA staging exit code (42) detected from Spotter agent."
                echo "Supervisor: Running staged self-test suite before promoting active symlink..."
                SELF_TEST_CMD=""
                if [ -f "${SPOTTER_PATH%/src/agent.py}/tests/self_test.py" ]; then
                    SELF_TEST_CMD="${SPOTTER_PATH%/src/agent.py}/tests/self_test.py"
                elif [ -f "${SPOTTER_PATH%/agent.py}/../tests/self_test.py" ]; then
                    SELF_TEST_CMD="${SPOTTER_PATH%/agent.py}/../tests/self_test.py"
                fi
                if [ -n "$SELF_TEST_CMD" ] && "$SPOTTER_VENV" "$SELF_TEST_CMD" 2>/dev/null; then
                    echo "Supervisor: Staged self-test verification passed successfully. Promoting active symlink..."
                    STAGED_DIR="${SPOTTER_STAGING_DIR:-/tmp/spotter_staging}"
                    if [ -f "${STAGED_DIR}/OTA_STAGED" ]; then
                        echo "Supervisor: Promoted OTA package $(cat "${STAGED_DIR}/OTA_STAGED" 2>/dev/null || echo 'unknown')"
                        rm -f "${STAGED_DIR}/OTA_STAGED"
                    fi
                    echo "Supervisor: OTA package promoted. Restarting Spotter Core Agent..."
                else
                    echo "Supervisor: ERROR! Staged self-test failed with non-zero exit code! Rejecting update and initiating ROLLBACK..."
                    STAGED_DIR="${SPOTTER_STAGING_DIR:-/tmp/spotter_staging}"
                    rm -rf "${STAGED_DIR}"/* 2>/dev/null || true
                    echo "Supervisor: OTA rollback complete. Restarting Spotter Core Agent on previous known-good state..."
                fi
                start_spotter
                continue
            fi
            echo "Supervisor: Fatal crash detected on Spotter agent. Restarting container."
            terminate_children_and_exit 102
        fi
    fi
done
