"""
Example script for connecting a UDMI device to Cloud IoT Core.

This script demonstrates how to use the `create_device_with_jwt` factory
to instantiate a device, configure it with the necessary GCP IoT Core
credentials (project, registry, device), and run its main loop.

The device will authenticate using a JWT generated from an RS256 private key.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
# CONFIGURE THESE VALUES to match your GCP IoT Core setup
PROJECT_ID = "gcp-project-id"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.bos.goog"
MQTT_PORT = 8883  # secure port, mqtt client will automatically use TLS
ALGORITHM = "RS256"  # Algorithm for signing the JWT
PRIVATE_KEY_FILE = "/path/to/ZZ-TRI-FECTA/devices/AHU-1/rsa_private.pem"

# The full client ID string required by Cloud IoT Core's MQTT bridge
CLIENT_ID = (
    f"projects/{PROJECT_ID}/locations/{REGION}/"
    f"registries/{REGISTRY_ID}/devices/{DEVICE_ID}"
)

if __name__ == "__main__":
    # Set up basic logging to see device activity in the console
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    # Main execution block with error handling
    try:
        # 1. Create the UDMI EndpointConfiguration object
        # This tells the client where to connect
        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": CLIENT_ID,
            "hostname": MQTT_HOST,
            "port": MQTT_PORT,
            "auth_provider": {
                "jwt": {
                    "audience": PROJECT_ID,
                }
            }
        })

        # 2. Use the factory to create the device instance.
        device = create_device(endpoint_config, key_file=PRIVATE_KEY_FILE)
        device.run()

    except Exception as e:
        # Catch-all for other critical errors (e.g., network, auth failures)
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
