"""What an external (physical) device needs to reach the local isolated broker.

Every value is derived from the files `bin/udmi start` actually uses, never
assumed, and each carries the source it was read from. Anything that cannot be
determined is reported as ``"unknown"`` with the reason, rather than guessed.

Sources (paths relative to the UDMI root):
  * `etc/mosquitto_udmi.conf` -- listener, TLS and client-certificate settings.
    `bin/start_mosquitto` rewrites only the port (to MQTT_PORT) and the cert
    directory; it adds no bind address.
  * `bin/setup_ca` / `bin/keygen` -- the CA and server certificate are issued
    into `<site_model>/reflector/`.
  * `bin/mosquctl_site` / `bin/mosquctl_device` -- which devices get broker
    credentials, and the username/password derivation.
  * `bin/pubber` (mqtt provider block) -- client ID, gateway handling, topic
    prefix and transport.
  * `docs/specs/mqtt_client.md`, `docs/specs/tech_stack.md`, `docs/specs/uufi.md`.
"""

import json
import os
import re
import subprocess
from typing import Any, Dict, List, Optional

from workbench.server import discovery
from workbench.server.testbed import TestbedError, validate_local_spec

BROKER_CONF = "etc/mosquitto_udmi.conf"
UNKNOWN = "unknown"

DOCS = [
    {"path": "docs/specs/mqtt_client.md", "title": "MQTT client credentials (username/password derivation)"},
    {"path": "docs/specs/tech_stack.md", "title": "MQTT topic suffixes (state, events, config)"},
    {"path": "docs/specs/uufi.md", "title": "Local `bin/udmi start` endpoint, certs and credentials (sec. 9.1)"},
    {"path": "docs/specs/connecting.md", "title": "Device connection models (direct / adapter / gateway)"},
]


def _read_broker_conf(udmi_root: str) -> Dict[str, List[str]]:
    """Directive -> values from the broker template, ignoring comments."""
    directives: Dict[str, List[str]] = {}
    with open(os.path.join(udmi_root, BROKER_CONF), "r") as fh:
        for raw in fh:
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            key, _, value = line.partition(" ")
            directives.setdefault(key, []).append(value.strip())
    return directives


def non_loopback_ipv4() -> Dict[str, Any]:
    """This machine's non-loopback IPv4 addresses, from `ip -4 -o addr show`."""
    try:
        res = subprocess.run(
            ["ip", "-4", "-o", "addr", "show"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3.0,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return {"addresses": [], "error": f"`ip -4 -o addr show` failed: {exc}"}
    if res.returncode != 0:
        return {"addresses": [], "error": f"`ip -4 -o addr show` exited {res.returncode}: {res.stderr.strip()}"}
    found = []
    for line in res.stdout.splitlines():
        match = re.search(r"^\d+:\s+(\S+)\s+inet\s+(\d+\.\d+\.\d+\.\d+)/", line)
        if match and not match.group(2).startswith("127."):
            found.append({"interface": match.group(1), "address": match.group(2)})
    return {"addresses": found, "error": None}


def _server_cert_sans(cert_path: str) -> Dict[str, Any]:
    """Subject alternative names of the broker's server certificate."""
    if not os.path.isfile(cert_path):
        return {"names": None, "error": f"{cert_path} does not exist yet (created by bin/setup_ca on start)"}
    try:
        res = subprocess.run(
            ["openssl", "x509", "-noout", "-ext", "subjectAltName", "-in", cert_path],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3.0,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return {"names": None, "error": f"openssl failed: {exc}"}
    if res.returncode != 0:
        return {"names": None, "error": f"openssl exited {res.returncode}: {res.stderr.strip()}"}
    names = re.findall(r"(?:DNS|IP Address):([^,\s]+)", res.stdout)
    return {"names": names, "error": None}


def _first_existing(directory: str, names: List[str]) -> Optional[str]:
    for name in names:
        if os.path.isfile(os.path.join(directory, name)):
            return name
    return None


def describe_connection(
    udmi_root: str, project_spec: Optional[str], site_model: Optional[str], device_id: Optional[str]
) -> Dict[str, Any]:
    """Connection facts for an external device targeting the spec's local broker."""
    udmi_root = os.path.abspath(udmi_root)
    port = validate_local_spec(project_spec)
    if not site_model:
        raise TestbedError("Missing required field: 'site_model'")
    if not device_id:
        raise TestbedError("Missing required field: 'device_id'")
    site_dir = discovery.resolve_site_model(udmi_root, site_model)
    device_dir = os.path.join(site_dir, "devices", device_id)
    if not os.path.isdir(device_dir):
        raise TestbedError(f"Device directory not found: {device_dir}")

    unknowns: List[str] = []
    rel = lambda path: os.path.relpath(path, udmi_root) if path.startswith(udmi_root) else path

    # --- Broker listener / TLS, from the template bin/start_mosquitto uses.
    conf = _read_broker_conf(udmi_root)
    listeners = conf.get("listener", [])
    bind_host = listeners[0].split()[1] if listeners and len(listeners[0].split()) > 1 else None
    tls_used = "cafile" in conf and "certfile" in conf and "keyfile" in conf
    require_cert = conf.get("require_certificate", ["false"])[-1] == "true"
    anonymous = conf.get("allow_anonymous", ["true"])[-1] == "true"

    # --- Hosts a device can dial.
    ips = non_loopback_ipv4()
    if ips["error"]:
        unknowns.append(f"Non-loopback address: {ips['error']}")
    reflector = os.path.join(site_dir, "reflector")
    ca_path = os.path.join(reflector, "ca.crt")
    server_cert = os.path.join(reflector, "rsa_private.crt")
    sans = _server_cert_sans(server_cert)
    if sans["error"]:
        unknowns.append(f"Server certificate names: {sans['error']}")

    def _in_cert(host: str) -> Any:
        return UNKNOWN if sans["names"] is None else host in sans["names"]

    hosts = [{"host": a["address"], "interface": a["interface"], "in_server_cert": _in_cert(a["address"])}
             for a in ips["addresses"]]
    hosts.append({"host": "localhost", "interface": "loopback (this machine only)",
                  "in_server_cert": _in_cert("localhost")})
    unknowns.append(
        "Whether mosquitto binds all interfaces: the listener sets no address "
        f"({BROKER_CONF}), so it follows mosquitto's own default; not probed from here."
    )

    # --- Identity, from cloud_iot_config.json and the device metadata.
    with open(os.path.join(site_dir, "cloud_iot_config.json"), "r") as fh:
        registry_id = json.load(fh).get("registry_id") or UNKNOWN
    if registry_id == UNKNOWN:
        unknowns.append("registry_id is missing from cloud_iot_config.json")
    metadata: Dict[str, Any] = {}
    metadata_path = os.path.join(device_dir, "metadata.json")
    if os.path.isfile(metadata_path):
        with open(metadata_path, "r") as fh:
            metadata = json.load(fh)
    gateway_id = (metadata.get("gateway") or {}).get("gateway_id")
    auth_type = (metadata.get("cloud") or {}).get("auth_type")
    target_id = gateway_id or device_id
    client_id = f"/r/{registry_id}/d/{target_id}"
    topic_prefix = f"/r/{registry_id}/d/{device_id}"

    key_dir = os.path.join(site_dir, "devices", target_id)
    key_file = _first_existing(key_dir, ["rsa_private.pkcs8", "ec_private.pkcs8"])
    cert_file = _first_existing(key_dir, ["rsa_private.crt", "ec_private.crt"])
    pem_file = _first_existing(key_dir, ["rsa_private.pem", "ec_private.pem"])
    if not key_file:
        unknowns.append(f"No private key (rsa|ec)_private.pkcs8 in {rel(key_dir)}")

    unknowns.append("A device-side commands topic: pubber subscribes only to config and errors.")

    return {
        "project_spec": project_spec.strip(),
        "broker": {
            "hosts": hosts,
            "port": port,
            "bind_address": bind_host or "none set (mosquitto default)",
            "anonymous_allowed": anonymous,
            "source": f"{BROKER_CONF} (port rewritten to MQTT_PORT by bin/start_mosquitto)",
        },
        "tls": {
            "used": tls_used,
            "transport": "ssl" if tls_used else "tcp",
            "client_certificate_required": require_cert,
            "ca_certificate": rel(ca_path),
            "ca_exists": os.path.isfile(ca_path),
            "server_certificate": rel(server_cert),
            "server_certificate_names": sans["names"] if sans["names"] is not None else UNKNOWN,
            "device_certificate": rel(os.path.join(key_dir, cert_file)) if cert_file else UNKNOWN,
            "device_private_key_pem": rel(os.path.join(key_dir, pem_file)) if pem_file else UNKNOWN,
            "source": f"{BROKER_CONF}, bin/setup_ca, bin/keygen",
        },
        "identity": {
            "client_id": client_id,
            "username": client_id,
            "password_rule": (
                f"first 8 hex chars of sha256 of {rel(os.path.join(key_dir, key_file))}"
                if key_file else UNKNOWN
            ),
            "password_command": (
                f"sha256sum < {rel(os.path.join(key_dir, key_file))} | head -c 8" if key_file else UNKNOWN
            ),
            "via_gateway": gateway_id,
            "broker_account_provisioned_on_start": bool(auth_type),
            "provisioning_note": (
                f"metadata.json cloud.auth_type = {auth_type!r}: bin/mosquctl_site creates the broker account on start"
                if auth_type else
                "metadata.json has no cloud.auth_type, so bin/mosquctl_site creates no broker account for it"
            ),
            "source": "bin/pubber (mqtt provider), bin/mosquctl_device, bin/mosquctl_site",
        },
        "topics": {
            "prefix": topic_prefix,
            "publish": [f"{topic_prefix}/state", f"{topic_prefix}/events/<subfolder>"],
            "subscribe": [f"{topic_prefix}/config", f"{topic_prefix}/errors"],
            "source": "bin/pubber topic_prefix; pubber MqttDevice/MqttPublisher; docs/specs/tech_stack.md",
        },
        "device_key": {
            "directory": rel(key_dir),
            "private_key": rel(os.path.join(key_dir, key_file)) if key_file else UNKNOWN,
        },
        "docs": DOCS,
        "unknowns": unknowns,
    }
