#!/usr/bin/env python3
"""UDMI Workbench HTTP/SSE Gateway (Layer 4 entrypoint).

Serves the Workbench single-page application and the JSON/SSE API backed by
real repository data and real `bin/sequencer` executions.

Fixes carried over from the v1 server:
  * `--port` is actually honoured (v1 parsed only `--features`, so the
    launcher's port selection silently had no effect).
  * Threaded request handling, so a long-running stream cannot block the UI.
  * Errors return CORS and correlation headers, matching success responses.
"""

import argparse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import sys
from urllib.parse import urlparse

from mantis import provider_setup
from mantis.mcp_server import MCPServer
from mantis.session import SessionManager
from mantis.tools.registry import get_mcp_tools
from workbench.server.http_util import HttpResponder
from workbench.server.logger import SERVER_LOGGER
from workbench.server.mantis_adapter import MantisSessionStore
from workbench.server.notifications import Notifier
from workbench.server.routes import ApiRoutes, classify_exception
from workbench.server.runner import SequencerRunner
from workbench.server.site_roots import SiteRootRegistry
from workbench.server.testbed import TestbedManager

STATIC_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "static"))
# Paths the shell owns. A deep link to any of these must return index.html so
# the client router can resolve it; otherwise a reload lands on a 404. The
# assistant and diagnostics tabs became drawers, and `/logs` is the linkable
# path that raises the log drawer over whichever view is mounted.
SPA_ROUTES = ("/sequencer", "/devices", "/logs")


class WorkbenchGateway(ThreadingHTTPServer):
    """Threaded HTTP server owning all shared Workbench services."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, server_address, handler_class, udmi_root: str, config_path: str = None):
        super().__init__(server_address, handler_class)
        self.udmi_root = os.path.abspath(udmi_root)
        self.session_mgr = SessionManager(udmi_root=self.udmi_root)
        self.mcp_server = MCPServer(session_mgr=self.session_mgr)
        self.notifier = Notifier(config_path=config_path)
        self.mantis_store = MantisSessionStore(session_mgr=self.session_mgr, notifier=self.notifier)
        self.runner = SequencerRunner(self.udmi_root)
        self.testbed = TestbedManager(self.udmi_root)
        self.site_roots = SiteRootRegistry(self.udmi_root, config_path=config_path)
        self.routes = ApiRoutes(self)

    def mcp_tool_names(self):
        return [tool["name"] for tool in get_mcp_tools()]

    def server_close(self):
        self.runner.shutdown()
        self.testbed.shutdown()
        super().server_close()


class WorkbenchRequestHandler(SimpleHTTPRequestHandler):
    """Routes API requests and serves the SPA from workbench/static."""

    server_version = "UDMI-Workbench/3.0"
    protocol_version = "HTTP/1.1"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=STATIC_DIR, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, format, *args):
        """Access logs are emitted as structured records instead of stderr text."""

    def _responder(self) -> HttpResponder:
        correlation_id = (
            self.headers.get("X-Correlation-ID") or SERVER_LOGGER.new_correlation_id()
        )
        return HttpResponder(self, correlation_id)

    def do_OPTIONS(self):
        self._responder().no_content()

    def do_GET(self):
        parsed = urlparse(self.path)
        responder = self._responder()

        if parsed.path.startswith("/api/"):
            try:
                if not self.server.routes.handle_get(parsed.path, parsed.query, responder):
                    responder.error(f"Unknown API endpoint: {parsed.path}", 404)
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception as exc:
                try:
                    responder.error(f"{exc.__class__.__name__}: {exc}", classify_exception(exc))
                except (BrokenPipeError, ConnectionResetError):
                    pass
            return

        if parsed.path == "/" or any(
            parsed.path == route or parsed.path.startswith(route + "/") for route in SPA_ROUTES
        ):
            self.path = "/index.html"
        try:
            super().do_GET()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        parsed = urlparse(self.path)
        responder = self._responder()
        try:
            if not self.server.routes.handle_post(parsed.path, responder):
                responder.error(f"Unknown API endpoint: {parsed.path}", 404)
        except json.JSONDecodeError as exc:
            responder.error(f"Malformed JSON body: {exc}", 400)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as exc:
            try:
                responder.error(f"{exc.__class__.__name__}: {exc}", classify_exception(exc))
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_DELETE(self):
        parsed = urlparse(self.path)
        responder = self._responder()
        try:
            if not self.server.routes.handle_delete(parsed.path, parsed.query, responder):
                responder.error(f"Unknown API endpoint: {parsed.path}", 404)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as exc:
            try:
                responder.error(f"{exc.__class__.__name__}: {exc}", classify_exception(exc))
            except (BrokenPipeError, ConnectionResetError):
                pass


def create_gateway(
    host: str = "127.0.0.1",
    port: int = 8080,
    udmi_root: str = None,
    config_path: str = None,
) -> WorkbenchGateway:
    """Builds a bound gateway instance (port 0 selects an ephemeral port)."""
    root = udmi_root or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    return WorkbenchGateway((host, port), WorkbenchRequestHandler, root, config_path=config_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="UDMI Workbench Gateway")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address (default: 127.0.0.1)")
    parser.add_argument("--port", "-p", type=int, default=8080, help="Bind port (default: 8080)")
    parser.add_argument("--udmi-root", default=None, help="UDMI repository root")
    args = parser.parse_args()

    # The Mantis agent runs in this process: export the saved provider setup
    # (`bin/mantis setup`) here, refusing to start if it contradicts the environment.
    problem = provider_setup.apply_to_environment()
    if problem:
        print(f"Error: {problem}", file=sys.stderr)
        sys.exit(1)

    gateway = create_gateway(host=args.host, port=args.port, udmi_root=args.udmi_root)
    bound_port = gateway.server_address[1]
    print(f"UDMI Workbench serving http://{args.host}:{bound_port}")
    print(f"Repository root: {gateway.udmi_root}")
    try:
        gateway.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down; terminating active sequencer sessions.")
    finally:
        gateway.server_close()


if __name__ == "__main__":
    main()
