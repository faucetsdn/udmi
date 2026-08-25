#!/usr/bin/env python3
"""UDMI Test Infrastructure MCP Server & CLI Tool.

Supports:
1. Standard MCP JSON-RPC 2.0 protocol over stdio for AI agent tool calling.
2. Direct CLI invocation (e.g. `bin/test_setup ensure <test_id>`) for CI and manual workflows.
"""

import argparse
import json
import os
import sys
from typing import Any, Dict, List, Optional

from mcp.session_manager import SessionManager


MCP_TOOLS = [
    {
        "name": "ensure_test_setup",
        "description": (
            "Ensures that an isolated local UDMI test infrastructure stack (Mosquitto broker, "
            "UDMIS control plane, etcd, influxd, postgresql, and optional DUT) is running inside a "
            "tmux session, healthy, and ready for UUFI client traffic."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "test_id": {
                    "type": "string",
                    "description": "Unique test run identifier (e.g. 'gummi_dev_1', 'suite_pointset').",
                },
                "site_model": {
                    "type": "string",
                    "description": "Path to the target site model directory.",
                    "default": "sites/udmi_site_model",
                },
                "dut_device_id": {
                    "type": "string",
                    "description": "Optional device ID to automatically launch as a Pubber DUT.",
                },
                "dut_serial_no": {
                    "type": "string",
                    "description": "Optional serial number for the emulated DUT.",
                },
                "clean": {
                    "type": "boolean",
                    "description": "Whether to clean existing state before startup (default: true).",
                    "default": True,
                },
                "timeout_seconds": {
                    "type": "integer",
                    "description": "Maximum seconds to wait for stack readiness (default: 150).",
                    "default": 150,
                },
            },
            "required": ["test_id"],
        },
    },
    {
        "name": "terminate_test_setup",
        "description": "Terminates the test infrastructure and tmux session associated with a test_id.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "test_id": {
                    "type": "string",
                    "description": "Identifier of the test session to terminate.",
                },
                "clean_workspace": {
                    "type": "boolean",
                    "description": "Whether to purge per-instance runtime storage (default: true).",
                    "default": True,
                },
            },
            "required": ["test_id"],
        },
    },
    {
        "name": "list_test_setups",
        "description": "Lists all active UDMI test sessions and their connection details.",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
    {
        "name": "list_test_windows",
        "description": "Lists the available semantic window tags for an active test session (e.g. 'main', 'dut').",
        "inputSchema": {
            "type": "object",
            "properties": {
                "test_id": {
                    "type": "string",
                    "description": "Identifier of the active test session.",
                },
            },
            "required": ["test_id"],
        },
    },
    {
        "name": "get_test_logs",
        "description": (
            "Captures live console output from a named semantic tmux window (e.g. 'main', 'dut') "
            "for an active test session. The window MUST be specified using a semantic tag, not a numerical index."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "test_id": {
                    "type": "string",
                    "description": "Identifier of the test session.",
                },
                "window": {
                    "type": "string",
                    "description": "Semantic window tag (e.g. 'main', 'dut'). Numerical indices are not permitted.",
                    "default": "main",
                },
                "lines": {
                    "type": "integer",
                    "description": "Number of lines to capture (default: 100).",
                    "default": 100,
                },
            },
            "required": ["test_id"],
        },
    },
]


class MCPServer:
    """Handles JSON-RPC 2.0 MCP messages over stdio."""

    def __init__(self, session_mgr: SessionManager):
        self.session_mgr = session_mgr

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
            # No response for notifications
            return None

        if method == "ping":
            return {"jsonrpc": "2.0", "id": req_id, "result": {}}

        if method == "tools/list":
            return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": MCP_TOOLS}}

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
                                "text": json.dumps(result_data, indent=2),
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
        if name == "ensure_test_setup":
            return self.session_mgr.ensure_test_setup(
                test_id=args["test_id"],
                site_model=args.get("site_model", "sites/udmi_site_model"),
                dut_device_id=args.get("dut_device_id"),
                dut_serial_no=args.get("dut_serial_no"),
                clean=args.get("clean", True),
                timeout_seconds=args.get("timeout_seconds", 150),
            )
        if name == "terminate_test_setup":
            return self.session_mgr.terminate_test_setup(
                test_id=args["test_id"],
                clean_workspace=args.get("clean_workspace", True),
            )
        if name == "list_test_setups":
            return self.session_mgr.list_test_setups()
        if name == "list_test_windows":
            return self.session_mgr.list_test_windows(test_id=args["test_id"])
        if name == "get_test_logs":
            return self.session_mgr.get_test_logs(
                test_id=args["test_id"],
                window=args.get("window", "main"),
                lines=args.get("lines", 100),
            )
        raise ValueError(f"Unknown tool: {name}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="UDMI Test Infrastructure Control (MCP Server & CLI)"
    )
    subparsers = parser.add_subparsers(dest="command")

    # ensure subcommand
    ensure_parser = subparsers.add_parser(
        "ensure", help="Start or ensure isolated local UDMI test infrastructure"
    )
    ensure_parser.add_argument("test_id", help="Unique identifier for the test run")
    ensure_parser.add_argument(
        "site_model",
        nargs="?",
        default="sites/udmi_site_model",
        help="Path to site model (default: sites/udmi_site_model)",
    )
    ensure_parser.add_argument(
        "--dut", dest="dut_device_id", help="Device ID to launch as emulated DUT"
    )
    ensure_parser.add_argument(
        "--serial", dest="dut_serial_no", help="Serial number for emulated DUT"
    )
    ensure_parser.add_argument(
        "--no-clean",
        dest="clean",
        action="store_false",
        default=True,
        help="Do not clean existing session state",
    )
    ensure_parser.add_argument(
        "--timeout",
        dest="timeout_seconds",
        type=int,
        default=150,
        help="Readiness timeout in seconds (default: 150)",
    )
    ensure_parser.add_argument(
        "--json", action="store_true", help="Output full JSON response"
    )

    # terminate subcommand
    term_parser = subparsers.add_parser(
        "terminate", help="Terminate a running test infrastructure setup"
    )
    term_parser.add_argument("test_id", help="Identifier of the test session")
    term_parser.add_argument(
        "--no-clean",
        dest="clean_workspace",
        action="store_false",
        default=True,
        help="Do not delete per-instance workspace directory",
    )
    term_parser.add_argument(
        "--json", action="store_true", help="Output full JSON response"
    )

    # list subcommand
    list_parser = subparsers.add_parser("list", help="List all active test setups")
    list_parser.add_argument(
        "--json", action="store_true", help="Output full JSON response"
    )

    # windows subcommand
    windows_parser = subparsers.add_parser(
        "windows", help="List available semantic windows for an active test session"
    )
    windows_parser.add_argument("test_id", help="Identifier of the test session")
    windows_parser.add_argument(
        "--json", action="store_true", help="Output full JSON response"
    )

    # status subcommand
    status_parser = subparsers.add_parser(
        "status", help="Get status of a specific test setup"
    )
    status_parser.add_argument("test_id", help="Identifier of the test session")
    status_parser.add_argument(
        "--json", action="store_true", help="Output full JSON response"
    )

    # logs subcommand
    logs_parser = subparsers.add_parser(
        "logs", help="View logs from a test setup tmux window"
    )
    logs_parser.add_argument("test_id", help="Identifier of the test session")
    logs_parser.add_argument(
        "window",
        nargs="?",
        default="main",
        help="Semantic window tag (default: main). Numerical indices are not permitted.",
    )
    logs_parser.add_argument(
        "-n", "--lines", type=int, default=100, help="Number of lines to capture"
    )

    # mcp subcommand
    subparsers.add_parser("mcp", help="Run in stdio MCP server mode")

    args = parser.parse_args()

    session_mgr = SessionManager()

    if args.command == "ensure":
        res = session_mgr.ensure_test_setup(
            test_id=args.test_id,
            site_model=args.site_model,
            dut_device_id=args.dut_device_id,
            dut_serial_no=args.dut_serial_no,
            clean=args.clean,
            timeout_seconds=args.timeout_seconds,
        )
        if args.json:
            print(json.dumps(res, indent=2))
        else:
            print(res["connection_url"])

    elif args.command == "terminate":
        res = session_mgr.terminate_test_setup(
            test_id=args.test_id, clean_workspace=args.clean_workspace
        )
        if args.json:
            print(json.dumps(res, indent=2))
        else:
            print(f"Terminated {res['session_name']}")

    elif args.command == "list":
        res = session_mgr.list_test_setups()
        if args.json:
            print(json.dumps(res, indent=2))
        else:
            if not res:
                print("No active UDMI test sessions found.")
            else:
                for item in res:
                    print(
                        f"{item.get('test_id', 'unknown')}: "
                        f"url={item.get('connection_url', 'N/A')} "
                        f"session={item.get('session_name', 'N/A')} "
                        f"windows={item.get('windows', [])}"
                    )

    elif args.command == "windows":
        windows = session_mgr.list_test_windows(args.test_id)
        if args.json:
            print(json.dumps(windows, indent=2))
        else:
            if not windows:
                print(f"No active windows found for {args.test_id}")
            else:
                print(f"Available windows for {args.test_id}: {', '.join(windows)}")

    elif args.command == "status":
        res = session_mgr.get_session_info(args.test_id)
        if res is None:
            if session_mgr.is_session_active(
                session_mgr.sanitize_session_name(args.test_id)
            ):
                res = {
                    "status": "ACTIVE_UNTRACKED",
                    "test_id": args.test_id,
                    "windows": session_mgr.list_test_windows(args.test_id),
                }
            else:
                res = {"status": "NOT_FOUND", "test_id": args.test_id}
        if args.json:
            print(json.dumps(res, indent=2))
        else:
            print(f"Status: {res.get('status')}")
            if "connection_url" in res:
                print(f"Connection URL: {res['connection_url']}")
            if "windows" in res:
                print(f"Windows: {', '.join(res['windows'])}")

    elif args.command == "logs":
        logs = session_mgr.get_test_logs(
            test_id=args.test_id, window=args.window, lines=args.lines
        )
        print(logs)

    else:
        # Default to MCP stdio mode if 'mcp' or no arguments
        server = MCPServer(session_mgr)
        server.run()


if __name__ == "__main__":
    main()
