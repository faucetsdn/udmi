"""Console Manager for GUMMI task terminal emulation and backend tmux agent session."""

import base64
import json
import os
import re
import shlex
import subprocess
from typing import Any, Dict, List, Optional, Tuple


def build_tmux_keys(sess: str, keys: List[str]) -> List[str]:
    """Builds tmux send-keys command arguments from hex byte strings."""
    if keys in (["7f"], ["08"]):
        return ["tmux", "send-keys", "-t", sess, "BSpace"]
    elif keys == ["1b", "5b", "33", "7e"]:
        return ["tmux", "send-keys", "-t", sess, "DC"]
    return ["tmux", "send-keys", "-t", sess, "-H"] + keys


class GummiConsoleManager:
    """Manages backend tmux session 'gummi~agent' and terminal I/O for Jetski."""

    def __init__(
        self,
        session_name: str = "gummi~agent",
        repo_root: Optional[str] = None,
        runtime_dir: Optional[str] = None,
        mock_mode: bool = False,
    ):
        self.session_name = session_name
        self.mock_mode = mock_mode
        self.repo_root = repo_root or os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "..")
        )
        self.runtime_dir = runtime_dir or os.path.join(self.repo_root, "var")
        os.makedirs(self.runtime_dir, exist_ok=True)
        self.log_file = os.path.join(self.runtime_dir, "gummi_agent.log")
        self.exit_file = os.path.join(self.runtime_dir, "gummi_agent.exit")
        self.conv_file = os.path.join(self.runtime_dir, ".jetski_conv_id")
        self.last_cols = 120
        self.last_rows = 30
        self.last_error: Optional[str] = None
        self._mock_log = "GUMMI Task Console [gummi~agent]\r\nWelcome to Jetski interactive session.\r\n> "
        self._mock_running: bool = False
        self._mock_active: bool = False
        self._mock_error: bool = False
        self._mock_exit_code: int = 0

    def _record_error_log(self, msg: str) -> None:
        """Appends error message to the session log file."""
        try:
            with open(self.log_file, "a", encoding="utf-8") as f:
                f.write(f"\r\n[ERROR] {msg}\r\n")
        except Exception:
            pass

    def is_running(self) -> bool:
        """Returns True if the tmux session is active."""
        if self.mock_mode:
            return self._mock_running
        try:
            res = subprocess.run(["tmux", "has-session", "-t", self.session_name], capture_output=True)
            return res.returncode == 0
        except FileNotFoundError:
            return False
        except Exception:
            return False

    def get_conv_id(self) -> Optional[str]:
        """Reads cached conversation ID if available."""
        if os.path.exists(self.conv_file):
            try:
                with open(self.conv_file, "r", encoding="utf-8") as f:
                    return f.read().strip()
            except Exception:
                pass
        return None

    def start_jetski(
        self,
        prompt: Optional[str] = None,
        cols: Optional[int] = None,
        rows: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Starts or attaches to the jetski tmux session."""
        c = int(cols) if cols else self.last_cols
        r = int(rows) if rows else self.last_rows
        self.last_cols = c
        self.last_rows = r

        self.last_error = None

        if self.mock_mode:
            self._mock_running = True
            self._mock_error = False
            self._mock_exit_code = 0
            return {"status": "started", "session": self.session_name, "conv_id": "mock-conv-123"}

        conv_id = ""
        if os.path.exists(self.conv_file):
            try:
                with open(self.conv_file, "r", encoding="utf-8") as f:
                    conv_id = f.read().strip()
            except Exception:
                pass

        if not conv_id:
            my_env = os.environ.copy()
            my_env["PATH"] = (
                my_env.get("PATH", "")
                + ":"
                + os.path.expanduser("~/bin")
                + ":"
                + os.path.expanduser("~/.gemini/jetski/bin")
            )
            try:
                res = subprocess.run(
                    [
                        "agentapi",
                        "new-conversation",
                        f"Hi! Let's work on the GUMMI interface in {self.repo_root}.",
                    ],
                    capture_output=True,
                    text=True,
                    env=my_env,
                    cwd=self.repo_root,
                    timeout=10,
                )
                match = re.search(r'"conversationId":\s*"([^"]+)"', res.stdout)
                if match:
                    conv_id = match.group(1)
            except Exception:
                pass

            if conv_id:
                try:
                    with open(self.conv_file, "w", encoding="utf-8") as f:
                        f.write(conv_id)
                except Exception:
                    pass

        if self.is_running():
            return {"status": "already_running", "session": self.session_name, "conv_id": conv_id}

        if os.path.exists(self.exit_file):
            try:
                os.remove(self.exit_file)
            except Exception:
                pass

        if prompt:
            if conv_id:
                cmd_str = f"jetski --repl_mode --conversation {conv_id} -i {shlex.quote(prompt)}"
            else:
                cmd_str = f"jetski --repl_mode -i {shlex.quote(prompt)}"
        else:
            if conv_id:
                cmd_str = f"jetski --repl_mode --conversation {conv_id}"
            else:
                cmd_str = "jetski --repl_mode"

        diag_cmd = (
            f"export PATH=\"{os.path.expanduser('~/bin')}:{os.path.expanduser('~/.gemini/jetski/bin')}:$PATH\"; "
            f"export COLUMNS={c}; export LINES={r}; "
        )
        wrapped_command = f"( {diag_cmd} {cmd_str} ) ; echo $? > {self.exit_file}"

        try:
            res = subprocess.run(
                [
                    "tmux",
                    "new-session",
                    "-d",
                    "-s",
                    self.session_name,
                    "-x",
                    str(c),
                    "-y",
                    str(r),
                    "-c",
                    self.repo_root,
                    wrapped_command,
                ],
                capture_output=True,
                text=True,
            )
            if res.returncode != 0:
                err_detail = res.stderr.strip() or f"tmux new-session exited with code {res.returncode}"
                self.last_error = err_detail
                self._record_error_log(err_detail)
                return {
                    "status": "error",
                    "error": "Error starting session",
                    "message": err_detail,
                    "button_state": "red",
                    "session": self.session_name,
                }
        except FileNotFoundError as e:
            err_detail = f"tmux executable not found: {e}"
            self.last_error = err_detail
            self._record_error_log(err_detail)
            return {
                "status": "error",
                "error": "Error starting session",
                "message": err_detail,
                "button_state": "red",
                "session": self.session_name,
            }
        except Exception as e:
            err_detail = f"Failed to start session: {e}"
            self.last_error = err_detail
            self._record_error_log(err_detail)
            return {
                "status": "error",
                "error": "Error starting session",
                "message": err_detail,
                "button_state": "red",
                "session": self.session_name,
            }

        try:
            subprocess.run(
                ["tmux", "pipe-pane", "-t", self.session_name, "-o", f"cat > {self.log_file}"],
                capture_output=True,
            )
        except Exception:
            pass

        return {"status": "started", "session": self.session_name, "conv_id": conv_id, "button_state": "green"}

    def get_pane_child_pids(self) -> List[str]:
        """Returns child process PIDs running in the tmux session pane."""
        if self.mock_mode:
            return []
        try:
            res = subprocess.run(
                ["tmux", "list-panes", "-t", self.session_name, "-F", "#{pane_pid}"],
                capture_output=True,
                text=True,
            )
            if res.returncode != 0:
                return []
            parent_pids = [p.strip() for p in res.stdout.strip().splitlines() if p.strip()]
            child_pids: List[str] = []
            for p in parent_pids:
                c_res = subprocess.run(["pgrep", "-P", p], capture_output=True, text=True)
                if c_res.returncode == 0:
                    child_pids.extend([c.strip() for c in c_res.stdout.strip().splitlines() if c.strip()])
            return child_pids
        except Exception:
            return []

    def is_active(self) -> bool:
        """Determines if the running session is actively performing work vs idle."""
        if self.mock_mode:
            return getattr(self, "_mock_active", False)
        if not self.is_running():
            return False

        try:
            child_pids = self.get_pane_child_pids()
            if len(child_pids) > 1:
                return True
        except Exception:
            pass

        return False

    def get_diagnostics(self) -> Dict[str, Any]:
        """Analyzes session state and exit code."""
        running = self.is_running()
        if self.mock_mode:
            has_error = getattr(self, "_mock_error", False) or bool(self.last_error)
            exit_code = getattr(self, "_mock_exit_code", 0)
            is_active = getattr(self, "_mock_active", False)
            if not running:
                if has_error or exit_code != 0:
                    code = exit_code or 1
                    err_msg = self.last_error or f"Agent process exited with code {code}."
                    return {
                        "state": "error",
                        "status_text": "Error starting session" if self.last_error else f"Exited (code {code})",
                        "severity": "error",
                        "button_state": "red",
                        "running": False,
                        "active": False,
                        "exit_code": code,
                        "alert": err_msg,
                    }
                return {
                    "state": "not_running",
                    "status_text": "Not Running",
                    "severity": "neutral",
                    "button_state": "blue",
                    "running": False,
                    "active": False,
                    "alert": None,
                }
            else:
                if is_active:
                    return {
                        "state": "active",
                        "status_text": "Actively Working",
                        "severity": "warning",
                        "button_state": "yellow",
                        "running": True,
                        "active": True,
                        "alert": None,
                    }
                return {
                    "state": "idle",
                    "status_text": "Idle",
                    "severity": "success",
                    "button_state": "green",
                    "running": True,
                    "active": False,
                    "alert": None,
                }

        if not running:
            if self.last_error:
                return {
                    "state": "error",
                    "status_text": "Error starting session",
                    "severity": "error",
                    "button_state": "red",
                    "running": False,
                    "active": False,
                    "alert": self.last_error,
                    "exit_code": 1,
                }
            if os.path.exists(self.exit_file):
                code = 1
                try:
                    with open(self.exit_file, "r", encoding="utf-8") as f:
                        code_str = f.read().strip()
                    if code_str.isdigit() or (code_str.startswith("-") and code_str[1:].isdigit()):
                        code = int(code_str)
                except Exception:
                    pass
                if code != 0:
                    err_detail = ""
                    if os.path.exists(self.log_file):
                        try:
                            with open(self.log_file, "r", encoding="utf-8", errors="replace") as lf:
                                lines = [l.strip() for l in lf.readlines() if l.strip()]
                                if lines:
                                    err_detail = lines[-1]
                        except Exception:
                            pass
                    msg = f"Agent process exited with code {code}."
                    if err_detail:
                        msg += f" Details: {err_detail}"
                    return {
                        "state": "error",
                        "status_text": f"Exited (code {code})",
                        "severity": "error",
                        "button_state": "red",
                        "running": False,
                        "active": False,
                        "alert": msg,
                        "exit_code": code,
                    }
            return {
                "state": "not_running",
                "status_text": "Not Running",
                "severity": "neutral",
                "button_state": "blue",
                "running": False,
                "active": False,
                "alert": None,
            }

        active = self.is_active()
        if active:
            button_state = "yellow"
            status_text = "Actively Working"
            state = "active"
            severity = "warning"
        else:
            button_state = "green"
            status_text = "Idle"
            state = "idle"
            severity = "success"

        return {
            "state": state,
            "status_text": status_text,
            "severity": severity,
            "button_state": button_state,
            "running": True,
            "active": active,
            "alert": None,
        }

    def get_log(self, offset: int = 0) -> Dict[str, Any]:
        """Reads console output from tmux capture or log file."""
        diag = self.get_diagnostics()
        running = self.is_running()
        button_state = diag.get("button_state", "blue" if not running else "green")
        if self.mock_mode:
            data_bytes = self._mock_log.encode("utf-8")
            if offset >= len(data_bytes):
                return {
                    "data": "",
                    "offset": len(data_bytes),
                    "cleared": False,
                    "running": running,
                    "diagnostics": diag,
                    "button_state": button_state,
                }
            chunk = data_bytes[offset:]
            return {
                "data": base64.b64encode(chunk).decode("ascii"),
                "offset": len(data_bytes),
                "cleared": False,
                "running": running,
                "diagnostics": diag,
                "button_state": button_state,
            }

        data_b64 = ""
        new_offset = offset
        cleared = False

        if os.path.exists(self.log_file):
            file_len = os.path.getsize(self.log_file)
            if offset > file_len:
                offset = 0
                cleared = True

            if offset == 0 and running:
                cap = subprocess.run(
                    ["tmux", "capture-pane", "-S", "-", "-e", "-p", "-t", self.session_name],
                    capture_output=True,
                )
                if cap.returncode == 0 and cap.stdout:
                    cursor_code = ""
                    pos_res = subprocess.run(
                        ["tmux", "display-message", "-p", "-t", self.session_name, "#{cursor_x},#{cursor_y}"],
                        capture_output=True,
                        text=True,
                    )
                    if pos_res.returncode == 0 and "," in pos_res.stdout:
                        try:
                            cx, cy = map(int, pos_res.stdout.strip().split(","))
                            cursor_code = f"\033[{cy + 1};{cx + 1}H"
                        except Exception:
                            pass
                    raw_lines = cap.stdout.split(b"\n")
                    while raw_lines and not raw_lines[-1].strip():
                        raw_lines.pop()
                    screen_data = b"\r\n".join(raw_lines)
                    payload = b"\033[H" + screen_data + cursor_code.encode("utf-8")
                    data_b64 = base64.b64encode(payload).decode("ascii")
                    new_offset = file_len
                    return {
                        "data": data_b64,
                        "offset": new_offset,
                        "cleared": cleared,
                        "running": running,
                        "diagnostics": diag,
                        "button_state": button_state,
                    }

            with open(self.log_file, "rb") as f:
                if offset == 0 and file_len > 500000:
                    f.seek(max(0, file_len - 200000))
                else:
                    f.seek(offset)
                data = f.read()
                new_offset = file_len if (offset == 0 and file_len > 500000) else f.tell()
                data_b64 = base64.b64encode(data).decode("ascii")
        elif running and offset == 0:
            cap = subprocess.run(
                ["tmux", "capture-pane", "-S", "-", "-e", "-p", "-t", self.session_name],
                capture_output=True,
            )
            if cap.returncode == 0 and cap.stdout:
                data_b64 = base64.b64encode(cap.stdout).decode("ascii")

        return {
            "data": data_b64,
            "offset": new_offset,
            "cleared": cleared,
            "running": running,
            "diagnostics": diag,
            "button_state": button_state,
        }

    def send_keys(self, hex_keys: List[str]) -> Tuple[bool, str]:
        """Sends hex keys to tmux session."""
        if not hex_keys:
            return False, "Missing keys"
        if self.mock_mode:
            return True, "ok"
        if not self.is_running():
            return False, f"Session {self.session_name} does not exist"
        args = build_tmux_keys(self.session_name, hex_keys)
        try:
            res = subprocess.run(args, capture_output=True, text=True)
            if res.returncode != 0:
                return False, res.stderr
            return True, "ok"
        except Exception as e:
            return False, str(e)

    def resize(self, cols: int, rows: int) -> bool:
        """Resizes the tmux session window."""
        self.last_cols = cols
        self.last_rows = rows
        if self.mock_mode or not self.is_running():
            return True
        try:
            res = subprocess.run(
                ["tmux", "resize-window", "-t", self.session_name, "-x", str(cols), "-y", str(rows)],
                capture_output=True,
            )
            return res.returncode == 0
        except Exception:
            return False

    def kill(self) -> bool:
        """Terminates the tmux session."""
        self.last_error = None
        if self.mock_mode:
            self._mock_running = False
            self._mock_error = False
            return True
        try:
            res = subprocess.run(["tmux", "kill-session", "-t", self.session_name], capture_output=True)
            return res.returncode == 0
        except Exception:
            return False
