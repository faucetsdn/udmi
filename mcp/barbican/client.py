"""Barbican MCP JSON-RPC Client.

Allows Python components (such as ui/v2/server.py) to interact with the
Barbican service via JSON-RPC 2.0 without making any direct database calls
or depending on internal datastore implementation details.
"""

import itertools
import json
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional


class BarbicanClient:
    """Client for communicating with the Barbican MCP JSON-RPC service."""

    def __init__(
        self,
        endpoint: Optional[str] = None,
        port: Optional[int] = None,
        timeout: float = 5.0,
    ):
        if port is not None:
            self.endpoint = f"http://127.0.0.1:{port}/rpc"
        elif endpoint:
            if not endpoint.startswith("http://") and not endpoint.startswith("https://"):
                endpoint = f"http://{endpoint}"
            if not endpoint.rstrip("/").endswith("/rpc"):
                endpoint = f"{endpoint.rstrip('/')}/rpc"
            self.endpoint = endpoint
        else:
            self.endpoint = "http://127.0.0.1:8085/rpc"

        self.timeout = timeout
        self._id_counter = itertools.count(1)

    def call_rpc(self, method: str, params: Optional[Dict[str, Any]] = None) -> Any:
        """Execute a JSON-RPC 2.0 call against the MCP server."""
        req_id = next(self._id_counter)
        payload = {
            "jsonrpc": "2.0",
            "id": req_id,
            "method": method,
            "params": params or {},
        }
        data = json.dumps(payload).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        req = urllib.request.Request(self.endpoint, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                resp_data = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"JSON-RPC HTTP error ({e.code}) connecting to {self.endpoint}: {body}") from e
        except Exception as e:
            raise RuntimeError(f"Failed to connect to Barbican MCP server at {self.endpoint}: {e}") from e

        if "error" in resp_data:
            err = resp_data["error"]
            err_msg = err.get("message", str(err)) if isinstance(err, dict) else str(err)
            raise RuntimeError(f"JSON-RPC server error [{err.get('code', -1)}]: {err_msg}")

        return resp_data.get("result")

    def call_tool(self, name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        """Invoke an MCP tool via the standard tools/call JSON-RPC method."""
        res = self.call_rpc("tools/call", {"name": name, "arguments": arguments or {}})
        if isinstance(res, dict) and res.get("isError"):
            content = res.get("content", [{}])[0].get("text", "Unknown tool error")
            raise RuntimeError(f"MCP tool error ({name}): {content}")
        content_items = res.get("content", []) if isinstance(res, dict) else []
        if content_items:
            text = content_items[0].get("text", "")
            try:
                return json.loads(text)
            except Exception:
                return text
        return res

    def list_registries(self, prefix: str = "/r/") -> Dict[str, Any]:
        """Fetch unique UDMI registries and total devices count."""
        return self.call_rpc("list_registries", {"prefix": prefix})

    def list_devices(self, registry_id: str) -> Dict[str, Any]:
        """Fetch devices list for a registry."""
        return self.call_rpc("list_devices", {"registry_id": registry_id})

    def get_device_properties(self, registry_id: str, device_id: str) -> Dict[str, Any]:
        """Fetch properties map for a device."""
        return self.call_rpc("get_device_properties", {"registry_id": registry_id, "device_id": device_id})

    def get_entry(self, key: str) -> Optional[str]:
        """Fetch value for an exact key."""
        return self.call_rpc("get_entry", {"key": key})

    def get_prefix_entries(self, prefix: str) -> Dict[str, str]:
        """Fetch all key-value entries matching a prefix."""
        return self.call_rpc("get_prefix_entries", {"prefix": prefix})

    def put_entry(self, key: str, value: str) -> bool:
        """Store key-value entry."""
        return self.call_rpc("put_entry", {"key": key, "value": value})

    def delete_entry(self, key: str, is_prefix: bool = False) -> int:
        """Delete key or prefix range."""
        return self.call_rpc("delete_entry", {"key": key, "is_prefix": is_prefix})

    def health(self) -> Dict[str, Any]:
        """Check connection status and health."""
        return self.call_rpc("health", {})

    def tools_list(self) -> List[Dict[str, Any]]:
        """List all available MCP tools."""
        res = self.call_rpc("tools/list", {})
        return res.get("tools", [])
