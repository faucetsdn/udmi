"""
Sample: COV (Change of Value) Throttling

This script demonstrates how to reduce data traffic using `cov_increment`.

SCENARIO:
1. The script simulates a sensor that changes value by 1.0 every second.
2. Initially (without config), the device reports EVERY change (every second).
3. You send a config setting `cov_increment` to 5.0.
4. The device immediately slows down reporting to once every ~5 seconds.

INSTRUCTIONS:
1. Run script. Observe logs scrolling fast.
2. Publish this JSON to apply throttling:
   {
     "pointset": {
       "points": {
         "ramp_sensor": { "cov_increment": 5.0 }
       }
     }
   }
3. Watch the logs slow down.
"""

import logging
import sys
import threading
import time

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("CovSample")


def simulate_fast_sensor(manager: PointsetManager):
    """Updates the sensor value by 1.0 every second."""
    val = 0.0
    while True:
        time.sleep(1.0)
        val += 1.0

        manager.set_point_value("ramp_sensor", val)
        LOGGER.debug(f"Sensor read: {val} (Manager updated)")


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

        # Use fast sample rate to allow fast COV checks
        managers = get_default_managers(sample_rate_sec=1)
        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        sim = threading.Thread(target=simulate_fast_sensor,
                               args=(pointset_manager,), daemon=True)
        sim.start()

        LOGGER.info("Device running. Sensor changing every 1.0s.")
        LOGGER.info("Observe: events/pointset frequency.")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        sys.exit(0)
