"""UUFI Data Provider for UDMI MCP Server.

Encapsulates external-facing messaging transport (MQTT/UUFI and PubSub/UUFI), Layer 1 service
handshakes, device configuration mutations, system model operations, live state queries, and
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
from typing import Any, Deque, Dict, List, Optional, Tuple, Union
import urllib.parse
import uuid

script_dir = os.path.dirname(os.path.realpath(__file__))
repo_root = os.path.abspath(os.path.join(script_dir, "../.."))
common_py = os.path.join(repo_root, "common", "src", "main", "python")
if os.path.exists(common_py) and common_py not in sys.path:
    sys.path.insert(0, common_py)

import paho.mqtt.client as mqtt
from udmi.common.project_spec import parse_project_spec
from udmi.common.site_model import find_ca_file, find_cert_file

SYSTEM_SOURCES = ("udmis", "pubsub+udmis")
DEFAULT_PUBSUB_TOPIC = "udmi_uufi"
DEFAULT_UDMI_VERSION = "1.5.7"


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

        self.transport = "mqtt"
        self.host = "127.0.0.1"
        self.port = 1883
        self.pubsub_topic = DEFAULT_PUBSUB_TOPIC
        self.pubsub_subscription: Optional[str] = None

        self._parse_transport_config(client_id=client_id, prefix=prefix)

        # Inbound event ring buffer and subscription management
        self._lock = threading.RLock()
        self._event_condition = threading.Condition(self._lock)
        self._event_counter = 0
        self._event_buffer: Deque[Dict[str, Any]] = deque(maxlen=2000)

        # Correlation waiters for request-reply queries, handshakes, and config acks
        self._pending_responses: Dict[str, Dict[str, Any]] = {}

        # Outgoing publication history (bounded to prevent unbounded growth in long-running servers)
        self._published_messages: Deque[Dict[str, Any]] = deque(maxlen=2000)

        # Background MQTT / PubSub state
        self._mqtt_client: Optional[Any] = None
        self._pubsub_publisher: Optional[Any] = None
        self._pubsub_subscriber: Optional[Any] = None
        self._pubsub_topic_path: Optional[str] = None
        self._pubsub_sub_path: Optional[str] = None
        self._streaming_pull_future: Optional[Any] = None
        self.is_connected = False
        self._running = False
        self._thread: Optional[threading.Thread] = None

        if auto_connect:
            self.connect()

    def _parse_transport_config(
        self,
        client_id: Optional[str],
        prefix: Optional[str],
    ) -> None:
        spec = self.project_spec.strip()
        if spec.startswith("pubsub://"):
            parsed = urllib.parse.urlparse(spec)
            host_part = parsed.netloc.split("@")[-1]
            if parsed.port is not None or ":" in host_part:
                raise ValueError(
                    f"Port specification is prohibited for pubsub:// transport: {spec}"
                )
            if not parsed.hostname:
                raise ValueError(f"Missing GCP project ID in pubsub:// URI: {spec}")
            self.transport = "pubsub"
            self.project_id = parsed.hostname
            self.prefix = prefix if prefix is not None else ""
            user = parsed.username or client_id or "unknown"
            path_segs = [seg for seg in parsed.path.split("/") if seg]
            self.pubsub_topic = path_segs[0] if path_segs else DEFAULT_PUBSUB_TOPIC
            self.pubsub_subscription = f"{self.pubsub_topic}+{user}"
            self.client_id = user
            self.principal = f"{user}@"
            return

        spec_info = (
            parse_project_spec(spec)
            if parse_project_spec
            else {"provider": "mqtt", "project": "vibrant", "port": None, "prefix": "", "user": None}
        )
        if spec_info.get("provider") == "pubsub":
            raise ValueError(
                f"Non-canonical //pubsub/ spec is not supported; "
                f"use pubsub://[user@]project[/topic]: {spec}"
            )

        # Default MQTT / MQTTS transport
        self.transport = "mqtt"
        self.project_id = spec_info.get("project") or "vibrant"
        self.prefix = prefix if prefix is not None else (spec_info.get("prefix") or "")

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
        self.principal = self.client_id

    @staticmethod
    def _is_system_source(source: Optional[str]) -> bool:
        return source in SYSTEM_SOURCES

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
        inner_payload: Optional[Union[Dict[str, Any], str]] = None,
    ) -> Dict[str, Any]:
        """Creates a standard UUFI message envelope with mandatory headers and payload.

        - inner_payload=None produces a canonical single-channel Query (`"payload": None`).
        - inner_payload=<str> preserves the string sentinel without injecting object metadata.
        - inner_payload=<dict> copies the dict and ensures `version` and `timestamp` are set.
        """
        now_iso = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        tx_id = transaction_id or f"UUFI:{uuid.uuid4().hex[:12]}"

        payload: Optional[Union[Dict[str, Any], str]]
        if inner_payload is None:
            payload = None
        elif isinstance(inner_payload, str):
            payload = inner_payload
        else:
            payload = dict(inner_payload)
            if "version" not in payload:
                payload["version"] = DEFAULT_UDMI_VERSION
            if "timestamp" not in payload:
                payload["timestamp"] = now_iso

        envelope: Dict[str, Any] = {
            "subType": sub_type,
            "subFolder": sub_folder,
            "projectId": self.project_id,
            "transactionId": tx_id,
            "publishTime": now_iso,
            "source": self.client_id,
            "principal": self.principal,
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
        """Establishes connection to the configured MQTT or PubSub transport."""
        if self.transport == "pubsub":
            return self._connect_pubsub()
        return self._connect_mqtt()

    def _connect_pubsub(self) -> bool:
        if self.is_connected and self._pubsub_publisher:
            return True

        try:
            from google.cloud import pubsub_v1
        except ImportError as e:
            print(f"Warning: google-cloud-pubsub not available: {e}", file=sys.stderr)
            return False

        self._running = True
        try:
            publisher = pubsub_v1.PublisherClient()
            subscriber = pubsub_v1.SubscriberClient()
            topic_path = publisher.topic_path(self.project_id, self.pubsub_topic)
            sub_path = subscriber.subscription_path(self.project_id, self.pubsub_subscription)

            def _callback(message: Any) -> None:
                try:
                    message.ack()
                    self._handle_inbound_pubsub(dict(message.attributes), message.data)
                except Exception as ex:
                    print(f"Error handling inbound PubSub UUFI message: {ex}", file=sys.stderr)

            self._pubsub_publisher = publisher
            self._pubsub_subscriber = subscriber
            self._pubsub_topic_path = topic_path
            self._pubsub_sub_path = sub_path
            self._streaming_pull_future = subscriber.subscribe(sub_path, callback=_callback)
            self.is_connected = True
            return True
        except Exception as e:
            print(f"Failed to connect to PubSub transport: {e}", file=sys.stderr)
            self.is_connected = False
            return False

    def _connect_mqtt(self) -> bool:
        if self.is_connected and self._mqtt_client:
            return True

        if not mqtt:
            print("Warning: paho-mqtt not available in environment", file=sys.stderr)
            return False

        self._running = True
        try:
            with socket.create_connection((self.host, self.port), timeout=1.0):
                pass
        except Exception as e:
            print(
                f"Warning: MQTT broker unreachable at {self.host}:{self.port}: {e}",
                file=sys.stderr,
            )
            return False

        try:
            client = mqtt.Client(client_id=self.client_id)
            user = os.environ.get("MQTT_USER", "rocket")
            pwd = os.environ.get("MQTT_PASS", "monkey")
            if user:
                client.username_pw_set(user, pwd)

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
                    sub_filter = f"/{self.prefix}/uufi/#" if self.prefix else "/uufi/#"
                    c.subscribe(sub_filter, qos=1)
                else:
                    self.is_connected = False

            def _on_disconnect(c, userdata, rc):
                self.is_connected = False

            def _on_message(c, userdata, msg):
                try:
                    self._handle_inbound_raw(msg.topic, msg.payload)
                except Exception as ex:
                    print(f"Error handling inbound UUFI message: {ex}", file=sys.stderr)

            client.on_connect = _on_connect
            client.on_disconnect = _on_disconnect
            client.on_message = _on_message

            client.connect(self.host, self.port, keepalive=60)
            client.loop_start()
            self._mqtt_client = client

            t0 = time.time()
            while time.time() - t0 < 3.0 and not self.is_connected:
                time.sleep(0.05)

            return self.is_connected
        except Exception as e:
            print(f"Failed to connect to MQTT broker: {e}", file=sys.stderr)
            self.is_connected = False
            return False

    def disconnect(self) -> None:
        """Disconnects and terminates active MQTT or PubSub loops."""
        self._running = False
        if self._mqtt_client:
            try:
                self._mqtt_client.loop_stop()
                self._mqtt_client.disconnect()
            except Exception:
                pass
            self._mqtt_client = None
        if self._streaming_pull_future:
            try:
                self._streaming_pull_future.cancel()
            except Exception:
                pass
            self._streaming_pull_future = None
        if self._pubsub_subscriber:
            try:
                self._pubsub_subscriber.close()
            except Exception:
                pass
            self._pubsub_subscriber = None
        self._pubsub_publisher = None
        self.is_connected = False

    def health(self) -> Dict[str, Any]:
        """Probes the transport connection and reports UUFI session status."""
        if self.transport == "pubsub":
            return {
                "status": "UP" if self.is_connected else "DISCONNECTED",
                "transport": "pubsub",
                "broker": f"pubsub://{self.project_id}/{self.pubsub_topic}",
                "subscription": self.pubsub_subscription,
                "latency_ms": 0.0,
                "prefix": self.prefix,
                "client_id": self.client_id,
                "principal": self.principal,
                "handshake_status": self.handshake_status,
                "active_registry": self.active_registry,
                "negotiated_functions_ver": self.negotiated_functions_ver,
                "buffered_events_count": len(self._event_buffer),
            }

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
            "transport": "mqtt",
            "broker": f"{self.host}:{self.port}",
            "latency_ms": latency,
            "prefix": self.prefix,
            "client_id": self.client_id,
            "principal": self.principal,
            "handshake_status": self.handshake_status,
            "active_registry": self.active_registry,
            "negotiated_functions_ver": self.negotiated_functions_ver,
            "buffered_events_count": len(self._event_buffer),
        }

    # --------------------------------------------------------------------------
    # Inbound Message Ingress & Correlation (uufi.md §2.2, §3.5, §4)
    # --------------------------------------------------------------------------

    def _handle_inbound_pubsub(self, attributes: Dict[str, str], payload_bytes: bytes) -> None:
        """Processes unwrapped PubSub attributes + JSON data bytes."""
        msg_principal = attributes.get("principal")
        if msg_principal and msg_principal != self.principal:
            return

        try:
            raw_text = payload_bytes.decode("utf-8").strip() if payload_bytes else ""
            inner_payload: Any = json.loads(raw_text) if raw_text else None
        except Exception:
            return

        envelope: Dict[str, Any] = dict(attributes)
        envelope["payload"] = inner_payload

        sub_type = envelope.get("subType") or "unknown"
        sub_folder = envelope.get("subFolder") or "unknown"
        reg_id = envelope.get("deviceRegistryId")
        dev_id = envelope.get("deviceId")
        topic = self.get_topic(sub_type, sub_folder, registry_id=reg_id, device_id=dev_id)
        self._process_inbound_message(topic, envelope, inner_payload)

    def _handle_inbound_raw(self, topic: str, payload_bytes: bytes) -> None:
        """Processes raw MQTT topic and bytes, notifying pending waiters and buffering events."""
        try:
            raw_text = payload_bytes.decode("utf-8")
            data = json.loads(raw_text) if raw_text.strip() else {}
        except Exception:
            return

        envelope = data if isinstance(data, dict) else {}
        msg_principal = envelope.get("principal")
        if msg_principal and msg_principal != self.principal:
            return
        inner_payload = envelope["payload"] if "payload" in envelope else envelope

        sub_type = envelope.get("subType")
        sub_folder = envelope.get("subFolder")
        reg_id = envelope.get("deviceRegistryId")
        dev_id = envelope.get("deviceId")

        if not sub_type or not sub_folder:
            parts = [p for p in topic.strip("/").split("/") if p]
            for i, p in enumerate(parts):
                if p == "r" and i + 1 < len(parts):
                    reg_id = reg_id or parts[i + 1]
                elif p == "d" and i + 1 < len(parts):
                    dev_id = dev_id or parts[i + 1]
                elif p == "c" and i + 2 < len(parts):
                    sub_type = sub_type or parts[i + 1]
                    sub_folder = sub_folder or parts[i + 2]
            if sub_type and "subType" not in envelope:
                envelope["subType"] = sub_type
            if sub_folder and "subFolder" not in envelope:
                envelope["subFolder"] = sub_folder
            if reg_id and "deviceRegistryId" not in envelope:
                envelope["deviceRegistryId"] = reg_id
            if dev_id and "deviceId" not in envelope:
                envelope["deviceId"] = dev_id

        self._process_inbound_message(topic, envelope, inner_payload)

    def _process_inbound_message(
        self,
        topic: str,
        envelope: Dict[str, Any],
        inner_payload: Any,
    ) -> None:
        if inner_payload is None:
            return

        sub_type = envelope.get("subType")
        sub_folder = envelope.get("subFolder")
        reg_id = envelope.get("deviceRegistryId")
        dev_id = envelope.get("deviceId")
        tx_id = envelope.get("transactionId")
        source = envelope.get("source")

        # 1. Check pending response waiters (correlated via transactionId and channel)
        with self._lock:
            reply_tx = None
            if isinstance(inner_payload, dict):
                reply_block = inner_payload.get("reply", {})
                if isinstance(reply_block, dict):
                    reply_tx = reply_block.get("transaction_id")

            target_tx = tx_id or reply_tx
            if target_tx and target_tx in self._pending_responses:
                slot = self._pending_responses[target_tx]
                expected_type = slot.get("expected_sub_type")
                expected_folder = slot.get("expected_sub_folder")
                channel_matches = (
                    (expected_type is None or sub_type == expected_type)
                    and (expected_folder is None or sub_folder == expected_folder)
                )
                if channel_matches and (
                    not slot.get("require_system_source") or self._is_system_source(source)
                ):
                    if isinstance(inner_payload, dict) and "control" in inner_payload:
                        control_block = inner_payload.get("control") or {}
                        status = (
                            control_block.get("status")
                            if isinstance(control_block, dict)
                            else {}
                        ) or {}
                        raw_level = status.get("level") if isinstance(status, dict) else None
                        try:
                            level = int(raw_level) if raw_level is not None else 500
                        except (TypeError, ValueError):
                            level = 500
                        if level < 500:
                            slot.setdefault("progress", []).append(inner_payload)
                        else:
                            slot["error"] = status
                            slot["response"] = {
                                "envelope": envelope,
                                "payload": inner_payload,
                                "topic": topic,
                            }
                            event_obj = slot.get("event")
                            if event_obj:
                                event_obj.set()
                    else:
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
                "source": source,
                "principal": envelope.get("principal"),
                "envelope": envelope,
                "payload": inner_payload,
            }
            self._event_buffer.append(event_record)
            self._event_condition.notify_all()

    def inject_inbound_message(self, topic: str, data: Dict[str, Any]) -> None:
        """In-process MQTT injection for testing without live MQTT broker."""
        raw_bytes = json.dumps(data).encode("utf-8")
        self._handle_inbound_raw(topic, raw_bytes)

    def inject_inbound_pubsub_message(self, attributes: Dict[str, str], payload: Any) -> None:
        """In-process PubSub injection for testing without live GCP PubSub."""
        raw_bytes = json.dumps(payload).encode("utf-8")
        self._handle_inbound_pubsub(attributes, raw_bytes)

    @staticmethod
    def _raise_control_error(operation: str, status: Dict[str, Any]) -> None:
        raw_level = status.get("level")
        level = 500 if raw_level is None else raw_level
        category = status.get("category", "unknown")
        message = status.get("message", "UUFI control error")
        detail = status.get("detail")
        suffix = f" ({detail})" if detail else ""
        raise RuntimeError(f"UUFI {operation} failed [{category}, level={level}]: {message}{suffix}")

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
                "require_system_source": True,
                "expected_sub_type": "config",
                "expected_sub_folder": "udmi",
                "progress": [],
            }

        start_time = time.time()
        last_publish = 0.0

        try:
            while time.time() - start_time < timeout_sec:
                if time.time() - last_publish >= 5.0:
                    self._publish_raw(req_topic, envelope)
                    last_publish = time.time()

                if response_event.wait(timeout=0.5):
                    break

            with self._lock:
                slot = self._pending_responses.pop(tx_id, None)
                if slot and slot.get("error"):
                    self._raise_control_error("handshake", slot["error"])
                if slot and slot.get("response"):
                    resp_data = slot["response"]
                    payload = resp_data.get("payload", {})
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

        raise TimeoutError(
            f"UUFI Handshake timed out after {timeout_sec}s awaiting reply on {resp_topic} for {tx_id}"
        )

    # --------------------------------------------------------------------------
    # Device Configuration Mutation (uufi.md §2.2, §4, §6, §8)
    # --------------------------------------------------------------------------

    def publish_config(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str,
        payload: Union[Dict[str, Any], str],
        wait_for_ack: bool = False,
        timeout_sec: float = 15.0,
    ) -> Dict[str, Any]:
        """Publishes a configuration update (dict or string sentinel) and optionally waits for Ack."""
        topic = self.get_topic("config", sub_folder, registry_id=registry_id, device_id=device_id)
        envelope = self.create_envelope(
            sub_type="config",
            sub_folder=sub_folder,
            registry_id=registry_id,
            device_id=device_id,
            inner_payload=payload,
        )
        tx_id = envelope["transactionId"]

        if not wait_for_ack:
            self._publish_raw(topic, envelope)
            return {
                "status": "DISPATCHED",
                "topic": topic,
                "transactionId": tx_id,
                "publishTime": envelope["publishTime"],
                "registry_id": registry_id,
                "device_id": device_id,
                "sub_folder": sub_folder,
            }

        response_event = threading.Event()
        with self._lock:
            self._pending_responses[tx_id] = {
                "event": response_event,
                "response": None,
                "require_system_source": True,
                "expected_sub_type": "config",
                "expected_sub_folder": sub_folder,
                "progress": [],
            }

        try:
            self._publish_raw(topic, envelope)
            if response_event.wait(timeout=timeout_sec):
                with self._lock:
                    slot = self._pending_responses.pop(tx_id, None)
                    if slot and slot.get("error"):
                        self._raise_control_error(f"config/{sub_folder}", slot["error"])
                    if slot and slot.get("response"):
                        return {
                            "status": "ACKNOWLEDGED",
                            "topic": topic,
                            "transactionId": tx_id,
                            "publishTime": envelope["publishTime"],
                            "registry_id": registry_id,
                            "device_id": device_id,
                            "sub_folder": sub_folder,
                            "ack": slot["response"]["payload"],
                            "progress": list(slot.get("progress", [])),
                        }
        finally:
            with self._lock:
                self._pending_responses.pop(tx_id, None)

        raise TimeoutError(
            f"Config ack timed out after {timeout_sec}s for {registry_id}/{device_id} "
            f"config/{sub_folder} ({tx_id})"
        )

    # --------------------------------------------------------------------------
    # Single-Channel Queries & System Model Operations (uufi.md §1, §4, §5)
    # --------------------------------------------------------------------------

    def _execute_query(
        self,
        sub_type: str,
        sub_folder: str,
        registry_id: str,
        device_id: str,
        tx_prefix: str,
        timeout_sec: float,
    ) -> Any:
        tx_id = f"UUFI:{tx_prefix}:{uuid.uuid4().hex[:8]}"
        req_topic = self.get_topic(
            sub_type, sub_folder, registry_id=registry_id, device_id=device_id
        )
        envelope = self.create_envelope(
            sub_type=sub_type,
            sub_folder=sub_folder,
            registry_id=registry_id,
            device_id=device_id,
            transaction_id=tx_id,
            inner_payload=None,
        )

        response_event = threading.Event()
        with self._lock:
            self._pending_responses[tx_id] = {
                "event": response_event,
                "response": None,
                "require_system_source": True,
                "expected_sub_type": sub_type,
                "expected_sub_folder": sub_folder,
                "progress": [],
            }

        try:
            self._publish_raw(req_topic, envelope)
            if response_event.wait(timeout=timeout_sec):
                with self._lock:
                    slot = self._pending_responses.pop(tx_id, None)
                    if slot and slot.get("error"):
                        self._raise_control_error(f"{sub_type}/{sub_folder} query", slot["error"])
                    if slot and slot.get("response"):
                        return slot["response"]["payload"]
        finally:
            with self._lock:
                self._pending_responses.pop(tx_id, None)

        raise TimeoutError(
            f"{sub_type}/{sub_folder} query timed out after {timeout_sec}s "
            f"for device {device_id}"
        )

    def query_system_model(
        self,
        registry_id: str,
        device_id: str,
        timeout_sec: float = 15.0,
    ) -> Any:
        """Queries device system model via single-channel null-payload request on model/system."""
        return self._execute_query(
            sub_type="model",
            sub_folder="system",
            registry_id=registry_id,
            device_id=device_id,
            tx_prefix="model",
            timeout_sec=timeout_sec,
        )

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

    def query_state(
        self,
        registry_id: str,
        device_id: str,
        sub_folder: str = "blobset",
        timeout_sec: float = 15.0,
    ) -> Any:
        """Queries cached device state via single-channel null-payload request on state/<subFolder>."""
        return self._execute_query(
            sub_type="state",
            sub_folder=sub_folder,
            registry_id=registry_id,
            device_id=device_id,
            tx_prefix="state",
            timeout_sec=timeout_sec,
        )

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
        """Publishes envelope via MQTT (JSON wrapper) or PubSub (attributes + unwrapped data)."""
        record: Dict[str, Any] = {
            "topic": topic,
            "envelope": envelope,
            "transport": self.transport,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        if self.transport == "pubsub":
            attributes = {
                k: str(v) for k, v in envelope.items() if k != "payload" and v is not None
            }
            data_bytes = json.dumps(envelope.get("payload")).encode("utf-8")
            record["attributes"] = attributes
            record["data"] = data_bytes

        with self._lock:
            self._published_messages.append(record)

        if self.transport == "pubsub":
            if self._pubsub_publisher and self.is_connected and self._pubsub_topic_path:
                future = self._pubsub_publisher.publish(
                    self._pubsub_topic_path,
                    record["data"],
                    **record["attributes"],
                )
                future.result(timeout=5.0)
        elif self._mqtt_client and self.is_connected:
            raw_data = json.dumps(envelope)
            inf = self._mqtt_client.publish(topic, raw_data, qos=1)
            inf.wait_for_publish(timeout=5.0)

    def get_published_messages(self) -> List[Dict[str, Any]]:
        """Returns log of published messages for inspection or testing."""
        with self._lock:
            return list(self._published_messages)
