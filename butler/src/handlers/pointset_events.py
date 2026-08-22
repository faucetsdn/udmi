"""Handler for UDMI pointset events writing metrics to InfluxDB."""

from typing import Any, Dict, Optional
from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class PointsetEventsHandler(BaseHandler):
    """Processes pointset event telemetry and saves to InfluxDB."""

    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        if sub_folder == "pointset" and sub_type == "events":
            return True
        if sub_folder == "pointset" and sub_type != "state":
            points = payload.get("points", {})
            if isinstance(points, dict):
                return any(
                    "present_value" in p or "presentValue" in p
                    for p in points.values()
                    if isinstance(p, dict)
                )
        return False

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        count = influx_manager.write_pointset_payload(envelope, payload)
        return {
            "target": "influx",
            "measurement": "point_value",
            "count": count,
        }
