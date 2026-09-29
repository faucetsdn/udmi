"""HTTP/SSE primitives for the UDMI Workbench gateway (Layer 4).

Centralizes JSON responses, CORS, SSE framing, and X-Correlation-ID handling so
route handlers stay focused on domain logic. Unlike the v1 server, error
responses carry the same CORS and correlation headers as success responses.
"""

import json
import os
import shutil
from typing import Any, Dict, Optional

from workbench.server.logger import SERVER_LOGGER

CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-Correlation-ID",
}


class HttpResponder:
    """Wraps a BaseHTTPRequestHandler with consistent response helpers."""

    def __init__(self, handler: Any, correlation_id: str):
        self.handler = handler
        self.correlation_id = correlation_id

    def _common_headers(self) -> None:
        for key, value in CORS_HEADERS.items():
            self.handler.send_header(key, value)
        self.handler.send_header("X-Correlation-ID", self.correlation_id)

    def json(self, data: Any, status: int = 200) -> None:
        payload = json.dumps(data, default=str).encode("utf-8")
        self.handler.send_response(status)
        self.handler.send_header("Content-Type", "application/json; charset=utf-8")
        self.handler.send_header("Content-Length", str(len(payload)))
        self._common_headers()
        self.handler.end_headers()
        self.handler.wfile.write(payload)

    def error(self, message: str, status: int = 400, module: str = "Gateway") -> None:
        """Returns an explicit, actionable error. Never a silent empty payload."""
        SERVER_LOGGER.error(
            module,
            "request.error",
            correlation_id=self.correlation_id,
            error={"code": f"HTTP_{status}", "message": message},
        )
        self.json({"error": message, "correlation_id": self.correlation_id}, status=status)

    def attachment(self, content: bytes, filename: str, content_type: str) -> None:
        """Returns a file as a download.

        Reports are served as attachments rather than wrapped in the JSON envelope
        so the browser writes a real file with a real name; embedding a 4 MiB log in
        a JSON string only to have the client re-materialise it wastes both ends.
        The filename is quoted and stripped of quotes/newlines because it reaches the
        client inside a header value.
        """
        safe_name = filename.replace('"', "").replace("\r", "").replace("\n", "")
        self.handler.send_response(200)
        self.handler.send_header("Content-Type", content_type)
        self.handler.send_header("Content-Length", str(len(content)))
        self.handler.send_header(
            "Content-Disposition", f'attachment; filename="{safe_name}"'
        )
        self._common_headers()
        self.handler.end_headers()
        self.handler.wfile.write(content)

    def attachment_file(self, path: str, filename: str, content_type: str) -> None:
        """Streams a file from disk as a download without reading it into memory."""
        safe_name = filename.replace('"', "").replace("\r", "").replace("\n", "")
        size = os.path.getsize(path)
        with open(path, "rb") as source:
            self.handler.send_response(200)
            self.handler.send_header("Content-Type", content_type)
            self.handler.send_header("Content-Length", str(size))
            self.handler.send_header(
                "Content-Disposition", f'attachment; filename="{safe_name}"'
            )
            self._common_headers()
            self.handler.end_headers()
            shutil.copyfileobj(source, self.handler.wfile, 1024 * 1024)

    def no_content(self, status: int = 204) -> None:
        self.handler.send_response(status)
        self._common_headers()
        self.handler.end_headers()

    def begin_sse(self) -> None:
        """Opens an SSE stream and signals connection close on completion."""
        self.handler.close_connection = True
        self.handler.send_response(200)
        self.handler.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.handler.send_header("Cache-Control", "no-cache")
        self.handler.send_header("Connection", "close")
        self.handler.send_header("X-Accel-Buffering", "no")
        self._common_headers()
        self.handler.end_headers()

    def sse(self, event: str, data: Dict[str, Any]) -> bool:
        """Writes one SSE frame. Returns False if the client disconnected."""
        frame = f"event: {event}\ndata: {json.dumps(data, default=str)}\n\n"
        try:
            self.handler.wfile.write(frame.encode("utf-8"))
            self.handler.wfile.flush()
            return True
        except (BrokenPipeError, ConnectionResetError):
            SERVER_LOGGER.warn(
                "Gateway",
                "sse.client_disconnected",
                correlation_id=self.correlation_id,
                details={"event": event},
            )
            return False


def read_json_body(handler: Any) -> Dict[str, Any]:
    """Parses a JSON request body, failing explicitly on malformed input."""
    length = int(handler.headers.get("Content-Length", 0) or 0)
    if length <= 0:
        return {}
    raw = handler.rfile.read(length).decode("utf-8")
    if not raw.strip():
        return {}
    return json.loads(raw)


def single(params: Dict[str, Any], key: str, default: Optional[str] = None) -> Optional[str]:
    """Extracts a single query-string value."""
    values = params.get(key)
    if not values:
        return default
    return values[0]


def require(params: Dict[str, Any], key: str) -> str:
    """Extracts a required query-string value or raises a descriptive error."""
    value = single(params, key)
    if value is None or value == "":
        raise ValueError(f"Missing required query parameter: '{key}'")
    return value
