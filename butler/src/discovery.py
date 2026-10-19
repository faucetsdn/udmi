"""Discovery module for Butler mapping service."""

import sys
import uuid
from datetime import datetime, timezone

try:
    from src.connection import ButlerConnection
except (ImportError, ModuleNotFoundError):
    from butler.src.connection import ButlerConnection


def run_discovery(conn_spec, registry_id, device_id=None, target_families=None, site_model=None):
    """Dispatches discovery configuration message over the configured project spec / broker."""
    is_registry_discovery = not device_id
    actual_device_id = None if is_registry_discovery else device_id
    connection = ButlerConnection(conn_spec, registry_id, site_model, actual_device_id)
    actual_registry = connection.actual_registry

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    families_dict = {}
    if is_registry_discovery and not target_families:
        target_families = ["iot"]

    if target_families:
        for f in target_families:
            if f == "iot":
                families_dict[f] = {
                    "generation": now,
                    "depth": "details",
                    "source": "model",
                }
            else:
                families_dict[f] = {"generation": now}
    else:
        families_dict = {
            "vendor": {"generation": now},
            "bacnet": {"generation": now},
            "ipv4": {"generation": now},
        }

    tx_id = f"TXN-{uuid.uuid4().hex[:8]}"
    source_id = "butler"
    payload = {
        "version": "1.5.7",
        "timestamp": now,
        "generation": now,
        "families": families_dict,
    }
    if not is_registry_discovery:
        payload["enumerations"] = {
            "families": "entries",
            "points": "entries",
            "features": "entries",
        }
    msg = {
        "subType": "config",
        "subFolder": "discovery",
        "deviceRegistryId": actual_registry,
        "projectId": connection.project or "vibrant",
        "transactionId": tx_id,
        "publishTime": now,
        "source": source_id,
        "principal": source_id,
        "payload": payload,
    }
    if actual_device_id:
        msg["deviceId"] = actual_device_id

    topics = connection.get_discovery_topics(actual_device_id)
    topic_message_pairs = [(t, msg) for t in topics]
    connection.publish_messages(topic_message_pairs)
