"""Testbed infrastructure and Pubber emulator manager for UDMI Workbench (Layer 4).

Manages local substrate services (`bin/udmi start/stop/restart`) and simulated
device emulators (`bin/pubber`) in unprivileged user-space mode. Every call names
its project spec (`//mqtt/localhost:<port>`); ports are derived from it per call.
Provides non-blocking health probing and structured service log tailing.
"""

import os
import re
import signal
import socket
import subprocess
import threading
import time
from typing import Any, Dict, Optional

from workbench.server import discovery
from workbench.server.logger import SERVER_LOGGER


#: Shown in error messages as an example of a valid spec. Never used as a
#: fallback: every call names its own spec (see `validate_local_spec`).
LOCAL_SPEC_EXAMPLE = "//mqtt/localhost:18833"
DEFAULT_ETCD_PORT = 2379
# The privileged MQTT port the substrate treats as "not isolated mode"; see
# the isolated-mode block in `etc/shell_common.sh`.
DEFAULT_MQTT_BROKER_PORT = 8883

#: The only spec shape the local substrate accepts: an explicit localhost port,
#: which is what selects unprivileged isolated mode (AGENTS.md).
LOCAL_SPEC_PATTERN = re.compile(r"^//mqtt/localhost:(\d+)$")
#: Lowest port an unprivileged user may bind.
MIN_UNPRIVILEGED_PORT = 1024
#: Lines of setup log quoted in `last_error` when a `bin/udmi` command fails.
ERROR_LOG_TAIL_LINES = 20
#: Seconds the tracked Pubber group gets to exit on SIGTERM before SIGKILL.
PUBBER_TERM_GRACE_SEC = 5.0
#: Window for the spec's ports to close after `bin/udmi stop` before it is
#: reported as failed (the script itself always exits 0).
STOP_VERIFY_TIMEOUT_SEC = 10.0
STOP_VERIFY_POLL_SEC = 0.5

# Anchored command-line patterns for the two components with no health port.
# These are matched against the whole command line, so they begin at its start:
# an unanchored fragment matches any process that merely mentions the path.
#: `bin/pubber`, optionally under the `/bin/bash -e` wrapper the script uses.
PUBBER_PROCESS_PATTERN = r"^(/[^ ]*/bash +-e +)?[^ ]*/bin/pubber( |$)"
#: The UDMIS pod, launched as a jar or via `bin/`-prefixed wrappers.
UDMIS_PROCESS_PATTERN = r"^[^ ]*(java|/bash)[^ ]* .*udmis[^ ]*\.jar( |$)|^[^ ]*/bin/(start_)?udmis( |$)"


def check_tcp_port(host: str, port: int, timeout: float = 0.4) -> bool:
    """Non-blocking TCP socket health check."""
    if not port or port <= 0:
        return False
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except Exception:
        return False


def is_pid_alive(pid: Optional[int]) -> bool:
    """Returns True if the process exists and is alive."""
    if not pid or pid <= 0:
        return False
    try:
        os.kill(pid, 0)
        return True
    except (OSError, ProcessLookupError):
        return False


def _self_lineage() -> set:
    """This process and its ancestors.

    `pgrep` omits itself but not the shell that invoked us, nor the gateway
    process, either of which may carry a service path in its command line.
    """
    lineage, pid = set(), os.getpid()
    while pid and pid > 1 and pid not in lineage:
        lineage.add(pid)
        try:
            with open(f"/proc/{pid}/status", "r") as fh:
                for line in fh:
                    if line.startswith("PPid:"):
                        pid = int(line.split()[1])
                        break
                else:
                    break
        except (OSError, ValueError):
            break
    return lineage


def find_processes(pattern: str) -> list:
    """PIDs whose full command line matches `pattern`, excluding our own tree.

    `pattern` is an extended regular expression matched against the whole
    command line, so callers must anchor it. An unanchored fragment matches
    any process that merely mentions the path -- a `tail` of a log, an editor,
    another operator's shell -- and reporting a service UP on that basis is
    how a dead emulator comes to look healthy.
    """
    try:
        res = subprocess.run(
            ["pgrep", "-f", pattern],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=2.0,
        )
    except Exception:
        return []
    if res.returncode != 0:
        return []
    mine = _self_lineage()
    pids = []
    for token in res.stdout.split():
        try:
            pid = int(token)
        except ValueError:
            continue
        if pid not in mine:
            pids.append(pid)
    return pids


def is_process_running(pattern: str) -> bool:
    """True when some process outside our own tree matches `pattern`."""
    return bool(find_processes(pattern))


def derive_etcd_port(mqtt_port: int) -> int:
    """Returns the etcd client port the substrate binds for a given MQTT port.

    Mirrors the canonical isolated-mode offset in `etc/shell_common.sh` (and
    `etc/tmux_common.sh`): any MQTT port other than the privileged default 8883
    selects user-space ports at MQTT_PORT+1 (etcd), +2 (influx), +3 (postgres).
    The privileged default keeps etcd on its own well-known port.
    """
    if mqtt_port and mqtt_port != DEFAULT_MQTT_BROKER_PORT:
        return mqtt_port + 1
    return DEFAULT_ETCD_PORT


class TestbedError(Exception):
    """Raised when a testbed request fails validation (HTTP 400)."""


class TestbedCommandError(Exception):
    """Raised when a `bin/udmi` or Pubber control command fails (HTTP 500)."""


def validate_local_spec(spec: Optional[str]) -> int:
    """Returns the MQTT port of a local isolated-mode spec, or raises TestbedError.

    The local substrate only runs unprivileged, which requires an explicit
    port (`//mqtt/localhost:<port>`, AGENTS.md). A blank field, a cloud
    project, or a portless localhost spec is rejected rather than being
    silently replaced by a default port.
    """
    example = LOCAL_SPEC_EXAMPLE
    text = (spec or "").strip()
    if not text:
        raise TestbedError(
            "Missing required field: 'project_spec'. The local testbed needs an "
            f"explicit localhost port, e.g. '{example}'."
        )
    match = LOCAL_SPEC_PATTERN.match(text)
    if not match:
        raise TestbedError(
            f"Project spec '{text}' is not a local isolated-mode spec. The local "
            "testbed only runs '//mqtt/localhost:<port>' with an explicit "
            f"unprivileged port, e.g. '{example}'."
        )
    port = int(match.group(1))
    if port < MIN_UNPRIVILEGED_PORT or port > 65535:
        raise TestbedError(
            f"Port {port} in '{text}' is outside the unprivileged range "
            f"{MIN_UNPRIVILEGED_PORT}-65535; use e.g. '{example}'."
        )
    if port == DEFAULT_MQTT_BROKER_PORT:
        raise TestbedError(
            f"Port {port} selects the privileged, non-isolated substrate "
            f"(etc/shell_common.sh); use another port, e.g. '{example}'."
        )
    return port


def read_log_tail(path: str, lines: int = ERROR_LOG_TAIL_LINES) -> str:
    """Last `lines` lines of a log file, or an explicit note that it is unreadable."""
    try:
        with open(path, "r", errors="replace") as fh:
            return "".join(fh.readlines()[-lines:]).strip()
    except OSError as exc:
        return f"(setup log {path} unreadable: {exc})"


class TestbedManager:
    """Controls local UDMI testbed substrate and Pubber lifecycle."""

    def __init__(self, udmi_root: str):
        self.udmi_root = os.path.abspath(udmi_root)
        self.out_dir = os.path.join(self.udmi_root, "out")
        os.makedirs(self.out_dir, exist_ok=True)

        self._lock = threading.Lock()
        self.active_site_model: Optional[str] = None
        # The spec the last `start` used. Reported for context only: status
        # probes always use the spec the caller passes.
        self.active_project_spec: Optional[str] = None

        self.setup_process: Optional[subprocess.Popen] = None
        self.setup_log_path = os.path.join(self.out_dir, "testbed_setup.log")
        self.is_starting = False
        self.start_time: Optional[float] = None
        self.last_error: Optional[str] = None

        self.pubber_process: Optional[subprocess.Popen] = None
        self._pubber_log_handle = None
        self.pubber_info: Dict[str, Any] = {}
        self.pubber_log_path = os.path.join(self.out_dir, "pubber.log")

    def _resolve_site_model(self, site_model: str) -> str:
        """Resolves site model path and validates cloud_iot_config.json."""
        if not site_model:
            raise TestbedError("Missing required field: 'site_model'")
        abs_path = discovery.resolve_site_model(self.udmi_root, site_model)
        config_path = os.path.join(abs_path, "cloud_iot_config.json")
        if not os.path.isfile(config_path):
            raise TestbedError(f"Site model missing cloud_iot_config.json at {abs_path}")
        return abs_path

    def _fail_setup(self, command: str, returncode: int, correlation_id: Optional[str]) -> None:
        """Records a failed `bin/udmi` setup command as the testbed's last error."""
        tail = read_log_tail(self.setup_log_path)
        self.last_error = f"bin/udmi {command} exited with code {returncode}:\n{tail}"
        SERVER_LOGGER.error(
            "TestbedManager",
            f"testbed.{command}.failed",
            correlation_id=correlation_id,
            layer="GATEWAY",
            details={"returncode": returncode, "log_path": self.setup_log_path},
        )

    def start(
        self,
        site_model: str,
        project_spec: Optional[str],
        clean: bool = False,
        correlation_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Launches local UDMI infrastructure services via bin/udmi start.

        `project_spec` must be `//mqtt/localhost:<port>`; see `validate_local_spec`.
        """
        with self._lock:
            abs_site_model = self._resolve_site_model(site_model)
            mqtt_port = validate_local_spec(project_spec)
            spec = project_spec.strip()
            self.active_site_model = site_model
            self.active_project_spec = spec
            self.is_starting = True
            self.start_time = time.time()
            self.last_error = None

            SERVER_LOGGER.info(
                "TestbedManager",
                "testbed.start",
                correlation_id=correlation_id,
                layer="GATEWAY",
                details={
                    "site_model": site_model,
                    "project_spec": spec,
                    "mqtt_port": mqtt_port,
                    "clean": clean,
                },
            )

            # Clean previous log
            try:
                with open(self.setup_log_path, "w") as f:
                    f.write(f"=== Starting UDMI Local Infrastructure ({spec}) ===\n")
            except Exception:
                pass

            def _run():
                try:
                    env = dict(os.environ)
                    env["UDMI_NO_SUDO"] = "true"
                    env["MQTT_PORT"] = str(mqtt_port)
                    udmi = os.path.join(self.udmi_root, "bin", "udmi")

                    if clean:
                        with open(self.setup_log_path, "a") as log_file:
                            log_file.write("=== bin/udmi clean ===\n")
                            log_file.flush()
                            cleaned = subprocess.run(
                                [udmi, "clean", spec],
                                cwd=self.udmi_root,
                                env=env,
                                stdout=log_file,
                                stderr=subprocess.STDOUT,
                                timeout=30.0,
                            )
                        if cleaned.returncode != 0:
                            self._fail_setup("clean", cleaned.returncode, correlation_id)
                            return

                    cmd = [udmi, "start", abs_site_model, spec]
                    with open(self.setup_log_path, "a") as log_file:
                        proc = subprocess.Popen(
                            cmd,
                            cwd=self.udmi_root,
                            env=env,
                            stdout=log_file,
                            stderr=subprocess.STDOUT,
                            start_new_session=True,
                        )
                        self.setup_process = proc
                        try:
                            proc.wait(timeout=120.0)
                        except subprocess.TimeoutExpired:
                            try:
                                os.killpg(proc.pid, signal.SIGKILL)
                            except ProcessLookupError:
                                pass
                            proc.wait()
                            raise
                    if proc.returncode != 0:
                        self._fail_setup("start", proc.returncode, correlation_id)
                except Exception as e:
                    self.last_error = f"bin/udmi setup failed: {e}"
                    SERVER_LOGGER.error(
                        "TestbedManager",
                        "testbed.start.error",
                        error=e,
                        correlation_id=correlation_id,
                        layer="GATEWAY",
                    )
                finally:
                    self.is_starting = False

            thread = threading.Thread(target=_run, daemon=True)
            thread.start()

            return {
                "status": "INITIALIZING",
                "site_model": site_model,
                "project_spec": spec,
                "mqtt_port": mqtt_port,
                "message": "Local testbed starting in user-space isolated mode",
            }

    def _ports_still_open(self, mqtt_port: int) -> list:
        """Labels of this spec's ports still accepting connections after the verify window."""
        probes = {
            f"MQTT tcp://localhost:{mqtt_port}": mqtt_port,
            f"etcd tcp://localhost:{derive_etcd_port(mqtt_port)}": derive_etcd_port(mqtt_port),
        }
        deadline = time.time() + STOP_VERIFY_TIMEOUT_SEC
        while True:
            open_now = [label for label, port in probes.items() if check_tcp_port("localhost", port)]
            if not open_now or time.time() >= deadline:
                return open_now
            time.sleep(STOP_VERIFY_POLL_SEC)

    def stop(self, project_spec: Optional[str], correlation_id: Optional[str] = None) -> Dict[str, Any]:
        """Stops the local UDMI infrastructure for `project_spec` and any tracked Pubber.

        The spec is passed to `bin/udmi stop`: without it the script resolves
        tmux namespace 'default' (etc/tmux_common.sh), which is not the
        instance on this port. Raises TestbedCommandError when the command
        fails; the failure is also kept in `last_error` so status reports ERROR.
        """
        mqtt_port = validate_local_spec(project_spec)
        spec = project_spec.strip()
        with self._lock:
            self.active_project_spec = spec
            SERVER_LOGGER.info(
                "TestbedManager",
                "testbed.stop",
                correlation_id=correlation_id,
                layer="GATEWAY",
            )
            # 1. Stop the tracked pubber, if any
            pubber = self._stop_pubber_internal()

            # 2. Stop local services
            env = dict(os.environ)
            env["UDMI_NO_SUDO"] = "true"
            try:
                with open(self.setup_log_path, "a") as log_file:
                    log_file.write("=== bin/udmi stop ===\n")
                    log_file.flush()
                    stopped = subprocess.run(
                        [os.path.join(self.udmi_root, "bin", "udmi"), "stop", spec],
                        cwd=self.udmi_root,
                        env=env,
                        stdout=log_file,
                        stderr=subprocess.STDOUT,
                        timeout=30.0,
                    )
            except (OSError, subprocess.SubprocessError) as e:
                self.last_error = f"bin/udmi stop failed: {e}"
                SERVER_LOGGER.error(
                    "TestbedManager",
                    "testbed.stop.error",
                    error=e,
                    correlation_id=correlation_id,
                    layer="GATEWAY",
                )
                raise TestbedCommandError(self.last_error) from e

            if stopped.returncode != 0:
                self._fail_setup("stop", stopped.returncode, correlation_id)
                raise TestbedCommandError(self.last_error)

            # `bin/udmi stop` swallows every per-session failure (`|| true`)
            # and exits 0, so its return code proves nothing. The ports this
            # spec owns must actually close. The UDMIS process probe is not
            # used here: its pattern is not scoped to this instance and would
            # flag another checkout's pod.
            still_up = self._ports_still_open(mqtt_port)
            if still_up:
                self.last_error = (
                    f"bin/udmi stop {spec} exited 0 but {', '.join(still_up)} still "
                    f"accepted connections after {STOP_VERIFY_TIMEOUT_SEC:.0f}s"
                )
                SERVER_LOGGER.error(
                    "TestbedManager",
                    "testbed.stop.unverified",
                    correlation_id=correlation_id,
                    layer="GATEWAY",
                    details={"still_up": still_up, "project_spec": spec},
                )
                raise TestbedCommandError(self.last_error)

            self.is_starting = False
            self.setup_process = None
            self.last_error = None
            return {
                "status": "STOPPED",
                "project_spec": spec,
                "message": f"Local pipeline services for {spec} stopped",
                "pubber": pubber,
            }

    def restart(
        self,
        site_model: str,
        project_spec: Optional[str],
        correlation_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Stops, cleans, and starts local testbed infrastructure."""
        # Reject a bad spec before tearing anything down.
        validate_local_spec(project_spec)
        self.stop(project_spec, correlation_id=correlation_id)
        time.sleep(1.0)
        return self.start(
            site_model=site_model,
            project_spec=project_spec,
            clean=True,
            correlation_id=correlation_id,
        )

    def start_pubber(
        self,
        site_model: str,
        device_id: str,
        project_spec: Optional[str],
        serial_no: str = "1234",
        correlation_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Starts a simulated Pubber device instance in background."""
        with self._lock:
            if not device_id:
                raise TestbedError("Missing required field: 'device_id'")
            abs_site_model = self._resolve_site_model(site_model)

            device_dir = os.path.join(abs_site_model, "devices", device_id)
            if not os.path.isdir(device_dir):
                raise TestbedError(f"Device directory not found: {device_dir}")

            mqtt_port = validate_local_spec(project_spec)
            spec = project_spec.strip()
            serial = (serial_no or "1234").strip()

            # Terminate existing pubber if running
            self._stop_pubber_internal()

            pubber_log_file = os.path.join(self.out_dir, f"pubber_{device_id}.log")
            self.pubber_log_path = pubber_log_file

            SERVER_LOGGER.info(
                "TestbedManager",
                "pubber.start",
                correlation_id=correlation_id,
                layer="GATEWAY",
                details={
                    "device_id": device_id,
                    "site_model": site_model,
                    "project_spec": spec,
                    "serial_no": serial,
                },
            )

            env = dict(os.environ)
            env["UDMI_NO_SUDO"] = "true"
            env["MQTT_PORT"] = str(mqtt_port)

            cmd = [
                os.path.join(self.udmi_root, "bin", "pubber"),
                abs_site_model,
                spec,
                device_id,
                serial,
            ]

            log_f = open(pubber_log_file, "w")
            log_f.write(f"=== Starting Pubber for {device_id} ({spec}) ===\n")
            log_f.flush()

            # A new session makes the pid a process-group id owned solely by
            # this emulator, which is what `_terminate_group` signals.
            proc = subprocess.Popen(
                cmd,
                cwd=self.udmi_root,
                env=env,
                stdout=log_f,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
            self.pubber_process = proc
            self._pubber_log_handle = log_f
            self.pubber_info = {
                "device_id": device_id,
                "serial_no": serial,
                "site_model": site_model,
                "project_spec": spec,
                "pid": proc.pid,
                "started_at": time.time(),
            }

            return {
                "status": "RUNNING",
                "device_id": device_id,
                "serial_no": serial,
                "pid": proc.pid,
                "message": f"Pubber emulator started for {device_id}",
            }

    @staticmethod
    def _group_alive(pgid: int) -> bool:
        """True while any member of process group `pgid` still exists."""
        try:
            os.killpg(pgid, 0)
            return True
        except ProcessLookupError:
            return False

    def _terminate_group(self, proc: subprocess.Popen) -> bool:
        """SIGTERM, then SIGKILL, the process group led by `proc`.

        Pubber is spawned with `start_new_session=True`, so its pid is also the
        id of a process group holding only the `bin/pubber` wrapper and the JVM
        it launches. Signalling that group reaches exactly the emulator we
        started and nothing else on the machine. Returns False when the group
        had already vanished.
        """
        pgid = proc.pid
        try:
            os.killpg(pgid, signal.SIGTERM)
        except ProcessLookupError:
            proc.poll()
            return False
        except PermissionError as exc:
            raise TestbedCommandError(
                f"Not permitted to signal Pubber process group {pgid}: {exc}"
            ) from exc
        deadline = time.time() + PUBBER_TERM_GRACE_SEC
        try:
            proc.wait(timeout=PUBBER_TERM_GRACE_SEC)
        except subprocess.TimeoutExpired:
            pass
        while time.time() < deadline and self._group_alive(pgid):
            time.sleep(0.05)
        if self._group_alive(pgid):
            try:
                os.killpg(pgid, signal.SIGKILL)
            except ProcessLookupError:
                return True
            try:
                proc.wait(timeout=PUBBER_TERM_GRACE_SEC)
            except subprocess.TimeoutExpired as exc:
                raise TestbedCommandError(
                    f"Pubber process group {pgid} survived SIGKILL for "
                    f"{PUBBER_TERM_GRACE_SEC:.0f}s"
                ) from exc
        return True

    def _stop_pubber_internal(self) -> Dict[str, Any]:
        """Terminates the tracked Pubber process group and reports what happened.

        Only the emulator this manager spawned is ever signalled. When nothing
        is tracked, nothing is killed and the result says so.
        """
        proc = self.pubber_process
        if proc is None:
            return {
                "status": "NOT_RUNNING",
                "device_id": None,
                "message": "No Workbench-launched Pubber is tracked; nothing was stopped",
            }
        device = self.pubber_info.get("device_id")
        exit_code = proc.poll()
        signalled = self._terminate_group(proc)
        self.pubber_process = None
        self.pubber_info = {}
        if self._pubber_log_handle is not None:
            self._pubber_log_handle.close()
            self._pubber_log_handle = None
        if signalled:
            return {
                "status": "STOPPED",
                "device_id": device,
                "pid": proc.pid,
                "message": f"Pubber emulator for {device} (process group {proc.pid}) stopped",
            }
        return {
            "status": "NOT_RUNNING",
            "device_id": device,
            "pid": proc.pid,
            "message": (
                f"Tracked Pubber for {device} (pid {proc.pid}) had already exited "
                f"with code {exit_code if exit_code is not None else proc.returncode}; "
                f"nothing was stopped"
            ),
        }

    def stop_pubber(
        self,
        project_spec: Optional[str],
        device_id: Optional[str] = None,
        correlation_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Stops the tracked Pubber device instance.

        A `device_id` or `project_spec` that does not match the tracked
        emulator is rejected rather than stopping an emulator the caller did
        not ask about.
        """
        validate_local_spec(project_spec)
        spec = project_spec.strip()
        with self._lock:
            tracked_device = self.pubber_info.get("device_id")
            tracked_spec = self.pubber_info.get("project_spec")
            SERVER_LOGGER.info(
                "TestbedManager",
                "pubber.stop",
                correlation_id=correlation_id,
                layer="GATEWAY",
                details={"device_id": device_id, "tracked_device": tracked_device},
            )
            if device_id and self.pubber_process is not None and device_id != tracked_device:
                raise TestbedError(
                    f"The tracked Pubber emulator is for device '{tracked_device}', "
                    f"not '{device_id}'; nothing was stopped."
                )
            if self.pubber_process is not None and spec != tracked_spec:
                raise TestbedError(
                    f"The tracked Pubber emulator is connected to '{tracked_spec}', "
                    f"not '{spec}'; nothing was stopped."
                )
            return self._stop_pubber_internal()

    def get_status(self, project_spec: Optional[str]) -> Dict[str, Any]:
        """Executes non-blocking health checks for the substrate named by `project_spec`.

        The spec is required on every call and is the only source of the
        probed ports: MQTT is its explicit port, etcd follows the isolated-mode
        offset. Nothing is remembered between calls.
        """
        mqtt_port = validate_local_spec(project_spec)
        spec = project_spec.strip()
        etcd_port = derive_etcd_port(mqtt_port)
        # Lifecycle state belongs to the spec that produced it; another spec's
        # in-flight start or failure says nothing about this one.
        owns_lifecycle = self.active_project_spec == spec
        is_starting = self.is_starting and owns_lifecycle
        last_error = self.last_error if owns_lifecycle else None
        mqtt_up = check_tcp_port("localhost", mqtt_port)

        pod_ready_path = os.path.join(self.udmi_root, "var", "pod_ready.txt")
        udmis_sentinel = os.path.isfile(pod_ready_path)
        # Anchored: the sentinel file alone survives a crash, and an unanchored
        # "udmis" matches this repository's own path in any command line.
        udmis_alive = is_process_running(UDMIS_PROCESS_PATTERN)
        udmis_up = udmis_sentinel and udmis_alive

        # The verdict must rest on the probe we report. A bare `pgrep etcd`
        # fallback would claim UP on the strength of a process that may be
        # bound to a different port, or wedged, or another checkout's.
        etcd_up = check_tcp_port("localhost", etcd_port)

        # The subprocess we started is the only first-hand evidence. Failing
        # that, an anchored command-line match covers an emulator an operator
        # launched by hand; a loose `bin/pubber` fragment used to match any
        # shell that merely named the path, so a dead emulator read as UP.
        pubber_dev = self.pubber_info.get("device_id")
        pubber_pid = self.pubber_info.get("pid")
        if self.pubber_process and self.pubber_process.poll() is None:
            pubber_alive = True
            pubber_evidence = f"tracked process {self.pubber_process.pid}"
        else:
            external = find_processes(PUBBER_PROCESS_PATTERN)
            pubber_alive = bool(external)
            if pubber_alive:
                pubber_pid = external[0]
                pubber_evidence = f"external process {external[0]}"
            else:
                pubber_evidence = "no matching process"

        # Check overall state. A recorded command failure outranks partial
        # liveness: a `bin/udmi start` that exited non-zero is an ERROR even if
        # some of the services it launched happen to be listening.
        if is_starting:
            overall = "INITIALIZING"
        elif last_error:
            overall = "ERROR"
        elif mqtt_up and udmis_up:
            overall = "UP"
        elif mqtt_up or udmis_up or etcd_up:
            overall = "INITIALIZING"
        else:
            overall = "DOWN"

        def _comp_status(is_up: bool, label_when_down: str = "DOWN") -> str:
            if is_up:
                return "UP"
            if is_starting:
                return "INITIALIZING"
            return label_when_down

        return {
            "overall": overall,
            "project_spec": spec,
            "started_project_spec": self.active_project_spec,
            "site_model": self.active_site_model,
            "last_error": last_error,
            "components": {
                "mqtt_broker": {
                    "name": "Local Mosquitto Broker",
                    "status": _comp_status(mqtt_up),
                    "port": mqtt_port,
                    "probe": f"tcp://localhost:{mqtt_port}",
                },
                "udmis": {
                    "name": "Local UDMIS Pod",
                    "status": _comp_status(udmis_up),
                    "sentinel": "var/pod_ready.txt",
                    "sentinel_exists": udmis_sentinel,
                    "process_alive": udmis_alive,
                    "probe": "sentinel + process",
                },
                "etcd": {
                    "name": "etcd State Store",
                    "status": _comp_status(etcd_up),
                    "port": etcd_port,
                    "probe": f"tcp://localhost:{etcd_port}",
                },
                "pubber": {
                    "name": "Pubber Emulator",
                    "status": "UP" if pubber_alive else "DOWN",
                    "device_id": pubber_dev,
                    "pid": pubber_pid,
                    "probe": pubber_evidence,
                },
            },
        }

    def get_logs(self, component: str = "setup", tail: int = 100) -> Dict[str, Any]:
        """Reads recent log entries for a given component."""
        comp = (component or "setup").lower().strip()
        lines_count = max(10, min(1000, tail))

        log_file = self.setup_log_path
        if comp == "pubber":
            log_file = self.pubber_log_path
        elif comp == "udmis":
            candidate = os.path.join(self.out_dir, "udmis.log")
            if os.path.isfile(candidate):
                log_file = candidate
        elif comp == "mosquitto":
            candidate = os.path.join(self.out_dir, "mosquitto.log")
            if os.path.isfile(candidate):
                log_file = candidate

        content = ""
        if os.path.isfile(log_file):
            try:
                with open(log_file, "r", errors="replace") as f:
                    all_lines = f.readlines()
                    content = "".join(all_lines[-lines_count:])
            except Exception as e:
                content = f"Error reading log file {log_file}: {e}"
        else:
            content = f"No logs available yet for component '{comp}'."

        return {
            "component": comp,
            "log_path": os.path.relpath(log_file, self.udmi_root),
            "logs": content,
        }

    def shutdown(self) -> None:
        """Cleans up any running Pubber subprocess during gateway shutdown."""
        self._stop_pubber_internal()
