"""
Sample: Basic Telemetry

This script demonstrates how to use the PointsetManager to report
telemetry data (sensor readings).
"""

import logging
import time
import random
import sys
import threading

from udmi.core.factory import create_device_with_basic_auth
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration

# --- Config ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger(__name__)


def update_sensors_loop(pointset_manager):
    """
    Simulates a loop that reads hardware sensors and updates the PointsetManager.
    """
    LOGGER.info("Starting sensor simulation loop...")
    while True:
        # 1. Simulate reading data from hardware
        temp = round(random.uniform(20.0, 25.0), 2)
        pressure = round(random.uniform(1000, 1020), 1)

        # 2. Update the PointsetManager
        # The manager caches these values and publishes them periodically
        # based on the sample_rate_sec defined during initialization.
        pointset_manager.set_point_value("supply_temp", temp)
        pointset_manager.set_point_value("static_pressure", pressure)

        LOGGER.info(f"Sensors updated: Temp={temp}, Pressure={pressure}")

        time.sleep(2)


if __name__ == "__main__":
    try:
        # 1. Configure Connection
        endpoint = EndpointConfiguration(
            client_id=f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix="/r/ZZ-TRI-FECTA/d/"
        )

        # 2. Instantiate PointsetManager
        my_pointset_manager = PointsetManager(sample_rate_sec=5)
        managers = [SystemManager(), my_pointset_manager]

        # 3. Create Device
        device = create_device_with_basic_auth(
            endpoint_config=endpoint,
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD,
            managers=managers
        )

        # 4. Start Device in a background thread
        # This allows the main thread to run our sensor loop.
        device_thread = threading.Thread(target=device.run, daemon=True)
        device_thread.start()

        # 5. Run the main application logic
        update_sensors_loop(my_pointset_manager)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
