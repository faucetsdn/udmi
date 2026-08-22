"""Handler for UDMI alarm messages."""

from typing import Any, Dict, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


def parse_bool(val: Any) -> Optional[bool]:
    """Parses boolean or string boolean values."""
    if val is None:
        return None
    if isinstance(val, bool):
        return val
    return str(val).lower() == "true"


class AlarmsHandler(BaseHandler):
    """Processes alarm messages and saves rows into PostgreSQL udmi_alarms table."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        return sub_folder in ("alarms", "alarmset") or "alarm_type" in payload or "in_alarm" in payload

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        row = {
            "timestamp": payload.get("timestamp") or envelope.get("publishTime"),
            "device_registry_id": envelope.get("deviceRegistryId"),
            "device_id": envelope.get("deviceId"),
            "alarm_category": payload.get("alarm_category"),
            "alarm_priority": payload.get("alarm_priority"),
            "alarm_type": payload.get("alarm_type"),
            "controller": payload.get("controller"),
            "equipment": payload.get("equipment"),
            "fault": parse_bool(payload.get("fault")),
            "from_state": payload.get("from_state"),
            "to_state": payload.get("to_state"),
            "generation_time": payload.get("generation_time"),
            "in_alarm": parse_bool(payload.get("in_alarm")),
            "location_path": payload.get("location_path"),
            "message_text": payload.get("message_text"),
            "out_of_service": parse_bool(payload.get("out_of_service")),
            "overridden": parse_bool(payload.get("overridden")),
        }

        postgres_manager.insert_row("udmi_alarms", row)

        return {
            "target": "postgres",
            "table": "udmi_alarms",
            "count": 1,
        }
