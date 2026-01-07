"""
Sample: COV (Change of Value) Throttling

This script demonstrates how to reduce data traffic using `cov_increment`.

SCENARIO:
1. The script simulates a sensor ('ramp_sensor') that increases by 1.0 every second.
2. Initially, the device reports EVERY change (Frequency: ~1Hz).
3. You send a config setting `cov_increment` to 5.0.
4. The device filters updates and only reports when the value changes by 5.0 (Frequency: ~0.2Hz).

INSTRUCTIONS:
1. Run this script. Observe the "Publishing pointset" logs appearing every second.
2. Publish this JSON to the config topic: /devices/{DEVICE_ID}/config
   {
     "pointset": {
       "points": {
         "ramp_sensor": { "cov_increment": 5.0 }
       },
       "sample_rate_sec": 1
     }
   }
3. Observe the logs slow down (updates appear every ~5 seconds).
"""

import logging
import sys
import threading
import time

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("CovSample")


def simulate_fast_sensor(manager: PointsetManager):
    """
    Simulates a sensor reading that changes rapidly (every 1.0s).
    """
    val = 0.0
    LOGGER.info("Starting sensor simulation (1.0s interval)...")

    while True:
        time.sleep(1.0)
        val += 1.0

        manager.set_point_value("ramp_sensor", val)

        LOGGER.debug(f"Internal Sensor Read: {val} (Manager updated)")


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

        managers = get_default_managers(sample_rate_sec=1)
        device = create_device(endpoint, managers)

        pointset_manager = device.get_manager(PointsetManager)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        sim = threading.Thread(target=simulate_fast_sensor,
                               args=(pointset_manager,), daemon=True)
        sim.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")
        LOGGER.info("Waiting for Config on topic: %s%s/config", topic_prefix,
                    DEVICE_ID)

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
