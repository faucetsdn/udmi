"""Session process validation and execution tools for Mantis."""

import json
import re
import os
import subprocess
from typing import Any, Dict, Optional

from mantis.session import SessionManager


DISALLOWED_PATTERNS = [
    r"\bsudo\b",
    r"\bsu\b",
    r"\bcurl\b",
    r"\bwget\b",
    r"\bmkfifo\b",
    r"\bnc\b",
    r"\bncat\b",
    r"/dev/tcp",
    r"chmod\s+777\s+/",
    r"rm\s+-rf\s+/[^\s]*",
    r":\(\)\s*\{",
]

ALLOWED_PREFIXES = (
    "bin/",
    "java",
    "python",
    "python3",
    "venv/bin/python",
    "venv/bin/python3",
    "pytest",
    "export",
    "cd",
    "echo",
)


def validate_session_command(command: str) -> None:
    """Validate command against security policy and allowlisted executables."""
    if not command or not command.strip():
        raise ValueError("Command string cannot be empty.")

    clean_cmd = command.strip()

    # Reject dangerous shell injection tokens
    for pat in DISALLOWED_PATTERNS:
        if re.search(pat, clean_cmd, re.IGNORECASE):
            raise ValueError(
                f"Command rejected: contains disallowed security token matching '{pat}'."
            )

    # Check subcommands split by &&, ;, or |
    subcommands = re.split(r"&&|;|\|", clean_cmd)
    for sub in subcommands:
        s = sub.strip()
        if not s:
            continue
        # Remove leading environment variable assignments e.g. "FOO=BAR bin/sequencer"
        s_no_env = re.sub(r"^[A-Za-z0-9_]+=[^\s]+\s+", "", s).strip()
        if not any(s_no_env.startswith(prefix) for prefix in ALLOWED_PREFIXES):
            raise ValueError(
                f"Command segment '{s}' rejected: executable must start with an approved prefix: {ALLOWED_PREFIXES}"
            )


def start_session_process(
    session_mgr: SessionManager,
    test_id: str,
    window: str,
    command: str,
    env: Optional[Dict[str, str]] = None,
) -> Dict[str, Any]:
    """Validate command security and launch process inside a named window of an active session."""
    validate_session_command(command)
    if hasattr(session_mgr, "start_session_process"):
        return session_mgr.start_session_process(test_id=test_id, window=window, command=command, env=env)

    session_name = session_mgr.sanitize_session_name(test_id)
    if not session_mgr.is_session_active(session_name):
        raise RuntimeError(f"Session '{session_name}' for test_id '{test_id}' is not active.")

    if str(window).strip().isdigit():
        raise ValueError(
            f"Window parameter must be a semantic tag (e.g. 'sequencer', 'dut'), not a numerical index '{window}'."
        )

    run_dir = os.path.join(session_mgr.instances_dir, session_name)
    info = session_mgr.get_session_info(test_id) or {}
    ports = info.get("ports", {})
    mqtt_port = ports.get("mqtt", session_mgr.derive_port_block(test_id))

    env_exports = [
        f"export UDMI_ROOT='{session_mgr.udmi_root}'",
        f"export UDMI_RUN_DIR='{run_dir}'",
        f"export MQTT_PORT='{mqtt_port}'",
        f"export UDMI_NO_SUDO='true'",
    ]
    if env:
        for k, v in env.items():
            env_exports.append(f"export {k}='{v}'")

    full_cmd = f"{' && '.join(env_exports)} && cd '{session_mgr.udmi_root}' && {command}"

    existing_windows = session_mgr.list_test_windows(test_id)
    if window in existing_windows:
        subprocess.run(
            ["tmux", "send-keys", "-t", f"{session_name}:{window}", full_cmd, "C-m"],
            check=True,
        )
    else:
        subprocess.run(
            [
                "tmux",
                "new-window",
                "-t",
                session_name,
                "-n",
                window,
                f"bash -c {json.dumps(full_cmd)}",
            ],
            check=True,
        )

    return {
        "status": "STARTED",
        "test_id": test_id,
        "session_name": session_name,
        "window": window,
        "command": command,
    }
