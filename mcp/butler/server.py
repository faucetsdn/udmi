#!/usr/bin/env python3
"""UDMI Butler MCP Server & RPC Daemon.

Supports:
1. Standard MCP JSON-RPC 2.0 protocol over stdio for AI agent tool calling.
2. HTTP JSON-RPC 2.0 endpoint (POST /rpc, POST /) for headless inter-service RPC.
3. Health probe endpoint (GET /health).
4. Direct CLI inspection and querying.
"""

import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
import sys
from typing import Any, Dict, List, Optional
import urllib.parse

from mcp.butler.provider import ButlerProvider


MCP_TOOLS = [
    {
        "name": "health",
        "description": "Check connection health and status of the Butler datastores.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
    {
        "name": "get_discovery_events",
        "description": "Query discovery events for a device registry to support reconciliation mapping.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The target device registry identifier (e.g. 'ZZ-TRI-FECTA')",
                }
            },
            "required": ["registry_id"],
        },
    },
    {
        "name": "get_discovered_devices",
        "description": (
            "Retrieve normalized discovered devices with addresses (BACnet, IPv4, vendor) "
            "and gateway relationships for mapping reconciliation."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The target device registry identifier",
                }
            },
            "required": ["registry_id"],
        },
    },
    {
        "name": "get_device_messages",
        "description": (
            "Retrieve chronological message lifecycle history (base model, discovery events, "
            "and proposals) for a specific device."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The target device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "The target device identifier (e.g. 'AHU-22')",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "record_message",
        "description": (
            "Record a device model, discovery event, or proposal message into the "
            "Butler message lifecycle store."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Device identifier",
                },
                "sub_type": {
                    "type": "string",
                    "description": "Message subType (e.g. 'model', 'events', 'propose')",
                },
                "sub_folder": {
                    "type": "string",
                    "description": "Message subFolder (e.g. 'system', 'discovery', 'localnet', 'pointset')",
                },
                "payload": {
                    "type": "object",
                    "description": "Structured message payload",
                },
                "project_id": {
                    "type": "string",
                    "description": "Optional project identifier",
                },
                "timestamp": {
                    "type": "string",
                    "description": "Optional ISO 8601 message timestamp",
                },
            },
            "required": ["registry_id", "device_id", "sub_type", "sub_folder", "payload"],
        },
    },
    {
        "name": "get_device_telemetry",
        "description": "Query time-series point telemetry values for a device over a time window.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Device identifier",
                },
                "point_names": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Optional list of point telemetry names to filter by",
                },
                "start": {
                    "type": "string",
                    "description": "Query start window (default: '-1h')",
                    "default": "-1h",
                },
                "stop": {
                    "type": "string",
                    "description": "Query stop window (default: 'now()')",
                    "default": "now()",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "write_telemetry",
        "description": "Record point telemetry measurements for a device.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Device identifier",
                },
                "points": {
                    "type": "object",
                    "description": "Key-value dictionary of point names to values",
                },
                "timestamp": {
                    "type": "string",
                    "description": "Optional ISO 8601 timestamp",
                },
                "project_id": {
                    "type": "string",
                    "description": "Optional project identifier",
                },
            },
            "required": ["registry_id", "device_id", "points"],
        },
    },
    {
        "name": "clear_registry_mapping_data",
        "description": "Reset/clear discovery events and mapping proposals for a specified registry.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The registry identifier to clear",
                }
            },
            "required": ["registry_id"],
        },
    },
    {
        "name": "get_portfolio_summary",
        "description": "Retrieve aggregate device counts, online/offline breakdown, and recent alerts count across registries.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
    {
        "name": "get_alerts",
        "description": "Query recent validation and alarm events with optional filtering.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of alerts to retrieve",
                    "default": 50,
                },
                "min_level": {
                    "type": "integer",
                    "description": "Minimum alert level severity threshold (e.g. 500 for error/warning)",
                    "default": 500,
                },
            },
        },
    },
    {
        "name": "get_devices",
        "description": "Retrieve paginated, filtered devices inventory with status and metadata.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "default": 100},
                "offset": {"type": "integer", "default": 0},
                "registry_id": {"type": "string"},
                "device_prefix": {"type": "string"},
                "make": {"type": "string"},
                "model": {"type": "string"},
                "status": {"type": "string"},
                "search": {"type": "string"},
            },
        },
    },
    {
        "name": "get_device_detail",
        "description": "Retrieve comprehensive metadata, system state, point states, and recent validation events for a device.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "Device registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "Device identifier",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "create_rollout",
        "description": "Create and launch a new staged configuration rollout campaign.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "Human-readable rollout campaign name"},
                "target_filter": {"type": "object", "description": "Filter criteria for targeting devices"},
                "target_payload": {"type": "object", "description": "Configuration payload to deploy"},
                "target_subfolder": {"type": "string", "default": "system"},
                "batch_size": {"type": "integer", "default": 10},
                "batch_interval_sec": {"type": "integer", "default": 60},
                "total_devices": {"type": "integer", "default": 10},
            },
            "required": ["name", "target_payload"],
        },
    },
    {
        "name": "list_rollouts",
        "description": "List all active, paused, completed, or cancelled rollout campaigns.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
    {
        "name": "get_rollout",
        "description": "Retrieve details and progress of a specific rollout campaign by ID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "rollout_id": {"type": "integer", "description": "Rollout campaign ID"},
            },
            "required": ["rollout_id"],
        },
    },
    {
        "name": "update_rollout",
        "description": "Update rollout campaign status (e.g. PAUSED, CANCELLED, RUNNING) or convergence progress.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "rollout_id": {"type": "integer", "description": "Rollout campaign ID"},
                "status": {"type": "string", "description": "New rollout status (PAUSED, CANCELLED, RUNNING, COMPLETED)"},
                "converged_devices": {"type": "integer", "description": "Number of devices that have converged"},
                "failed_devices": {"type": "integer", "description": "Number of devices that failed deployment"},
            },
            "required": ["rollout_id"],
        },
    },
]


class ButlerMcpServer:
    """Core Butler MCP Server instance handling JSON-RPC 2.0 requests."""

    def __init__(self, provider: Optional[ButlerProvider] = None):
        self.provider = provider or ButlerProvider()

    def handle_request(self, request: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Process a single JSON-RPC 2.0 request."""
        req_id = request.get("id")
        method = request.get("method")
        params = request.get("params", {})

        if not method:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32600, "message": "Invalid Request: missing method"},
            }

        # 1. initialize
        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "serverInfo": {
                        "name": "udmi-butler",
                        "version": "1.0.0",
                    },
                    "capabilities": {
                        "tools": {
                            "listChanged": False,
                        }
                    },
                },
            }

        # 2. ping
        if method == "ping":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {},
            }

        # 3. notifications / initialized
        if method in ["notifications/initialized", "initialized"]:
            return None

        # 4. tools/list
        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": MCP_TOOLS},
            }

        # 5. tools/call
        if method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            try:
                res = self._dispatch_tool(tool_name, tool_args)
                text_output = (
                    json.dumps(res, indent=2)
                    if isinstance(res, (dict, list))
                    else str(res)
                )
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": text_output,
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
                        "content": [
                            {
                                "type": "text",
                                "text": f"Error executing {tool_name}: {str(e)}",
                            }
                        ],
                        "isError": True,
                    },
                }

        # 6. Direct method routing
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
        """Execute a tool or method against ButlerProvider."""
        if name == "health":
            return self.provider.health()
        if name == "get_discovery_events":
            return self.provider.get_discovery_events(registry_id=args["registry_id"])
        if name == "get_discovered_devices":
            return self.provider.get_discovered_devices(registry_id=args["registry_id"])
        if name == "get_device_messages":
            return self.provider.get_device_messages(
                registry_id=args["registry_id"], device_id=args["device_id"]
            )
        if name == "record_message":
            return self.provider.record_message(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                sub_type=args["sub_type"],
                sub_folder=args["sub_folder"],
                payload=args["payload"],
                project_id=args.get("project_id"),
                timestamp=args.get("timestamp"),
            )
        if name == "get_device_telemetry":
            return self.provider.get_device_telemetry(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                point_names=args.get("point_names"),
                start=args.get("start", "-1h"),
                stop=args.get("stop", "now()"),
            )
        if name == "write_telemetry":
            return self.provider.write_telemetry(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
                points=args["points"],
                timestamp=args.get("timestamp"),
                project_id=args.get("project_id"),
            )
        if name == "clear_registry_mapping_data":
            return self.provider.clear_registry_mapping_data(registry_id=args["registry_id"])
        if name == "get_portfolio_summary":
            return self.provider.get_portfolio_summary()
        if name == "get_alerts":
            return self.provider.get_alerts(
                limit=args.get("limit", 50),
                min_level=args.get("min_level", 500),
            )
        if name == "get_devices":
            return self.provider.get_devices(
                limit=args.get("limit", 100),
                offset=args.get("offset", 0),
                registry_id=args.get("registry_id"),
                device_prefix=args.get("device_prefix"),
                make=args.get("make"),
                model=args.get("model"),
                status=args.get("status"),
                search=args.get("search"),
            )
        if name == "get_device_detail":
            return self.provider.get_device_detail(
                registry_id=args["registry_id"],
                device_id=args["device_id"],
            )
        if name == "create_rollout":
            return self.provider.create_rollout(
                name=args.get("name", "Untitled Rollout"),
                target_filter=args.get("target_filter", {}),
                target_payload=args.get("target_payload", {}),
                target_subfolder=args.get("target_subfolder", "system"),
                batch_size=int(args.get("batch_size", 10)),
                batch_interval_sec=int(args.get("batch_interval_sec", 60)),
                total_devices=int(args.get("total_devices", 10)),
            )
        if name == "list_rollouts":
            return self.provider.list_rollouts()
        if name == "get_rollout":
            return self.provider.get_rollout(rollout_id=int(args["rollout_id"]))
        if name == "update_rollout":
            return self.provider.update_rollout(
                rollout_id=int(args["rollout_id"]),
                status=args.get("status"),
                converged_devices=args.get("converged_devices"),
                failed_devices=args.get("failed_devices"),
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


class ButlerMcpHttpHandler(BaseHTTPRequestHandler):
    """HTTP handler supporting JSON-RPC 2.0 and health endpoints."""

    server_instance: Optional[ButlerMcpServer] = None

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

        if path in ("/health", "/livez", "/readyz"):
            health_data = self.server_instance.provider.health()
            content = json.dumps(health_data).encode("utf-8")
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
            self.wfile.write(b"404 Not Found")


def run_http_server(
    server_instance: ButlerMcpServer,
    port: int = 8088,
    host: str = "0.0.0.0",
) -> None:
    """Run standalone HTTP JSON-RPC and REST server."""
    ButlerMcpHttpHandler.server_instance = server_instance
    httpd = HTTPServer((host, port), ButlerMcpHttpHandler)
    httpd.allow_reuse_address = True
    print(f"Butler MCP Server listening on http://{host}:{port}", file=sys.stderr)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="UDMI Butler MCP Server & Mapping Data Access Daemon"
    )
    subparsers = parser.add_subparsers(dest="command")

    # mcp subcommand
    subparsers.add_parser("mcp", help="Run in stdio MCP server mode")

    # serve subcommand
    serve_parser = subparsers.add_parser("serve", help="Run HTTP JSON-RPC server")
    serve_parser.add_argument("--port", type=int, default=8088, help="HTTP server listen port (default: 8088)")
    serve_parser.add_argument("--host", default="0.0.0.0", help="HTTP server listen host (default: 0.0.0.0)")
    serve_parser.add_argument("--pg-port", type=int, default=None, help="Target PostgreSQL port")
    serve_parser.add_argument("--influx-port", type=int, default=None, help="Target InfluxDB port")

    # CLI subcommands
    health_parser = subparsers.add_parser("health", help="Check Butler datastores health")
    health_parser.add_argument("--pg-port", type=int, default=None)
    health_parser.add_argument("--influx-port", type=int, default=None)

    disc_parser = subparsers.add_parser("discovery-events", help="List discovery events for registry")
    disc_parser.add_argument("registry_id", help="Registry ID")
    disc_parser.add_argument("--pg-port", type=int, default=None)

    devs_parser = subparsers.add_parser("discovered-devices", help="List discovered devices for registry")
    devs_parser.add_argument("registry_id", help="Registry ID")
    devs_parser.add_argument("--pg-port", type=int, default=None)

    msgs_parser = subparsers.add_parser("device-messages", help="List lifecycle messages for a device")
    msgs_parser.add_argument("registry_id", help="Registry ID")
    msgs_parser.add_argument("device_id", help="Device ID")
    msgs_parser.add_argument("--pg-port", type=int, default=None)

    telem_parser = subparsers.add_parser("telemetry", help="Get device telemetry series")
    telem_parser.add_argument("registry_id", help="Registry ID")
    telem_parser.add_argument("device_id", help="Device ID")
    telem_parser.add_argument("--points", nargs="*", default=None, help="Point names")
    telem_parser.add_argument("--influx-port", type=int, default=None)

    args = parser.parse_args()

    pg_port = getattr(args, "pg_port", None)
    influx_port = getattr(args, "influx_port", None)

    provider = ButlerProvider(pg_port=pg_port, influx_port=influx_port)
    server_instance = ButlerMcpServer(provider)

    if args.command == "serve":
        run_http_server(server_instance, port=args.port, host=args.host)
        return

    if args.command == "health":
        h = provider.health()
        print(json.dumps(h, indent=2))
        return

    if args.command == "discovery-events":
        events = provider.get_discovery_events(args.registry_id)
        print(json.dumps(events, indent=2))
        return

    if args.command == "discovered-devices":
        devs = provider.get_discovered_devices(args.registry_id)
        print(json.dumps(devs, indent=2))
        return

    if args.command == "device-messages":
        msgs = provider.get_device_messages(args.registry_id, args.device_id)
        print(json.dumps(msgs, indent=2))
        return

    if args.command == "telemetry":
        telem = provider.get_device_telemetry(args.registry_id, args.device_id, point_names=args.points)
        print(json.dumps(telem, indent=2))
        return

    # Default to MCP stdio mode
    server_instance.run_stdio()


if __name__ == "__main__":
    main()
