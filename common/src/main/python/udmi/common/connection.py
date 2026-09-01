"""Connection and transport abstraction for MQTT and Cloud Pub/Sub."""

import hashlib
import json
import os
import ssl
import sys
import uuid
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

import paho.mqtt.client as mqtt
from udmi.common.project_spec import parse_project_spec
from udmi.common.site_model import (
    DEFAULT_REGION,
    create_jwt,
    find_ca_file,
    find_cert_file,
    find_key_file,
    load_private_key,
    load_site_config,
)

DEFAULT_GBOS_HOSTNAME = "mqtt.bos.goog"
DEFAULT_CLEARBLADE_HOSTNAME_FORMAT = "{}-mqtt.clearblade.com"
DEFAULT_BRIDGE_PORT = 8883
HASH_PASSWORD_LENGTH = 8


class MessageConnection:
    """Manages connection to MQTT broker or Cloud Pub/Sub for publishing and subscribing."""

    def __init__(
        self,
        conn_spec: Optional[str],
        registry_id: Optional[str] = None,
        site_model: Optional[str] = None,
        device_id: Optional[str] = None,
    ):
        self.conn_spec = conn_spec
        self.site_model = site_model
        self.device_id = device_id
        self.spec_info = parse_project_spec(conn_spec)
        self.provider = (self.spec_info["provider"] or "mqtt").lower()
        self.project = self.spec_info["project"]
        self.namespace = self.spec_info["namespace"]
        self.prefix = self.spec_info["prefix"]
        self.port_override = self.spec_info["port"]

        self.site_config = load_site_config(site_model)
        self.cloud_region = self.site_config.get("cloud_region", DEFAULT_REGION)
        base_registry = registry_id or self.site_config.get("registry_id", "default")
        self.actual_registry = (
            f"{self.namespace}~{base_registry}" if self.namespace else base_registry
        )

        self.key_file = find_key_file(site_model, device_id)
        self.key_obj, self.key_bytes = load_private_key(self.key_file)

        self._mqtt_client: Optional[mqtt.Client] = None
        self._resolve_endpoint()

    def _resolve_endpoint(self) -> None:
        client_uuid = uuid.uuid4().hex[:8]
        self.is_tls = False
        self.username: Optional[str] = None
        self.password: Optional[str] = None

        if self.provider == "gbos":
            self.host = DEFAULT_GBOS_HOSTNAME
            self.port = self.port_override or DEFAULT_BRIDGE_PORT
            self.is_tls = True
            self.username = "unused"
            if self.key_obj:
                self.password = create_jwt(self.project, self.key_obj)
            target_device = self.device_id or "UDMI-REFLECT"
            self.client_id = (
                f"projects/{self.project}/locations/{self.cloud_region}/"
                f"registries/{self.actual_registry}/devices/{target_device}"
            )

        elif self.provider == "clearblade":
            self.host = DEFAULT_CLEARBLADE_HOSTNAME_FORMAT.format(self.cloud_region)
            self.port = self.port_override or DEFAULT_BRIDGE_PORT
            self.is_tls = True
            self.username = "unused"
            if self.key_obj:
                self.password = create_jwt(self.project, self.key_obj)
            target_device = self.device_id or "UDMI-REFLECT"
            self.client_id = (
                f"projects/{self.project}/locations/{self.cloud_region}/"
                f"registries/{self.actual_registry}/devices/{target_device}"
            )

        elif self.provider == "jwt":
            self.host = self.project
            self.port = self.port_override or DEFAULT_BRIDGE_PORT
            self.is_tls = True
            self.username = "unused"
            if self.key_obj:
                self.password = create_jwt(self.project, self.key_obj)
            self.client_id = f"udmi_{client_uuid}"

        elif self.provider == "pubsub":
            self.host = self.project
            self.port = None
            self.client_id = f"udmi_{client_uuid}"

        else:  # 'mqtt', 'ssl', 'local', etc.
            self.host = self.project or "localhost"
            env_port = os.environ.get("MQTT_PORT")
            self.port = self.port_override or (
                int(env_port) if env_port else DEFAULT_BRIDGE_PORT
            )
            self.client_id = (
                f"/{self.prefix}/{self.actual_registry}/udmi_{client_uuid}"
                if self.prefix
                else f"/{self.actual_registry}/udmi_{client_uuid}"
            )

            ca_cert = find_ca_file(self.site_model)
            if ca_cert and os.path.exists(ca_cert):
                self.is_tls = True

            env_user = os.environ.get("MQTT_USER")
            env_pass = os.environ.get("MQTT_PASS")
            if env_user:
                self.username = env_user
                self.password = env_pass or "monkey"
            elif self.key_bytes:
                derived_pass = hashlib.sha256(self.key_bytes).hexdigest()[:HASH_PASSWORD_LENGTH]
                self.username = f"/r/UDMI-REFLECT/d/{self.actual_registry}"
                self.password = derived_pass

    def create_mqtt_client(self) -> mqtt.Client:
        """Creates and configures a paho MQTT client instance."""
        client = mqtt.Client(client_id=self.client_id)
        if self.username:
            client.username_pw_set(self.username, self.password)

        if self.is_tls:
            ca_cert = find_ca_file(self.site_model)
            cert_pair = find_cert_file(self.site_model, self.device_id)

            if ca_cert and os.path.exists(ca_cert):
                if cert_pair:
                    client.tls_set(
                        ca_certs=ca_cert,
                        certfile=cert_pair[0],
                        keyfile=cert_pair[1],
                        cert_reqs=ssl.CERT_NONE,
                    )
                else:
                    client.tls_set(ca_certs=ca_cert, cert_reqs=ssl.CERT_NONE)
            else:
                client.tls_set(cert_reqs=ssl.CERT_NONE)
            client.tls_insecure_set(True)

        return client

    def get_default_subscribe_topics(self) -> List[str]:
        """Returns standard topic filters for consuming device events and state."""
        return [
            f"/r/{self.actual_registry}/d/+/#",
            "/r/+/d/+/#",
            "/uufi/#",
            "events/#",
            "state/#",
        ]

    def subscribe_mqtt(
        self,
        topics: List[str],
        on_message_callback: Callable[[str, bytes], None],
        on_connect_callback: Optional[Callable[[], None]] = None,
    ) -> mqtt.Client:
        """Connects and subscribes to given MQTT topics, calling on_message_callback(topic, payload)."""
        client = self.create_mqtt_client()

        def _on_connect(c, userdata, flags, rc):
            if rc == 0:
                for t in topics:
                    c.subscribe(t, qos=1)
                if on_connect_callback:
                    on_connect_callback()
            else:
                print(f"MQTT connection failed with code {rc}", file=sys.stderr)

        def _on_message(c, userdata, msg):
            on_message_callback(msg.topic, msg.payload)

        client.on_connect = _on_connect
        client.on_message = _on_message

        print(
            f"Connecting to MQTT provider '{self.provider}' at {self.host}:{self.port} with client_id '{self.client_id}'...",
            file=sys.stderr,
        )
        client.connect(self.host, self.port, keepalive=60)
        self._mqtt_client = client
        return client

    def publish_messages(self, topic_message_pairs: List[Tuple[str, Any]]) -> None:
        """Connects once and publishes multiple (topic, message_dict) pairs."""
        if not topic_message_pairs:
            return

        client = self.create_mqtt_client()
        client.connect(self.host, self.port, 60)
        client.loop_start()
        try:
            for topic, msg in topic_message_pairs:
                payload = json.dumps(msg) if not isinstance(msg, (str, bytes)) else msg
                inf = client.publish(topic, payload, qos=1)
                inf.wait_for_publish(timeout=10)
        finally:
            client.loop_stop()
            client.disconnect()
