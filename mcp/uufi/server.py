#!/usr/bin/env python3
"""UDMI UUFI MCP Server & RPC Daemon.

Implements the Unified UDMI Functional Interface (UUFI) external-facing messaging
specification (docs/specs/uufi.md) over MCP JSON-RPC 2.0 (stdio and HTTP).

Supports:
1. Standard MCP JSON-RPC 2.0 protocol over stdio for AI agent tool calling.
2. HTTP JSON-RPC 2.0 endpoint (POST /rpc, POST /) for headless inter-service RPC.
3. Health probe endpoint (GET /health).
4. Direct CLI inspection, handshaking, and event polling.
"""

import argparse
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
import sys
from typing import Any, Dict, Optional
import urllib.parse

from mcp.uufi.provider import UUFIProvider


MCP_TOOLS = [
    {
        "name": "health",
        "description": "Check connection health, latency, and status of the UUFI broker and session.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
    {
        "name": "handshake",
        "description": (
            "Execute Layer 1 UUFI service handshake on /uufi/c/state/udmi to establish "
            "communication presence, negotiate capabilities, and discover registry ID."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "functions_ver": {
                    "type": "integer",
                    "description": "Client functional capability version (default: 9)",
                },
                "timeout_sec": {
                    "type": "number",
                    "description": "Handshake response timeout in seconds (default: 10.0)",
                },
            },
        },
    },
    {
        "name": "publish_config",
        "description": (
            "Publish device configuration update to the UUFI bus "
            "(subType: config) with mandatory envelope headers and QoS 1."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Target device registry identifier (e.g. 'ZZ-TRI-FECTA')",
                },
                "device_id": {
                    "type": "string",
                    "description": "Target device identifier (e.g. 'AHU-1')",
                },
                "sub_folder": {
                    "type": "string",
                    "description": "Configuration subFolder (e.g. 'system', 'pointset', 'blobset')",
                },
                "payload": {
                    "type": "object",
                    "description": "UDMI-compliant configuration payload dictionary",
                },
            },
            "required": ["registry_id", "device_id", "sub_folder", "payload"],
        },
    },
    {
        "name": "query_state",
        "description": (
            "Query live device state on the UUFI bus (subType: query, subFolder: state) "
            "and await correlated state response matching transactionId."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Target device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Target device identifier",
                },
                "sub_folder": {
                    "type": "string",
                    "description": "Target state subFolder (e.g. 'blobset', 'pointset', 'system')",
                },
                "timeout_sec": {
                    "type": "number",
                    "description": "Query response timeout in seconds (default: 15.0 per uufi.md)",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "query_system_model",
        "description": (
            "Query device system model on the UUFI bus (subType: query, subFolder: system) "
            "and await correlated model/system reply."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Target device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Target device identifier",
                },
                "timeout_sec": {
                    "type": "number",
                    "description": "Query response timeout in seconds (default: 15.0)",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "update_system_model",
        "description": (
            "Publish partial-merge system model update for a device on the UUFI bus "
            "(subType: model, subFolder: system) conforming to schema/model_system.json."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Target device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Target device identifier",
                },
                "payload": {
                    "type": "object",
                    "description": "Flat model_system payload dictionary (e.g. {'software': {'system': '2.0.0'}})",
                },
            },
            "required": ["registry_id", "device_id", "payload"],
        },
    },
    {
        "name": "poll_events",
        "description": (
            "Poll inbound device messages (state reports, pointset telemetry, discovery events, "
            "status/error reports) delivered over the UUFI bus."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "cursor": {
                    "type": "integer",
                    "description": "Event ID cursor to read events from (default: 0)",
                },
                "timeout_sec": {
                    "type": "number",
                    "description": "Maximum seconds to wait for new events (default: 0.0)",
                },
                "sub_types": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Optional filter for subTypes (e.g. ['state', 'events'])",
                },
                "sub_folders": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Optional filter for subFolders (e.g. ['pointset', 'status', 'discovery'])",
                },
                "registry_id": {
                    "type": "string",
                    "description": "Optional filter for registry ID",
                },
                "device_id": {
                    "type": "string",
                    "description": "Optional filter for device ID",
                },
            },
        },
    },
]


class UUFIMcpServer:
    """UUFI MCP Server handling JSON-RPC 2.0 requests over stdio and HTTP."""

    def __init__(self, provider: Optional[UUFIProvider] = None):
        self.provider = provider or UUFIProvider()

    def handle_request(self, req: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Processes a single JSON-RPC 2.0 request dictionary."""
        req_id = req.get("id")
        method = req.get("method")
        params = req.get("params") or {}

        if not method:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32600, "message": "Invalid Request: missing method"},
            }

        # 1. MCP initialize handshake
        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {"listChanged": False},
                    },
                    "serverInfo": {
                        "name": "uufi-mcp",
                        "version": "1.0.0",
                    },
                },
            }

        if method == "notifications/initialized":
            return None

        # 2. MCP tools/list
        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": MCP_TOOLS},
            }

        # 3. MCP tools/call
        if method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments") or {}
            try:
                result_data = self._dispatch_tool(tool_name, tool_args)
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": (
                                    json.dumps(result_data)
                                    if not isinstance(result_data, str)
                                    else result_data
                                ),
                            }
                        ],
                        "isError": False,
                    },
                }
            except Exception as e:
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [{"type": "text", "text": f"Error executing {tool_name}: {str(e)}"}],
                        "isError": True,
                    },
                }

        # 4. Direct method routing
        try:
            res = self._dispatch_tool(method, params)
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": res,
            }
        except ValueError:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32601, "message": f"Method not found: {method}"},
            }
        except Exception as e:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32603, "message": f"Internal error: {str(e)}"},
            }

    def _dispatch_tool(self, name: str, args: Dict[str, Any]) -> Any:
        """Executes a tool or method against UUFIProvider."""
        if name == "health":
            return self.provider.health()
        if name == "handshake":
            return self.provider.handshake(
                functions_ver=int(args.get("functions_ver", 9)),
                timeout_sec=float(args.get("timeout_sec", 10.0)),
            )
        if name == "publish_config":
            return self.provider.publish_config(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                sub_folder=args["sub_folder"],
                payload=args["payload"],
            )
        if name == "query_state":
            return self.provider.query_state(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                sub_folder=args.get("sub_folder", "blobset"),
                timeout_sec=float(args.get("timeout_sec", 15.0)),
            )
        if name == "query_system_model":
            return self.provider.query_system_model(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                timeout_sec=float(args.get("timeout_sec", 15.0)),
            )
        if name == "update_system_model":
            return self.provider.update_system_model(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                payload=args["payload"],
            )
        if name == "poll_events":
            return self.provider.poll_events(
                cursor=int(args.get("cursor", 0)),
                timeout_sec=float(args.get("timeout_sec", 0.0)),
                sub_types=args.get("sub_types"),
                sub_folders=args.get("sub_folders"),
                registry_id=args.get("registry_id"),
                device_id=args.get("device_id"),
            )
        raise ValueError(f"Unknown tool or method: {name}")

    def run_stdio(self) -> None:
        """Standard MCP stdio loop."""
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            try:
                request = json.loads(line)
                response = self.handle_request(request)
                if response is not None:
                    sys.stdout.write(json.dumps(response) + "\n")
                    sys.stdout.flush()
            except Exception as e:
                err_resp = {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32603, "message": str(e)},
                }
                sys.stdout.write(json.dumps(err_resp) + "\n")
                sys.stdout.flush()


class UUFIMcpHttpHandler(BaseHTTPRequestHandler):
    """HTTP handler supporting JSON-RPC 2.0 and health endpoints."""

    server_instance: Optional[UUFIMcpServer] = None

    def log_message(self, fmt: str, *args: Any) -> None:
        pass

    def send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
        self.send_header("Access-Control-Allow-Headers", "*")

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self.send_cors_headers()
        self.end_headers()

    def do_POST(self) -> None:
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            req_data = json.loads(body)
            resp = self.server_instance.handle_request(req_data)
            resp_bytes = json.dumps(resp).encode("utf-8") if resp else b""
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(resp_bytes)))
            self.send_cors_headers()
            self.end_headers()
            if resp_bytes:
                self.wfile.write(resp_bytes)
        except Exception as e:
            err_bytes = json.dumps(
                {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": str(e)}}
            ).encode("utf-8")
            self.send_response(500)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(err_bytes)))
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(err_bytes)

    def do_HEAD(self) -> None:
        self.do_GET(is_head=True)

    def do_GET(self, is_head: bool = False) -> None:
        parsed_url = urllib.parse.urlparse(self.path)
        path = parsed_url.path

        if path == "/health":
            health_data = self.server_instance.provider.health()
            content = json.dumps(health_data).encode("utf-8")
            status = 200 if health_data.get("status") in ("UP", "REACHABLE") else 503
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_cors_headers()
            self.end_headers()
            if not is_head:
                self.wfile.write(content)
            return

        if path == "/api/events":
            query = urllib.parse.parse_qs(parsed_url.query)
            cursor = int(query.get("cursor", ["0"])[0])
            events_data = self.server_instance.provider.poll_events(cursor=cursor)
            content = json.dumps(events_data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_cors_headers()
            self.end_headers()
            if not is_head:
                self.wfile.write(content)
            return

        self.send_response(404)
        self.send_header("Content-Type", "text/plain")
        self.send_cors_headers()
        self.end_headers()
        if not is_head:
            self.wfile.write(b"Not Found")


def run_http_server(
    provider: UUFIProvider,
    port: int = 8087,
    host: str = "127.0.0.1",
) -> None:
    """Starts standalone HTTP JSON-RPC 2.0 daemon."""
    server = UUFIMcpServer(provider)
    UUFIMcpHttpHandler.server_instance = server

    httpd = HTTPServer((host, port), UUFIMcpHttpHandler)
    print(f"UUFI MCP Server listening on http://{host}:{port}/rpc", file=sys.stderr)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        provider.disconnect()


def main():
    parser = argparse.ArgumentParser(description="UDMI UUFI MCP Server")
    subparsers = parser.add_subparsers(dest="command")

    # serve command
    serve_parser = subparsers.add_parser("serve", help="Start HTTP JSON-RPC 2.0 server daemon")
    serve_parser.add_argument("--port", type=int, default=8087, help="Listen port (default: 8087)")
    serve_parser.add_argument("--host", default="127.0.0.1", help="Listen host (default: 127.0.0.1)")
    serve_parser.add_argument("--project-spec", default=None, help="Target project spec")
    serve_parser.add_argument("--site-model", default=None, help="Site model directory")

    # health command
    health_parser = subparsers.add_parser("health", help="Check UUFI connection health")
    health_parser.add_argument("--project-spec", default=None, help="Target project spec")

    # handshake command
    hs_parser = subparsers.add_parser("handshake", help="Execute Layer 1 handshake")
    hs_parser.add_argument("--project-spec", default=None, help="Target project spec")
    hs_parser.add_argument("--functions-ver", type=int, default=9, help="Functional version")

    # publish-config command
    pub_parser = subparsers.add_parser("publish-config", help="Publish device configuration")
    pub_parser.add_argument("registry_id", help="Device registry ID")
    pub_parser.add_argument("device_id", help="Device ID")
    pub_parser.add_argument("sub_folder", help="Configuration subFolder (e.g. system, pointset)")
    pub_parser.add_argument("payload", help="JSON payload or filepath")
    pub_parser.add_argument("--project-spec", default=None, help="Target project spec")

    # poll-events command
    poll_parser = subparsers.add_parser("poll-events", help="Poll inbound messages from bus")
    poll_parser.add_argument("--cursor", type=int, default=0, help="Starting event cursor")
    poll_parser.add_argument("--timeout", type=float, default=2.0, help="Wait timeout seconds")
    poll_parser.add_argument("--project-spec", default=None, help="Target project spec")

    args = parser.parse_args()

    if args.command == "serve":
        provider = UUFIProvider(
            project_spec=args.project_spec,
            site_model=args.site_model,
            auto_connect=True,
        )
        run_http_server(provider, port=args.port, host=args.host)
        return

    if args.command == "health":
        provider = UUFIProvider(project_spec=args.project_spec, auto_connect=True)
        print(json.dumps(provider.health(), indent=2))
        provider.disconnect()
        return

    if args.command == "handshake":
        provider = UUFIProvider(project_spec=args.project_spec, auto_connect=True)
        try:
            res = provider.handshake(functions_ver=args.functions_ver)
            print(json.dumps(res, indent=2))
        except Exception as e:
            print(f"Handshake failed: {e}", file=sys.stderr)
            sys.exit(1)
        finally:
            provider.disconnect()
        return

    if args.command == "publish-config":
        provider = UUFIProvider(project_spec=args.project_spec, auto_connect=True)
        try:
            payload_data = json.loads(args.payload)
        except Exception:
            if os.path.exists(args.payload):
                with open(args.payload, "r", encoding="utf-8") as f:
                    payload_data = json.load(f)
            else:
                print("Error: payload must be valid JSON or file path", file=sys.stderr)
                sys.exit(1)
        res = provider.publish_config(args.registry_id, args.device_id, args.sub_folder, payload_data)
        print(json.dumps(res, indent=2))
        provider.disconnect()
        return

    if args.command == "poll-events":
        provider = UUFIProvider(project_spec=args.project_spec, auto_connect=True)
        events = provider.poll_events(cursor=args.cursor, timeout_sec=args.timeout)
        print(json.dumps(events, indent=2))
        provider.disconnect()
        return

    # Default to stdio mode if stdin is piped or no command supplied
    provider = UUFIProvider(auto_connect=True)
    server = UUFIMcpServer(provider)
    server.run_stdio()


if __name__ == "__main__":
    main()
