"""
Sample: Dynamic Point Provisioning with Initial Model

This script demonstrates how a device handles points from two sources:
1. Internal Model: Points hardcoded/defined in the device firmware.
2. Dynamic Config: Points provisioned on-the-fly via Cloud Configuration.

SCENARIO:
1. Device starts with a built-in point: 'internal_temp'.
2. You send a Config adding: 'expansion_sensor_1'.
3. The device seamlessly merges them and manages both.

INSTRUCTIONS:
1. Run this script.
2. Observe logs: Only 'internal_temp' is managed initially.
3. Publish this JSON to: /devices/{DEVICE_ID}/config
   {
     "pointset": {
       "points": {
         "expansion_sensor_1": { "units": "Bar" }
       }
     }
   }
4. Observe logs: 'expansion_sensor_1' is added dynamically.
"""

import logging
import sys
import threading
import time

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import EndpointConfiguration, PointsetModel, PointPointsetModel
from udmi.schema import Metadata

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("DynamicProvSample")


def report_managed_points(manager: PointsetManager):
    """
    Periodically logs the inventory of points the device is tracking.
    """
    last_set = set()

    while True:
        # Access the public property .points (returns a dict of Point objects)
        current_points = list(manager.points.keys())
        current_set = set(current_points)

        # Log only if there is a change or periodically to confirm aliveness
        if current_set != last_set:
            LOGGER.info("!" * 50)
            LOGGER.info(f"POINT INVENTORY UPDATED: {len(current_points)} points")
            LOGGER.info(f"List: {current_points}")
            LOGGER.info("!" * 50)
            last_set = current_set
        else:
            # Heartbeat log just to show we are waiting
            LOGGER.debug(f"Still managing {len(current_points)} points...")

        time.sleep(2)


if __name__ == "__main__":
    try:
        topic_prefix = f"/r/{REGISTRY_ID}/d/"
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"{topic_prefix}{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": topic_prefix,
            "auth_provider": {
                "basic": {"username": BROKER_USERNAME,
                          "password": BROKER_PASSWORD}
            }
        })

        managers = get_default_managers()
        device = create_device(
            endpoint,
            managers,
            initial_model=Metadata(
                pointset=PointsetModel(
                    points={
                        "internal_temp": PointPointsetModel(
                            units="Celsius",
                            range_min=0,
                            range_max=100
                        )
                    }
                )
            )
        )
        pointset_manager = device.get_manager(PointsetManager)
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")
        LOGGER.info("Waiting for Dynamic Config...")

        report_managed_points(pointset_manager)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)