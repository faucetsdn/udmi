"""
Sample: COV (Change of Value) Throttling

This script demonstrates how to reduce data traffic using `cov_increment`.

SCENARIO:
1.  **High-Frequency Sensor**: The script simulates a sensor ('ramp_sensor')
    that increases by 1.0 every second.
2.  **Initial Behavior**: The device has a `sample_rate_sec` of 1. Since the
    value changes every second, it reports *every* second.
3.  **Config Update**: You send a config setting `cov_increment` to 5.0.
4.  **Throttled Behavior**: The device still samples every second, but now it
    only *publishes* when the value has changed by at least 5.0 (every ~5 seconds).

INSTRUCTIONS:
1.  Run the script.
2.  Observe the "Publishing pointset" logs appearing every second.
3.  Publish the JSON config payload printed below.
4.  Observe the logs slow down (updates appear every ~5 seconds).
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

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

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

        # We update the manager every second.
        # The manager decides whether to publish based on COV logic.
        manager.set_point_value("ramp_sensor", val)

        LOGGER.debug(f"Internal Sensor Read: {val} (Manager updated)")


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

        # Initialize with fast sampling (1s) to demonstrate the issue
        managers = get_default_managers(sample_rate_sec=1)
        device = create_device(endpoint, managers)

        pointset_manager = device.get_manager(PointsetManager)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        sim = threading.Thread(target=simulate_fast_sensor,
                               args=(pointset_manager,), daemon=True)
        sim.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. Observe the rapid telemetry logs (every 1s).")
        print("2. To apply COV throttling, publish this config:")
        print("-" * 20)
        json_payload = ('{ "pointset": { "points": { "ramp_sensor": { "cov_increment": 5.0 } }, '
                        '"sample_rate_sec": 1 } }')
        print(json_payload)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{json_payload}'")
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
