"""UUFI MCP JSON-RPC Client.

Allows Python components (such as GUMMI web servers, management tools, and automated tests)
to interact with the UUFI messaging fabric via JSON-RPC 2.0 without depending directly on
paho-mqtt, TLS certificates, raw topic paths, or broker connection lifecycles.
"""

import itertools
import json
from typing import Any, Dict, List, Optional
import urllib.error
import urllib.request


class UUFIClient:
    """Client for communicating with the UUFI MCP JSON-RPC service."""

    def __init__(
        self,
        endpoint: Optional[str] = None,
        port: Optional[int] = None,
        timeout: float = 15.0,
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
            self.endpoint = "http://127.0.0.1:8087/rpc"

        self.timeout = timeout
        self._id_counter = itertools.count(1)

    def call_rpc(self, method: str, params: Optional[Dict[str, Any]] = None) -> Any:
        """Execute a JSON-RPC 2.0 call against the UUFI MCP server."""
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
            raise RuntimeError(f"Failed to connect to UUFI MCP server at {self.endpoint}: {e}") from e

        if "error" in resp_data:
            err = resp_data["error"]
            err_msg = err.get("message", str(err)) if isinstance(err, dict) else str(err)
            raise RuntimeError(f"JSON-RPC server error [{err.get('code', -1)}]: {err_msg}")

        return resp_data.get("result")

    def call_tool(self, name: str, arguments: Optional[Dict[str, Any]] = None) -> Any:
        """Invoke an MCP tool via standard tools/call JSON-RPC method."""
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

    def health(self) -> Dict[str, Any]:
        """Check connection health, latency, and status of the UUFI broker and session."""
        return self.call_rpc("health")

    def handshake(self, functions_ver: int = 9, timeout_sec: float = 10.0) -> Dict[str, Any]:
        """Execute Layer 1 UUFI service handshake to establish communication presence and negotiate capabilities."""
        return self.call_rpc("handshake", {"functions_ver": functions_ver, "timeout_sec": timeout_sec})

    def publish_config(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Publish device configuration update to the UUFI bus."""
        return self.call_rpc(
            "publish_config",
            {
                "registry_id": registry_id,
                "device_id": device_id,
                "sub_folder": sub_folder,
                "payload": payload,
            },
        )

    def query_state(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str = "blobset",
        timeout_sec: float = 15.0,
    ) -> Dict[str, Any]:
        """Query live device state on the UUFI bus and wait for correlated state response."""
        return self.call_rpc(
            "query_state",
            {
                "registry_id": registry_id,
                "device_id": device_id,
                "sub_folder": sub_folder,
                "timeout_sec": timeout_sec,
            },
        )

    def query_system_model(
        self,
        registry_id: str,
        device_id: str,
        timeout_sec: float = 15.0,
    ) -> Dict[str, Any]:
        """Query device system model on the UUFI bus and wait for correlated model response."""
        return self.call_rpc(
            "query_system_model",
            {
                "registry_id": registry_id,
                "device_id": device_id,
                "timeout_sec": timeout_sec,
            },
        )

    def update_system_model(
        self,
        registry_id: str,
        device_id: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Publish partial-merge model update for a device on the UUFI bus."""
        return self.call_rpc(
            "update_system_model",
            {
                "registry_id": registry_id,
                "device_id": device_id,
                "payload": payload,
            },
        )

    def poll_events(
        self,
        cursor: int = 0,
        timeout_sec: float = 0.0,
        sub_types: Optional[List[str]] = None,
        sub_folders: Optional[List[str]] = None,
        registry_id: Optional[str] = None,
        device_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Poll inbound device messages delivered over the UUFI bus."""
        return self.call_rpc(
            "poll_events",
            {
                "cursor": cursor,
                "timeout_sec": timeout_sec,
                "sub_types": sub_types,
                "sub_folders": sub_folders,
                "registry_id": registry_id,
                "device_id": device_id,
            },
        )
