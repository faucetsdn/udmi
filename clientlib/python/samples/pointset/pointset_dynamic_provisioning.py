"""
Sample: Dynamic Point Provisioning

This script demonstrates how the device automatically creates points
based on the configuration received from the cloud.

SCENARIO:
1. The device starts with NO points configured.
2. It waits for a UDMI Config message.
3. When you send a config defining 'new_room_sensor', the device
   instantly creates it and starts managing it.

INSTRUCTIONS:
1. Run this script.
2. Publish points to the device config topic:
   {
     "pointset": {
       "points": {
         "new_room_sensor": { "units": "Celsius" },
         "outdoor_humidity": { "units": "%" }
       }
     }
   }
3. Watch the logs to see the points being provisioned.
"""

import logging
import sys
import threading
import time

from udmi.core.factory import create_device
from udmi.core.managers import PointsetManager
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("DynamicProvSample")


def report_managed_points(manager: PointsetManager):
    """Periodically prints the list of points the device currently knows about."""
    while True:
        time.sleep(5)
        points = list(manager._points.keys())
        if points:
            LOGGER.info(f"Currently managed points: {points}")
        else:
            LOGGER.warning(
                "No points provisioned yet. Waiting for Config...")


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

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info("Device running. Listening for configuration...")

        # The monitoring loop
        report_managed_points(pointset_manager)

    except KeyboardInterrupt:
        sys.exit(0)
