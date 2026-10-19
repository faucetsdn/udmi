"""Handler for UDMI discovery messages."""

from typing import Any, Dict, List, Optional
from butler.src.handlers.base import BaseHandler
from butler.src.handlers.metadata import MetadataHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


def get_discovered_family_addr(payload: Dict[str, Any], family: str) -> Optional[str]:
    """Returns the discovered address for a given family."""
    if not isinstance(payload, dict):
        return None
    if family == payload.get("family") and payload.get("addr") is not None:
        return str(payload.get("addr"))
    families = payload.get("families")
    if isinstance(families, dict):
        family_block = families.get(family)
        if isinstance(family_block, dict) and family_block.get("addr") is not None:
            return str(family_block.get("addr"))
    model = payload.get("model")
    if isinstance(model, dict):
        localnet = model.get("localnet")
        model_families = localnet.get("families") if isinstance(localnet, dict) else None
        if isinstance(model_families, dict):
            family_block = model_families.get(family)
            if isinstance(family_block, dict) and family_block.get("addr") is not None:
                return str(family_block.get("addr"))
    return None


class DiscoveryHandler(BaseHandler):
    """Processes discovery events and saves rows into PostgreSQL udmi_discovery table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return sub_folder == "discovery" and sub_type in (None, "events")

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        if isinstance(payload, dict) and "payload" in payload and isinstance(payload.get("payload"), dict):
            payload = payload["payload"]
        family = payload.get("family")
        event_no = payload.get("event_no")
        model_data = payload.get("model") if isinstance(payload.get("model"), dict) else {}

        if isinstance(event_no, int) and event_no <= 0 and not payload.get("addr") and not model_data:
            return {
                "target": "postgres",
                "table": "udmi_discovery",
                "count": 0,
            }

        ports: List[Dict[str, Any]] = []
        if family == "ether" and (refs := payload.get("refs")):
            if isinstance(refs, dict):
                fields = ("state", "service", "protocol", "product", "version", "banner")
                ports = [
                    {"port": k, **{f: v.get("adjunct", {}).get(f) for f in fields}}
                    for k, v in refs.items()
                    if isinstance(v, dict)
                ]

        raw_system = payload.get("system") if isinstance(payload.get("system"), dict) else model_data.get("system", {})
        system = raw_system if isinstance(raw_system, dict) else {}
        hardware = system.get("hardware", {}) if isinstance(system.get("hardware"), dict) else {}
        ancillary = system.get("ancillary", {}) if isinstance(system.get("ancillary"), dict) else {}
        status = payload.get("status", {}) if isinstance(payload.get("status"), dict) else {}

        device_id = envelope.get("deviceId")
        if not device_id and family == "iot" and payload.get("addr"):
            device_id = str(payload.get("addr"))

        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "generation": payload.get("generation"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "device_id": device_id,
            "message_id": envelope.get("messageId"),
            "scan_family": family,
            "ether_addr": get_discovered_family_addr(payload, "ether"),
            "ipv4_addr": get_discovered_family_addr(payload, "ipv4"),
            "bacnet_addr": get_discovered_family_addr(payload, "bacnet"),
            "hostname": get_discovered_family_addr(payload, "hostname"),
            "fqdn": get_discovered_family_addr(payload, "fqdn"),
            "hardware_make": hardware.get("make"),
            "hardware_model": hardware.get("model"),
            "firmware_version": ancillary.get("firmware"),
            "serial_no": system.get("serial_no"),
            "status_level": status.get("level"),
            "status_category": status.get("category"),
            "status_message": status.get("message"),
            "ports": ports,
        }

        postgres_manager.insert_row("udmi_discovery", row)

        if family == "iot" and model_data:
            model_env = dict(envelope)
            if device_id:
                model_env["deviceId"] = device_id
            MetadataHandler().process_message(model_env, model_data, postgres_manager, influx_manager)

        return {
            "target": "postgres",
            "table": "udmi_discovery",
            "count": 1,
        }
