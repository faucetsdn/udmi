"""HTTP Web and REST API Server for GUMMI."""

from http.server import HTTPServer, SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import mimetypes
import os
import queue
import re
import sys
import threading
import time
from typing import Any, Dict, List, Optional, Union
from urllib.parse import parse_qs, unquote, urlparse

from gummi.src.db import GummiDB
from gummi.src.uufi import GummiUUFIClient


class GummiRequestHandler(SimpleHTTPRequestHandler):
    """Custom HTTP request handler serving GUMMI static files and REST API routes."""

    server_version = "GUMMI/1.0"

    def __init__(self, *args, **kwargs):
        # Set static directory root
        static_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), "static")
        super().__init__(*args, directory=static_dir, **kwargs)

    @property
    def db(self) -> GummiDB:
        return self.server.db

    @property
    def uufi(self) -> GummiUUFIClient:
        return self.server.uufi

    def _send_json(self, data: Any, status_code: int = 200) -> None:
        """Helper to send JSON responses with CORS headers."""
        payload = json.dumps(data, default=str).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()
        self.wfile.write(payload)

    def _read_json_body(self) -> Dict[str, Any]:
        """Helper to parse JSON request body."""
        content_len = int(self.headers.get("Content-Length", 0))
        if content_len == 0:
            return {}
        raw = self.rfile.read(content_len).decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def do_OPTIONS(self):
        """Handles CORS preflight requests."""
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        """Routes GET requests to API handlers or static files."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        query = parse_qs(parsed_url.query)

        # ----------------------------------------------------------------------
        # REST API Routes
        # ----------------------------------------------------------------------

        # 1. System & Capabilities
        if path == "/api/system/capabilities":
            caps = {
                "environment": "LOCAL_BRIDGEHEAD",
                "auth_mode": "NO_AUTH",
                "uufi_status": "ACTIVE" if self.uufi.is_connected else "DISCONNECTED",
                "features": [
                    "portfolio",
                    "devices",
                    "config_management",
                    "managed_rollout",
                    "bridgehead_admin",
                ],
            }
            return self._send_json(caps)

        # 2. Bridgehead & Infrastructure Status
        if path == "/api/bridgehead/status":
            health = self.db.check_component_health()
            return self._send_json(health)

        # 3. Portfolio Summary & Alerts
        if path == "/api/portfolio/summary":
            summary = self.db.get_portfolio_summary()
            summary["active_rollouts_count"] = len([r for r in self.uufi.list_rollouts() if r.get("status") == "RUNNING"])
            return self._send_json(summary)

        if path == "/api/portfolio/alerts":
            limit = int(query.get("limit", ["50"])[0])
            min_level = int(query.get("min_level", ["500"])[0])
            alerts = self.db.get_alerts(limit=limit, min_level=min_level)
            return self._send_json({"alerts": alerts})

        # 4. Devices Explorer
        if path == "/api/devices":
            limit = int(query.get("limit", ["100"])[0])
            offset = int(query.get("offset", ["0"])[0])
            reg_id = query.get("registry_id", [None])[0]
            dev_prefix = query.get("device_prefix", [None])[0]
            make = query.get("make", [None])[0]
            model = query.get("model", [None])[0]
            status = query.get("status", [None])[0]
            search = query.get("search", [None])[0]

            res = self.db.get_devices(
                limit=limit,
                offset=offset,
                registry_id=reg_id,
                device_prefix=dev_prefix,
                make=make,
                model=model,
                status=status,
                search=search,
            )
            return self._send_json(res)

        # 5. Device Detail, Telemetry & Message Lifecycle
        dev_match = re.match(r"^/api/devices/([^/]+)/([^/]+)(/(telemetry|messages))?$", path)
        if dev_match:
            reg_id = unquote(dev_match.group(1))
            dev_id = unquote(dev_match.group(2))
            sub_path = dev_match.group(4)

            if sub_path == "telemetry":
                pts_raw = query.get("points", [""])[0]
                points = [p.strip() for p in pts_raw.split(",") if p.strip()]
                start = query.get("start", ["-1h"])[0]
                stop = query.get("stop", ["now()"])[0]
                telem = self.db.get_device_telemetry(reg_id, dev_id, points, start=start, stop=stop)
                return self._send_json(telem)
            elif sub_path == "messages":
                messages = self.db.get_device_messages(reg_id, dev_id)
                return self._send_json({"registry_id": reg_id, "device_id": dev_id, "messages": messages})
            else:
                detail = self.db.get_device_detail(reg_id, dev_id)
                if detail is None:
                    # Provide fallback/placeholder
                    detail = {
                        "registry_id": reg_id,
                        "device_id": dev_id,
                        "metadata": {"make": "Unknown", "model": "Unknown", "last_seen": None},
                        "state": {"system": {}, "pointset": {"points": {}}},
                        "config": {"system": {}},
                        "events": [],
                    }
                return self._send_json(detail)

        # 6. Managed Rollouts
        if path == "/api/rollouts":
            rollouts = self.uufi.list_rollouts()
            return self._send_json(rollouts)

        # 7. Real-Time Event Stream (Server-Sent Events)
        if path == "/api/stream/events":
            return self._handle_sse()

        # ----------------------------------------------------------------------
        # Fallback to Static Asset Serving
        # ----------------------------------------------------------------------
        return super().do_GET()

    def do_POST(self):
        """Routes POST requests."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        body = self._read_json_body()

        # 1. Device Configuration Mutation (/api/devices/<reg>/<dev>/config)
        cfg_match = re.match(r"^/api/devices/([^/]+)/([^/]+)/config$", path)
        if cfg_match:
            reg_id = unquote(cfg_match.group(1))
            dev_id = unquote(cfg_match.group(2))
            sub_folder = body.get("sub_folder", "system")
            payload = body.get("payload", {})

            res = self.uufi.publish_config(reg_id, dev_id, sub_folder, payload)
            return self._send_json(res, status_code=200)

        # 2. Managed Rollout Creation (/api/rollouts)
        if path == "/api/rollouts":
            name = body.get("name", "Untitled Rollout")
            target_filter = body.get("target_filter", {})
            target_payload = body.get("target_payload", {})
            target_subfolder = body.get("target_subfolder", "system")
            batch_size = int(body.get("batch_size", 10))
            batch_interval_sec = int(body.get("batch_interval_sec", 60))

            rollout = self.uufi.create_rollout(
                name=name,
                target_filter=target_filter,
                target_payload=target_payload,
                target_subfolder=target_subfolder,
                batch_size=batch_size,
                batch_interval_sec=batch_interval_sec,
            )
            return self._send_json(rollout, status_code=201)

        # 3. Rollout Controls (/api/rollouts/<id>/pause or cancel)
        ctrl_match = re.match(r"^/api/rollouts/(\d+)/(pause|cancel)$", path)
        if ctrl_match:
            r_id = int(ctrl_match.group(1))
            action = ctrl_match.group(2)
            if action == "pause":
                res = self.uufi.pause_rollout(r_id)
            else:
                res = self.uufi.cancel_rollout(r_id)
            return self._send_json(res, status_code=200)

        # 4. Mapping Lifecycle Simulation & Seeder (/api/mapping/run or /api/mapping/seed)
        if path in ("/api/mapping/run", "/api/mapping/seed"):
            reg_id = body.get("registry_id", "ZZ-TRI-FECTA")
            result = self.db.populate_mapping_scenario(reg_id)
            return self._send_json(result, status_code=200)

        return self._send_json({"error": "Not Found"}, status_code=404)

    def _handle_sse(self) -> None:
        """Handles Server-Sent Events subscription."""
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

        event_q = self.uufi.register_sse_subscriber()
        # Send initial connection event
        self.wfile.write(b"event: connected\ndata: {\"status\": \"STREAM_OPEN\"}\n\n")
        self.wfile.flush()

        try:
            while True:
                try:
                    msg = event_q.get(timeout=15.0)
                    self.wfile.write(msg.encode("utf-8"))
                    self.wfile.flush()
                except queue.Empty:
                    # Heartbeat comment to keep connection alive
                    self.wfile.write(b": heartbeat\n\n")
                    self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            self.uufi.unregister_sse_subscriber(event_q)


class GummiServer:
    """GUMMI Application Server encapsulating HTTP web tier and background workers."""

    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 8080,
        project_spec: Optional[str] = None,
        site_model: Optional[str] = None,
    ):
        self.host = host
        self.port = port
        self.project_spec = project_spec
        self.site_model = site_model

        self.db = GummiDB()
        self.uufi = GummiUUFIClient(project_spec=project_spec, site_model=site_model)
        self.httpd: Optional[ThreadingHTTPServer] = None

    def start(self) -> None:
        """Starts background workers and HTTP server."""
        self.uufi.start()
        server_address = (self.host, self.port)
        self.httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
        self.httpd.daemon_threads = True
        self.httpd.db = self.db
        self.httpd.uufi = self.uufi
        print(f"GUMMI Server listening on http://{self.host}:{self.port}")
        try:
            self.httpd.serve_forever()
        except KeyboardInterrupt:
            self.stop()

    def stop(self) -> None:
        """Gracefully terminates server."""
        if self.httpd:
            self.httpd.shutdown()
        self.uufi.stop()
        print("GUMMI Server stopped.")


def main():
    import argparse
    parser = argparse.ArgumentParser(description="GUMMI Fleet Management Web Server")
    parser.add_argument("--port", "-p", type=int, default=int(os.environ.get("GUMMI_PORT", "8080")), help="HTTP server port")
    parser.add_argument("--host", default=os.environ.get("GUMMI_HOST", "0.0.0.0"), help="HTTP bind address")
    parser.add_argument("--project-spec", default=os.environ.get("TARGET_PROJECT", "//mqtt/localhost"), help="Target project spec")
    parser.add_argument("--site-model", default=os.environ.get("SITE_MODEL", "sites/udmi_site_model"), help="Site model directory")
    args = parser.parse_args()

    server = GummiServer(
        host=args.host,
        port=args.port,
        project_spec=args.project_spec,
        site_model=args.site_model,
    )
    server.start()


if __name__ == "__main__":
    main()
