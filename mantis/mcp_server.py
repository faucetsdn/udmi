"""Mantis MCP Server & HTTP/SSE Transport.

Exposes the full Mantis diagnostic tool registry (mantis.tools.registry) over
standard Model Context Protocol (MCP JSON-RPC 2.0) via stdio and HTTP/SSE,
keeping mcp/infra/server.py untouched and strictly upstream-compatible.
"""

import argparse
import asyncio
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys
from typing import Any, Dict, Optional
import urllib.parse

from mantis.session import SessionManager
from mantis.tools.registry import execute_tool as reg_execute
from mantis.tools.registry import get_mcp_tools


class MCPHttpHandler(BaseHTTPRequestHandler):
    """HTTP & SSE Request Handler for remote MCP transport."""

    server_instance: Any = None

    def log_message(self, format, *args):
        """Suppress noisy default request logging to stderr."""
        pass

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        tools = get_mcp_tools()
        if parsed.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps({
                    "status": "OK",
                    "server": "udmi-test-infra",
                    "version": "1.0.0",
                    "tools_count": len(tools),
                }).encode("utf-8")
            )
        elif parsed.path == "/sse":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            endpoint_msg = "event: endpoint\ndata: /message\n\n"
            self.wfile.write(endpoint_msg.encode("utf-8"))
            self.wfile.flush()
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path in ("/message", "/rpc", "/"):
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            try:
                req = json.loads(body)
                resp = self.server_instance.handle_request(req)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                if resp is not None:
                    self.wfile.write(json.dumps(resp).encode("utf-8"))
            except Exception as e:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                err = {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32603, "message": str(e)},
                }
                self.wfile.write(json.dumps(err).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()


class MCPServer:
    """Handles JSON-RPC 2.0 MCP messages for Mantis over stdio or HTTP/SSE."""

    def __init__(self, session_mgr: Optional[SessionManager] = None):
        self.session_mgr = session_mgr or SessionManager()

    def run_http(self, host: str = "127.0.0.1", port: int = 8080) -> HTTPServer:
        """Create and bind HTTP/SSE server instance."""
        MCPHttpHandler.server_instance = self
        return HTTPServer((host, port), MCPHttpHandler)

    def run(self) -> None:
        """Main stdio loop for MCP protocol."""
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

    async def run_async(self) -> None:
        """Asynchronous non-blocking stdio loop for MCP protocol."""
        loop = asyncio.get_running_loop()
        reader = asyncio.StreamReader()
        protocol = asyncio.StreamReaderProtocol(reader)
        await loop.connect_read_pipe(lambda: protocol, sys.stdin)

        while True:
            line_bytes = await reader.readline()
            if not line_bytes:
                break
            line = line_bytes.decode("utf-8").strip()
            if not line:
                continue
            try:
                request = json.loads(line)
                response = await self.handle_request_async(request)
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

    async def handle_request_async(self, req: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Asynchronously processes an MCP request."""
        req_id = req.get("id")
        method = req.get("method")
        params = req.get("params", {})

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {}},
                    "serverInfo": {
                        "name": "udmi-test-infra",
                        "version": "1.0.0",
                    },
                },
            }

        if method == "notifications/initialized":
            return None

        if method == "ping":
            return {"jsonrpc": "2.0", "id": req_id, "result": {}}

        if method == "tools/list":
            return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": get_mcp_tools()}}

        if method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            try:
                loop = asyncio.get_running_loop()
                result_data = await loop.run_in_executor(
                    None, self.execute_tool, tool_name, tool_args
                )
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": (
                                    json.dumps(result_data, indent=2)
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
                        "content": [{"type": "text", "text": f"Error: {e}"}],
                        "isError": True,
                    },
                }

        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {"code": -32601, "message": f"Method not found: {method}"},
        }

    def handle_request(self, req: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        req_id = req.get("id")
        method = req.get("method")
        params = req.get("params", {})

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {}},
                    "serverInfo": {
                        "name": "udmi-test-infra",
                        "version": "1.0.0",
                    },
                },
            }

        if method == "notifications/initialized":
            return None

        if method == "ping":
            return {"jsonrpc": "2.0", "id": req_id, "result": {}}

        if method == "tools/list":
            return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": get_mcp_tools()}}

        if method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            try:
                result_data = self.execute_tool(tool_name, tool_args)
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": (
                                    json.dumps(result_data, indent=2)
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
                        "content": [{"type": "text", "text": f"Error: {e}"}],
                        "isError": True,
                    },
                }

        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {"code": -32601, "message": f"Method not found: {method}"},
        }

    def execute_tool(self, name: str, args: Dict[str, Any]) -> Any:
        return reg_execute(name=name, args=args, session_mgr=self.session_mgr)


def main() -> None:
    parser = argparse.ArgumentParser(description="Mantis MCP Server")
    subparsers = parser.add_subparsers(dest="command")
    subparsers.add_parser("mcp", help="Run in stdio MCP server mode")
    serve_parser = subparsers.add_parser("serve", help="Run MCP server over HTTP/SSE")
    serve_parser.add_argument("--port", "-p", type=int, default=8080)
    serve_parser.add_argument("--host", "-H", type=str, default="127.0.0.1")

    args, _ = parser.parse_known_args()
    server = MCPServer()
    if args.command == "serve":
        httpd = server.run_http(host=args.host, port=args.port)
        print(f"Mantis MCP HTTP/SSE Server running on http://{args.host}:{args.port}")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            httpd.server_close()
    else:
        server.run()
