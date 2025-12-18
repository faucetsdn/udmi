"""
Example script for connecting a UDMI device to a generic MQTT broker
using mTLS (mutual Transport Layer Security) for authentication.

This script demonstrates how to use the `create_mqtt_device_instance` factory
and configure it with the necessary client-side certificates (mTLS).
This is a common and secure method for authenticating with local or private
MQTT brokers that are configured to require client certificates.
"""

import logging
import os
import sys

from udmi.core.factory import ClientConfig
from udmi.core.factory import create_device
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
# CONFIGURE THESE VALUES for your local or custom MQTT broker
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 8883  # Use the mTLS secure port (8883 is standard)

# --- mTLS Certificate Configuration ---
# Point this to the directory containing your certificates
CERT_DIR = "/path/to/certs"

# The Certificate Authority (CA) that signed the broker's and client's certs
CA_CERT_FILE = os.path.join(CERT_DIR, "ca.crt")

# The client's public certificate
CLIENT_CERT_FILE = os.path.join(CERT_DIR, "client.crt")

# The client's private key
CLIENT_KEY_FILE = os.path.join(CERT_DIR, "client.key")

if __name__ == "__main__":
    # Set up basic logging to see device activity in the console
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    # Define a custom topic prefix
    topic_prefix = "/r/ZZ-TRI-FECTA/d/"
    client_id = f"{topic_prefix}{DEVICE_ID}"

    try:
        # 1. Create the UDMI EndpointConfiguration object
        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": topic_prefix,
        })

        logging.info("Creating mTLS device instance using the factory...")

        # 2. Use the base `create_mqtt_device_instance` factory.
        # We pass `auth_provider=None` because authentication is handled
        # by the TLS layer (mTLS), not by sending a username/password.
        device = create_device(
            endpoint_config=endpoint_config,
            client_config=ClientConfig(
                tls_config=TlsConfig(
                    ca_certs=CA_CERT_FILE,
                    cert_file=CLIENT_CERT_FILE,
                    key_file=CLIENT_KEY_FILE,
                )
            ),
        )

        # 3. Start the device's main loop.
        device.run()
    except FileNotFoundError as e:
        # Add a specific error for this common configuration issue
        logging.error(f"Critical Error: A certificate file was not found.")
        logging.error(f"Please check your certificate paths: {e}")
        sys.exit(1)
    except Exception as e:
        # Catch-all for other critical errors (e.g., connection, SSL handshake)
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
