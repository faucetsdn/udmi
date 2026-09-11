#!/usr/bin/env python3
"""UDMI Barbican MCP Server & RPC Daemon.

Supports:
1. Standard MCP JSON-RPC 2.0 protocol over stdio for AI agent tool calling.
2. HTTP JSON-RPC 2.0 endpoint (POST /rpc, POST /) for headless inter-service RPC.
3. Static file hosting for the explorer web UI.
4. Direct CLI inspection and querying.
"""

import argparse
import json
import os
import re
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any, Dict, List, Optional
import urllib.parse

from mcp.barbican.provider import BarbicanProvider


MCP_TOOLS = [
    {
        "name": "list_registries",
        "description": "List all unique UDMI device registries and total count of registered devices in Barbican.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "prefix": {
                    "type": "string",
                    "description": "Key prefix for registries scan (default: '/r/')",
                    "default": "/r/",
                }
            },
        },
    },
    {
        "name": "list_devices",
        "description": "List all devices registered under a given UDMI registry in Barbican.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The target registry identifier (e.g. 'ZZ-TRI-FECTA')",
                }
            },
            "required": ["registry_id"],
        },
    },
    {
        "name": "get_device_properties",
        "description": "Retrieve all key-value properties and configuration state for a specific device in Barbican.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "registry_id": {
                    "type": "string",
                    "description": "The registry identifier",
                },
                "device_id": {
                    "type": "string",
                    "description": "The device identifier (e.g. 'AHU-1')",
                },
            },
            "required": ["registry_id", "device_id"],
        },
    },
    {
        "name": "get_entry",
        "description": "Retrieve the exact value of a single key from the Barbican datastore.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "key": {
                    "type": "string",
                    "description": "The exact datastore key to fetch",
                }
            },
            "required": ["key"],
        },
    },
    {
        "name": "get_prefix_entries",
        "description": "Retrieve all key-value entries starting with the given prefix in the Barbican datastore.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "prefix": {
                    "type": "string",
                    "description": "Key prefix to query",
                }
            },
            "required": ["prefix"],
        },
    },
    {
        "name": "put_entry",
        "description": "Store a key-value entry in the Barbican datastore.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "key": {
                    "type": "string",
                    "description": "Datastore key path",
                },
                "value": {
                    "type": "string",
                    "description": "String value to store",
                },
            },
            "required": ["key", "value"],
        },
    },
    {
        "name": "delete_entry",
        "description": "Delete a key or key prefix from the Barbican datastore.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "key": {
                    "type": "string",
                    "description": "The key or prefix to delete",
                },
                "is_prefix": {
                    "type": "boolean",
                    "description": "Whether to delete all keys matching the prefix",
                    "default": False,
                },
            },
            "required": ["key"],
        },
    },
    {
        "name": "health",
        "description": "Check connection health and status of the Barbican service.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
]


class BarbicanMcpServer:
    """Core MCP Server instance handling JSON-RPC 2.0 requests."""

    def __init__(self, provider: Optional[BarbicanProvider] = None):
        self.provider = provider or BarbicanProvider()

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
                        "name": "udmi-barbican",
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

        # 3. tools/list
        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": MCP_TOOLS},
            }

        # 4. tools/call
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

        # 5. Direct method routing
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
        """Execute a tool or method against BarbicanProvider."""
        if name == "list_registries":
            prefix = args.get("prefix", "/r/")
            return self.provider.list_registries(prefix)
        if name == "list_devices":
            return self.provider.list_devices(registry_id=args["registry_id"])
        if name == "get_device_properties":
            return self.provider.get_device_properties(
                registry_id=args["registry_id"], device_id=args["device_id"]
            )
        if name == "get_entry":
            return self.provider.get_entry(key=args["key"])
        if name == "get_prefix_entries":
            return self.provider.get_prefix_entries(prefix=args["prefix"])
        if name == "put_entry":
            return self.provider.put_entry(key=args["key"], value=args["value"])
        if name == "delete_entry":
            return self.provider.delete_entry(
                key=args["key"], is_prefix=args.get("is_prefix", False)
            )
        if name == "health":
            return self.provider.health()
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


class BarbicanMcpHttpHandler(BaseHTTPRequestHandler):
    """HTTP handler supporting JSON-RPC 2.0, REST endpoints, and static web explorer assets."""

    server_instance: Optional[BarbicanMcpServer] = None
    static_dir: Optional[str] = None

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
            self.send_json(health_data, is_head=is_head)
            return

        self.serve_static(path, is_head=is_head)

    def send_json(self, obj: Any, status: int = 200, is_head: bool = False) -> None:
        content = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.send_cors_headers()
        self.end_headers()
        if not is_head:
            self.wfile.write(content)

    def serve_static(self, path: str, is_head: bool = False) -> None:
        if path.startswith("/barbican_explorer"):
            path = path[len("/barbican_explorer"):]
        elif path.startswith("/etcd_explorer"):
            path = path[len("/etcd_explorer"):]
        if not path or path == "/":
            path = "/index.html"

        static_dir = self.static_dir or os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "..", "udmis", "src", "main", "resources", "etcd_explorer")
        )
        file_path = os.path.normpath(os.path.join(static_dir, path.lstrip("/")))

        if not file_path.startswith(static_dir) or not os.path.isfile(file_path):
            self.send_response(404)
            self.send_header("Content-Type", "text/plain")
            self.send_cors_headers()
            self.end_headers()
            if not is_head:
                self.wfile.write(b"404 Not Found")
            return

        content_type = "text/html; charset=utf-8"
        if file_path.endswith(".js"):
            content_type = "application/javascript; charset=utf-8"
        elif file_path.endswith(".css"):
            content_type = "text/css; charset=utf-8"
        elif file_path.endswith(".json"):
            content_type = "application/json; charset=utf-8"

        try:
            with open(file_path, "rb") as f:
                content = f.read()
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(content)))
            self.send_cors_headers()
            self.end_headers()
            if not is_head:
                self.wfile.write(content)
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "text/plain")
            self.send_cors_headers()
            self.end_headers()
            if not is_head:
                self.wfile.write(str(e).encode("utf-8"))


def run_http_server(
    server_instance: BarbicanMcpServer,
    port: int = 8085,
    host: str = "0.0.0.0",
    static_dir: Optional[str] = None,
) -> None:
    """Run standalone HTTP JSON-RPC and REST server."""
    BarbicanMcpHttpHandler.server_instance = server_instance
    BarbicanMcpHttpHandler.static_dir = static_dir
    httpd = HTTPServer((host, port), BarbicanMcpHttpHandler)
    httpd.allow_reuse_address = True
    print(f"Barbican MCP Server listening on http://{host}:{port}", file=sys.stderr)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="UDMI Barbican MCP Server & Diagnostic RPC Service"
    )
    subparsers = parser.add_subparsers(dest="command")

    # mcp subcommand
    mcp_parser = subparsers.add_parser("mcp", help="Run in stdio MCP server mode")
    mcp_parser.add_argument("--etcd-port", type=int, default=None, help="Target backend port")
    mcp_parser.add_argument("--etcd-target", default=None, help="Target backend URL")
    mcp_parser.add_argument("--ca-file", default=None, help="Path to CA certificate")
    mcp_parser.add_argument("--cert-file", default=None, help="Path to client certificate")
    mcp_parser.add_argument("--key-file", default=None, help="Path to client private key")

    # serve subcommand
    serve_parser = subparsers.add_parser("serve", help="Run HTTP JSON-RPC & Explorer REST server")
    serve_parser.add_argument("--port", type=int, default=8085, help="HTTP server listen port (default: 8085)")
    serve_parser.add_argument("--host", default="0.0.0.0", help="HTTP server listen host (default: 0.0.0.0)")
    serve_parser.add_argument("--etcd-port", type=int, default=None, help="Target backend port")
    serve_parser.add_argument("--etcd-target", default=None, help="Target backend URL")
    serve_parser.add_argument("--ca-file", default=None, help="Path to CA certificate")
    serve_parser.add_argument("--cert-file", default=None, help="Path to client certificate")
    serve_parser.add_argument("--key-file", default=None, help="Path to client private key")

    # CLI query subcommands
    reg_parser = subparsers.add_parser("registries", help="List registries")
    reg_parser.add_argument("--prefix", default="/r/", help="Prefix (default: /r/)")
    reg_parser.add_argument("--etcd-port", type=int, default=None)

    dev_parser = subparsers.add_parser("devices", help="List devices in registry")
    dev_parser.add_argument("registry_id", help="Registry ID")
    dev_parser.add_argument("--etcd-port", type=int, default=None)

    prop_parser = subparsers.add_parser("properties", help="Get properties of device")
    prop_parser.add_argument("registry_id", help="Registry ID")
    prop_parser.add_argument("device_id", help="Device ID")
    prop_parser.add_argument("--etcd-port", type=int, default=None)

    get_parser = subparsers.add_parser("get", help="Get key value")
    get_parser.add_argument("key", help="Datastore key")
    get_parser.add_argument("--etcd-port", type=int, default=None)

    put_parser = subparsers.add_parser("put", help="Put key value")
    put_parser.add_argument("key", help="Datastore key")
    put_parser.add_argument("value", help="Datastore value")
    put_parser.add_argument("--etcd-port", type=int, default=None)

    health_parser = subparsers.add_parser("health", help="Check Barbican health")
    health_parser.add_argument("--etcd-port", type=int, default=None)

    args = parser.parse_args()

    # Determine backend target
    target = None
    if hasattr(args, "etcd_target") and args.etcd_target:
        target = args.etcd_target
    elif hasattr(args, "etcd_port") and args.etcd_port:
        target = f"http://127.0.0.1:{args.etcd_port}"

    ca_file = getattr(args, "ca_file", None)
    cert_file = getattr(args, "cert_file", None)
    key_file = getattr(args, "key_file", None)

    provider = BarbicanProvider(
        target=target,
        ca_file=ca_file,
        cert_file=cert_file,
        key_file=key_file,
    )
    server_instance = BarbicanMcpServer(provider)

    if args.command == "serve":
        run_http_server(server_instance, port=args.port, host=args.host)
        return

    if args.command == "registries":
        res = provider.list_registries(args.prefix)
        print(json.dumps(res, indent=2))
        return

    if args.command == "devices":
        res = provider.list_devices(args.registry_id)
        print(json.dumps(res, indent=2))
        return

    if args.command == "properties":
        res = provider.get_device_properties(args.registry_id, args.device_id)
        print(json.dumps(res, indent=2))
        return

    if args.command == "get":
        val = provider.get_entry(args.key)
        if val is not None:
            print(val)
        else:
            print(f"Key not found: {args.key}", file=sys.stderr)
            sys.exit(1)
        return

    if args.command == "put":
        ok = provider.put_entry(args.key, args.value)
        if ok:
            print(f"Stored {args.key}")
        else:
            print("Failed to store", file=sys.stderr)
            sys.exit(1)
        return

    if args.command == "health":
        h = provider.health()
        print(json.dumps(h, indent=2))
        return

    # Default to MCP stdio mode
    server_instance.run_stdio()


if __name__ == "__main__":
    main()
