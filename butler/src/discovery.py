"""Discovery module for Butler mapping service."""

import hashlib
import json
import os
import ssl
import sys
import time
import uuid
from datetime import datetime, timezone
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


def find_key_file(site_model, device_id):
    """Finds private key in site_model reflector or device directory."""
    if not site_model:
        return None
    candidates = [
        os.path.join(site_model, "reflector", "rsa_private.pkcs8"),
        os.path.join(site_model, "reflector", "rsa_private.pem"),
        os.path.join(site_model, "devices", device_id, "rsa_private.pkcs8"),
        os.path.join(site_model, "devices", device_id, "rsa_private.pem"),
        os.path.join(site_model, "devices", device_id, "rsa_private.key"),
    ]
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


def run_discovery(conn_spec, registry_id, device_id, target_families, site_model=None):
    """Dispatches discovery configuration message over the configured project spec / broker."""
    spec_info = parse_project_spec(conn_spec)
    provider = (spec_info["provider"] or "mqtt").lower()
    project = spec_info["project"]
    namespace = spec_info["namespace"]
    prefix = spec_info["prefix"]
    port_override = spec_info["port"]

    site_config = load_site_config(site_model)
    cloud_region = site_config.get("cloud_region", DEFAULT_REGION)
    base_registry = registry_id or site_config.get("registry_id", "default")
    actual_registry = f"{namespace}~{base_registry}" if namespace else base_registry

    key_file = find_key_file(site_model, device_id)
    key_obj, key_bytes = load_private_key(key_file)

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    families_dict = {}
    if target_families:
        for f in target_families:
            families_dict[f] = {"generation": now}
    else:
        families_dict = {
            "vendor": {"generation": now},
            "bacnet": {"generation": now},
            "ipv4": {"generation": now},
        }

    tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
    client_uuid = uuid.uuid4().hex[:8]

    # Resolve provider-specific endpoint, authentication, and topics
    is_tls = False
    username = None
    password = None

    if provider == "gbos":
        host = DEFAULT_GBOS_HOSTNAME
        port = port_override or DEFAULT_BRIDGE_PORT
        is_tls = True
        username = "unused"
        if key_obj:
            password = create_jwt(project, key_obj)
        else:
            print("Warning: No private key found for GBOS JWT signing", file=sys.stderr)
        client_id = f"projects/{project}/locations/{cloud_region}/registries/{actual_registry}/devices/{device_id}"
        topics = [
            f"/devices/{device_id}/commands/discovery",
            f"/uufi/r/{actual_registry}/d/{device_id}/c/config/discovery",
        ]

    elif provider == "clearblade":
        host = DEFAULT_CLEARBLADE_HOSTNAME_FORMAT.format(cloud_region)
        port = port_override or DEFAULT_BRIDGE_PORT
        is_tls = True
        username = "unused"
        if key_obj:
            password = create_jwt(project, key_obj)
        else:
            print("Warning: No private key found for ClearBlade JWT signing", file=sys.stderr)
        client_id = f"projects/{project}/locations/{cloud_region}/registries/{actual_registry}/devices/{device_id}"
        topics = [
            f"/devices/{device_id}/commands/discovery",
            f"/uufi/r/{actual_registry}/d/{device_id}/c/config/discovery",
        ]

    elif provider == "jwt":
        host = project
        port = port_override or DEFAULT_BRIDGE_PORT
        is_tls = True
        username = "unused"
        if key_obj:
            password = create_jwt(project, key_obj)
        client_id = f"discovery_{client_uuid}"
        topics = [
            f"/uufi/r/{actual_registry}/d/{device_id}/c/config/discovery",
            f"/devices/{device_id}/config",
        ]

    else:  # 'mqtt', 'ssl', 'local', or generic broker
        host = project or "localhost"
        is_isolated = str(port_override) in ("46432", "8883") or os.environ.get("MQTT_PORT") is not None
        port = port_override or (DEFAULT_BRIDGE_PORT if is_isolated else 1883)
        client_id = f"/{prefix}/{actual_registry}/discovery_{client_uuid}" if prefix else f"/{actual_registry}/discovery_{client_uuid}"

        # Handle local MQTT TLS certs if available
        if site_model:
            ca_cert = os.path.join(site_model, "reflector", "ca.crt")
            if os.path.exists(ca_cert):
                is_tls = True

        # Handle credentials
        env_user = os.environ.get("MQTT_USER")
        env_pass = os.environ.get("MQTT_PASS")
        if env_user:
            username = env_user
            password = env_pass or "monkey"
        elif key_bytes:
            derived_pass = hashlib.sha256(key_bytes).hexdigest()[:HASH_PASSWORD_LENGTH]
            username = f"/r/UDMI-REFLECT/d/{actual_registry}"
            password = derived_pass

        uufi_topic = f"/uufi/r/{actual_registry}/d/{device_id}/c/config/discovery"
        if prefix:
            uufi_topic = f"/{prefix}{uufi_topic}"
        topics = [uufi_topic]

    source_id = f"{actual_registry}/{client_id}" if actual_registry else client_id
    payload = {
        "version": "1.5.2",
        "timestamp": now,
        "generation": now,
        "families": families_dict,
        "enumerations": {"families": "entries", "points": "entries", "features": "entries"},
    }
    msg = {
        "subType": "config",
        "subFolder": "discovery",
        "deviceRegistryId": actual_registry,
        "deviceId": device_id,
        "projectId": project or "vibrant",
        "transactionId": tx_id,
        "publishTime": now,
        "source": source_id,
        "principal": source_id,
        "payload": payload,
    }

    client = mqtt.Client(client_id=client_id)
    if username:
        client.username_pw_set(username, password)

    if is_tls:
        if site_model:
            ca_cert = os.path.join(site_model, "reflector", "ca.crt")
            cert_file = os.path.join(site_model, "reflector", "rsa_private.crt")
            key_pem = os.path.join(site_model, "reflector", "rsa_private.pem")
            if os.path.exists(ca_cert) and os.path.exists(cert_file) and os.path.exists(key_pem):
                client.tls_set(
                    ca_certs=ca_cert,
                    certfile=cert_file,
                    keyfile=key_pem,
                    cert_reqs=ssl.CERT_NONE,
                )
            else:
                client.tls_set(cert_reqs=ssl.CERT_NONE)
        else:
            client.tls_set(cert_reqs=ssl.CERT_NONE)
        client.tls_insecure_set(True)

    print(
        f"Connecting to provider '{provider}' at {host}:{port} with client_id '{client_id}'...",
        file=sys.stderr,
    )
    client.connect(host, port, 60)
    for topic in topics:
        print(f"Publishing discovery config to {topic}: {msg}", file=sys.stderr)
        client.publish(topic, json.dumps(msg), qos=1)
    client.disconnect()
