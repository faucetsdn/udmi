"""
Sample: Pointset Writeback (Actuation)

This script demonstrates how to handle "Set Value" commands from the cloud.

SCENARIO:
1. The device listens for configuration updates.
2. The Cloud sends a config with `"set_value": 24.0` for a specific point.
3. The device triggers the `on_writeback` callback to actuate the hardware.

INSTRUCTIONS:
1. Run this script.
2. Publish this JSON to the device config topic:
   {
     "pointset": {
       "points": {
         "thermostat_target": { "set_value": 24.0 }
       }
     }
   }
3. Watch the logs for the ACTUATION COMMAND.
"""

import logging
import sys
import threading
import time
from typing import Any

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("WritebackSample")


def on_writeback(point_name: str, value: Any):
    """
    Callback triggered when the Cloud sends a 'set_value'.
    """
    LOGGER.info("!" * 60)
    LOGGER.info(f"HARDWARE ACTUATION: Setting '{point_name}' to {value}")
    LOGGER.info("!" * 60)


if __name__ == "__main__":
    try:
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": "/r/ZZ-TRI-FECTA/d/",
            "auth_provider": {
                "basic": {"username": BROKER_USERNAME,
                          "password": BROKER_PASSWORD}
            }
        })

        device = create_device(endpoint)
        pointset_manager = device.get_manager(PointsetManager)
        # Register callback for writeback
        pointset_manager.set_writeback_handler(on_writeback)


        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info("Device running. Waiting for 'set_value' command...")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        sys.exit(0)