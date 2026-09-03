"""Session and process manager for UDMI test infrastructure using tmux."""

import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import time
from typing import Any, Dict, List, Optional


class SessionManager:
    """Manages isolated UDMI local test infrastructure instances inside tmux sessions."""

    def __init__(self, udmi_root: Optional[str] = None):
        if udmi_root is None:
            # Fallback to repo root relative to this file
            udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        self.udmi_root = os.path.abspath(udmi_root)
        self.instances_dir = os.path.join(self.udmi_root, "var", "instances")
        os.makedirs(self.instances_dir, exist_ok=True)

    def sanitize_session_name(self, test_id: str) -> str:
        """Derive a valid tmux session name from a test_id string."""
        clean_id = re.sub(r"[^a-zA-Z0-9_-]", "_", test_id.strip())
        if not clean_id:
            clean_id = "default"
        if not clean_id.startswith("udmi_"):
            return f"udmi_{clean_id}"
        return clean_id

    def is_port_available(self, port: int) -> bool:
        """Check if a local TCP port is free for binding."""
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                s.bind(("127.0.0.1", port))
                return True
            except OSError:
                return False

    def is_port_block_available(self, base_port: int) -> bool:
        """Check if the required block of ports is available."""
        # Check MQTT (P), etcd (P+1), influx (P+2), postgres (P+3), etcd-peer (P+1001)
        required_ports = [
            base_port,
            base_port + 1,
            base_port + 2,
            base_port + 3,
            base_port + 1001,
        ]
        return all(self.is_port_available(p) for p in required_ports)

    def derive_port_block(
        self,
        test_id: str,
        base_min: int = 20000,
        base_max: int = 55000,
        block_size: int = 10,
    ) -> int:
        """Deterministically map a test_id to an available port block."""
        hash_val = int(hashlib.sha256(test_id.encode("utf-8")).hexdigest(), 16)
        slot_count = (base_max - base_min) // block_size
        start_slot = hash_val % slot_count

        for i in range(slot_count):
            slot = (start_slot + i) % slot_count
            candidate_port = base_min + (slot * block_size)
            if self.is_port_block_available(candidate_port):
                return candidate_port

        raise RuntimeError(
            f"No available port block found in range [{base_min}, {base_max}] for test_id '{test_id}'"
        )

    def is_session_active(self, session_name: str) -> bool:
        """Check if a tmux session is currently alive."""
        res = subprocess.run(
            ["tmux", "has-session", "-t", session_name],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return res.returncode == 0

    def get_session_info(self, test_id: str) -> Optional[Dict[str, Any]]:
        """Retrieve stored session information if available."""
        session_name = self.sanitize_session_name(test_id)
        info_path = os.path.join(self.instances_dir, session_name, "session_info.json")
        if os.path.isfile(info_path):
            try:
                with open(info_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if self.is_session_active(session_name):
                        data["windows"] = self.list_test_windows(test_id)
                    return data
            except Exception:
                return None
        return None

    def ensure_test_setup(
        self,
        test_id: str,
        site_model: str = "sites/udmi_site_model",
        dut_device_id: Optional[str] = None,
        dut_serial_no: Optional[str] = None,
        exclude: Optional[List[str]] = None,
        added: Optional[List[str]] = None,
        clean: bool = True,
        timeout_seconds: int = 150,
    ) -> Dict[str, Any]:
        """Start or ensure isolated local UDMI infrastructure inside a tmux session."""
        session_name = self.sanitize_session_name(test_id)
        run_dir = os.path.join(self.instances_dir, session_name)
        info_path = os.path.join(run_dir, "session_info.json")

        site_model_path = os.path.abspath(os.path.join(self.udmi_root, site_model))
        if not os.path.isdir(site_model_path):
            site_model_path = os.path.abspath(site_model)

        if not os.path.isdir(site_model_path):
            raise ValueError(f"Site model directory not found: {site_model}")

        # Check if already running
        if not clean and self.is_session_active(session_name):
            existing_info = self.get_session_info(test_id)
            if existing_info:
                mqtt_port = existing_info.get("ports", {}).get("mqtt")
                if mqtt_port and self._probe_tcp("127.0.0.1", mqtt_port, timeout=1.0):
                    existing_info["status"] = "ALREADY_ACTIVE"
                    return existing_info

        # Always clean previous session and workspace if clean=True or inactive
        self.terminate_test_setup(test_id, clean_workspace=clean)

        # Allocate port
        mqtt_port = self.derive_port_block(test_id)
        etcd_port = mqtt_port + 1
        influx_port = mqtt_port + 2
        postgres_port = mqtt_port + 3

        os.makedirs(os.path.join(run_dir, "var"), exist_ok=True)
        os.makedirs(os.path.join(run_dir, "out"), exist_ok=True)

        project_spec = f"//mqtt/localhost:{mqtt_port}"
        connection_url = f"mqtt://rocket:monkey@localhost:{mqtt_port}"

        # Build filter flags
        filter_flags = []
        if exclude:
            if isinstance(exclude, str):
                exclude = [s.strip() for s in exclude.split(",") if s.strip()]
            for svc in exclude:
                filter_flags.append(f"!{svc}")
        if added:
            if isinstance(added, str):
                added = [s.strip() for s in added.split(",") if s.strip()]
            for svc in added:
                filter_flags.append(f"++{svc}")

        filter_str = (" " + " ".join(filter_flags)) if filter_flags else ""

        # Build udmi start command
        start_cmd = (
            f"export UDMI_ROOT='{self.udmi_root}' && "
            f"export UDMI_RUN_DIR='{run_dir}' && "
            f"export MQTT_PORT='{mqtt_port}' && "
            f"export ETCD_PORT='{etcd_port}' && "
            f"export INFLUX_PORT='{influx_port}' && "
            f"export POSTGRES_PORT='{postgres_port}' && "
            f"cd '{self.udmi_root}' && "
            f"bin/udmi start block '{site_model_path}' '{project_spec}'{filter_str}"
        )

        # Launch in background tmux session
        subprocess.run(
            [
                "tmux",
                "new-session",
                "-d",
                "-s",
                session_name,
                "-n",
                "main",
                f"bash -c {json.dumps(start_cmd)}",
            ],
            check=True,
        )

        # Enable remain-on-exit so pane logs are preserved if process crashes
        subprocess.run(
            ["tmux", "set-option", "-t", session_name, "remain-on-exit", "on"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        # Poll readiness
        ready = self._wait_for_readiness(
            session_name=session_name,
            run_dir=run_dir,
            site_model_path=site_model_path,
            port=mqtt_port,
            timeout_seconds=timeout_seconds,
        )

        if not ready:
            logs = self.get_test_logs(test_id, window="main", lines=60)
            self.terminate_test_setup(test_id, clean_workspace=False)
            raise TimeoutError(
                f"Test setup '{test_id}' failed to become ready within {timeout_seconds}s.\n"
                f"Recent console output:\n{logs}"
            )

        # Optionally launch DUT
        if dut_device_id:
            dut_serial = dut_serial_no or f"dut-{test_id}"
            dut_cmd = (
                f"export UDMI_ROOT='{self.udmi_root}' && "
                f"export UDMI_RUN_DIR='{run_dir}' && "
                f"cd '{self.udmi_root}' && "
                f"bin/start_dut '{site_model_path}' '{project_spec}' '{dut_device_id}' '{dut_serial}'"
            )
            subprocess.run(
                [
                    "tmux",
                    "new-window",
                    "-t",
                    session_name,
                    "-n",
                    "dut",
                    f"bash -c {json.dumps(dut_cmd)}",
                ],
                check=False,
            )

        windows = self.list_test_windows(test_id)

        result_info = {
            "status": "READY",
            "test_id": test_id,
            "session_name": session_name,
            "connection_url": connection_url,
            "project_spec": project_spec,
            "windows": windows,
            "tls": {
                "ca_cert": os.path.join(site_model_path, "reflector", "ca.crt"),
                "client_cert": os.path.join(site_model_path, "reflector", "rsa_private.crt"),
                "client_key": os.path.join(site_model_path, "reflector", "rsa_private.pem"),
            },
            "credentials": {
                "username": "rocket",
                "password": "monkey",
            },
            "ports": {
                "mqtt": mqtt_port,
                "etcd": etcd_port,
                "influx": influx_port,
                "postgres": postgres_port,
            },
            "exclude": exclude or [],
            "added": added or [],
            "site_model": site_model_path,
            "run_dir": run_dir,
        }

        with open(info_path, "w", encoding="utf-8") as f:
            json.dump(result_info, f, indent=2)

        return result_info

    def terminate_test_setup(
        self, test_id: str, clean_workspace: bool = True
    ) -> Dict[str, Any]:
        """Terminate an active test setup and its tmux session."""
        session_name = self.sanitize_session_name(test_id)
        run_dir = os.path.join(self.instances_dir, session_name)

        if self.is_session_active(session_name):
            subprocess.run(
                ["tmux", "kill-session", "-t", session_name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            time.sleep(0.5)

        if clean_workspace and os.path.isdir(run_dir):
            shutil.rmtree(run_dir, ignore_errors=True)

        return {
            "status": "TERMINATED",
            "test_id": test_id,
            "session_name": session_name,
        }

    def list_test_windows(self, test_id: str) -> List[str]:
        """List available semantic window tags for an active test session."""
        session_name = self.sanitize_session_name(test_id)
        if not self.is_session_active(session_name):
            return []

        res = subprocess.run(
            ["tmux", "list-windows", "-t", session_name, "-F", "#{window_name}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=False,
        )
        if res.returncode != 0:
            return []

        windows = [line.strip() for line in res.stdout.strip().splitlines() if line.strip()]
        return windows

    def list_test_setups(self) -> List[Dict[str, Any]]:
        """List all active UDMI test sessions."""
        res = subprocess.run(
            ["tmux", "list-sessions", "-F", "#{session_name}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=False,
        )
        if res.returncode != 0:
            return []

        active_sessions = []
        for line in res.stdout.strip().splitlines():
            sname = line.strip()
            if sname.startswith("udmi_"):
                test_id = sname[5:]
                info = self.get_session_info(test_id) or {
                    "status": "ACTIVE",
                    "test_id": test_id,
                    "session_name": sname,
                }
                info["windows"] = self.list_test_windows(test_id)
                active_sessions.append(info)
        return active_sessions

    def get_test_logs(
        self, test_id: str, window: str = "main", lines: int = 100
    ) -> str:
        """Capture recent pane logs from a named semantic tmux window."""
        session_name = self.sanitize_session_name(test_id)
        if not self.is_session_active(session_name):
            raise RuntimeError(f"Session '{session_name}' for test_id '{test_id}' is not active.")

        # Reject numerical indices to enforce semantic window tags
        if str(window).strip().isdigit():
            available = self.list_test_windows(test_id)
            raise ValueError(
                f"Window parameter must be a semantic tag (e.g. 'main', 'dut'), not a numerical index '{window}'. "
                f"Available semantic windows: {available}"
            )

        available_windows = self.list_test_windows(test_id)
        if window not in available_windows:
            raise ValueError(
                f"Semantic window '{window}' not found in session '{session_name}'. "
                f"Available semantic windows: {available_windows}"
            )

        target = f"{session_name}:{window}"
        res = subprocess.run(
            ["tmux", "capture-pane", "-t", target, "-p", "-S", f"-{lines}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        if res.returncode == 0:
            return res.stdout
        return f"(Could not capture logs for {target}: {res.stderr.strip()})"

    def _probe_tcp(self, host: str, port: int, timeout: float = 1.0) -> bool:
        """Attempt a simple TCP connection."""
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return True
        except (OSError, socket.timeout):
            return False

    def _wait_for_readiness(
        self,
        session_name: str,
        run_dir: str,
        site_model_path: str,
        port: int,
        timeout_seconds: int,
    ) -> bool:
        """Wait until Mosquitto, UDMIS pod_ready, and certificates are all present and ready."""
        start_time = time.time()
        pod_ready_file = os.path.join(run_dir, "var", "pod_ready.txt")
        ca_cert_file = os.path.join(site_model_path, "reflector", "ca.crt")

        while time.time() - start_time < timeout_seconds:
            # Check if session died prematurely
            if not self.is_session_active(session_name):
                return False

            # Check Mosquitto port
            tcp_ok = self._probe_tcp("127.0.0.1", port, timeout=0.5)

            # Check pod_ready.txt
            pod_ok = os.path.isfile(pod_ready_file) or os.path.isfile(
                os.path.join(self.udmi_root, "var", "pod_ready.txt")
            )

            # Check CA certificate
            ca_ok = os.path.isfile(ca_cert_file) and os.path.getsize(ca_cert_file) > 0

            if tcp_ok and pod_ok and ca_ok:
                return True

            time.sleep(0.5)

        return False
