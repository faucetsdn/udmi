"""
Sample: Connect to Generic Broker (Basic Auth)

This script demonstrates how to connect a UDMI device to a standard MQTT broker
(like Mosquitto, HiveMQ, or RabbitMQ) using Username/Password authentication.

Use Cases:
1.  **Local Development**: Testing against a local Mosquitto container.
2.  **On-Premise**: Connecting to a local aggregator or gateway.
3.  **Non-GCP Clouds**: Connecting to platforms that use standard MQTT Auth.

Behavior:
- The presence of `auth_provider.basic` tells the library to use those credentials.
- It skips looking for or generating 'rsa_private.pem'.
- If Port is 1883, it connects via TCP.
- If Port is 8883, it connects via TLS (Server Auth only) + Basic Auth.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- Configuration ---
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

        # 1. Define Endpoint with Basic Auth
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

        # 2. Create Device
        # The factory detects 'basic' auth and configures the client accordingly.
        device = create_device(endpoint_config)

        # 3. Run
        logger.info("Starting main event loop (Ctrl+C to stop)...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        # Catch connection refused, auth errors, etc.
        logger.critical(f"Critical error during startup: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Device shut down gracefully.")
