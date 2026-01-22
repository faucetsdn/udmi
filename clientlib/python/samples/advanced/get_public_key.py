"""
Example script for retrieving the Public Key and connecting to Cloud IoT Core.

This script demonstrates the "Just-in-Time" provisioning workflow:
1.  The factory initializes the device.
2.  If the private key is missing, the library **GENERATES** a new RSA key pair.
3.  The script extracts the Public Key from the internal CredentialManager.
4.  It prints the Public Key so you can register it with the Cloud.
5.  It enters the main loop, attempting to connect (which will succeed once you register the key).
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import AuthProvider
from udmi.schema import EndpointConfiguration
from udmi.schema import Jwt

# --- Configuration Constants ---
# ! UPDATE THESE VALUES to match your GCP IoT Core setup !
PROJECT_ID = "your-project-id"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.googleapis.com"
MQTT_PORT = 8883
ALGORITHM = "RS256"

# Path to the private key.
# NOTE: If this file does not exist, the library will GENERATE it automatically.
PRIVATE_KEY_FILE = "rsa_private.pem"

# The full client ID string required by Cloud IoT Core's MQTT bridge
CLIENT_ID = (
    f"projects/{PROJECT_ID}/locations/{REGION}/"
    f"registries/{REGISTRY_ID}/devices/{DEVICE_ID}"
)

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        # 1. Create the UDMI EndpointConfiguration object
        endpoint_config = EndpointConfiguration(
            client_id=CLIENT_ID,
            hostname=MQTT_HOST,
            port=MQTT_PORT,
            auth_provider=AuthProvider(
                jwt=Jwt(
                    audience=PROJECT_ID,
                )
            )
        )

        logging.info("Initializing UDMI Device...")

        # 2. Use the factory to create the device instance.
        # This will initialize the CredentialManager and generate the key file if missing.
        device = create_device(endpoint_config, key_file=PRIVATE_KEY_FILE)

        # 3. Retrieve and Print the Public Key
        if device.credential_manager:
            try:
                public_key_pem = device.credential_manager.get_public_key_pem()

                print("\n" + "=" * 60)
                print(f"DEVICE PUBLIC KEY ({ALGORITHM})")
                print(f"Source File: {PRIVATE_KEY_FILE}")
                print("-" * 60 + "\n")
                print(public_key_pem)
                print("=" * 60)
                print("ACTION REQUIRED:")
                print("1. Copy the key block above.")
                print(f"2. Go to the GCP Console -> IoT Core -> Registry {REGISTRY_ID} -> Device {DEVICE_ID}")
                print("3. Click 'Authentication' -> 'Add Public Key'.")
                print("4. Paste the key and click Save.")
                print("5. The device below will connect automatically once saved.")
                print("=" * 60 + "\n")

            except Exception as e:
                logging.error(f"Failed to extract public key: {e}")
                sys.exit(1)
        else:
            logging.warning(
                "CredentialManager not available (Device might be using Basic Auth).")

        # 4. Start the main loop
        # The device will likely fail to connect initially (RC=5 Not Authorized)
        # until you perform the steps above. It will retry automatically.
        logging.info("Starting device connection loop...")
        device.run()

    except KeyboardInterrupt:
        logging.info("Stopped by user.")
    except Exception as e:
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
