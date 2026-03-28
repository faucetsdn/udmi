"""
Sample: Writeback (Modern Approach via Custom Point)

This script demonstrates how to handle "Set Value" commands from the cloud
using the modern approach: encapsulating the actuation logic directly within
a custom Point implementation.

SCENARIO:
1.  **CustomPoint**: A custom subclass of `BasicPoint` that overrides `set_value`.
2.  **Actuation**: You send a config with `"set_value": 24.0` for 'thermostat_target'.
3.  **Encapsulation**: The manager routes the writeback to the specific point's `set_value()`.
4.  **Result**: The point handles validation, simulates hardware feedback, and returns the state.
"""

import logging
import sys
import threading
import time
from typing import Any, Optional

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager, WritebackResult
from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import AuthProvider, Basic, EndpointConfiguration, PointPointsetModel
from udmi.schema import Entry, ValueState

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("WritebackModernSample")


class ActuatingPoint(BasicPoint):
    """
    A custom Point that handles its own underlying hardware writebacks.

    Replaces the legacy global callback by encapsulating the actuation 
    (hardware interaction) logic securely inside the point's set_value implementation.
    """
    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        super().__init__(name, model)
        # We manually mark this True for the demo, normally set by cloud metadata
        self._writable = True

    def get_value(self) -> Any:
        return 0.0

    def set_value(self, value: Any) -> Any:
        """
        Invoked automatically by the manager when a writeback occurs.
        Can return ValueState, WritebackResult, or silently return None/ValueState.applied.
        """
        LOGGER.info("!" * 60)
        LOGGER.info(f"[{self._name}] HARDWARE ACTUATION: Set To {value}")
        LOGGER.info("!" * 60)

        # Example 1: Validation failure
        if isinstance(value, (int, float)) and value < 0:
            LOGGER.warning(f"[{self._name}] Rejecting negative value: {value}")
            return ValueState.invalid

        # Example 2: Simulating mechanical failure
        if value == 999.0:
            LOGGER.error(f"[{self._name}] Simulating mechanical jam")
            return WritebackResult(
                value_state=ValueState.failure,
                status=Entry(message="Actuator mechanical jam identified", level=500)
            )

        # Example 3: Crash
        if value == 666.0:
            LOGGER.error(f"[{self._name}] Simulating crash")
            raise RuntimeError("Unexpected write failure crash")

        time.sleep(0.1)
        LOGGER.info(f"[{self._name}] Hardware confirmed actuation.")
        return ValueState.applied

    def validate_value(self, value: Any) -> bool:
        return True


def actuating_point_factory(name: str, model: Optional[PointPointsetModel] = None):
    return ActuatingPoint(name, model)


if __name__ == "__main__":
    try:
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"
        endpoint = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=TOPIC_PREFIX,
            auth_provider=AuthProvider(
                basic=Basic(
                    username=BROKER_USERNAME,
                    password=BROKER_PASSWORD
                )
            )
        )

        managers = get_default_managers(point_factory=actuating_point_factory)
        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)

        pointset_manager.add_point("thermostat_target")

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Device {DEVICE_ID} is running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("Publish JSON config updates using mosquitto_pub:")
        print("-" * 20)
        print("1. Success case (Applied):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 24.0 }} }} }} }}'")
        print("\n2. Invalid case (-5.0):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": -5.0 }} }} }} }}'")
        print("\n3. Failure case (999.0):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 999.0 }} }} }} }}'")
        print("\n4. Crash case (666.0):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 666.0 }} }} }} }}'")
        print("-" * 20)
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
