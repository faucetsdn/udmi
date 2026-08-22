"""Handler for UDMI validation messages."""

from typing import Any, Dict, List, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class ValidationHandler(BaseHandler):
    """Processes validation messages and saves rows into PostgreSQL udmi_validation table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return (
            sub_folder == "validation"
            or sub_type == "validation"
            or "status" in payload
            and "errors" in payload
        )

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        status = payload.get("status", {}) if isinstance(payload.get("status"), dict) else {}
        errors_in = payload.get("errors", [])
        errors_list: List[Dict[str, Any]] = []
        if isinstance(errors_in, list):
            for err in errors_in:
                if isinstance(err, dict):
                    errors_list.append({
                        "message": err.get("message"),
                        "detail": err.get("detail"),
                        "category": err.get("category"),
                        "level": err.get("level"),
                    })

        sub_type_str = payload.get("sub_type") or envelope.get("subType", "unknown")
        sub_folder_str = payload.get("sub_folder") or envelope.get("subFolder", "unknown")

        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "device_id": envelope.get("deviceId"),
            "message_type": f"{sub_type_str}_{sub_folder_str}",
            "message": status.get("message"),
            "detail": status.get("detail"),
            "category": status.get("category"),
            "level": status.get("level"),
            "errors": errors_list,
        }

        postgres_manager.insert_row("udmi_validation", row)

        return {
            "target": "postgres",
            "table": "udmi_validation",
            "count": 1,
        }
