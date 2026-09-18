"""UUFI client, configuration dispatcher, and rollout manager for GUMMI.

Integrates GUMMI's management plane with the external UUFI messaging fabric
via the UUFI MCP Client, providing declarative configuration dispatch,
rollout orchestration, and real-time SSE streaming without direct paho-mqtt
broker dependencies.
"""

from datetime import datetime, timezone
import json
import os
import queue
import sys
import threading
import time
from typing import Any, Callable, Dict, List, Optional
import uuid

from mcp.uufi.client import UUFIClient
class GummiUUFIClient:
    """Manages UUFI messaging, configuration mutations, and live event distribution for GUMMI."""

    def __init__(
        self,
        project_spec: Optional[str] = None,
        site_model: Optional[str] = None,
        uufi_port: Optional[int] = None,
        uufi_endpoint: Optional[str] = None,
        uufi_client: Optional[Any] = None,
        mock_mode: bool = False,
        db: Optional[Any] = None,
    ):
        self.mock_mode = mock_mode
        self.project_spec = project_spec
        self.site_model = site_model
        self.uufi_port = uufi_port or 8087
        self.uufi_endpoint = uufi_endpoint
        self.client_id = f"gummi_{uuid.uuid4().hex[:8]}"
        self.db = db

        # Canonical UUFI MCP client interface (no opportunistic fallback heuristics)
        if self.mock_mode:
            self.uufi = None
        elif uufi_client is not None:
            self.uufi = uufi_client
        else:
            self.uufi = UUFIClient(
                endpoint=self.uufi_endpoint,
                port=self.uufi_port if not self.uufi_endpoint else None,
            )

        self.event_subscribers: List[queue.Queue] = []
        self._active_rollouts_cache: List[Dict[str, Any]] = []
        self._lock = threading.RLock()
        self.cursor = 0

        # Background polling worker thread
        self._thread: Optional[threading.Thread] = None
        self._running = False

    @property
    def is_connected(self) -> bool:
        """Reports whether the underlying UUFI transport is connected."""
        if self.mock_mode:
            return True
        if not self.uufi:
            return False
        try:
            h = self.uufi.health()
            return h.get("status") in ("UP", "REACHABLE", "ACTIVE")
        except Exception:
            return False

    def start(self) -> None:
        """Starts background event polling loop and rollout monitoring."""
        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        """Stops background connection and polling."""
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.0)

    def _run_loop(self) -> None:
        """Background loop polling UUFI events and updating SSE subscribers and rollouts."""
        if self.mock_mode:
            while self._running:
                time.sleep(0.5)
            return

        last_health_check = 0.0
        while self._running:
            try:
                # Poll events from UUFI MCP server
                res = self.uufi.poll_events(cursor=self.cursor, timeout_sec=0.5)
                events = res.get("events", []) if isinstance(res, dict) else []
                new_cursor = res.get("cursor", self.cursor) if isinstance(res, dict) else self.cursor

                for ev in events:
                    ev_id = ev.get("id", 0)
                    if ev_id > self.cursor:
                        self.cursor = ev_id

                    sub_type = ev.get("subType")
                    sub_folder = ev.get("subFolder")
                    reg_id = ev.get("deviceRegistryId", "default")
                    dev_id = ev.get("deviceId", "unknown")
                    payload = ev.get("payload", {})

                    if sub_type == "state":
                        self.broadcast_event("device_state", {
                            "registry_id": reg_id,
                            "device_id": dev_id,
                            "sub_folder": sub_folder,
                            "timestamp": ev.get("timestamp"),
                        })
                    elif sub_folder in ("status", "validation"):
                        self.broadcast_event("alert", {
                            "topic": ev.get("topic"),
                            "payload": payload,
                            "timestamp": ev.get("timestamp"),
                        })

                if new_cursor > self.cursor:
                    self.cursor = new_cursor

                # Periodically broadcast system status and sync active rollouts
                now = time.time()
                if now - last_health_check >= 5.0:
                    last_health_check = now
                    try:
                        h = self.uufi.health()
                        st = "CONNECTED" if h.get("status") in ("UP", "REACHABLE", "ACTIVE") else "DISCONNECTED"
                        self.broadcast_event("system_status", {"uufi": st, "broker": h.get("broker")})
                    except Exception:
                        self.broadcast_event("system_status", {"uufi": "DISCONNECTED"})
                        
                    try:
                        if self.db:
                            rollouts = self.db.list_rollouts()
                            new_cache = []
                            for r in rollouts:
                                if r.get("status") in ("RUNNING", "COMPLETED"):
                                    # Find previous state in cache
                                    prev = next((x for x in self._active_rollouts_cache if x["id"] == r["id"]), None)
                                    if prev:
                                        if prev.get("converged_devices") != r.get("converged_devices") or prev.get("status") != r.get("status"):
                                            self.broadcast_event("rollout_progress", r)
                                    new_cache.append(r)
                            self._active_rollouts_cache = new_cache
                    except Exception:
                        pass

            except Exception:
                for _ in range(10):
                    if not self._running:
                        break
                    time.sleep(0.1)

    # --------------------------------------------------------------------------
    # Configuration Mutation
    # --------------------------------------------------------------------------

    def publish_config(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Publishes configuration update via the UUFI MCP client."""
        tx_id = str(uuid.uuid4())
        topic = f"/uufi/r/{registry_id}/d/{device_id}/c/config/{sub_folder}"

        if self.mock_mode:
            self.broadcast_event("config_dispatched", {
                "registry_id": registry_id,
                "device_id": device_id,
                "sub_folder": sub_folder,
                "transaction_id": tx_id,
                "dispatched": False,
                "simulated": True,
            })
            return {
                "status": "SIMULATED",
                "transaction_id": tx_id,
                "topic": topic,
                "message": "Configuration simulated in mock mode.",
            }

        if not self.uufi:
            raise ConnectionError("UUFI client is not configured or unavailable")

        res = self.uufi.publish_config(registry_id, device_id, sub_folder, payload)
        if not isinstance(res, dict) or res.get("status") != "DISPATCHED":
            err_msg = res.get("error") if isinstance(res, dict) else "Unknown dispatch error"
            raise RuntimeError(f"Failed to dispatch config to UUFI bus: {err_msg}")

        tx_id = res.get("transactionId") or res.get("transaction_id") or tx_id
        topic = res.get("topic") or topic

        self.broadcast_event("config_dispatched", {
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": sub_folder,
            "transaction_id": tx_id,
            "dispatched": True,
        })

        return {
            "status": "DISPATCHED",
            "transaction_id": tx_id,
            "topic": topic,
            "message": "Configuration successfully published to UUFI bus.",
        }

    # --------------------------------------------------------------------------
    # Managed Rollouts Convergence
    # --------------------------------------------------------------------------



    # --------------------------------------------------------------------------
    # Server-Sent Events (SSE) Streaming
    # --------------------------------------------------------------------------

    def register_sse_subscriber(self) -> queue.Queue:
        """Registers a queue for receiving live broadcast events."""
        q = queue.Queue(maxsize=100)
        with self._lock:
            self.event_subscribers.append(q)
        return q

    def unregister_sse_subscriber(self, q: queue.Queue) -> None:
        """Removes an SSE client queue."""
        with self._lock:
            if q in self.event_subscribers:
                self.event_subscribers.remove(q)

    def broadcast_event(self, event_type: str, data: Any) -> None:
        """Emits an event to all connected SSE clients."""
        payload = f"event: {event_type}\ndata: {json.dumps(data)}\n\n"
        with self._lock:
            for q in list(self.event_subscribers):
                try:
                    q.put_nowait(payload)
                except queue.Full:
                    pass
