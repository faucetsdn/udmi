"""
Sample: Blobset Firmware Update (OTA)

This script demonstrates how to use the SystemManager's blobset capabilities
to handle Over-The-Air (OTA) updates or arbitrary file downloads.

Workflow:
1. The device registers a callback handler for a specific blob key (e.g., "firmware").
2. The operator publishes a Config message containing a 'blobset' block.
3. The SystemManager detects the new generation, fetches the data, verifies the SHA256 hash, and calls the handler.
4. The handler "applies" the update.
5. The SystemManager reports the status (Phase.apply -> Phase.final) in the state message.
"""

import base64
import datetime
import hashlib
import json
import logging
import sys
import time

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("FirmwareDemo")


def firmware_update_handler(blob_key: str, data: bytes):
    """
    This is the callback that **simulates** the actual firmware installation.
    The library has ALREADY verified the SHA256 hash before calling this.
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"⚡ FIRMWARE UPDATE RECEIVED for key: '{blob_key}'")
    LOGGER.info(f"📦 Size: {len(data)} bytes")

    try:
        content_str = data.decode('utf-8')
        LOGGER.info(f"📄 Content: {content_str}")
    except UnicodeDecodeError:
        LOGGER.info("📄 Content: <Binary Data>")

    LOGGER.info("⚙️ Simulating installation process... (2 seconds)")
    time.sleep(2)
    LOGGER.info("✅ FIRMWARE INSTALLED SUCCESSFULLY.")
    LOGGER.info("------------------------------------------------")


def generate_ota_trigger_payload(firmware_content: str) -> str:
    """
    Helper to generate a valid UDMI Config message with a blobset
    containing the firmware as a Data URI.
    """
    data_bytes = firmware_content.encode('utf-8')
    sha256_hash = hashlib.sha256(data_bytes).hexdigest()
    b64_data = base64.b64encode(data_bytes).decode('utf-8')
    data_uri = f"data:application/octet-stream;base64,{b64_data}"
    generation_id = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    payload = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "version": UDMI_VERSION,
        "blobset": {
            "blobs": {
                "firmware": {
                    "phase": "final",
                    "url": data_uri,
                    "sha256": sha256_hash,
                    "generation": generation_id
                }
            }
        }
    }
    return json.dumps(payload, indent=2)


if __name__ == "__main__":
    try:
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"
        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": TOPIC_PREFIX,
            "auth_provider": {
                "basic": {
                    "username": BROKER_USERNAME,
                    "password": BROKER_PASSWORD
                }
            }
        })
        device = create_device(endpoint_config)

        # --- REGISTER HANDLER ---
        sys_manager = device.get_manager(SystemManager)
        if not sys_manager:
            LOGGER.error(
                "SystemManager not found. Cannot register OTA handler.")
            sys.exit(1)
        sys_manager.register_blob_handler("firmware", firmware_update_handler)
        LOGGER.info("Registered 'firmware' blob handler.")

        trigger_json = generate_ota_trigger_payload(
            "FIRMWARE-V2.5-STABLE-BUILD-99")

        print("\n" + "=" * 80)
        print("🚀 OTA FIRMWARE DEMO INSTRUCTIONS:")
        print("1. The device is running.")
        print("2. To simulate an OTA update, publish this JSON to:")
        print(f"   Topic: {TOPIC_PREFIX}{DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print("The device will detect the new 'generation', download the blob,")
        print("verify the hash, and execute the handler.")
        print("=" * 80 + "\n")

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
