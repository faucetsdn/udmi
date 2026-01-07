"""
Sample: Pointset Writeback (Actuation)

This script demonstrates how to handle "Set Value" commands from the cloud.

SCENARIO:
1. The device connects and listens for configuration updates.
2. The Cloud (or user via MQTT) sends a config with `"set_value": 24.0` for 'thermostat_target'.
3. The UDMI library parses this change.
4. The library triggers the registered `on_writeback` callback.
5. You (the developer) use this callback to send the signal to your actual hardware.

INSTRUCTIONS:
1. Run this script.
2. Publish this JSON to the device config topic: /devices/{DEVICE_ID}/config
   {
     "pointset": {
       "points": {
         "thermostat_target": { "set_value": 24.0 }
       }
     }
   }
3. Watch the logs for the "HARDWARE ACTUATION" message.
"""

import logging
import sys
import threading
import time
from typing import Any

from udmi.core.factory import create_device, get_default_managers
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

logging.basicConfig(level=logging.DEBUG,
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
        topic_prefix = f"/r/{REGISTRY_ID}/d/"
        endpoint = EndpointConfiguration(
            client_id=f"{topic_prefix}{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix,
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
        LOGGER.info("Waiting for 'set_value' config updates...")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)