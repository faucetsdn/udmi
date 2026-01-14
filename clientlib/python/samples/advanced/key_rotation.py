"""
Sample: Command-Driven Key Rotation for ClearBlade / Cloud IoT Core.

This script demonstrates the "Zero-Downtime" Key Rotation capability.

SCENARIO:
1.  **Connect**: The device connects using its initial private key.
2.  **Command**: The user sends a `rotate_key` command to the device.
3.  **Rotation Logic (Library)**:
    a.  Backs up the old key to `rsa_private.pem.<timestamp>.bak`.
    b.  Generates a NEW RSA key pair and overwrites `rsa_private.pem`.
    c.  Calls the registered `on_rotate_key` callback with the NEW Public Key.
4.  **Cloud Update (Application)**:
    a.  The callback prints the new public key.
    b.  It waits for the user (or an API script) to register this key with the Cloud.
    c.  It returns `True` to signal success.
5.  **Reconnect (Library)**:
    a.  The library disconnects the MQTT client.
    b.  It generates a new JWT using the NEW private key.
    c.  It reconnects to the cloud.

USAGE:
1.  Run the script.
2.  Send a command to the device topic: `/devices/{DEVICE_ID}/commands/rotate_key`
    (Payload can be empty JSON: `{}`).
3.  Follow the instructions printed in the console to update the cloud key.
"""

import logging
import sys
import time

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider
from udmi.schema import EndpointConfiguration
from udmi.schema import Jwt

# --- Configuration Constants ---
# ! UPDATE THESE TO MATCH YOUR REAL GCP RESOURCES !
PROJECT_ID = "your-gcp-project"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.googleapis.com"
MQTT_PORT = 8883

# Path to the private key.
KEY_FILE = "rsa_private.pem"

# Construct Client ID
CLIENT_ID = (
    f"projects/{PROJECT_ID}/locations/{REGION}/"
    f"registries/{REGISTRY_ID}/devices/{DEVICE_ID}"
)

# Setup Logging
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("RotationSample")


# --- Key Rotation Callback ---

def on_rotate_key(new_public_key_pem: str, backup_identifier: str) -> bool:
    """
    Callback invoked by SystemManager AFTER generating a new key but BEFORE reconnecting.

    Args:
        new_public_key_pem: The PEM-encoded string of the NEW public key.
        backup_identifier: The path/ID of the backup for the OLD key.

    Returns:
        True if the cloud update was successful (proceed to reconnect).
        False if the update failed (trigger rollback to old key).
    """
    print("\n" + "=" * 80)
    print(" [ACTION REQUIRED] KEY ROTATION STARTED ")
    print("=" * 80)
    print(f"1. A new key pair has been generated and saved to disk.")
    print(f"2. The OLD key has been backed up to: {backup_identifier}")
    print(f"3. COPY the NEW Public Key below and add it to the Cloud Console:")
    print("-" * 80)
    print(new_public_key_pem)
    print("-" * 80)

    # Simulate waiting for API upload or manual entry
    # In a fully automated production app, you would make a REST API call here
    # to your cloud provider (e.g. Google Cloud Asset Inventory or IoT Core Admin API)
    # to register the new key.
    print(">>> Waiting 30 seconds for you to update the Cloud Console...")
    print(">>> (If you don't update the cloud, the reconnection will fail!)")

    try:
        for i in range(30, 0, -5):
            print(f"Resuming in {i} seconds...")
            time.sleep(5)
    except KeyboardInterrupt:
        print("Aborting...")
        return False

    print("Resuming... Assuming Cloud is updated.")

    # Return True to signal that the upload "succeeded" and we should proceed to reconnect.
    return True


if __name__ == "__main__":
    try:
        # 1. Endpoint Configuration
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

        # 2. Create the Device
        # The factory will initialize the CredentialManager (loading 'rsa_private.pem')
        device = create_device(endpoint_config, key_file=KEY_FILE)

        # 3. Register the Rotation Callback
        system_manager = device.get_manager(SystemManager)

        if system_manager:
            LOGGER.info("Registering Key Rotation Handler...")
            system_manager.register_key_rotation_callback(on_rotate_key)
        else:
            LOGGER.error("SystemManager not found! Rotation will not work.")
            sys.exit(1)

        LOGGER.info(
            f"Device initialized. Listening for 'rotate_key' command on {DEVICE_ID}...")

        # 4. Run the Device
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
