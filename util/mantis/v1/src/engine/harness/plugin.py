import os
import sys
import json
import subprocess
from typing import Any, Dict, List, Optional

class PluginExecutionError(Exception):
    """Raised when a subprocess plugin execution fails or exits with a non-zero code."""
    def __init__(self, command: List[str], exit_code: int, stderr_output: str):
        self.command = command
        self.exit_code = exit_code
        self.stderr_output = stderr_output
        super().__init__(
            f"Plugin subprocess {command} failed with exit code {exit_code}.\n"
            f"Stderr Logs:\n{stderr_output}"
        )

class SubprocessPluginRunner:
    """
    A language-agnostic runner that executes custom scripts/binaries as plugins.
    Communicates using JSON over stdin/stdout.
    """

    def __init__(self, cmd: List[str]):
        self.cmd = cmd

    def run(self, input_data: Dict[str, Any], env: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
        """
        Executes the plugin binary, writes input_data to stdin as JSON,
        and reads stdout as JSON return.
        """
        env_map = os.environ.copy()
        if env:
            env_map.update(env)

        try:
            # Execute command
            proc = subprocess.Popen(
                self.cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env_map
            )
        except Exception as e:
            raise RuntimeError(f"Failed to start plugin subprocess {self.cmd}: {e}")

        # Stream parameters and close stdin to signal EOF
        input_payload = json.dumps(input_data)
        try:
            stdout_output, stderr_output = proc.communicate(input=input_payload)
        except Exception as e:
            proc.kill()
            raise RuntimeError(f"Communication failure during plugin subprocess {self.cmd}: {e}")

        # Process exit codes
        exit_code = proc.returncode
        if exit_code != 0:
            raise PluginExecutionError(self.cmd, exit_code, stderr_output)

        # Parse return payload
        try:
            return json.loads(stdout_output.strip())
        except json.JSONDecodeError as e:
            raise RuntimeError(
                f"Plugin subprocess {self.cmd} returned invalid JSON stdout:\n"
                f"Raw stdout:\n{stdout_output}\n"
                f"Raw stderr:\n{stderr_output}"
            )
