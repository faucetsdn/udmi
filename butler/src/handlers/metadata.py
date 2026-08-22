"""Handler for UDMI metadata and site metadata messages."""

from typing import Any, Dict, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class MetadataHandler(BaseHandler):
    """Processes metadata and site metadata messages into PostgreSQL udmi_metadata table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return (
            sub_type == "model"
            or sub_folder == "metadata"
            or "localnet" in payload
            or ("system" in payload and "location" in payload.get("system", {}))
        )

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        system = payload.get("system", {}) if isinstance(payload.get("system"), dict) else {}
        hardware = system.get("hardware", {}) if isinstance(system.get("hardware"), dict) else {}
        location = system.get("location", {}) if isinstance(system.get("location"), dict) else {}
        localnet = payload.get("localnet", {}) if isinstance(payload.get("localnet"), dict) else {}
        families = localnet.get("families", {}) if isinstance(localnet.get("families"), dict) else {}
        cloud = payload.get("cloud", {}) if isinstance(payload.get("cloud"), dict) else {}

        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "device_id": envelope.get("deviceId"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "system_hardware_make": hardware.get("make"),
            "system_hardware_model": hardware.get("model"),
            "system_hardware_sku": hardware.get("sku"),
            "system_hardware_rev": hardware.get("rev"),
            "system_location_room": location.get("room"),
            "system_location_floor": location.get("floor"),
            "localnet_families_ipv4_addr": families.get("ipv4", {}).get("addr")
            if isinstance(families.get("ipv4"), dict)
            else None,
            "localnet_families_ether_addr": families.get("ether", {}).get("addr")
            if isinstance(families.get("ether"), dict)
            else None,
            "cloud_connection_type": cloud.get("connection_type")
            or cloud.get("auth_type"),
            "metadata": payload,
        }

        postgres_manager.insert_row("udmi_metadata", row)

        return {
            "target": "postgres",
            "table": "udmi_metadata",
            "count": 1,
        }
