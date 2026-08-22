"""Handler for UDMI point state messages."""

from typing import Any, Dict, List, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class PointStateHandler(BaseHandler):
    """Processes point state messages and saves rows into PostgreSQL udmi_point_state table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return sub_folder == "pointset" and sub_type == "state"

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        device_id = envelope.get("deviceId")
        device_registry_id = envelope.get("deviceRegistryId")
        message_id = envelope.get("messageId")
        timestamp = payload.get("timestamp") or envelope.get("publishTime")

        points = payload.get("points", {})
        if not isinstance(points, dict):
            return {"target": "postgres", "table": "udmi_point_state", "count": 0}

        rows: List[Dict[str, Any]] = []
        for point_name, point in points.items():
            if not isinstance(point, dict):
                continue
            status = point.get("status", {}) if isinstance(point.get("status"), dict) else {}
            rows.append({
                "timestamp": timestamp,
                "device_id": device_id,
                "device_registry_id": device_registry_id,
                "message_id": message_id,
                "point_name": point_name,
                "value_state": point.get("value_state"),
                "units": point.get("units"),
                "status_timestamp": status.get("timestamp"),
                "level": status.get("level"),
                "category": status.get("category"),
                "message": status.get("message"),
                "detail": status.get("detail"),
            })

        if rows:
            postgres_manager.insert_rows("udmi_point_state", rows)

        return {
            "target": "postgres",
            "table": "udmi_point_state",
            "count": len(rows),
        }
