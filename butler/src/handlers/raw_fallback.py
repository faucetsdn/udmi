"""Fallback handler for unhandled UDMI messages, saving raw JSONB to PostgreSQL."""

from typing import Any, Dict, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class RawFallbackHandler(BaseHandler):
    """Saves arbitrary UDMI messages into udmi_messages table as JSONB."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return True

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        row = {
            "project_id": envelope.get("projectId"),
            "registry_id": envelope.get("deviceRegistryId"),
            "device_id": envelope.get("deviceId"),
            "sub_folder": envelope.get("subFolder"),
            "sub_type": envelope.get("subType"),
            "publish_time": envelope.get("publishTime"),
            "payload": payload,
        }

        postgres_manager.insert_row("udmi_messages", row)

        return {
            "target": "postgres",
            "table": "udmi_messages",
            "count": 1,
        }
