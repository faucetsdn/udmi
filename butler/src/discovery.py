import json
import os
import ssl
import sys
import uuid
from datetime import datetime, timezone
from urllib.parse import urlparse
import paho.mqtt.client as mqtt

def parse_conn_spec(spec):
    prefix = ""
    is_ssl = False
    if not spec.startswith("//"):
        parsed = urlparse(spec)
        if parsed.scheme in ("mqtt", "mqtts", "ssl"):
            host_port = parsed.netloc.split("@")[-1]
            prefix = parsed.path.strip("/")
            if not prefix and parsed.username and not parsed.password:
                prefix = parsed.username
            spec = f"//mqtt/{host_port}/{prefix}" if prefix else f"//mqtt/{host_port}"

    if spec.startswith("//"):
        spec_body = spec[2:]
        if "/" in spec_body:
            provider, endpoint = spec_body.split("/", 1)
            is_ssl = provider in ("ssl", "mqtts", "tcps", "wss", "mqtt")
            spec = f"mqtt://{endpoint}"

    url = urlparse(spec)
    host = url.hostname or "localhost"
    is_ssl = is_ssl or url.scheme in ("ssl", "mqtts", "tcps", "wss") or str(url.port) in ("46432", "8883")
    port = url.port or (8883 if is_ssl else 1883)
    prefix = url.path.strip("/") if url.path and url.path != "/" else ""
    username = url.username or os.environ.get("MQTT_USER", "rocket")
    password = url.password or os.environ.get("MQTT_PASS", "monkey")
    return host, port, username, password, is_ssl, prefix

def run_discovery(conn_spec, registry_id, device_id, target_families, site_model=None):
    host, port, username, password, is_ssl, prefix = parse_conn_spec(conn_spec)
    client_id = f"discovery_{uuid.uuid4().hex[:8]}"
    client = mqtt.Client(client_id=client_id)
    if username:
        client.username_pw_set(username, password)
    if is_ssl:
        if site_model:
            ca_cert = os.path.join(site_model, "reflector", "ca.crt")
            cert_file = os.path.join(site_model, "reflector", "rsa_private.crt")
            key_file = os.path.join(site_model, "reflector", "rsa_private.pem")
            if os.path.exists(ca_cert):
                client.tls_set(ca_certs=ca_cert, certfile=cert_file, keyfile=key_file, cert_reqs=ssl.CERT_NONE)
            else:
                client.tls_set(cert_reqs=ssl.CERT_NONE)
        else:
            client.tls_set(cert_reqs=ssl.CERT_NONE)
        client.tls_insecure_set(True)

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    families_dict = {}
    if target_families:
        for f in target_families:
            families_dict[f] = {"generation": now}
    else:
        families_dict = {"vendor": {"generation": now}, "bacnet": {"generation": now}, "ipv4": {"generation": now}}

    topic = f"/uufi/r/{registry_id}/d/{device_id}/c/config/discovery"
    if prefix:
        topic = f"/{prefix}{topic}"

    tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
    source_id = f"{registry_id}/{client_id}" if registry_id else client_id
    payload = {
        "version": "1.5.2",
        "timestamp": now,
        "generation": now,
        "families": families_dict,
        "enumerations": {"families": "entries", "points": "entries", "features": "entries"}
    }
    msg = {
        "subType": "config",
        "subFolder": "discovery",
        "deviceRegistryId": registry_id,
        "deviceId": device_id,
        "projectId": "vibrant",
        "transactionId": tx_id,
        "publishTime": now,
        "source": source_id,
        "principal": source_id,
        "payload": payload
    }

    print(f"Publishing discovery config to {topic}: {msg}", file=sys.stderr)
    client.connect(host, port, 60)
    client.publish(topic, json.dumps(msg), qos=1)
    client.disconnect()
