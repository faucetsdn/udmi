"""Site model utilities for loading configuration, keys, and certificates."""

import json
import os
import sys
import time
from typing import Any, Dict, Optional, Tuple
from cryptography.hazmat.primitives import serialization
import jwt


DEFAULT_REGION = "us-central1"


def load_site_config(site_model: Optional[str]) -> Dict[str, Any]:
    """Loads cloud_iot_config.json from site_model directory if present."""
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


def find_key_file(site_model: Optional[str], device_id: Optional[str] = None) -> Optional[str]:
    """Finds private key in site_model reflector or device directory or env."""
    candidates = []
    if os.environ.get("SSL_SECRETS_DIR"):
        candidates.extend([
            os.path.join(os.environ["SSL_SECRETS_DIR"], "rsa_private.pkcs8"),
            os.path.join(os.environ["SSL_SECRETS_DIR"], "rsa_private.pem"),
            os.path.join(os.environ["SSL_SECRETS_DIR"], "rsa_private.key"),
        ])
    if site_model:
        candidates.extend([
            os.path.join(site_model, "reflector", "rsa_private.pkcs8"),
            os.path.join(site_model, "reflector", "rsa_private.pem"),
            os.path.join(site_model, "reflector", "rsa_private.key"),
        ])
        if device_id:
            candidates.extend([
                os.path.join(site_model, "devices", device_id, "rsa_private.pkcs8"),
                os.path.join(site_model, "devices", device_id, "rsa_private.pem"),
                os.path.join(site_model, "devices", device_id, "rsa_private.key"),
            ])
    for cand in candidates:
        if cand and os.path.exists(cand):
            return cand
    return None


def find_ca_file(site_model: Optional[str]) -> Optional[str]:
    """Finds CA certificate in site_model or standard environment locations."""
    candidates = []
    if os.environ.get("CA_CERT"):
        candidates.append(os.environ.get("CA_CERT"))
    if os.environ.get("SSL_SECRETS_DIR"):
        candidates.append(os.path.join(os.environ["SSL_SECRETS_DIR"], "ca.crt"))
    if site_model:
        candidates.extend([
            os.path.join(site_model, "reflector", "ca.crt"),
            os.path.join(site_model, "ca.crt"),
        ])
    candidates.extend([
        "/etc/mosquitto/certs/ca.crt",
        "/var/mosquitto/certs/ca.crt",
    ])
    for cand in candidates:
        if cand and os.path.exists(cand):
            return cand
    return None


def find_cert_file(site_model: Optional[str], device_id: Optional[str] = None) -> Optional[Tuple[str, str]]:
    """Finds client certificate and key in site_model or SSL_SECRETS_DIR."""
    candidates = []
    if site_model and device_id:
        candidates.append((
            os.path.join(site_model, "devices", device_id, "rsa_private.crt"),
            os.path.join(site_model, "devices", device_id, "rsa_private.pem"),
        ))
    if site_model:
        candidates.extend([
            (os.path.join(site_model, "reflector", "rsa_private.crt"), os.path.join(site_model, "reflector", "rsa_private.pem")),
            (os.path.join(site_model, "reflector", "server.crt"), os.path.join(site_model, "reflector", "server.key")),
        ])
    if os.environ.get("SSL_SECRETS_DIR"):
        candidates.append((
            os.path.join(os.environ["SSL_SECRETS_DIR"], "rsa_private.crt"),
            os.path.join(os.environ["SSL_SECRETS_DIR"], "rsa_private.pem"),
        ))
    for cert, key in candidates:
        if cert and key and os.path.exists(cert) and os.path.exists(key):
            return cert, key
    return None


def load_private_key(key_path: Optional[str]) -> Tuple[Optional[Any], Optional[bytes]]:
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


def create_jwt(project_id: str, key_obj: Any, lifetime_seconds: int = 3600) -> str:
    """Generates RS256 JWT for Cloud IoT / GBOS authentication."""
    now = int(time.time())
    payload = {
        "iat": now,
        "exp": now + lifetime_seconds,
        "aud": project_id,
    }
    return jwt.encode(payload, key_obj, algorithm="RS256")
