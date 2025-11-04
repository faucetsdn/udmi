"""
Example script for connecting a UDMI device to a generic MQTT broker
using username and password authentication.

This script demonstrates how to use the `create_device_with_basic_auth` factory
to instantiate a device. This is useful for testing with local MQTT brokers
like Mosquitto or for connecting to non-GCP platforms that use basic auth.
"""

import logging
import sys

from udmi.core.factory import create_device_with_basic_auth
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
# CONFIGURE THESE VALUES for your local or custom MQTT broker
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"  # e.g., "localhost" or "test.mosquitto.org"
MQTT_PORT = 1883  # 1883 is the standard non-secure MQTT port

# Credentials for the MQTT broker
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecurepassword"

if __name__ == "__main__":
    # Set up basic logging to see device activity in the console
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        # Define a custom topic prefix. For Iot Core, this is locked, but for
        # other brokers, you can define your own.
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"

        # The client_id is often used by the broker for identifying the
        # connection. Here we make it the same as the full device topic path.
        client_id = f"{topic_prefix}{DEVICE_ID}"

        # 1. Create the UDMI EndpointConfiguration object
        # This tells the client where to connect and what topic prefix
        # to use when building full MQTT topics (e.g., for 'state').
        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix  # Pass the custom prefix
        )

        logging.info("Creating device instance using the basic auth factory...")

        # 2. Use the factory to create the device instance.
        # This factory wires up:
        # - MqttMessagingClient: Handles the Paho MQTT connection
        # - BasicAuthProvider: Stores the static username/password
        # - All other standard device components
        device = create_device_with_basic_auth(
            endpoint_config=endpoint_config,
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD
        )

        # 3. Start the device's main loop.
        # This will connect, authenticate, subscribe, and publish.
        device.run()
    except Exception as e:
        # Catch-all for critical errors (e.g., connection refused)
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
