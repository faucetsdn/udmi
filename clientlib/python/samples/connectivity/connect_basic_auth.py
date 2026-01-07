"""
Sample: Connect to Generic Broker (Basic Auth)

This script demonstrates how to connect a UDMI device to a standard MQTT broker
(like Mosquitto, HiveMQ, or RabbitMQ) using Username/Password authentication.

This is the primary method used for:
1. Local Development (testing against a local Mosquitto container).
2. On-Premise Deployments (connecting to a local aggregator/gateway).
3. Non-GCP Cloud Platforms that utilize Basic Auth.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("BasicAuthConnect")

    try:
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        # Create the UDMI EndpointConfiguration object - we explicitly define the 'auth_provider' block with 'basic' credentials.
        endpoint_config = EndpointConfiguration(
            client_id=client_id,
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

        logger.info(
            f"Initializing device {DEVICE_ID} connecting to {MQTT_HOSTNAME}:{MQTT_PORT}...")

        device = create_device(endpoint_config)

        logger.info("Starting main event loop (Ctrl+C to stop)...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        # Catch connection refused, auth errors, etc.
        logger.critical(f"Critical error during startup: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Device shut down gracefully.")
