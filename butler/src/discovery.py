"""Discovery module for Butler mapping service."""

import sys
import uuid
from datetime import datetime, timezone

try:
    from src.connection import ButlerConnection
except (ImportError, ModuleNotFoundError):
    from butler.src.connection import ButlerConnection


def run_discovery(conn_spec, registry_id, device_id, target_families, site_model=None):
    """Dispatches discovery configuration message over the configured project spec / broker."""
    connection = ButlerConnection(conn_spec, registry_id, site_model, device_id)
    actual_registry = connection.actual_registry

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
    source_id = "butler"
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
        "projectId": connection.project or "vibrant",
        "transactionId": tx_id,
        "publishTime": now,
        "source": source_id,
        "principal": source_id,
        "payload": payload,
    }

    topics = connection.get_discovery_topics(device_id)
    topic_message_pairs = [(t, msg) for t in topics]
    connection.publish_messages(topic_message_pairs)
