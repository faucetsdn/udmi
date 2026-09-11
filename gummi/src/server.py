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
from gummi.src.console import GummiConsoleManager


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

    @property
    def console(self) -> GummiConsoleManager:
        return getattr(self.server, "console", None)

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
        try:
            # 1. System & Capabilities
            if path == "/api/system/capabilities":
                is_mock = getattr(self.server, "mock_mode", False)
                enable_mapping_seed = getattr(self.server, "enable_mapping_seed", False)
                features = [
                    "portfolio",
                    "device_explorer",
                    "device_detail",
                    "etcd_explorer",
                    "config_management",
                    "managed_rollout",
                    "bridgehead_admin",
                    "jetski_console",
                ]
                if enable_mapping_seed:
                    features.append("mapping_seed")
                caps = {
                    "environment": "MOCK_MODE" if is_mock else "LOCAL_BRIDGEHEAD",
                    "mock_mode": is_mock,
                    "enable_mapping_seed": enable_mapping_seed,
                    "auth_mode": "NO_AUTH",
                    "uufi_status": "MOCK_MODE" if is_mock else ("ACTIVE" if self.uufi.is_connected else "DISCONNECTED"),
                    "features": features,
                }
                return self._send_json(caps)

            # 2. Bridgehead & Infrastructure Status
            if path == "/api/bridgehead/status":
                health = self.db.check_component_health()
                return self._send_json(health)

            # 3. Portfolio Summary & Alerts
            if path == "/api/portfolio/summary":
                summary = self.db.get_portfolio_summary()
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
                        return self._send_json(
                            {"error": f"Device {dev_id} not found in registry {reg_id}"},
                            status_code=404,
                        )
                    return self._send_json(detail)

            # 6. Barbican / ETCD Registries & Explorer APIs (etcd_explorer parity)
            if path in ("/api/registries", "/api/registries/"):
                prefix = query.get("prefix", ["/r/"])[0]
                registries = self.db.get_registries(prefix=prefix)
                return self._send_json(registries)

            etcd_devs_match = re.match(r"^/api/registries/([^/]+)/devices/?$", path)
            if etcd_devs_match:
                reg_id = unquote(etcd_devs_match.group(1))
                devices = self.db.get_registry_devices(reg_id)
                return self._send_json(devices)

            etcd_props_match = re.match(r"^/api/registries/([^/]+)/devices/([^/]+)/properties/?$", path)
            if etcd_props_match:
                reg_id = unquote(etcd_props_match.group(1))
                dev_id = unquote(etcd_props_match.group(2))
                properties = self.db.get_device_etcd_properties(reg_id, dev_id)
                return self._send_json(properties)

            # 7. Legacy etcd_explorer URL redirect / compatibility
            if path in ("/etcd_explorer", "/etcd_explorer/", "/barbican_explorer", "/barbican_explorer/"):
                self.send_response(302)
                self.send_header("Location", "/#explorer")
                self.end_headers()
                return

            # 8. Managed Rollouts
            if path == "/api/rollouts":
                rollouts = self.db.list_rollouts()
                return self._send_json(rollouts)

            # 7. Real-Time Event Stream (Server-Sent Events)
            if path == "/api/stream/events":
                return self._handle_sse()

            # 8. Terminal Console Log & Status
            if path == "/api/project/term-log":
                offset = int(query.get("offset", ["0"])[0])
                if not self.console:
                    return self._send_json({"data": "", "offset": 0, "cleared": False, "running": False})
                res = self.console.get_log(offset=offset)
                return self._send_json(res)

            if path == "/api/project/status":
                try:
                    running = self.console.is_running() if self.console else False
                    session_name = self.console.session_name if self.console else "gummi~agent"
                    diag = self.console.get_diagnostics() if self.console else {
                        "state": "not_running",
                        "status_text": "Not Running",
                        "severity": "neutral",
                        "button_state": "blue",
                        "running": False,
                        "active": False,
                        "alert": None,
                    }
                    button_state = diag.get("button_state", "blue" if not running else "green")
                    return self._send_json({
                        "active_task": {
                            "name": "Jetski (GUMMI)",
                            "session": session_name,
                            "command": "jetski --repl_mode",
                            "start_time": time.time(),
                        } if running else None,
                        "running": running,
                        "session": session_name,
                        "diagnostics": diag,
                        "button_state": button_state,
                    })
                except Exception as e:
                    return self._send_json({
                        "running": False,
                        "session": getattr(self.console, "session_name", "gummi~agent"),
                        "diagnostics": {
                            "state": "error",
                            "status_text": "Error checking status",
                            "severity": "error",
                            "button_state": "red",
                            "running": False,
                            "active": False,
                            "alert": str(e),
                            "exit_code": 1,
                        },
                        "button_state": "red",
                    })

        except ConnectionError as e:
            return self._send_json({"error": "Service unavailable", "message": str(e)}, status_code=503)
        except Exception as e:
            return self._send_json({"error": "Internal server error", "message": str(e)}, status_code=500)

        # ----------------------------------------------------------------------
        # Fallback to Static Asset Serving
        # ----------------------------------------------------------------------
        return super().do_GET()

    def do_POST(self):
        """Routes POST requests."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        body = self._read_json_body()

        try:
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

                rollout = self.db.create_rollout(
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
                res = self.db.update_rollout(r_id, status="PAUSED" if action == "pause" else "CANCELLED")
                if res:
                    self.uufi.broadcast_event("rollout_progress", res)
                if res is None:
                    return self._send_json({"error": f"Rollout {r_id} not found"}, status_code=404)
                return self._send_json(res, status_code=200)

            # 4. Mapping Lifecycle Simulation & Seeder (/api/mapping/run)
            if path == "/api/mapping/run":
                if not getattr(self.server, "enable_mapping_seed", False):
                    return self._send_json(
                        {"error": "Forbidden: mapping seed feature is disabled. Start server with --enable-mapping-seed to activate."},
                        status_code=403,
                    )
                reg_id = body.get("registry_id", "ZZ-TRI-FECTA")
                result = self.db.populate_mapping_scenario(reg_id)
                return self._send_json(result, status_code=200)

            # 5. Terminal Console Operations
            if path == "/api/project/jetski":
                prompt = body.get("prompt")
                cols = body.get("cols")
                rows = body.get("rows")
                if not self.console:
                    return self._send_json({"error": "Console manager unavailable", "button_state": "red"}, status_code=503)
                res = self.console.start_jetski(prompt=prompt, cols=cols, rows=rows)
                status_code = 500 if res.get("status") == "error" else 200
                return self._send_json(res, status_code=status_code)

            if path == "/api/project/term-input":
                hex_keys = body.get("hexKeys") or []
                if not self.console:
                    return self._send_json({"error": "Console manager unavailable"}, status_code=503)
                ok, err = self.console.send_keys(hex_keys)
                if not ok:
                    code = 404 if "not exist" in err else 500
                    return self._send_json({"error": err}, status_code=code)
                return self._send_json({"status": "ok"}, status_code=200)

            if path == "/api/project/term-resize":
                cols = int(body.get("cols", 120))
                rows = int(body.get("rows", 30))
                if self.console:
                    self.console.resize(cols, rows)
                return self._send_json({"status": "resized"}, status_code=200)

            if path == "/api/project/term-kill":
                if self.console:
                    self.console.kill()
                return self._send_json({"status": "killed"}, status_code=200)

            return self._send_json({"error": "Not Found"}, status_code=404)

        except ConnectionError as e:
            return self._send_json({"error": "Service unavailable", "message": str(e)}, status_code=503)
        except RuntimeError as e:
            return self._send_json({"error": "Operation failed", "message": str(e)}, status_code=502)
        except Exception as e:
            return self._send_json({"error": "Internal server error", "message": str(e)}, status_code=500)

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
        mock_mode: bool = False,
        enable_mapping_seed: bool = False,
        barbican_port: Optional[int] = None,
        barbican_endpoint: Optional[str] = None,
        barbican_client: Optional[Any] = None,
        butler_port: Optional[int] = None,
        butler_endpoint: Optional[str] = None,
        butler_client: Optional[Any] = None,
        uufi_port: Optional[int] = None,
        uufi_endpoint: Optional[str] = None,
        uufi_client: Optional[Any] = None,
        **kwargs,
    ):
        self.host = host
        self.port = port
        self.mock_mode = mock_mode
        self.enable_mapping_seed = enable_mapping_seed
        self.barbican_port = barbican_port or 8085
        self.butler_port = butler_port or 8088
        self.uufi_port = uufi_port or 8087
        self.barbican_endpoint = barbican_endpoint or os.environ.get("BARBICAN_ENDPOINT")
        self.butler_endpoint = butler_endpoint or os.environ.get("BUTLER_ENDPOINT")
        self.uufi_endpoint = uufi_endpoint or os.environ.get("UUFI_ENDPOINT")

        self.uufi = GummiUUFIClient(
            uufi_port=self.uufi_port,
            uufi_endpoint=self.uufi_endpoint,
            uufi_client=uufi_client,
            mock_mode=mock_mode,
        )
        self.db = GummiDB(
            butler_client=butler_client,
            barbican_client=barbican_client,
            uufi_client=self.uufi.uufi,
            butler_port=self.butler_port,
            barbican_port=self.barbican_port,
            uufi_port=self.uufi_port,
            butler_endpoint=self.butler_endpoint,
            barbican_endpoint=self.barbican_endpoint,
            uufi_endpoint=self.uufi_endpoint,
            mock_mode=mock_mode,
        )
        self.uufi.db = self.db
        self.console = GummiConsoleManager(
            session_name="gummi~agent",
            mock_mode=mock_mode,
        )
        self.httpd: Optional[ThreadingHTTPServer] = None

    def start(self) -> None:
        """Starts background workers and HTTP server."""
        self.uufi.start()
        server_address = (self.host, self.port)
        self.httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
        self.httpd.daemon_threads = True
        self.httpd.mock_mode = self.mock_mode
        self.httpd.enable_mapping_seed = self.enable_mapping_seed
        self.httpd.db = self.db
        self.httpd.uufi = self.uufi
        self.httpd.console = self.console
        mode_str = " [MOCK MODE]" if self.mock_mode else ""
        seed_str = " [MAPPING SEED ENABLED]" if self.enable_mapping_seed else ""
        print(f"GUMMI Server listening on http://{self.host}:{self.port}{mode_str}{seed_str}")
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
    raw_port = os.environ.get("GUMMI_PORT", "8080")
    if ":" in raw_port:
        raw_port = raw_port.split(":")[-1]
    try:
        default_port = int(raw_port)
    except (ValueError, TypeError):
        default_port = 8080
    parser = argparse.ArgumentParser(description="GUMMI Fleet Management Web Server")
    parser.add_argument("--port", "-p", type=int, default=default_port, help="HTTP server port")
    parser.add_argument("--host", default=os.environ.get("GUMMI_HOST", "0.0.0.0"), help="HTTP bind address")
    parser.add_argument(
        "--mock",
        action="store_true",
        default=False,
        help="Run in Mock Mode without connecting to live backend databases or services",
    )
    parser.add_argument(
        "--enable-mapping-seed",
        action="store_true",
        default=False,
        help="Enable synthetic mapping lifecycle seeding button and API endpoint",
    )
    parser.add_argument("--barbican-port", type=int, default=8085, help="Barbican MCP server port (default: 8085)")
    parser.add_argument("--barbican-endpoint", default=os.environ.get("BARBICAN_ENDPOINT"), help="Barbican MCP server endpoint URL")
    parser.add_argument("--butler-port", type=int, default=8088, help="Butler MCP server port (default: 8088)")
    parser.add_argument("--butler-endpoint", default=os.environ.get("BUTLER_ENDPOINT"), help="Butler MCP server endpoint URL")
    parser.add_argument("--uufi-port", type=int, default=8087, help="UUFI MCP server port (default: 8087)")
    parser.add_argument("--uufi-endpoint", default=os.environ.get("UUFI_ENDPOINT"), help="UUFI MCP server endpoint URL")
    args = parser.parse_args()

    server = GummiServer(
        host=args.host,
        port=args.port,
        mock_mode=args.mock,
        enable_mapping_seed=args.enable_mapping_seed,
        barbican_port=args.barbican_port,
        barbican_endpoint=args.barbican_endpoint,
        butler_port=args.butler_port,
        butler_endpoint=args.butler_endpoint,
        uufi_port=args.uufi_port,
        uufi_endpoint=args.uufi_endpoint,
    )
    server.start()


if __name__ == "__main__":
    main()
