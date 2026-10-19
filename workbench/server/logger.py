"""Structured Logger & Correlation Ring Buffer for UDMI Workbench (Layer 4).

Implements Section 7 of workbench/GEMINI.md:
- Emits canonical LogEntry records with ISO-8601 UTC timestamps and correlationIds.
- Maintains a thread-safe bounded ring buffer (1,000 entries) for diagnostics.
"""

from collections import deque
from datetime import datetime, timezone
import json
import sys
import threading
import uuid
from typing import Any, Deque, Dict, List, Optional


class WorkbenchServerLogger:
    """Thread-safe structured logger with correlation ID support and ring buffer."""

    def __init__(self, max_entries: int = 1000, emit_stderr: bool = False):
        self._buffer: Deque[Dict[str, Any]] = deque(maxlen=max_entries)
        self._lock = threading.Lock()
        self._emit_stderr = emit_stderr

    @staticmethod
    def new_correlation_id(prefix: str = "req") -> str:
        """Generates a compact correlation identifier."""
        return f"{prefix}-{uuid.uuid4().hex[:8]}"

    def log(
        self,
        level: str,
        module: str,
        event: str,
        correlation_id: Optional[str] = None,
        layer: str = "GATEWAY",
        duration_ms: Optional[float] = None,
        context: Optional[Dict[str, Any]] = None,
        details: Optional[Dict[str, Any]] = None,
        error: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Records a structured LogEntry into the ring buffer."""
        entry: Dict[str, Any] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": level.upper(),
            "layer": layer,
            "correlationId": correlation_id or self.new_correlation_id(),
            "module": module,
            "event": event,
        }
        if duration_ms is not None:
            entry["durationMs"] = round(duration_ms, 2)
        if context:
            entry["context"] = context
        if details:
            entry["details"] = details
        if error:
            entry["error"] = error

        with self._lock:
            self._buffer.append(entry)

        if self._emit_stderr:
            sys.stderr.write(json.dumps(entry) + "\n")
            sys.stderr.flush()

        return entry

    def info(self, module: str, event: str, **kwargs: Any) -> Dict[str, Any]:
        return self.log("INFO", module, event, **kwargs)

    def warn(self, module: str, event: str, **kwargs: Any) -> Dict[str, Any]:
        return self.log("WARN", module, event, **kwargs)

    def error(self, module: str, event: str, **kwargs: Any) -> Dict[str, Any]:
        return self.log("ERROR", module, event, **kwargs)

    def get_entries(
        self,
        limit: int = 200,
        correlation_id: Optional[str] = None,
        level: Optional[str] = None,
    ) -> List[Dict[str, Any]]:
        """Returns recent structured log entries, optionally filtered."""
        with self._lock:
            items = list(self._buffer)
        if correlation_id:
            items = [e for e in items if e.get("correlationId") == correlation_id]
        if level:
            items = [e for e in items if e.get("level") == level.upper()]
        return items[-limit:]

    def clear(self) -> None:
        with self._lock:
            self._buffer.clear()


SERVER_LOGGER = WorkbenchServerLogger()
