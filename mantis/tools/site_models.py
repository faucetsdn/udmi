"""Site model configuration and device metadata inspection."""

import json
import os
import re
from typing import Any, Dict, List, Optional


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def sanitize_credentials(data: Any) -> Any:
    """Recursively sanitize passwords, private keys, and tokens from dictionaries/strings."""
    if isinstance(data, dict):
        sanitized = {}
        for k, v in data.items():
            key_lower = k.lower()
            if any(secret_word in key_lower for secret_word in ("password", "secret", "private_key", "token", "key_data")):
                sanitized[k] = "***REDACTED***"
            else:
                sanitized[k] = sanitize_credentials(v)
        return sanitized
    elif isinstance(data, list):
        return [sanitize_credentials(item) for item in data]
    elif isinstance(data, str):
        # Mask PEM private keys
        if "-----BEGIN RSA PRIVATE KEY-----" in data or "-----BEGIN PRIVATE KEY-----" in data:
            return "[REDACTED_RSA_KEY]"
        # Mask URL credentials
        data = re.sub(r"://([^:]+):([^@]+)@", r"://\1:***@", data)
        return data
    return data


def resolve_site_model_path(site_model: str, udmi_root: Optional[str] = None) -> str:
    """Resolve site model path relative to UDMI root or as absolute path."""
    root = _get_udmi_root(udmi_root)
    candidate = os.path.abspath(os.path.join(root, site_model))
    if os.path.isdir(candidate):
        return candidate
    candidate2 = os.path.abspath(site_model)
    if os.path.isdir(candidate2):
        return candidate2
    return candidate


def list_site_model_devices(site_model_path: str) -> List[str]:
    """List all device IDs defined in a site model directory."""
    devices_dir = os.path.join(site_model_path, "devices")
    if not os.path.isdir(devices_dir):
        return []
    devices = []
    for entry in sorted(os.listdir(devices_dir)):
        dev_path = os.path.join(devices_dir, entry)
        if os.path.isdir(dev_path):
            devices.append(entry)
    return devices


def inspect_site_model(
    site_model: str,
    device_id: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Inspect site model configuration files and device metadata with automatic credential redaction."""
    site_path = resolve_site_model_path(site_model, udmi_root)
    if not os.path.isdir(site_path):
        return {
            "status": "ERROR",
            "error": f"Site model directory not found: {site_model} (resolved to {site_path})",
        }

    # Load cloud_iot_config.json if present
    cloud_config = {}
    cloud_config_file = os.path.join(site_path, "cloud_iot_config.json")
    if os.path.isfile(cloud_config_file):
        try:
            with open(cloud_config_file, "r", encoding="utf-8") as f:
                cloud_config = sanitize_credentials(json.load(f))
        except Exception as e:
            cloud_config = {"error": f"Failed to parse cloud_iot_config.json: {e}"}

    all_devices = list_site_model_devices(site_path)

    if device_id:
        dev_clean = device_id.strip()
        dev_dir = os.path.join(site_path, "devices", dev_clean)
        metadata_file = os.path.join(dev_dir, "metadata.json")

        if not os.path.isdir(dev_dir) or not os.path.isfile(metadata_file):
            return {
                "status": "ERROR",
                "site_model": site_path,
                "device_id": dev_clean,
                "error": f"Device metadata not found for device '{dev_clean}' in {site_path}/devices",
                "available_devices": all_devices,
            }

        try:
            with open(metadata_file, "r", encoding="utf-8") as f:
                metadata = json.load(f)
        except Exception as e:
            return {
                "status": "ERROR",
                "site_model": site_path,
                "device_id": dev_clean,
                "error": f"Failed to parse metadata.json for '{dev_clean}': {e}",
            }

        sanitized_meta = sanitize_credentials(metadata)
        points = sanitized_meta.get("pointset", {}).get("points", {})
        system = sanitized_meta.get("system", {})
        gateway = sanitized_meta.get("gateway", {})

        return {
            "status": "SUCCESS",
            "site_model": site_path,
            "device_id": dev_clean,
            "metadata_file": metadata_file,
            "metadata": sanitized_meta,
            "system": system,
            "gateway": gateway,
            "point_count": len(points) if isinstance(points, dict) else 0,
            "points": list(points.keys()) if isinstance(points, dict) else [],
        }

    # Summary of site model
    return {
        "status": "SUCCESS",
        "site_model": site_path,
        "cloud_iot_config": cloud_config,
        "device_count": len(all_devices),
        "devices": all_devices,
    }
