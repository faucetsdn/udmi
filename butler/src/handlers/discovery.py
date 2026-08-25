"""Handler for UDMI discovery messages."""

from typing import Any, Dict, List, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


def get_discovered_family_addr(payload: Dict[str, Any], family: str) -> Optional[str]:
    """Returns the discovered address for a given family."""
    try:
        if family == payload.get("family"):
            return str(payload.get("addr"))
        families = payload.get("families", {})
        if isinstance(families, dict) and family in families:
            return str(families[family].get("addr"))
    except Exception:
        pass
    return None


class DiscoveryHandler(BaseHandler):
    """Processes discovery events and saves rows into PostgreSQL udmi_discovery table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return sub_folder == "discovery"

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        family = payload.get("family")
        ports: List[Dict[str, Any]] = []
        if family == "ether" and (refs := payload.get("refs")):
            if isinstance(refs, dict):
                fields = ("state", "service", "protocol", "product", "version", "banner")
                ports = [
                    {"port": k, **{f: v.get("adjunct", {}).get(f) for f in fields}}
                    for k, v in refs.items()
                    if isinstance(v, dict)
                ]

        system = payload.get("system", {}) if isinstance(payload.get("system"), dict) else {}
        hardware = system.get("hardware", {}) if isinstance(system.get("hardware"), dict) else {}
        ancillary = system.get("ancillary", {}) if isinstance(system.get("ancillary"), dict) else {}
        status = payload.get("status", {}) if isinstance(payload.get("status"), dict) else {}

        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "generation": payload.get("generation"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "device_id": envelope.get("deviceId"),
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

        return {
            "target": "postgres",
            "table": "udmi_discovery",
            "count": 1,
        }
