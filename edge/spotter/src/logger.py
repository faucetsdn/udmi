"""Structured JSON Logging & Distributed W3C OpenTelemetry Trace Context for Spotter.

Formats all container logs as single-line structured JSON and generates standard
W3C traceparent headers to link edge packet captures with cloud telemetry pipelines.
"""

import json
import logging
import time
import secrets
from typing import Dict, Any, Optional
from datetime import datetime, timezone

class StructuredJsonFormatter(logging.Formatter):
    """Formats log records into single-line structured JSON objects for Cloud Logging / Vector."""
    
    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(record.created, timezone.utc).isoformat()
        log_entry: Dict[str, Any] = {
            "timestamp": timestamp,
            "severity": record.levelname,
            "component": record.name or "spotter",
            "message": record.getMessage()
        }
        if hasattr(record, "trace_id") and record.trace_id:
            log_entry["trace_id"] = record.trace_id
        if record.exc_info:
            log_entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(log_entry)

def setup_structured_logging(logger_name: str = "spotter_agent", level: int = logging.INFO) -> logging.Logger:
    """Configures structured single-line JSON logging on stdout and target loggers."""
    logger = logging.getLogger(logger_name)
    logger.setLevel(level)
    handler = logging.StreamHandler()
    handler.setFormatter(StructuredJsonFormatter())
    # Avoid duplicate handlers on reinit
    logger.handlers = [handler]
    logger.propagate = False
    return logger

def generate_w3c_traceparent() -> str:
    """Generates a valid W3C OpenTelemetry traceparent header (version 00).
    
    Format: 00-<32 hex trace_id>-<16 hex span_id>-01
    """
    trace_id = secrets.token_hex(16)
    span_id = secrets.token_hex(8)
    return f"00-{trace_id}-{span_id}-01"
