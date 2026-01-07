"""
Sample: Connect to Google Cloud IoT Core (JWT Auth)

This script demonstrates how to connect to Google Cloud IoT Core using
RS256 JWT (JSON Web Token) authentication.

Key Concepts:
1.  **JWT Generation**: The library automatically loads the RSA Private Key
    and signs a JWT with the required claims (aud, iat, exp).
2.  **Key Management**: The factory looks for 'rsa_private.pem'.
    - If found: It uses it to sign the JWT.
    - If missing: It GENERATES a new key pair.
3.  **Prerequisite**: The corresponding **Public Key** must be registered
    in the Cloud IoT Core registry for this device. If the library generates
    a new key, you must upload the new public key to the GCP Console before
    connecting.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import AuthProvider
from udmi.schema import EndpointConfiguration
from udmi.schema import Jwt

PROJECT_ID = "bos-platform-dev"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.bos.goog"
MQTT_PORT = 8883
PRIVATE_KEY_FILE = "/usr/local/google/home/heykhyati/Projects/udmi/sites/udmi_site_model/devices/AHU-1/rsa_private.pem"
CLIENT_ID = (
    f"projects/{PROJECT_ID}/locations/{REGION}/"
    f"registries/{REGISTRY_ID}/devices/{DEVICE_ID}"
)

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("IotCoreConnect")

    try:
        # Create the UDMI EndpointConfiguration object - we explicitly define the 'auth_provider' block with 'jwt' audience.
        endpoint_config = EndpointConfiguration(
            client_id=CLIENT_ID,
            hostname=MQTT_HOST,
            port=MQTT_PORT,
            auth_provider=AuthProvider(
                jwt=Jwt(
                    audience=PROJECT_ID
                )
            )
        )

        logger.info(
            f"Initializing device {DEVICE_ID} for project {PROJECT_ID}...")
        device = create_device(endpoint_config, key_file=PRIVATE_KEY_FILE)
        logger.info("Connecting to Cloud IoT Core...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Connection failed: {e}", exc_info=True)
        logger.warning(
            "\nTIP: If you see 'Connection Refused' or 'Not Authorized':\n"
            "1. Check if 'rsa_private.pem' matches the Public Key in GCP Console.\n"
            "2. Verify PROJECT_ID, REGION, and REGISTRY_ID are correct.\n"
        )
        sys.exit(1)

    logger.info("Device shut down gracefully.")
