"""UUFI Data Provider for UDMI MCP Server.

Encapsulates external-facing messaging transport (MQTT/UUFI), Layer 1 service handshakes,
device configuration mutations, system model operations, live state queries, and
event stream ingress according to the authoritative UUFI specification (docs/specs/uufi.md).
"""

from collections import deque
from datetime import datetime, timezone
import json
import os
import socket
import sys
import threading
import time
from typing import Any, Deque, Dict, List, Optional, Tuple
import uuid

script_dir = os.path.dirname(os.path.realpath(__file__))
repo_root = os.path.abspath(os.path.join(script_dir, "../.."))
common_py = os.path.join(repo_root, "common", "src", "main", "python")
if os.path.exists(common_py) and common_py not in sys.path:
    sys.path.insert(0, common_py)

import paho.mqtt.client as mqtt
from udmi.common.project_spec import parse_project_spec
from udmi.common.site_model import find_ca_file, find_cert_file
class UUFIProvider:
    """Provider encapsulating UUFI messaging, handshakes, queries, and event streams."""

    def __init__(
        self,
        project_spec: Optional[str] = None,
        site_model: Optional[str] = None,
        registry_id: Optional[str] = None,
        client_id: Optional[str] = None,
        prefix: Optional[str] = None,
        auto_connect: bool = False,
    ):
        self.project_spec = project_spec or os.environ.get("TARGET_PROJECT", "//mqtt/localhost")
        self.site_model = site_model or os.environ.get("SITE_MODEL", "sites/udmi_site_model")
        self.configured_registry = registry_id
        self.active_registry = registry_id or "default"
        self.handshake_status = "UNINITIALIZED"
        self.negotiated_functions_ver = 0

        # Parse project specification
        spec_info = (
            parse_project_spec(self.project_spec)
            if parse_project_spec
            else {"project": "vibrant", "port": None, "prefix": ""}
        )
        self.project_id = spec_info.get("project") or "vibrant"
        self.prefix = prefix if prefix is not None else (spec_info.get("prefix") or "")

        # Resolve host and port
        env_port = os.environ.get("MQTT_PORT")
        spec_port = spec_info.get("port")
        self.port = int(spec_port or env_port or 1883)
        resolved_host = os.environ.get("MQTT_HOST") or spec_info.get("project")
        if not resolved_host or resolved_host in ("localhost", "vibrant", "default"):
            resolved_host = "127.0.0.1"
        self.host = resolved_host

        # Unique Client ID per uufi.md §2.2
        nonce = uuid.uuid4().hex[:6]
        base_name = client_id or f"uufi_mcp_{nonce}"
        prefix_part = f"/{self.prefix}" if self.prefix else ""
        reg_part = f"/{self.active_registry}" if self.active_registry else ""
        self.client_id = f"{prefix_part}{reg_part}/{base_name}"

        # Inbound event ring buffer and subscription management
        self._lock = threading.RLock()
        self._event_condition = threading.Condition(self._lock)
        self._event_counter = 0
        self._event_buffer: Deque[Dict[str, Any]] = deque(maxlen=2000)

        # Correlation waiters for request-reply queries and handshakes: {tx_id: threading.Event, ...}
        self._pending_responses: Dict[str, Dict[str, Any]] = {}

        # Outgoing publication history (useful for test assertions and replay)
        self._published_messages: List[Dict[str, Any]] = []

        # Background MQTT state
        self._mqtt_client: Optional[Any] = None
        self.is_connected = False
        self._running = False
        self._thread: Optional[threading.Thread] = None

        if auto_connect:
            self.connect()

    # --------------------------------------------------------------------------
    # Topic & Envelope Helpers (uufi.md §2.2, §4, §8.4)
    # --------------------------------------------------------------------------

    def get_topic(
        self,
        sub_type: str,
        sub_folder: str,
        registry_id: Optional[str] = None,
        device_id: Optional[str] = None,
    ) -> str:
        """Constructs a rigidly structured, leading-slash UUFI topic path."""
        prefix_seg = f"/{self.prefix}" if self.prefix else ""
        if registry_id and device_id:
            return f"{prefix_seg}/uufi/r/{registry_id}/d/{device_id}/c/{sub_type}/{sub_folder}"
        return f"{prefix_seg}/uufi/c/{sub_type}/{sub_folder}"

    def create_envelope(
        self,
        sub_type: str,
        sub_folder: str,
        registry_id: Optional[str] = None,
        device_id: Optional[str] = None,
        transaction_id: Optional[str] = None,
        inner_payload: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Creates a standard UUFI message envelope with mandatory headers and nested payload."""
        now_iso = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        tx_id = transaction_id or f"UUFI:{uuid.uuid4().hex[:12]}"
        payload = dict(inner_payload or {})
        if "version" not in payload:
            payload["version"] = "1.5.2"
        if "timestamp" not in payload:
            payload["timestamp"] = now_iso

        envelope: Dict[str, Any] = {
            "subType": sub_type,
            "subFolder": sub_folder,
            "projectId": self.project_id,
            "transactionId": tx_id,
            "publishTime": now_iso,
            "source": self.client_id,
            "principal": self.client_id,
            "payload": payload,
        }
        if registry_id:
            envelope["deviceRegistryId"] = registry_id
        if device_id:
            envelope["deviceId"] = device_id

        return envelope

    # --------------------------------------------------------------------------
    # Connection Lifecycle
    # --------------------------------------------------------------------------

    def connect(self) -> bool:
        """Establishes connection to the MQTT broker and starts subscription loop."""
        if self.is_connected and self._mqtt_client:
            return True

        if not mqtt:
            print("Warning: paho-mqtt not available in environment", file=sys.stderr)
            return False

        self._running = True
        try:
            # Check TCP port availability
            with socket.create_connection((self.host, self.port), timeout=1.0):
                pass
        except Exception as e:
            print(f"Warning: MQTT broker unreachable at {self.host}:{self.port}: {e}", file=sys.stderr)
            return False

        try:
            client = mqtt.Client(client_id=self.client_id)
            user = os.environ.get("MQTT_USER", "rocket")
            pwd = os.environ.get("MQTT_PASS", "monkey")
            if user:
                client.username_pw_set(user, pwd)

            # Check for TLS certificates in site model or SSL_SECRETS_DIR
            ca_cert = find_ca_file(self.site_model)
            cert_pair = find_cert_file(self.site_model)
            if ca_cert and os.path.exists(ca_cert):
                import ssl
                if cert_pair:
                    client.tls_set(
                        ca_certs=ca_cert,
                        certfile=cert_pair[0],
                        keyfile=cert_pair[1],
                        cert_reqs=ssl.CERT_NONE,
                    )
                else:
                    client.tls_set(ca_certs=ca_cert, cert_reqs=ssl.CERT_NONE)
                client.tls_insecure_set(True)
            elif self.port == 8883 or str(os.environ.get("MQTT_PORT")) == "8883":
                import ssl
                client.tls_set(cert_reqs=ssl.CERT_NONE)
                client.tls_insecure_set(True)

            def _on_connect(c, userdata, flags, rc):
                if rc == 0:
                    self.is_connected = True
                    # Subscribe to all UUFI topics within prefix
                    sub_filter = f"/{self.prefix}/uufi/#" if self.prefix else "/uufi/#"
                    c.subscribe(sub_filter, qos=1)
                else:
                    self.is_connected = False

            def _on_disconnect(c, userdata, rc):
                self.is_connected = False

            def _on_message(c, userdata, msg):
                try:
                    payload_bytes = msg.payload
                    self._handle_inbound_raw(msg.topic, payload_bytes)
                except Exception as ex:
                    print(f"Error handling inbound UUFI message: {ex}", file=sys.stderr)

            client.on_connect = _on_connect
            client.on_disconnect = _on_disconnect
            client.on_message = _on_message

            client.connect(self.host, self.port, keepalive=60)
            client.loop_start()
            self._mqtt_client = client

            # Wait briefly for connection establishment
            t0 = time.time()
            while time.time() - t0 < 3.0 and not self.is_connected:
                time.sleep(0.05)

            return self.is_connected
        except Exception as e:
            print(f"Failed to connect to MQTT broker: {e}", file=sys.stderr)
            self.is_connected = False
            return False

    def disconnect(self) -> None:
        """Disconnects and terminates the background MQTT loop."""
        self._running = False
        if self._mqtt_client:
            try:
                self._mqtt_client.loop_stop()
                self._mqtt_client.disconnect()
            except Exception:
                pass
            self._mqtt_client = None
        self.is_connected = False

    def health(self) -> Dict[str, Any]:
        """Probes the broker connection and reports UUFI session status."""
        latency = 0.0
        status = "DISCONNECTED"
        try:
            t0 = time.perf_counter()
            with socket.create_connection((self.host, self.port), timeout=1.0):
                latency = round((time.perf_counter() - t0) * 1000, 2)
            status = "UP" if self.is_connected else "REACHABLE"
        except Exception:
            status = "DOWN"

        return {
            "status": status,
            "broker": f"{self.host}:{self.port}",
            "latency_ms": latency,
            "prefix": self.prefix,
            "client_id": self.client_id,
            "handshake_status": self.handshake_status,
            "active_registry": self.active_registry,
            "negotiated_functions_ver": self.negotiated_functions_ver,
            "buffered_events_count": len(self._event_buffer),
        }

    # --------------------------------------------------------------------------
    # Inbound Message Ingress & Correlation (uufi.md §2.2, §3.5)
    # --------------------------------------------------------------------------

    def _handle_inbound_raw(self, topic: str, payload_bytes: bytes) -> None:
        """Processes raw MQTT topic and bytes, notifying pending waiters and buffering events."""
        try:
            raw_text = payload_bytes.decode("utf-8")
            data = json.loads(raw_text) if raw_text.strip() else {}
        except Exception:
            return

        envelope = data if isinstance(data, dict) else {}
        inner_payload = envelope.get("payload", envelope)

        sub_type = envelope.get("subType")
        sub_folder = envelope.get("subFolder")
        reg_id = envelope.get("deviceRegistryId")
        dev_id = envelope.get("deviceId")
        tx_id = envelope.get("transactionId")

        # Fallback to parse attributes from topic if missing from envelope wrapper
        if not sub_type or not sub_folder:
            parts = [p for p in topic.strip("/").split("/") if p]
            # Pattern: .../r/{reg}/d/{dev}/c/{subType}/{subFolder} or .../c/{subType}/{subFolder}
            for i, p in enumerate(parts):
                if p == "r" and i + 1 < len(parts):
                    reg_id = reg_id or parts[i + 1]
                elif p == "d" and i + 1 < len(parts):
                    dev_id = dev_id or parts[i + 1]
                elif p == "c" and i + 2 < len(parts):
                    sub_type = sub_type or parts[i + 1]
                    sub_folder = sub_folder or parts[i + 2]

        # 1. Check pending response waiters (correlated via transactionId)
        with self._lock:
            # Check symmetric envelope transactionId or payload reply transaction_id
            reply_tx = None
            if isinstance(inner_payload, dict):
                reply_block = inner_payload.get("reply", {})
                if isinstance(reply_block, dict):
                    reply_tx = reply_block.get("transaction_id")

            target_tx = tx_id or reply_tx
            if target_tx and target_tx in self._pending_responses:
                slot = self._pending_responses[target_tx]
                slot["response"] = {
                    "envelope": envelope,
                    "payload": inner_payload,
                    "topic": topic,
                }
                event_obj = slot.get("event")
                if event_obj:
                    event_obj.set()

            # 2. Buffer message in thread-safe event ring buffer
            self._event_counter += 1
            event_id = self._event_counter
            event_record = {
                "id": event_id,
                "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "topic": topic,
                "subType": sub_type or "unknown",
                "subFolder": sub_folder or "unknown",
                "deviceRegistryId": reg_id,
                "deviceId": dev_id,
                "transactionId": tx_id,
                "envelope": envelope,
                "payload": inner_payload,
            }
            self._event_buffer.append(event_record)
            self._event_condition.notify_all()

    def inject_inbound_message(self, topic: str, data: Dict[str, Any]) -> None:
        """In-process injection for testing without live MQTT broker."""
        raw_bytes = json.dumps(data).encode("utf-8")
        self._handle_inbound_raw(topic, raw_bytes)

    # --------------------------------------------------------------------------
    # Layer 1 Handshake Protocol (uufi.md §3)
    # --------------------------------------------------------------------------

    def handshake(
        self,
        functions_ver: int = 9,
        timeout_sec: float = 10.0,
    ) -> Dict[str, Any]:
        """Executes Layer 1 service handshake against /uufi/c/state/udmi and verifies reply."""
        tx_id = f"UUFI:hs:{uuid.uuid4().hex[:8]}"
        req_topic = self.get_topic("state", "udmi")
        resp_topic = self.get_topic("config", "udmi")

        setup_payload = {
            "functions_ver": functions_ver,
            "transaction_id": tx_id,
            "msg_source": self.client_id,
        }
        envelope = self.create_envelope(
            sub_type="state",
            sub_folder="udmi",
            transaction_id=tx_id,
            inner_payload={"setup": setup_payload},
        )

        response_event = threading.Event()
        with self._lock:
            self._pending_responses[tx_id] = {
                "event": response_event,
                "response": None,
            }

        start_time = time.time()
        last_publish = 0.0

        try:
            while time.time() - start_time < timeout_sec:
                # Periodic publish (every 5 seconds per §3.6)
                if time.time() - last_publish >= 5.0:
                    self._publish_raw(req_topic, envelope)
                    last_publish = time.time()

                if response_event.wait(timeout=0.5):
                    break

            with self._lock:
                slot = self._pending_responses.pop(tx_id, None)
                if slot and slot.get("response"):
                    resp_data = slot["response"]
                    payload = resp_data.get("payload", {})
                    # Inspect setup block in reply
                    setup_block = payload.get("setup", {}) if isinstance(payload, dict) else {}
                    if isinstance(setup_block, dict):
                        discovered_reg = setup_block.get("deviceRegistryId")
                        if discovered_reg:
                            self.active_registry = discovered_reg
                        negotiated_ver = setup_block.get("functions_ver", functions_ver)
                        self.negotiated_functions_ver = int(negotiated_ver)

                    self.handshake_status = "ACTIVE"
                    return {
                        "status": "ACTIVE",
                        "deviceRegistryId": self.active_registry,
                        "functions_ver": self.negotiated_functions_ver,
                        "transactionId": tx_id,
                        "reply_topic": resp_topic,
                    }

        finally:
            with self._lock:
                self._pending_responses.pop(tx_id, None)

        # Timeout occurred
        raise TimeoutError(
            f"UUFI Handshake timed out after {timeout_sec}s awaiting reply on {resp_topic} for {tx_id}"
        )

    # --------------------------------------------------------------------------
    # Device Configuration Mutation (uufi.md §2.2, §6, §8)
    # --------------------------------------------------------------------------

    def publish_config(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Publishes configuration update with mandatory envelope duplication and QoS 1."""
        topic = self.get_topic("config", sub_folder, registry_id=registry_id, device_id=device_id)
        envelope = self.create_envelope(
            sub_type="config",
            sub_folder=sub_folder,
            registry_id=registry_id,
            device_id=device_id,
            inner_payload=payload,
        )

        self._publish_raw(topic, envelope)

        return {
            "status": "DISPATCHED",
            "topic": topic,
            "transactionId": envelope["transactionId"],
            "publishTime": envelope["publishTime"],
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": sub_folder,
        }

    # --------------------------------------------------------------------------
    # System Model Operations (uufi.md §2.2, §5)
    # --------------------------------------------------------------------------

    def query_system_model(
        self,
        registry_id: str,
        device_id: str,
        timeout_sec: float = 15.0,
    ) -> Dict[str, Any]:
        """Queries device system model on query/system and waits for correlated model/system reply."""
        tx_id = f"UUFI:model:{uuid.uuid4().hex[:8]}"
        req_topic = self.get_topic("query", "system", registry_id=registry_id, device_id=device_id)
        envelope = self.create_envelope(
            sub_type="query",
            sub_folder="system",
            registry_id=registry_id,
            device_id=device_id,
            transaction_id=tx_id,
            inner_payload={},
        )

        response_event = threading.Event()
        with self._lock:
            self._pending_responses[tx_id] = {
                "event": response_event,
                "response": None,
            }

        try:
            self._publish_raw(req_topic, envelope)
            if response_event.wait(timeout=timeout_sec):
                with self._lock:
                    slot = self._pending_responses.pop(tx_id, None)
                    if slot and slot.get("response"):
                        return slot["response"]["payload"]
        finally:
            with self._lock:
                self._pending_responses.pop(tx_id, None)

        raise TimeoutError(f"System model query timed out after {timeout_sec}s for device {device_id}")

    def update_system_model(
        self,
        registry_id: str,
        device_id: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Publishes a partial-merge system model update on model/system."""
        topic = self.get_topic("model", "system", registry_id=registry_id, device_id=device_id)
        envelope = self.create_envelope(
            sub_type="model",
            sub_folder="system",
            registry_id=registry_id,
            device_id=device_id,
            inner_payload=payload,
        )

        self._publish_raw(topic, envelope)

        return {
            "status": "DISPATCHED",
            "topic": topic,
            "transactionId": envelope["transactionId"],
            "publishTime": envelope["publishTime"],
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": "system",
        }

    # --------------------------------------------------------------------------
    # State Query Service (uufi.md §2.2, §6)
    # --------------------------------------------------------------------------

    def query_state(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str = "blobset",
        timeout_sec: float = 15.0,
    ) -> Dict[str, Any]:
        """Queries cached device state on query/state and awaits correlated state response."""
        tx_id = f"UUFI:state:{uuid.uuid4().hex[:8]}"
        req_topic = self.get_topic("query", "state", registry_id=registry_id, device_id=device_id)
        envelope = self.create_envelope(
            sub_type="query",
            sub_folder="state",
            registry_id=registry_id,
            device_id=device_id,
            transaction_id=tx_id,
            inner_payload={"target": sub_folder},
        )

        response_event = threading.Event()
        with self._lock:
            self._pending_responses[tx_id] = {
                "event": response_event,
                "response": None,
            }

        try:
            self._publish_raw(req_topic, envelope)
            if response_event.wait(timeout=timeout_sec):
                with self._lock:
                    slot = self._pending_responses.pop(tx_id, None)
                    if slot and slot.get("response"):
                        return slot["response"]["payload"]
        finally:
            with self._lock:
                self._pending_responses.pop(tx_id, None)

        raise TimeoutError(f"State query timed out after {timeout_sec}s for device {device_id} ({sub_folder})")

    # --------------------------------------------------------------------------
    # Event Stream Polling (uufi.md §2.2, §6)
    # --------------------------------------------------------------------------

    def poll_events(
        self,
        cursor: int = 0,
        timeout_sec: float = 0.0,
        sub_types: Optional[List[str]] = None,
        sub_folders: Optional[List[str]] = None,
        registry_id: Optional[str] = None,
        device_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Retrieves inbound messages from the event ring buffer since cursor, optionally waiting."""
        type_filter = set(sub_types) if sub_types else None
        folder_filter = set(sub_folders) if sub_folders else None

        def _gather_matching() -> Tuple[List[Dict[str, Any]], int]:
            matched: List[Dict[str, Any]] = []
            max_id = cursor
            for item in self._event_buffer:
                item_id = item["id"]
                if item_id <= cursor:
                    continue
                max_id = max(max_id, item_id)

                if type_filter and item.get("subType") not in type_filter:
                    continue
                if folder_filter and item.get("subFolder") not in folder_filter:
                    continue
                if registry_id and item.get("deviceRegistryId") != registry_id:
                    continue
                if device_id and item.get("deviceId") != device_id:
                    continue

                matched.append(item)
            return matched, max_id

        with self._lock:
            events, next_cursor = _gather_matching()
            if events or timeout_sec <= 0:
                return {
                    "cursor": next_cursor,
                    "events": events,
                }

            # Wait for incoming events on condition variable
            deadline = time.time() + timeout_sec
            while not events and time.time() < deadline:
                remaining = max(0.01, deadline - time.time())
                self._event_condition.wait(timeout=remaining)
                events, next_cursor = _gather_matching()

            return {
                "cursor": next_cursor,
                "events": events,
            }

    # --------------------------------------------------------------------------
    # Publication Mechanics
    # --------------------------------------------------------------------------

    def _publish_raw(self, topic: str, envelope: Dict[str, Any]) -> None:
        """Publishes envelope dictionary as JSON bytes to MQTT broker."""
        record = {
            "topic": topic,
            "envelope": envelope,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        with self._lock:
            self._published_messages.append(record)

        if self._mqtt_client and self.is_connected:
            raw_data = json.dumps(envelope)
            inf = self._mqtt_client.publish(topic, raw_data, qos=1)
            inf.wait_for_publish(timeout=5.0)

    def get_published_messages(self) -> List[Dict[str, Any]]:
        """Returns log of published messages for inspection or testing."""
        with self._lock:
            return list(self._published_messages)
