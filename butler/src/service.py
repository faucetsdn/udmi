"""Butler standalone database capture service."""

from datetime import datetime
import json
import os
import signal
import sys
import time
from typing import Any, Dict, List, Optional, Union

from butler.src.dispatcher import MessageDispatcher
from udmi.common.connection import MessageConnection
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager
from udmi.common.project_spec import parse_project_spec


def parse_mqtt_topic(topic: str) -> Dict[str, Optional[str]]:
    """Extracts envelope fields from a standard or UUFI MQTT topic."""
    parts = [p for p in topic.strip("/").split("/") if p]
    registry_id = None
    device_id = None
    sub_type = "events"
    sub_folder = "update"

    r_idx = -1
    d_idx = -1
    c_idx = -1
    for i, p in enumerate(parts):
        if p == "r":
            r_idx = i
        elif p == "d":
            d_idx = i
        elif p == "c":
            c_idx = i

    if r_idx >= 0 and r_idx + 1 < len(parts):
        registry_id = parts[r_idx + 1]
    if d_idx >= 0 and d_idx + 1 < len(parts):
        device_id = parts[d_idx + 1]

    if c_idx >= 0:
        if c_idx + 1 < len(parts):
            sub_type = parts[c_idx + 1]
        if c_idx + 2 < len(parts):
            sub_folder = parts[c_idx + 2]
    else:
        if d_idx >= 0:
            if d_idx + 2 < len(parts):
                sub_type = parts[d_idx + 2]
            if d_idx + 3 < len(parts):
                sub_folder = parts[d_idx + 3]
        elif r_idx >= 0:
            if r_idx + 2 < len(parts):
                sub_type = parts[r_idx + 2]
            if r_idx + 3 < len(parts):
                sub_folder = parts[r_idx + 3]
        elif len(parts) > 0 and parts[0] in ("events", "state", "commands", "config"):
            sub_type = parts[0]
            if len(parts) > 1:
                sub_folder = parts[1]

    return {
        "deviceRegistryId": registry_id,
        "deviceId": device_id or "empty",
        "subType": sub_type or "events",
        "subFolder": sub_folder or "update",
    }


class ButlerService:
    """Butler standalone service capturing messages from MQTT / PubSub into databases."""

    def __init__(
        self,
        project_spec: Optional[str] = None,
        site_model: Optional[str] = None,
        always_save_raw: bool = True,
    ):
        self.project_spec = project_spec
        self.site_model = site_model
        self.spec_info = parse_project_spec(project_spec)
        self.running = False

        self.postgres_manager = PostgresManager()
        self.influx_manager = InfluxManager()
        self.postgres_manager.init_default_tables()

        self.dispatcher = MessageDispatcher(
            postgres_manager=self.postgres_manager,
            influx_manager=self.influx_manager,
            always_save_raw=always_save_raw,
        )

        self.connection = MessageConnection(
            conn_spec=project_spec,
            site_model=site_model,
        )

    def process_raw_message(self, topic_or_channel: str, raw_payload: Union[str, bytes]) -> None:
        """Processes a single raw message received over MQTT or stream."""
        try:
            if isinstance(raw_payload, bytes):
                raw_payload = raw_payload.decode("utf-8")

            data = json.loads(raw_payload) if raw_payload.strip() else {}
        except Exception as e:
            print(f"Warning: Could not parse message payload as JSON: {e}", file=sys.stderr)
            return

        envelope: Dict[str, Any] = {}
        payload: Dict[str, Any] = {}

        if isinstance(data, dict) and "envelope" in data and "payload" in data:
            # Structured envelope + payload JSON stream (from pull_messages / pull_mqtt)
            envelope = data.get("envelope", {})
            payload = data.get("payload", {})
        else:
            # Direct MQTT topic message
            envelope = parse_mqtt_topic(topic_or_channel)
            envelope["projectId"] = self.spec_info.get("project")
            envelope["publishTime"] = datetime.utcnow().isoformat() + "Z"
            payload = data

        try:
            res = self.dispatcher.dispatch(envelope, payload)
            target = res.get("target", "unknown")
            count = res.get("count", 1)
            reg = envelope.get("deviceRegistryId", "unknown")
            dev = envelope.get("deviceId", "unknown")
            sf = envelope.get("subFolder", "unknown")
            st = envelope.get("subType", "unknown")
            print(f"butler:db:{target}/{reg}/{dev}/{sf}/{st} (records: {count})")
        except Exception as e:
            print(f"Error dispatching message to database: {e}", file=sys.stderr)

    def run_stdin(self) -> None:
        """Reads JSON stream line by line from stdin (for piped operation)."""
        print("Butler listening on stdin stream...", file=sys.stderr)
        self.running = True
        try:
            for line in sys.stdin:
                if not self.running:
                    break
                line = line.strip()
                if not line:
                    continue
                self.process_raw_message("stdin", line)
        except KeyboardInterrupt:
            pass
        finally:
            self.stop()

    def run_mqtt(self, topics: Optional[List[str]] = None) -> None:
        """Connects to MQTT broker and starts message loop."""
        subscribe_topics = topics or self.connection.get_default_subscribe_topics()
        print(f"Butler starting MQTT listener on topics: {subscribe_topics}", file=sys.stderr)
        self.running = True

        mqtt_client = self.connection.subscribe_mqtt(
            topics=subscribe_topics,
            on_message_callback=self.process_raw_message,
        )

        mqtt_client.loop_start()
        try:
            while self.running:
                time.sleep(0.5)
        except KeyboardInterrupt:
            pass
        finally:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
            self.stop()

    def run(self) -> None:
        """Starts service based on provider."""
        provider = self.spec_info.get("provider", "mqtt")
        if provider == "mqtt":
            self.run_mqtt()
        else:
            print(f"Starting Butler for provider: {provider}", file=sys.stderr)
            self.run_mqtt()

    def stop(self) -> None:
        """Shuts down Butler service and closes database connections."""
        self.running = False
        self.influx_manager.close()
        print("Butler service stopped.", file=sys.stderr)


