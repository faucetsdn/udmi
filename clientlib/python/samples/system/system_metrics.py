"""
Sample: System Metrics Reporting

This script demonstrates the built-in system health monitoring.

The SystemManager automatically:
1.  Samples CPU and Memory usage (requires `pip install psutil`).
2.  Publishes a `SystemEvent` to the 'events/system' topic.
3.  Repeats this at the configured `metrics_rate_sec`.

INSTRUCTIONS:
1.  Ensure psutil is installed: `pip install psutil`
2.  Run the script.
3.  Subscribe to the system events topic using the command below.
4.  Observe the JSON payloads containing 'metrics'.
"""

import logging
import sys

# Check for psutil explicitly to give a helpful error in this sample
try:
    import psutil
except ImportError:
    print("ERROR: 'psutil' module is required for this sample.")
    print("Please run: pip install psutil")
    sys.exit(1)

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
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
TOPIC_PREFIX = f"/r/{REGISTRY_ID}/d/"

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
    logger = logging.getLogger("MetricsSample")

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

        device = create_device(endpoint)
        sys_manager = device.get_manager(SystemManager)

        if sys_manager:
            # In a real scenario, this is set via the Cloud Config message:
            # { "system": { "metrics_rate_sec": 5 } }
            # Here we manually override the default (60s) to 5s for the demo.
            logger.info("Overriding metrics rate to 5 seconds...")
            sys_manager._metrics_rate_sec = 5

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. The device will publish system metrics every 5 seconds.")
        print("2. To view the data, run this command:")
        print("-" * 20)
        print(f"mosquitto_sub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/events/system'")
        print("=" * 60 + "\n")

        logger.info("Starting device...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
