"""
Sample: Command-Driven Key Rotation for ClearBlade / Cloud IoT Core.

This script demonstrates the "Zero-Downtime" Key Rotation capability.
1. Connects to the cloud using the initial key.
2. Registers a callback to handle 'rotate_key' commands.
3. When the command is received:
   a. Backs up the old key.
   b. Generates a NEW key pair.
   c. Pauses to allow you to upload the NEW Public Key to the Cloud Console.
   d. Automatically reconnects using the new credentials.

Usage:
  1. Run this script.
  2. Send a command to the device topic: /devices/{DEVICE_ID}/commands/rotate_key
     (Payload can be empty JSON: {})
"""

import logging
import sys
import time
import threading

# Import the SystemManager to register our callback
from udmi.core.managers import SystemManager
from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
PROJECT_ID = "bos-platform-dev"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.bos.goog"
MQTT_PORT = 8883
PRIVATE_KEY_FILE = "/usr/local/google/home/heykhyati/Projects/udmi/sites/udmi_site_model/devices/AHU-1/rsa_private.pem"

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

def on_rotate_key(new_public_key_pem: str, backup_path: str) -> bool:
    """
    This function is called by the SystemManager AFTER the new key is generated
    but BEFORE the device reconnects.

    In a fully automated system, this function would use the ClearBlade REST API
    to upload 'new_public_key_pem' to the registry.

    Here, we simulate that by asking the user to do it manually.
    """
    print("\n" + "=" * 80)
    print(" [ACTION REQUIRED] KEY ROTATION STARTED ")
    print("=" * 80)
    print(f"1. A new key pair has been generated and saved to disk.")
    print(f"2. The OLD key has been backed up to: {backup_path}")
    print(f"3. COPY the NEW Public Key below and add it to the Cloud Console:")
    print("-" * 80)
    print(new_public_key_pem)
    print("-" * 80)

    # Simulate waiting for API upload or manual entry
    # We use a loop here to 'block' the rotation flow until you are ready.
    # In production, this would be an API call like: client.add_device_key(DEVICE_ID, new_public_key_pem)
    print(">>> Waiting 30 seconds for you to update the Cloud Console...")
    print(
        ">>> (If you don't update the cloud, the reconnection will fail and revert!)")

    for i in range(30, 0, -5):
        print(f"Resuming in {i} seconds...")
        time.sleep(5)

    print("Resuming... Assuming Cloud is updated.")

    # Return True to signal that the upload "succeeded" and we should proceed to reconnect.
    # Return False to trigger a Rollback to the old key.
    return True


if __name__ == "__main__":
    try:
        # 1. Endpoint Configuration
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

        # 2. Create the Device
        # The factory will initialize the CredentialManager (loading 'rsa_private.pem')
        device = create_device(endpoint_config, key_file=PRIVATE_KEY_FILE)

        # 3. Register the Rotation Callback
        # We need to access the SystemManager instance inside the device.
        # The factory creates default managers, so we look for SystemManager in the list.
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