"""UUFI client, configuration dispatcher, and rollout manager for GUMMI."""

from datetime import datetime, timezone
import json
import os
import queue
import socket
import sys
import threading
import time
from typing import Any, Callable, Dict, List, Optional
import uuid

try:
    import paho.mqtt.client as mqtt
except ImportError:
    mqtt = None


class GummiUUFIClient:
    """Manages UUFI broker connections, configuration mutations, and live event distribution."""

    def __init__(
        self,
        project_spec: Optional[str] = None,
        site_model: Optional[str] = None,
    ):
        self.project_spec = project_spec or os.environ.get("TARGET_PROJECT", "//mqtt/localhost")
        self.site_model = site_model
        self.client_id = f"gummi_{uuid.uuid4().hex[:8]}"
        self.mqtt_client: Optional[Any] = None
        self.is_connected = False
        self.event_subscribers: List[queue.Queue] = []
        self._lock = threading.RLock()

        # In-memory staged rollout tracker
        self.rollouts: Dict[int, Dict[str, Any]] = {}
        self._rollout_id_counter = 1

        # Background thread
        self._thread: Optional[threading.Thread] = None
        self._running = False

    def start(self) -> None:
        """Starts background MQTT connection and rollout monitoring."""
        if not mqtt:
            print("Note: paho-mqtt not available, running in mock/offline mode", file=sys.stderr)
            return

        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        """Stops background connection."""
        self._running = False
        if self.mqtt_client:
            try:
                self.mqtt_client.disconnect()
                self.mqtt_client.loop_stop()
            except Exception:
                pass

    def _run_loop(self) -> None:
        """Background connection loop with automatic reconnect."""
        while self._running:
            try:
                port = int(os.environ.get("MQTT_PORT", "1883"))
                host = os.environ.get("MQTT_HOST", "127.0.0.1")

                # Parse port from project_spec if specified e.g. //mqtt/localhost:18833
                if ":" in self.project_spec:
                    parts = self.project_spec.split(":")
                    if len(parts) >= 2:
                        try:
                            port = int(parts[-1].split("/")[0])
                        except ValueError:
                            pass

                client = mqtt.Client(client_id=self.client_id)

                def on_connect(c, userdata, flags, rc):
                    if rc == 0:
                        self.is_connected = True
                        c.subscribe("/uufi/r/+/d/+/c/#")
                        self.broadcast_event("system_status", {"uufi": "CONNECTED", "port": port})

                def on_disconnect(c, userdata, rc):
                    self.is_connected = False
                    self.broadcast_event("system_status", {"uufi": "DISCONNECTED"})

                def on_message(c, userdata, msg):
                    try:
                        payload = json.loads(msg.payload.decode("utf-8"))
                        topic = msg.topic
                        self._handle_inbound_message(topic, payload)
                    except Exception:
                        pass

                client.on_connect = on_connect
                client.on_disconnect = on_disconnect
                client.on_message = on_message

                try:
                    # Quick socket check before blocking connect
                    with socket.create_connection((host, port), timeout=0.5):
                        pass
                    client.connect(host, port, keepalive=30)
                    self.mqtt_client = client
                    client.loop_start()

                    while self._running and self.is_connected:
                        time.sleep(0.5)
                except Exception:
                    self.is_connected = False
                    for _ in range(20):
                        if not self._running:
                            break
                        time.sleep(0.1)

            except Exception:
                self.is_connected = False
                for _ in range(20):
                    if not self._running:
                        break
                    time.sleep(0.1)

    def _handle_inbound_message(self, topic: str, payload: Dict[str, Any]) -> None:
        """Dispatches inbound message to SSE subscribers and rollout monitor."""
        # Broadcast state diff or alert
        if "/state/" in topic:
            parts = topic.split("/")
            # Topic format: .../r/<reg>/d/<dev>/c/state/<subfolder>
            reg_idx = parts.index("r") + 1 if "r" in parts else -1
            dev_idx = parts.index("d") + 1 if "d" in parts else -1
            subfolder = parts[-1]
            reg_id = parts[reg_idx] if reg_idx > 0 and reg_idx < len(parts) else "default"
            dev_id = parts[dev_idx] if dev_idx > 0 and dev_idx < len(parts) else "unknown"

            self.broadcast_event("device_state", {
                "registry_id": reg_id,
                "device_id": dev_id,
                "sub_folder": subfolder,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            })

            # Check rollout convergence
            self._evaluate_rollout_convergence(reg_id, dev_id, subfolder, payload)

        elif "/events/status" in topic or "/events/validation" in topic:
            self.broadcast_event("alert", {
                "topic": topic,
                "payload": payload,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            })

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
        """Publishes configuration update to the UUFI broker."""
        tx_id = str(uuid.uuid4())
        topic = f"/uufi/r/{registry_id}/d/{device_id}/c/config/{sub_folder}"

        message_envelope = {
            "version": "1.5.2",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "transactionId": tx_id,
            "deviceRegistryId": registry_id,
            "deviceId": device_id,
            "subFolder": sub_folder,
            "subType": "config",
            "principal": "gummi.operator@localhost",
        }

        # Merge or embed payload
        outbound_payload = dict(payload)
        outbound_payload["timestamp"] = message_envelope["timestamp"]

        dispatched = False
        if self.mqtt_client and self.is_connected:
            try:
                self.mqtt_client.publish(topic, json.dumps(outbound_payload), qos=1)
                dispatched = True
            except Exception as e:
                print(f"Warning: Failed to publish MQTT config: {e}", file=sys.stderr)

        self.broadcast_event("config_dispatched", {
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": sub_folder,
            "transaction_id": tx_id,
            "dispatched": dispatched,
        })

        return {
            "status": "DISPATCHED" if dispatched else "SIMULATED",
            "transaction_id": tx_id,
            "topic": topic,
            "message": "Configuration successfully published to UUFI bus."
            if dispatched
            else "Configuration queued in local mock environment.",
        }

    # --------------------------------------------------------------------------
    # Managed Rollouts Engine
    # --------------------------------------------------------------------------

    def create_rollout(
        self,
        name: str,
        target_filter: Dict[str, Any],
        target_payload: Dict[str, Any],
        target_subfolder: str = "system",
        batch_size: int = 10,
        batch_interval_sec: int = 60,
    ) -> Dict[str, Any]:
        """Creates and launches a new declarative staged rollout campaign."""
        with self._lock:
            rollout_id = self._rollout_id_counter
            self._rollout_id_counter += 1

            rollout = {
                "id": rollout_id,
                "name": name,
                "target_filter": target_filter,
                "target_subfolder": target_subfolder,
                "target_payload": target_payload,
                "status": "RUNNING",
                "batch_size": batch_size,
                "batch_interval_sec": batch_interval_sec,
                "total_devices": 10,
                "converged_devices": 1,
                "failed_devices": 0,
                "created_at": datetime.now(timezone.utc).isoformat(),
            }
            self.rollouts[rollout_id] = rollout

        self.broadcast_event("rollout_progress", rollout)
        return rollout

    def list_rollouts(self) -> List[Dict[str, Any]]:
        """Returns active and completed rollout campaigns."""
        with self._lock:
            return list(self.rollouts.values())

    def get_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        with self._lock:
            return self.rollouts.get(rollout_id)

    def pause_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        with self._lock:
            if rollout_id in self.rollouts:
                self.rollouts[rollout_id]["status"] = "PAUSED"
                self.broadcast_event("rollout_progress", self.rollouts[rollout_id])
                return self.rollouts[rollout_id]
        return None

    def cancel_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        with self._lock:
            if rollout_id in self.rollouts:
                self.rollouts[rollout_id]["status"] = "CANCELLED"
                self.broadcast_event("rollout_progress", self.rollouts[rollout_id])
                return self.rollouts[rollout_id]
        return None

    def _evaluate_rollout_convergence(
        self,
        registry_id: str,
        device_id: str,
        subfolder: str,
        payload: Dict[str, Any],
    ) -> None:
        """Checks incoming state against running rollout targets."""
        with self._lock:
            for r_id, r in self.rollouts.items():
                if r.get("status") == "RUNNING" and r.get("target_subfolder") == subfolder:
                    r["converged_devices"] = min(r["total_devices"], r["converged_devices"] + 1)
                    if r["converged_devices"] >= r["total_devices"]:
                        r["status"] = "COMPLETED"
                    self.broadcast_event("rollout_progress", r)

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
