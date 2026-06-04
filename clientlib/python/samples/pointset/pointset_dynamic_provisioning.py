"""
Sample: Dynamic Point Provisioning with Initial Model

This script demonstrates how a device handles points from two sources:
1.  **Internal Model**: Points hardcoded/defined in the device firmware (Metadata).
2.  **Dynamic Config**: Points provisioned on-the-fly via Cloud Configuration.

SCENARIO:
1.  **Startup**: The device boots with one built-in point: `internal_temp`.
2.  **Monitoring**: The script logs the current inventory of managed points.
3.  **Trigger**: You send a Config message adding a new point: `expansion_sensor_1`.
4.  **Provisioning**: The `PointsetManager` detects the new point in the config,
    instantiates it, and begins managing it alongside the built-in point.

INSTRUCTIONS:
1.  Run the script.
2.  Observe the logs showing "POINT INVENTORY UPDATED: 1 points".
3.  Publish the JSON config payload printed below.
4.  Observe the logs showing "POINT INVENTORY UPDATED: 2 points".
"""

import logging
import sys
import threading
import time

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration
from udmi.schema import Metadata
from udmi.schema import PointPointsetModel
from udmi.schema import PointsetModel

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
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

        # Log only if there is a change
        if current_set != last_set:
            LOGGER.info("!" * 60)
            LOGGER.info(f"POINT INVENTORY UPDATED: {len(current_points)} points")
            LOGGER.info(f"   Current List: {current_points}")
            LOGGER.info("!" * 60)
            last_set = current_set

        time.sleep(1)


if __name__ == "__main__":
    try:
        endpoint = EndpointConfiguration(
            client_id=f"{TOPIC_PREFIX}{DEVICE_ID}",
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

        # 1. Define Initial Metadata (Firmware Model)
        initial_metadata = Metadata(
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

        # 2. Create Device
        managers = get_default_managers()
        device = create_device(
            endpoint,
            managers,
            initial_model=initial_metadata
        )
        pointset_manager = device.get_manager(PointsetManager)

        # 3. Start Device
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. Observe that only 'internal_temp' is managed initially.")
        print("2. To provision a new point, publish this config:")
        print("-" * 20)
        json_payload = ('{ "pointset": { "points": { "expansion_sensor_1": { "units": "Bar" } } } }')
        print(json_payload)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{json_payload}'")
        print("=" * 60 + "\n")

        # 4. Monitor
        report_managed_points(pointset_manager)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
