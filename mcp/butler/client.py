"""Butler MCP JSON-RPC Client.

Allows Python components (such as mapping engines, CLI tools, and web services) to interact
with the Butler service via JSON-RPC 2.0 without making any direct database calls or
depending on internal relational or time-series datastore implementation details.
"""

import itertools
import json
from typing import Any, Dict, List, Optional
import urllib.error
import urllib.request


class ButlerClient:
    """Client for communicating with the Butler MCP JSON-RPC service."""

    def __init__(
        self,
        endpoint: Optional[str] = None,
        port: Optional[int] = None,
        timeout: float = 10.0,
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
            self.endpoint = "http://127.0.0.1:8088/rpc"

        self.timeout = timeout
        self._id_counter = itertools.count(1)

    def call_rpc(self, method: str, params: Optional[Dict[str, Any]] = None) -> Any:
        """Execute a JSON-RPC 2.0 call against the Butler MCP server."""
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
            raise RuntimeError(f"Failed to connect to Butler MCP server at {self.endpoint}: {e}") from e

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
        """Check connection status and health of the Butler service."""
        return self.call_rpc("health", {})

    def get_discovery_events(self, registry_id: str) -> List[Dict[str, Any]]:
        """Fetch discovery events for a device registry."""
        return self.call_rpc("get_discovery_events", {"registry_id": registry_id})

    def get_discovered_devices(self, registry_id: str) -> List[Dict[str, Any]]:
        """Fetch normalized discovered devices with addresses for reconciliation mapping."""
        return self.call_rpc("get_discovered_devices", {"registry_id": registry_id})

    def get_device_messages(self, registry_id: str, device_id: str) -> List[Dict[str, Any]]:
        """Fetch chronological lifecycle messages (models, discovery, proposals) for a device."""
        return self.call_rpc("get_device_messages", {"registry_id": registry_id, "device_id": device_id})

    def record_message(
        self,
        registry_id: str,
        device_id: str,
        sub_type: str,
        sub_folder: str,
        payload: Dict[str, Any],
        project_id: Optional[str] = None,
        timestamp: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Record a model, discovery event, or proposal into the Butler message lifecycle store."""
        params = {
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_type": sub_type,
            "sub_folder": sub_folder,
            "payload": payload,
        }
        if project_id:
            params["project_id"] = project_id
        if timestamp:
            params["timestamp"] = timestamp
        return self.call_rpc("record_message", params)

    def get_device_telemetry(
        self,
        registry_id: str,
        device_id: str,
        point_names: Optional[List[str]] = None,
        start: str = "-1h",
        stop: str = "now()",
    ) -> Dict[str, Any]:
        """Query time-series telemetry point values for a device."""
        params = {
            "registry_id": registry_id,
            "device_id": device_id,
            "start": start,
            "stop": stop,
        }
        if point_names:
            params["point_names"] = point_names
        return self.call_rpc("get_device_telemetry", params)

    def write_telemetry(
        self,
        registry_id: str,
        device_id: str,
        points: Dict[str, Any],
        timestamp: Optional[str] = None,
        project_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Record point telemetry measurements for a device."""
        params = {
            "registry_id": registry_id,
            "device_id": device_id,
            "points": points,
        }
        if timestamp:
            params["timestamp"] = timestamp
        if project_id:
            params["project_id"] = project_id
        return self.call_rpc("write_telemetry", params)

    def clear_registry_mapping_data(self, registry_id: str) -> Dict[str, Any]:
        """Delete discovery events and proposals for a registry."""
        return self.call_rpc("clear_registry_mapping_data", {"registry_id": registry_id})

    def get_portfolio_summary(self) -> Dict[str, Any]:
        """Fetch aggregate device counts, online/offline breakdown, and recent alerts count."""
        return self.call_rpc("get_portfolio_summary", {})

    def get_alerts(self, limit: int = 50, min_level: int = 500) -> List[Dict[str, Any]]:
        """Fetch recent validation alerts above severity threshold."""
        return self.call_rpc("get_alerts", {"limit": limit, "min_level": min_level})

    def get_devices(
        self,
        limit: int = 100,
        offset: int = 0,
        registry_id: Optional[str] = None,
        device_prefix: Optional[str] = None,
        make: Optional[str] = None,
        model: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Fetch paginated, filtered devices list."""
        params = {
            "limit": limit,
            "offset": offset,
        }
        if registry_id:
            params["registry_id"] = registry_id
        if device_prefix:
            params["device_prefix"] = device_prefix
        if make:
            params["make"] = make
        if model:
            params["model"] = model
        if status:
            params["status"] = status
        if search:
            params["search"] = search
        return self.call_rpc("get_devices", params)

    def get_device_detail(self, registry_id: str, device_id: str) -> Optional[Dict[str, Any]]:
        """Fetch detailed state, metadata, points, and validation events for a device."""
        return self.call_rpc("get_device_detail", {"registry_id": registry_id, "device_id": device_id})

    def create_rollout(
        self,
        name: str,
        target_filter: Dict[str, Any],
        target_payload: Dict[str, Any],
        target_subfolder: str = "system",
        batch_size: int = 10,
        batch_interval_sec: int = 60,
        total_devices: int = 0,
    ) -> Dict[str, Any]:
        """Create a new declarative staged rollout campaign."""
        params = {
            "name": name,
            "target_filter": target_filter,
            "target_payload": target_payload,
            "target_subfolder": target_subfolder,
            "batch_size": batch_size,
            "batch_interval_sec": batch_interval_sec,
            "total_devices": total_devices,
        }
        return self.call_rpc("create_rollout", params)

    def list_rollouts(self) -> List[Dict[str, Any]]:
        """List all active and completed rollout campaigns."""
        return self.call_rpc("list_rollouts", {})

    def get_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        """Retrieve details for a rollout campaign."""
        return self.call_rpc("get_rollout", {"rollout_id": rollout_id})

    def update_rollout(
        self,
        rollout_id: int,
        status: Optional[str] = None,
        converged_devices: Optional[int] = None,
        failed_devices: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Update rollout status (e.g. PAUSED, CANCELLED) or progress."""
        params: Dict[str, Any] = {"rollout_id": rollout_id}
        if status is not None:
            params["status"] = status
        if converged_devices is not None:
            params["converged_devices"] = converged_devices
        if failed_devices is not None:
            params["failed_devices"] = failed_devices
        return self.call_rpc("update_rollout", params)

    def tools_list(self) -> List[Dict[str, Any]]:
        """List all available MCP tools."""
        res = self.call_rpc("tools/list", {})
        return res.get("tools", [])
