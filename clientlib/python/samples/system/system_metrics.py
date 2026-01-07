"""
Sample: System Metrics Reporting

This script demonstrates the built-in system health monitoring.

The SystemManager automatically:
1.  Samples CPU and Memory usage (using `psutil`).
2.  Publishes a `SystemEvent` to the 'events/system' topic.
3.  Repeats this at the configured `metrics_rate_sec`.

Instructions:
1.  Run the script.
2.  Observe the logs showing "Published metrics: Total=... Free=..."
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
    logger = logging.getLogger("MetricsSample")

    try:
        endpoint = EndpointConfiguration(
            client_id=f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix="/r/ZZ-TRI-FECTA/d/",
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
            logger.info("Setting metrics rate to 5 seconds...")
            sys_manager._metrics_rate_sec = 5

        logger.info("Starting device. Watch for 'events/system' messages...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
