"""Handler for UDMI system state messages."""

from typing import Any, Dict, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class SystemStateHandler(BaseHandler):
    """Processes system state messages and saves rows into PostgreSQL udmi_system_state table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return sub_folder == "system" and sub_type == "state"

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        hardware = payload.get("hardware", {}) if isinstance(payload.get("hardware"), dict) else {}
        software_map = payload.get("software", {}) if isinstance(payload.get("software"), dict) else {}

        software_list = [
            {"id": str(k), "version": str(v)}
            for k, v in software_map.items()
        ]

        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "publish_timestamp": envelope.get("publishTime"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "device_id": envelope.get("deviceId"),
            "device_num_id": envelope.get("deviceNumId"),
            "gateway_id": envelope.get("gatewayId"),
            "make": hardware.get("make"),
            "model": hardware.get("model"),
            "serial_no": payload.get("serial_no"),
            "rev": hardware.get("rev"),
            "sku": hardware.get("sku"),
            "software": software_list,
        }

        postgres_manager.insert_row("udmi_system_state", row)

        return {
            "target": "postgres",
            "table": "udmi_system_state",
            "count": 1,
        }
