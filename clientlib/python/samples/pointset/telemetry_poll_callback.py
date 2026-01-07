"""
Sample: Telemetry Poll Callback

This script demonstrates the "Just-in-Time" data fetching pattern.
Instead of pushing data to the manager in a loop, we register a callback.
The PointsetManager calls this function immediately before it publishes telemetry.
"""

import logging
import random
import sys
from typing import Any
from typing import Dict

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import EndpointConfiguration

# --- Config ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger(__name__)


def my_sensor_poll() -> Dict[str, Any]:
    """
    Called by the PointsetManager background thread.
    Must return a dictionary of {point_name: value}.
    """
    # Simulate reading hardware registers
    temp = round(random.uniform(20.0, 25.0), 2)
    pressure = round(random.uniform(1000, 1020), 1)

    LOGGER.info("Library requested data poll...")

    return {
        "supply_temp": temp,
        "static_pressure": pressure
    }


if __name__ == "__main__":
    try:
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": "/r/ZZ-TRI-FECTA/d/",
            "auth_provider": {
                "basic": {
                    "username": BROKER_USERNAME,
                    "password": BROKER_PASSWORD
                }
            }
        })

        managers = get_default_managers(sample_rate_sec=5)

        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)
        pointset_manager.set_poll_callback(my_sensor_poll)

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
