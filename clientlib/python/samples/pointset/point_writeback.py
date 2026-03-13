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
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

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

    This is where you bridge the software world (UDMI) to the physical world.
    Examples:
      - Write to a Modbus register
      - Send a signal over Serial
      - Call a BACnet API
    """
    LOGGER.info("!" * 60)
    LOGGER.info(f"HARDWARE ACTUATION REQUIRED: Point '{point_name}' -> Set To {value}")
    LOGGER.info("!" * 60)

    time.sleep(0.1)
    LOGGER.info(f"Hardware confirm: '{point_name}' is now {value}")


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
        print("To trigger actuation, publish this JSON config:")
        print("-" * 20)
        json_payload = ('{ "pointset": { "points": { "thermostat_target": { "set_value": 24.0 } } } }')
        print(json_payload)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{json_payload}'")
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)