"""Session-scoped process execution for UDMI Workbench (Layer 4).

Runs real repository binaries (`bin/sequencer`) as detached process groups,
captures combined stdout/stderr to a per-session log, and exposes resumable
offset-based incremental reads.

Improvements over the v1 implementation:
  * Every run is addressable by `session_id` (v1 always tailed the newest run,
    making concurrent runs unreachable).
  * Finished sessions are reaped so the registry cannot grow unbounded.
  * `RESULT` / `Starting test` lines are parsed server-side into structured
    events, so the browser never has to regex raw console output.
"""

from datetime import datetime, timezone
import os
import re
import shutil
import signal
import subprocess
import threading
import uuid
from typing import Any, Callable, Dict, List, Optional

from workbench.server.logger import SERVER_LOGGER

RESULT_RE = re.compile(r"^RESULT\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s*(.*)$")
STARTING_RE = re.compile(r"\bStart(?:ing)?\s+test\s+([A-Za-z0-9_+]+)", re.IGNORECASE)

VALID_LOG_LEVELS = {"INFO": [], "DEBUG": ["-v"], "TRACE": ["-vv"]}

# CLI flags accepted by bin/sequencer for the minimum feature stage.
#   (no flag) -> min_stage=PREVIEW     -a -> min_stage=ALPHA     -x -> min_stage="=ALPHA"
VALID_STAGES = {"PREVIEW": [], "ALPHA": ["-a"], "ALPHA_ONLY": ["-x"]}

# Ordinal order of udmi.schema.FeatureDiscovery.FeatureStage. SequenceRunner
# admits a test when `stage.compareTo(min_stage) >= 0`, so anything ordered
# below the configured gate is skipped without ever reporting a result.
FEATURE_STAGE_ORDER = ("DISABLED", "ALPHA", "PREVIEW", "BETA", "STABLE")

# Each workbench option maps to the gate stage plus whether bin/sequencer's
# "=" prefix (exact match) applies.
STAGE_GATES = {
    "PREVIEW": ("PREVIEW", False),
    "ALPHA": ("ALPHA", False),
    "ALPHA_ONLY": ("ALPHA", True),
}

STAGE_LABELS = {
    "PREVIEW": "Stage: Preview & above (default)",
    "ALPHA": "Stage: Alpha & above (all)",
    "ALPHA_ONLY": "Stage: Alpha only",
}


class RunnerError(Exception):
    """Raised when a run cannot be started or addressed."""


class RunnerBusyError(RunnerError):
    """Raised when a run is requested while another session is still running.

    bin/sequencer writes shared, fixed paths (/tmp/sequencer_config.json and
    out/sequencer.*), so two concurrent runs would silently overwrite each
    other's configuration and results. Mapped to HTTP 409 by the routes.
    """

    def __init__(self, running: Dict[str, Any]):
        self.running = running
        super().__init__(
            f"Sequencer session '{running['session_id']}' (device "
            f"'{running['device_id']}', started {running['started_at']}) is still "
            "running. bin/sequencer writes shared files (/tmp/sequencer_config.json, "
            "out/sequencer.*), so only one run may execute at a time. Wait for it to "
            "finish or stop it, then start the new run."
        )


def stages_admitted(min_stage: str) -> List[str]:
    """Returns the feature stages that will actually run under `min_stage`.

    Mirrors SequenceRunner.processStage. A sequence whose stage is absent from
    this list is skipped silently by the Java runner, which is why the UI must
    surface the exclusion before the run starts.
    """
    if min_stage not in STAGE_GATES:
        raise RunnerError(
            f"Invalid min_stage '{min_stage}'. Expected one of: {', '.join(sorted(VALID_STAGES))}"
        )
    gate, exact = STAGE_GATES[min_stage]
    if exact:
        return [gate]
    threshold = FEATURE_STAGE_ORDER.index(gate)
    return [
        stage for stage in FEATURE_STAGE_ORDER[threshold:]
        if stage != "DISABLED"
    ]


class SequencerRunner:
    """Owns the lifecycle of all sequencer subprocess sessions."""

    def __init__(self, udmi_root: str, max_retained_sessions: int = 10):
        self.udmi_root = udmi_root
        self.max_retained_sessions = max_retained_sessions
        self.sessions_root = os.path.join(udmi_root, "out", "workbench_sessions")
        self._sessions: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.Lock()
        os.makedirs(self.sessions_root, exist_ok=True)

    def build_command(
        self,
        site_model_abs: str,
        project_spec: str,
        device_id: str,
        tests: List[str],
        log_level: str,
        min_stage: str,
        serial_no: Optional[str],
    ) -> List[str]:
        """Constructs the exact `bin/sequencer` invocation. Rejects unknown options."""
        if log_level not in VALID_LOG_LEVELS:
            raise RunnerError(
                f"Invalid log_level '{log_level}'. Expected one of: {', '.join(sorted(VALID_LOG_LEVELS))}"
            )
        if min_stage not in VALID_STAGES:
            raise RunnerError(
                f"Invalid min_stage '{min_stage}'. Expected one of: {', '.join(sorted(VALID_STAGES))}"
            )

        cmd = ["bin/sequencer"]
        cmd.extend(VALID_LOG_LEVELS[log_level])
        cmd.extend(VALID_STAGES[min_stage])
        if serial_no and str(serial_no).strip():
            cmd.extend(["-s", str(serial_no).strip()])
        cmd.extend([site_model_abs, project_spec, device_id])
        cmd.extend(tests)
        return cmd

    def start(
        self,
        site_model_abs: str,
        project_spec: str,
        device_id: str,
        tests: List[str],
        log_level: str = "INFO",
        min_stage: str = "PREVIEW",
        serial_no: Optional[str] = None,
        correlation_id: Optional[str] = None,
        on_exit: Optional[Callable[[Dict[str, Any], str], None]] = None,
    ) -> Dict[str, Any]:
        """Launches `bin/sequencer` in its own process group and registers the session.

        `on_exit(summary, log_text)`, when given, is called on a watcher thread once
        the process has exited, with the session summary (as `list_sessions` reports
        it) and the complete log. It runs whether or not any browser is watching.
        """
        if not project_spec:
            raise RunnerError("project_spec is required (e.g. //mqtt/localhost:18833)")
        if not device_id:
            raise RunnerError("device_id is required")

        cmd = self.build_command(
            site_model_abs, project_spec, device_id, tests, log_level, min_stage, serial_no
        )

        session_id = uuid.uuid4().hex[:12]
        session_dir = os.path.join(self.sessions_root, session_id)
        log_path = os.path.join(session_dir, "sequencer.log")

        # Check, launch, and register under one lock so two simultaneous
        # requests cannot both observe "nothing running" and both launch.
        with self._lock:
            running = self._running_session_locked()
            if running is not None:
                raise RunnerBusyError({
                    "session_id": running["session_id"],
                    "device_id": running["device_id"],
                    "started_at": running["started_at"],
                })

            os.makedirs(session_dir, exist_ok=True)
            log_handle = open(log_path, "wb", buffering=0)
            try:
                process = subprocess.Popen(
                    cmd,
                    cwd=self.udmi_root,
                    stdout=log_handle,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                )
            except OSError as exc:
                log_handle.close()
                raise RunnerError(f"Failed to launch {' '.join(cmd)}: {exc}") from exc

            started_at = datetime.now(timezone.utc).isoformat()
            self._sessions[session_id] = {
                "session_id": session_id,
                "process": process,
                "log_handle": log_handle,
                "log_path": log_path,
                "session_dir": session_dir,
                "command": cmd,
                "device_id": device_id,
                "site_model": site_model_abs,
                "project_spec": project_spec,
                "tests": tests,
                "started_at": started_at,
                "stopped": False,
            }

        SERVER_LOGGER.info(
            "SequencerRunner",
            "process.start",
            correlation_id=correlation_id,
            details={"command": cmd, "pid": process.pid},
            context={"sessionId": session_id, "deviceId": device_id},
        )
        self._prune_sessions()
        if on_exit is not None:
            threading.Thread(
                target=self._watch_exit,
                args=(session_id, on_exit, correlation_id),
                name=f"sequencer-exit-{session_id}",
                daemon=True,
            ).start()

        return {
            "session_id": session_id,
            "pid": process.pid,
            "command": cmd,
            "command_line": " ".join(cmd),
            "started_at": started_at,
            "log_path": os.path.relpath(log_path, self.udmi_root),
        }

    def read_since(self, session_id: str, offset: int = 0) -> Dict[str, Any]:
        """Returns new log bytes since `offset` plus parsed structured events."""
        session = self._get_session(session_id)
        log_path = session["log_path"]

        text = ""
        new_offset = offset
        if os.path.isfile(log_path):
            with open(log_path, "r", encoding="utf-8", errors="replace") as fh:
                fh.seek(0, os.SEEK_END)
                size = fh.tell()
                if offset < size:
                    fh.seek(offset)
                    text = fh.read()
                    new_offset = fh.tell()
                else:
                    new_offset = size

        process = session["process"]
        exit_code = process.poll()
        running = exit_code is None
        if not running:
            self._close_handle(session)

        return {
            "session_id": session_id,
            "running": running,
            "exit_code": exit_code,
            "stopped": session["stopped"],
            "offset": new_offset,
            "text": text,
            "events": self.parse_events(text),
        }

    @staticmethod
    def parse_events(text: str) -> List[Dict[str, Any]]:
        """Extracts structured per-test lifecycle events from raw console output."""
        events: List[Dict[str, Any]] = []
        for line in text.split("\n"):
            stripped = line.strip()
            if not stripped:
                continue

            result_match = RESULT_RE.match(stripped)
            if result_match:
                result, bucket, name, stage, score, message = result_match.groups()
                events.append({
                    "type": "result",
                    "test": name.split("+")[0],
                    "variant": name,
                    "result": result.lower(),
                    "bucket": bucket,
                    "stage": stage,
                    "score": score,
                    "message": message.strip(),
                })
                continue

            start_match = STARTING_RE.search(stripped)
            if start_match:
                events.append({
                    "type": "started",
                    "test": start_match.group(1).split("+")[0],
                })
        return events

    def stop(self, session_id: str, correlation_id: Optional[str] = None) -> Dict[str, Any]:
        """Terminates the whole process group for a session."""
        session = self._get_session(session_id)
        process = session["process"]

        if process.poll() is not None:
            self._close_handle(session)
            return {"session_id": session_id, "status": "ALREADY_EXITED", "exit_code": process.poll()}

        # Marked before the signal: an exit watcher woken by the kill must already
        # see that the operator, not the run, ended it.
        session["stopped"] = True
        try:
            os.killpg(os.getpgid(process.pid), signal.SIGTERM)
            process.wait(timeout=5)
            status = "STOPPED"
        except (ProcessLookupError, PermissionError, subprocess.TimeoutExpired):
            process.kill()
            process.wait(timeout=5)
            status = "KILLED"

        self._close_handle(session)
        SERVER_LOGGER.warn(
            "SequencerRunner",
            "process.stop",
            correlation_id=correlation_id,
            details={"status": status},
            context={"sessionId": session_id},
        )
        return {"session_id": session_id, "status": status, "exit_code": process.poll()}

    def list_sessions(self) -> List[Dict[str, Any]]:
        """Summarizes every known session without exposing process handles."""
        with self._lock:
            sessions = list(self._sessions.values())

        summaries = [self._summary(session) for session in sessions]
        summaries.sort(key=lambda s: s["started_at"], reverse=True)
        return summaries

    @staticmethod
    def _summary(session: Dict[str, Any]) -> Dict[str, Any]:
        exit_code = session["process"].poll()
        return {
            "session_id": session["session_id"],
            "device_id": session["device_id"],
            "project_spec": session["project_spec"],
            "site_model": session["site_model"],
            "tests": session["tests"],
            "command_line": " ".join(session["command"]),
            "started_at": session["started_at"],
            "running": exit_code is None,
            "exit_code": exit_code,
            "stopped": session["stopped"],
        }

    def _watch_exit(
        self,
        session_id: str,
        on_exit: Callable[[Dict[str, Any], str], None],
        correlation_id: Optional[str],
    ) -> None:
        """Waits for the session's process, then hands its summary and log to `on_exit`."""
        session = self._get_session(session_id)
        session["process"].wait()
        self._close_handle(session)
        with open(session["log_path"], "r", encoding="utf-8", errors="replace") as fh:
            log_text = fh.read()
        try:
            on_exit(self._summary(session), log_text)
        except Exception as exc:  # a failing callback must be visible, not lost with the thread
            SERVER_LOGGER.error(
                "SequencerRunner",
                "process.exit_callback_failed",
                correlation_id=correlation_id,
                context={"sessionId": session_id},
                error={"code": exc.__class__.__name__, "message": str(exc)},
            )

    def shutdown(self) -> None:
        """Terminates all running sessions (used on server shutdown and in tests)."""
        for summary in self.list_sessions():
            if summary["running"]:
                try:
                    self.stop(summary["session_id"])
                except (RunnerError, OSError):
                    pass

    def _get_session(self, session_id: str) -> Dict[str, Any]:
        with self._lock:
            session = self._sessions.get(session_id)
        if not session:
            raise RunnerError(f"Unknown session_id '{session_id}'. It may have been pruned.")
        return session

    def _running_session_locked(self) -> Optional[Dict[str, Any]]:
        """Returns a session whose process is still alive. Caller holds _lock."""
        for session in self._sessions.values():
            if session["process"].poll() is None:
                return session
        return None

    @staticmethod
    def _close_handle(session: Dict[str, Any]) -> None:
        handle = session.get("log_handle")
        if handle and not handle.closed:
            handle.close()

    def _prune_sessions(self) -> None:
        """Reaps finished sessions beyond the retention limit (registry and disk)."""
        with self._lock:
            finished = [
                s for s in self._sessions.values()
                if s["process"].poll() is not None
            ]
            finished.sort(key=lambda s: s["started_at"], reverse=True)
            for session in finished[self.max_retained_sessions:]:
                self._close_handle(session)
                self._sessions.pop(session["session_id"], None)
                shutil.rmtree(session["session_dir"], ignore_errors=True)
