"""
Sample: Basic Telemetry

This script demonstrates how to use the PointsetManager to report
telemetry data (sensor readings).

SCENARIO:
1.  **Acquisition Loop**: The main thread simulates reading hardware sensors
    every 2 seconds. It updates the `PointsetManager` immediately.
2.  **Reporting Loop**: The `PointsetManager` (background thread) is configured
    with a `sample_rate_sec` of 5 seconds.
3.  **Result**: You will see sensor updates logged every 2s, but MQTT publish
    logs only every 5s.

INSTRUCTIONS:
1.  Run the script.
2.  Observe the "Sensors updated" logs (frequency: 2s).
3.  Observe the "Publishing pointset" logs (frequency: 5s).
"""

import logging
import random
import sys
import threading
import time

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("TelemetrySample")


def update_sensors_loop(pointset_manager):
    """
    Simulates a loop that reads hardware sensors and updates the PointsetManager.
    """
    LOGGER.info("Starting sensor simulation loop (Interval: 2s)...")

    while True:
        # 1. Simulate reading data from hardware
        temp = round(random.uniform(20.0, 25.0), 2)
        pressure = round(random.uniform(1000, 1020), 1)

        # 2. Update the PointsetManager
        # NOTE: This does NOT trigger a network message immediately.
        # It updates the cached value. The manager's background thread
        # picks this up at the next 'sample_rate_sec' interval.
        pointset_manager.set_point_value("supply_temp", temp)
        pointset_manager.set_point_value("static_pressure", pressure)

        LOGGER.info(f"Sensors updated: Temp={temp}, Pressure={pressure}")

        time.sleep(2)


if __name__ == "__main__":
    try:
        # 1. Configure Connection
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"
        endpoint = EndpointConfiguration(
            client_id=client_id,
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

        # 2. Instantiate managers
        # We set the sample rate to 5 seconds.
        managers = get_default_managers(sample_rate_sec=5)

        # 3. Create Device
        device = create_device(endpoint, managers)

        # 4. Start Device in a background thread
        # This starts the PointsetManager's reporting loop.
        device_thread = threading.Thread(target=device.run, daemon=True)
        device_thread.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")
        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. Sensor Loop runs every 2 seconds (Data Acquisition).")
        print("2. Telemetry Loop runs every 5 seconds (Data Reporting).")
        print("   Notice that intermediate values are essentially downsampled.")
        print("=" * 60 + "\n")

        # 5. Run the main application logic
        update_sensors_loop(device.get_manager(PointsetManager))

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
