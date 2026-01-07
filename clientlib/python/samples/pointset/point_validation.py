"""
Sample: Automatic Point Validation via Metadata

This script demonstrates how to rely on the library's built-in validation logic.

SCENARIO:
1. We define a Pointset Model (Metadata) where 'bearing_temp' has a max of 80.0.
2. We apply this model to the manager.
3. We feed values to the manager.
   - When value is 75, library accepts it.
   - When value is 85, library automatically flags it as invalid and sets the status.

INSTRUCTIONS:
1. Run the script.
2. Observe that no manual 'if' checks are needed in the loop.
3. Watch the logs: The library detects the violation and sets the status.
"""

import logging
import math
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

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("AutoValidSample")


def simulate_temp_sensor(manager: PointsetManager):
    """
    Simulates a sensor. Notice: NO validation logic here.
    We just dump data into the manager.
    """
    tick = 0
    while True:
        time.sleep(1.0)
        tick += 1

        # Swing between 60 and 90
        # (sin + 1) * 15 => 0 to 30.  + 60 => 60 to 90.
        current_temp = 60 + ((math.sin(tick * 0.5) + 1) * 15)
        current_temp = round(current_temp, 2)

        # Simply set the value. The library handles the rest.
        manager.set_point_value("bearing_temp", current_temp)

        # We assume the library is doing its job; we can check the status to confirm for logging
        current_status = manager.points["bearing_temp"].status
        if current_status and current_status.level >= 500:
            LOGGER.warning(
                f"Sent: {current_temp} -> Library Status: {current_status.message}")
        else:
            LOGGER.info(f"Sent: {current_temp} -> OK")


if __name__ == "__main__":
    try:
        # 1. Setup Endpoint
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
                        "bearing_temp": PointPointsetModel(
                            units="Celsius",
                            range_min=0,
                            range_max=80.0
                        )
                    }
                )
            )
        )
        pointset_manager = device.get_manager(PointsetManager)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        sim = threading.Thread(target=simulate_temp_sensor,
                               args=(pointset_manager,), daemon=True)
        sim.start()

        LOGGER.info(f"Device {DEVICE_ID} running...")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)