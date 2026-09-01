"""Connection and messaging module for Butler services."""

import hashlib
import json
import os
import ssl
import sys
import time
import uuid
from cryptography.hazmat.primitives import serialization
import jwt
import paho.mqtt.client as mqtt

DEFAULT_GBOS_HOSTNAME = "mqtt.bos.goog"
DEFAULT_CLEARBLADE_HOSTNAME_FORMAT = "{}-mqtt.clearblade.com"
DEFAULT_REGION = "us-central1"
DEFAULT_BRIDGE_PORT = 8883
HASH_PASSWORD_LENGTH = 8


def parse_project_spec(spec):
    """Parses project spec conforming to: [//provider/]project[/namespace][+user]."""
    if not spec:
        return {
            "provider": "mqtt",
            "project": "localhost",
            "namespace": None,
            "port": None,
            "prefix": "",
            "user": None,
        }
    s = spec.strip()
    provider = None
    if s.startswith("//"):
        s = s[2:]
        if "/" in s:
            provider, s = s.split("/", 1)
        else:
            provider = s
            s = ""
    elif "://" in s:
        proto, s = s.split("://", 1)
        provider = "mqtt" if proto in ("mqtt", "mqtts", "ssl") else proto

    user = None
    if "+" in s:
        s, user = s.split("+", 1)
    elif " " in s:
        s, user = s.split(" ", 1)

    namespace = None
    prefix = ""
    port = None

    if "/" in s:
        project, rest = s.split("/", 1)
        namespace = rest
        prefix = rest
    else:
        project = s

    if ":" in project:
        project, port_str = project.split(":", 1)
        try:
            port = int(port_str)
        except ValueError:
            pass

    if provider in ("ssl", "mqtts"):
        provider = "mqtt"

    return {
        "provider": provider or "mqtt",
        "project": project or "localhost",
        "port": port,
        "namespace": namespace,
        "prefix": prefix,
        "user": user,
    }


def load_site_config(site_model):
    """Loads cloud_iot_config.json from site_model if present."""
    if not site_model:
        return {}
    config_path = os.path.join(site_model, "cloud_iot_config.json")
    if os.path.exists(config_path):
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"Warning: Failed to load {config_path}: {e}", file=sys.stderr)
    return {}


def find_key_file(site_model, device_id=None):
    """Finds private key in site_model reflector or device directory."""
    if not site_model:
        return None
    candidates = [
        os.path.join(site_model, "reflector", "rsa_private.pkcs8"),
        os.path.join(site_model, "reflector", "rsa_private.pem"),
    ]
    if device_id:
        candidates.extend([
            os.path.join(site_model, "devices", device_id, "rsa_private.pkcs8"),
            os.path.join(site_model, "devices", device_id, "rsa_private.pem"),
            os.path.join(site_model, "devices", device_id, "rsa_private.key"),
        ])
    for cand in candidates:
        if os.path.exists(cand):
            return cand
    return None


def load_private_key(key_path):
    """Loads a private key from file and returns (key_object, key_bytes)."""
    if not key_path or not os.path.exists(key_path):
        return None, None
    try:
        with open(key_path, "rb") as f:
            data = f.read()
    except Exception as e:
        print(f"Warning: Could not read key file {key_path}: {e}", file=sys.stderr)
        return None, None

    try:
        return serialization.load_pem_private_key(data, password=None), data
    except Exception:
        pass
    try:
        return serialization.load_der_private_key(data, password=None), data
    except Exception:
        pass
    return None, data


def create_jwt(project_id, key_obj, lifetime_seconds=3600):
    """Generates RS256 JWT for Cloud IoT / GBOS authentication."""
    now = int(time.time())
    payload = {
        "iat": now,
        "exp": now + lifetime_seconds,
        "aud": project_id,
    }
    return jwt.encode(payload, key_obj, algorithm="RS256")


class ButlerConnection:
    """Manages MQTT/UUFI connections for publishing discovery and model events."""

    def __init__(self, conn_spec, registry_id=None, site_model=None, device_id=None):
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
        self.actual_registry = f"{self.namespace}~{base_registry}" if self.namespace else base_registry

        self.key_file = find_key_file(site_model, device_id)
        self.key_obj, self.key_bytes = load_private_key(self.key_file)

        self._resolve_endpoint()

    def _resolve_endpoint(self):
        client_uuid = uuid.uuid4().hex[:8]
        self.is_tls = False
        self.username = None
        self.password = None

        if self.provider == "gbos":
            self.host = DEFAULT_GBOS_HOSTNAME
            self.port = self.port_override or DEFAULT_BRIDGE_PORT
            self.is_tls = True
            self.username = "unused"
            if self.key_obj:
                self.password = create_jwt(self.project, self.key_obj)
            else:
                print("Warning: No private key found for GBOS JWT signing", file=sys.stderr)
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
            else:
                print("Warning: No private key found for ClearBlade JWT signing", file=sys.stderr)
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
            self.client_id = f"butler_{client_uuid}"

        else:  # 'mqtt', 'ssl', 'local', etc.
            self.host = self.project or "localhost"
            env_port = os.environ.get("MQTT_PORT")
            self.port = self.port_override or (int(env_port) if env_port else DEFAULT_BRIDGE_PORT)
            self.client_id = (
                f"/{self.prefix}/{self.actual_registry}/butler_{client_uuid}"
                if self.prefix
                else f"/{self.actual_registry}/butler_{client_uuid}"
            )

            if self.site_model:
                ca_cert = os.path.join(self.site_model, "reflector", "ca.crt")
                if os.path.exists(ca_cert):
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

    def get_discovery_topics(self, device_id):
        """Returns list of topics to publish discovery config for target device."""
        if self.provider in ("gbos", "clearblade"):
            return [
                f"/devices/{device_id}/commands/discovery",
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/config/discovery",
            ]
        if self.provider == "jwt":
            return [
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/config/discovery",
                f"/devices/{device_id}/config",
            ]
        uufi_topic = f"/uufi/r/{self.actual_registry}/d/{device_id}/c/config/discovery"
        if self.prefix:
            uufi_topic = f"/{self.prefix}{uufi_topic}"
        return [uufi_topic]

    def get_model_topics(self, device_id):
        """Returns list of topics to publish model update for target device."""
        if self.provider in ("gbos", "clearblade"):
            return [
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/model/system",
                f"/devices/{device_id}/model",
            ]
        if self.provider == "jwt":
            return [
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/model/system",
                f"/devices/{device_id}/model",
            ]
        uufi_topic = f"/uufi/r/{self.actual_registry}/d/{device_id}/c/model/system"
        if self.prefix:
            uufi_topic = f"/{self.prefix}{uufi_topic}"
        return [uufi_topic]

    def get_propose_topics(self, device_id, sub_folder="system"):
        """Returns list of topics to publish propose update for target device."""
        if self.provider in ("gbos", "clearblade"):
            return [
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/propose/{sub_folder}",
                f"/devices/{device_id}/propose",
            ]
        if self.provider == "jwt":
            return [
                f"/uufi/r/{self.actual_registry}/d/{device_id}/c/propose/{sub_folder}",
                f"/devices/{device_id}/propose",
            ]
        uufi_topic = f"/uufi/r/{self.actual_registry}/d/{device_id}/c/propose/{sub_folder}"
        if self.prefix:
            uufi_topic = f"/{self.prefix}{uufi_topic}"
        return [uufi_topic]

    def publish_messages(self, topic_message_pairs):
        """Connects once and publishes multiple (topic, message_dict) pairs."""
        if not topic_message_pairs:
            return

        client = mqtt.Client(client_id=self.client_id)
        if self.username:
            client.username_pw_set(self.username, self.password)

        if self.is_tls:
            ca_cert = os.path.join(self.site_model, "reflector", "ca.crt") if self.site_model else None
            cert_candidates = []
            if self.site_model:
                if self.device_id:
                    cert_candidates.append((
                        os.path.join(self.site_model, "devices", self.device_id, "rsa_private.crt"),
                        os.path.join(self.site_model, "devices", self.device_id, "rsa_private.pem"),
                    ))
                cert_candidates.extend([
                    (os.path.join(self.site_model, "reflector", "rsa_private.crt"), os.path.join(self.site_model, "reflector", "rsa_private.pem")),
                    (os.path.join(self.site_model, "reflector", "server.crt"), os.path.join(self.site_model, "reflector", "server.key")),
                ])

            client_cert = None
            client_key = None
            for c_cert, c_key in cert_candidates:
                if os.path.exists(c_cert) and os.path.exists(c_key):
                    client_cert = c_cert
                    client_key = c_key
                    break

            if ca_cert and os.path.exists(ca_cert):
                if client_cert and client_key:
                    client.tls_set(
                        ca_certs=ca_cert,
                        certfile=client_cert,
                        keyfile=client_key,
                        cert_reqs=ssl.CERT_NONE,
                    )
                else:
                    client.tls_set(ca_certs=ca_cert, cert_reqs=ssl.CERT_NONE)
            else:
                client.tls_set(cert_reqs=ssl.CERT_NONE)
            client.tls_insecure_set(True)

        print(
            f"Connecting to provider '{self.provider}' at {self.host}:{self.port} with client_id '{self.client_id}'...",
            file=sys.stderr,
        )
        client.connect(self.host, self.port, 60)
        client.loop_start()
        try:
            for topic, msg in topic_message_pairs:
                print(f"Publishing message to {topic}: {msg}", file=sys.stderr)
                inf = client.publish(topic, json.dumps(msg), qos=1)
                inf.wait_for_publish(timeout=10)
        finally:
            client.loop_stop()
            client.disconnect()
