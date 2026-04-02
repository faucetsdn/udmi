"""
Sample: Pointset Writeback (Actuation)

This script demonstrates how to handle "Set Value" commands from the cloud.

SCENARIO:
1.  **Connection**: The device connects and listens for configuration updates.
2.  **Trigger**: You send a config with `"set_value": 24.0` for 'thermostat_target'.
3.  **Parsing**: The UDMI library detects the `set_value` field has changed.
4.  **Callback**: The library triggers the registered `on_writeback` callback.
5.  **Actuation**: The callback (your code) sends the signal to the "hardware".

INSTRUCTIONS:
1.  Run this script.
2.  Publish the JSON config payload printed below.
3.  Watch the logs for the "HARDWARE ACTUATION REQUIRED" message.
"""

import logging
import sys
import threading
import time
from typing import Any

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.core.managers import WritebackResult
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration
from udmi.schema import ValueState
from udmi.schema import Entry

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("WritebackSample")


def on_writeback(point_name: str, value: Any):
    """
    Callback triggered when the Cloud sends a 'set_value' in the configuration.

    This callback can return:
      - None / ValueState.applied -> Mark update as success (applied)
      - ValueState.<state> (e.g., ValueState.overridden, ValueState.invalid)
      - WritebackResult(ValueState.<state>, Optional[Entry]) -> Rich error reporting
    """
    LOGGER.info("!" * 60)
    LOGGER.info(f"HARDWARE ACTUATION REQUIRED: Point '{point_name}' -> Set To {value}")
    LOGGER.info("!" * 60)

    # Example 1: Validation failure (Setting value to negative)
    if isinstance(value, (int, float)) and value < 0:
        LOGGER.warning(f"Rejecting non-sensical negative value ({value}) for {point_name}")
        return ValueState.invalid

    # Example 2: Simulating hardware failure with a descriptive status message
    if value == 999.0:
        LOGGER.error(f"Simulating mechanical jam failure for {point_name}")
        return WritebackResult(
            value_state=ValueState.failure,
            status=Entry(message="Actuator mechanical jam identified", level=500)
        )

    # Example 3: Simulating a code crash inside the callback
    if value == 666.0:
         LOGGER.error(f"Simulating callback execution crash for {point_name}")
         raise RuntimeError("Unexpected write failure crash")

    time.sleep(0.1)
    LOGGER.info(f"Hardware confirm: '{point_name}' is now {value}")

    # Returning applied is the standard expected outcome
    return ValueState.applied


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

        managers = get_default_managers()
        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)

        # ---------------------------------------------------------------------
        # CRITICAL STEP: Register the Writeback Handler
        # Without this, the library updates its internal state to match the config,
        # but your physical device remains unchanged.
        # ---------------------------------------------------------------------
        pointset_manager.set_writeback_handler(on_writeback)
        LOGGER.info("Writeback handler registered.")

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Device {DEVICE_ID} is running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("To trigger actuation, publish JSON config updates using mosquitto_pub:")
        print("-" * 20)
        print("1. Success case (Applied):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 24.0 }} }} }} }}'")
        print("\n2. Invalid case (ValueState.invalid for negative value):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": -5.0 }} }} }} }}'")
        print("\n3. Failure case (WritebackResult with status msg):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 999.0 }} }} }} }}'")
        print("\n4. Crash case (Raises Exception -> transparent ValueState.failure):")
        print(f"   mosquitto_pub ... -m '{{ \"pointset\": {{ \"points\": {{ \"thermostat_target\": {{ \"set_value\": 666.0 }} }} }} }}'")
        print("-" * 20)
        print(f"Base command structure:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config'")
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)