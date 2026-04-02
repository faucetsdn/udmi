"""
Sample: Telemetry Poll Callback

This script demonstrates the "Just-in-Time" (Pull) data fetching pattern.

Instead of your application pushing data to the manager in a loop,
you register a callback function. The PointsetManager calls this function
immediately before it generates the telemetry message.

SCENARIO:
1.  **Setup**: We register `my_sensor_poll` as the poll callback.
2.  **Trigger**: Every `sample_rate_sec` (5s), the PointsetManager wakes up.
3.  **Execution**: It invokes `my_sensor_poll()`.
4.  **Reporting**: The returned dictionary is merged into the pointset and published.

INSTRUCTIONS:
1.  Run the script.
2.  Observe "Library requested data poll..." logs appearing every 5 seconds.
3.  See the corresponding "Publishing pointset" message immediately after.
"""

import logging
import random
import sys
from typing import Any
from typing import Dict

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import PointsetManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- Config ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger(__name__)


def my_sensor_poll() -> Dict[str, Any]:
    """
    Called by the PointsetManager background thread.

    This function should perform the actual hardware reads (e.g. I2C, Modbus).
    It must return a dictionary of {point_name: value}.
    """
    # Simulate reading hardware registers
    temp = round(random.uniform(20.0, 25.0), 2)
    pressure = round(random.uniform(1000, 1020), 1)

    LOGGER.info("Library requested data poll...")
    LOGGER.info(f"   Read Hardware: Temp={temp}, Pressure={pressure}")

    return {
        "supply_temp": temp,
        "static_pressure": pressure
    }


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

        # 4. Register the Poll Callback
        # This tells the manager to call our function before every publish.
        pointset_manager = device.get_manager(PointsetManager)
        pointset_manager.set_poll_callback(my_sensor_poll)

        LOGGER.info(f"Device {DEVICE_ID} initialized.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("The device is running. Watch the logs.")
        print("You will see the library 'pulling' data every 5 seconds.")
        print("=" * 60 + "\n")

        # 5. Run Device
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
