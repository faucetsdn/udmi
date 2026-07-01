"""
Sample: Preconfigured Points

This script demonstrates how to register and configure points locally at startup 
using explicit parameters (writable, ref, units, baseline_value).

SCENARIO:
1.  **Preconfiguration**: On startup, we register three points with their physical BACnet reference paths:
    - 'room_temp': Analog Input (units: Celsius, ref: BACnet/AI:1)
    - 'fan_speed': Analog Output (units: Percent, ref: BACnet/AO:2, writable: True, baseline_value: 50.0)
    - 'filter_status': Binary Input (units: boolean, ref: BACnet/BI:3)
2.  **Writeback Actuation**: Since 'fan_speed' is explicitly preconfigured as writable, 
    it is eligible for writebacks immediately. Writebacks to non-writable points will be rejected automatically.
3.  **Telemetry**: Telemetry is published periodically showing the readings.
"""

import logging
import random
import sys
import threading
import time
from typing import Optional

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager, PointProperties
from udmi.schema import AuthProvider, Basic, EndpointConfiguration

# --- Connection Configuration ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("PreconfiguredPointsSample")


def run_sensor_simulation(pointset_manager: PointsetManager):
    """
    Simulates the physical controller reading raw hardware inputs and 
    updating the PointsetManager.
    """
    LOGGER.info("Starting raw hardware sensor acquisition loop...")
    while True:
        # Simulate readings based on physical points
        room_temp = round(random.uniform(21.0, 24.0), 2)
        filter_dirty = random.choice([True, False])

        # Update the manager's cache
        pointset_manager.set_point_value("room_temp", room_temp)
        pointset_manager.set_point_value("filter_status", filter_dirty)

        LOGGER.info(
            f"[DAQ Read] Temp: {room_temp}°C, Filter Dirty: {filter_dirty}")
        time.sleep(5)


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

        # 2. Initialize Managers
        managers = get_default_managers(sample_rate_sec=10)
        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)

        # 3. Declare and Preconfigure Points Locally in Bulk
        # We configure physical refs, units, and writable state directly.
        LOGGER.info("Preconfiguring physical points locally on startup...")

        # Define raw dictionaries conforming to the PointProperties TypedDict
        points_definition: dict[str, PointProperties] = {
            "room_temp": {
                "writable": False,
                "ref": "BACnet/AI:1",
                "units": "Celsius"
            },
            "fan_speed": {
                "writable": True,
                "ref": "BACnet/AO:2",
                "units": "Percent",
                "baseline_value": 50.0
            },
            "filter_status": {
                "writable": False,
                "ref": "BACnet/BI:3"
            }
        }

        pointset_manager.add_points(points_definition)

        # 4. Start the Device Connection Loop in the background
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(
            f"Device {DEVICE_ID} is running with locally preconfigured points.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print(
            "Publish JSON configs using mosquitto_pub to actuate the fan speed:")
        print("-" * 20)
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m \\")
        print(
            "  '{ \"pointset\": { \"points\": { \"fan_speed\": { \"set_value\": 75.0 } } } }'")
        print("-" * 20)
        print("The writable fan_speed point accepts and applies the writeback.")
        print(
            "Any writebacks sent to 'room_temp' or 'filter_status' are automatically rejected as non-writable.")
        print("=" * 60 + "\n")

        # 5. Start Data Acquisition Loop
        run_sensor_simulation(pointset_manager)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
