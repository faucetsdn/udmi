"""InfluxDB time-series management and write utilities."""

from datetime import datetime
import os
import sys
from typing import Any, Dict, List, Optional, Union

from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS


class InfluxManager:
    """Manages InfluxDB connection and metric write operations."""

    def __init__(
        self,
        url: Optional[str] = None,
        token: Optional[str] = None,
        org: Optional[str] = None,
        bucket: Optional[str] = None,
    ):
        port = os.environ.get("INFLUX_PORT", os.environ.get("INFLUXDB_PORT", "8086"))
        host = os.environ.get("INFLUXDB_HOST", "127.0.0.1")
        self.url = url or os.environ.get("INFLUXDB_URL", f"http://{host}:{port}")
        self.token = token or os.environ.get("INFLUXDB_TOKEN", "test-influx-token-12345")
        self.org = org or os.environ.get("INFLUXDB_ORG", "bridgehead")
        self.bucket = bucket or os.environ.get("INFLUXDB_BUCKET", "home")

        self._client: Optional[InfluxDBClient] = None
        self._write_api = None

    def get_client(self) -> InfluxDBClient:
        """Returns the configured InfluxDBClient."""
        if self._client is None:
            self._client = InfluxDBClient(
                url=self.url,
                token=self.token,
                org=self.org,
            )
        return self._client

    def get_write_api(self):
        """Returns synchronous write API."""
        if self._write_api is None:
            client = self.get_client()
            self._write_api = client.write_api(write_options=SYNCHRONOUS)
        return self._write_api

    def write_points(self, points: List[Point]) -> None:
        """Writes a list of InfluxDB Point objects."""
        if not points:
            return
        api = self.get_write_api()
        try:
            api.write(bucket=self.bucket, org=self.org, record=points)
        except Exception as e:
            print(f"Warning: InfluxDB write failed: {e}", file=sys.stderr)
            raise

    def write_pointset_payload(self, envelope: Dict[str, Any], payload: Dict[str, Any]) -> int:
        """Parses a UDMI pointset event payload and writes point_value measurements to InfluxDB.

        Returns:
            Number of points written.
        """
        project_id = envelope.get("projectId", "unknown")
        registry_id = envelope.get("deviceRegistryId", "unknown")
        device_id = envelope.get("deviceId", "unknown")
        publish_time = envelope.get("publishTime")

        ts_ns = None
        if publish_time:
            try:
                cleaned_time = publish_time.replace("Z", "+00:00")
                dt = datetime.fromisoformat(cleaned_time)
                ts_ns = int(dt.timestamp() * 1e9)
            except Exception:
                pass

        if ts_ns is None:
            ts_ns = int(datetime.utcnow().timestamp() * 1e9)

        points_data = payload.get("points", {})
        if not isinstance(points_data, dict):
            return 0

        points_to_write = []
        for point_name, point_def in points_data.items():
            if not isinstance(point_def, dict):
                continue
            present_value = point_def.get("present_value")
            if present_value is None:
                present_value = point_def.get("presentValue")
            if present_value is None:
                continue

            p = (
                Point("point_value")
                .tag("device_id", device_id)
                .tag("registry_id", registry_id)
                .tag("project_id", project_id)
                .tag("point_name", point_name)
                .time(ts_ns)
            )

            if isinstance(present_value, bool):
                p = p.field("present_value_bool", present_value)
            elif isinstance(present_value, (int, float)):
                p = p.field("present_value_num", float(present_value))
            else:
                p = p.field("present_value_str", str(present_value))

            points_to_write.append(p)

        if points_to_write:
            self.write_points(points_to_write)

        return len(points_to_write)

    def close(self) -> None:
        """Closes InfluxDB client connections."""
        if self._client is not None:
            self._client.close()
            self._client = None
            self._write_api = None
