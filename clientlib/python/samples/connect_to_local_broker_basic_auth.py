"""
Example script for connecting a UDMI device to a generic MQTT broker
using username and password authentication.

This script demonstrates how to use the `create_device_with_basic_auth` factory
to instantiate a device. This is useful for testing with local MQTT brokers
like Mosquitto or for connecting to non-GCP platforms that use basic auth.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
# CONFIGURE THESE VALUES for your local or custom MQTT broker
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"  # e.g., "localhost" or "test.mosquitto.org"
MQTT_PORT = 1883  # 1883 is the standard non-secure MQTT port

# Credentials for the MQTT broker
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

if __name__ == "__main__":
    # Set up basic logging to see device activity in the console
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        # 1. Create the UDMI EndpointConfiguration object
        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": topic_prefix,
            "auth_provider": {
                "basic": {
                    "username": BROKER_USERNAME,
                    "password": BROKER_PASSWORD
                }
            }
        })

        logging.info("Creating device instance using the basic auth factory...")

        # 2. Use the factory to create the device instance.
        device = create_device(endpoint_config)

        # 3. Start the device's main loop.
        device.run()

    except Exception as e:
        # Catch-all for critical errors (e.g., connection refused)
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
