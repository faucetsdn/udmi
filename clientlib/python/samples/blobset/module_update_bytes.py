"""
Sample: Blobset Module Update (OTA)

This script demonstrates how to use the SystemManager's blobset capabilities
to handle Over-The-Air (OTA) updates or arbitrary file downloads.

SCENARIO:
1.  **Callback**: The device registers a handler for the blob key "udmi_module".
2.  **Trigger**: The operator publishes a Config message containing a 'blobset'.
    This includes a Data URI (Base64 module) and its SHA256 hash.
3.  **Library Logic**:
    a.  Detects the new `generation` ID.
    b.  Updates State: `phase` -> `apply`.
    c.  Downloads the blob content.
    d.  Verifies the SHA256 hash matches the config.
    e.  Invokes the registered `udmi_module_update_handler` with the valid data.
    f.  Updates State: `phase` -> `final`.
4.  **Application Logic**: The handler simulates "installing" the module.

USAGE:
1.  Run the script.
2.  Copy the `mosquitto_pub` command printed to the console.
3.  Run the command to trigger the update.
4.  Watch the logs for the download, verification, and installation steps.
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
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("ModuleUpdateDemo")


def udmi_module_update_handler(blob_key: str, data: bytes):
    """
    Callback for processing the verified blob data.

    The library GUARANTEES that 'data' matches the SHA256 hash in the config
    before calling this function.
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"MODULE UPDATE RECEIVED for key: '{blob_key}'")
    LOGGER.info(f"Size: {len(data)} bytes")

    try:
        content_str = data.decode('utf-8')
        LOGGER.info(f"Content: {content_str}")
    except UnicodeDecodeError:
        LOGGER.info("Content: <Binary Data>")

    LOGGER.info("Simulating installation process... (2 seconds)")
    time.sleep(2)
    LOGGER.info("MODULE INSTALLED SUCCESSFULLY.")
    LOGGER.info("------------------------------------------------")


def generate_ota_trigger_payload(module_content: str) -> str:
    """
    Helper to generate a valid UDMI Config message with a blobset
    containing the module as a Data URI.
    """
    data_bytes = module_content.encode('utf-8')
    sha256_hash = hashlib.sha256(data_bytes).hexdigest()
    b64_data = base64.b64encode(data_bytes).decode('utf-8')
    data_uri = f"data:application/octet-stream;base64,{b64_data}"

    generation_id = datetime.datetime.now().strftime("%Y%m%d%H%M%S")

    payload = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "version": UDMI_VERSION,
        "blobset": {
            "blobs": {
                "udmi_module": {
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
        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=TOPIC_PREFIX,
            auth_provider=AuthProvider(
                basic=Basic(
                    username=BROKER_USERNAME,
                    password=BROKER_PASSWORD
                )
            )
        )
        device = create_device(endpoint_config)

        # --- REGISTER HANDLER ---
        sys_manager = device.get_manager(SystemManager)
        if not sys_manager:
            LOGGER.error(
                "SystemManager not found. Cannot register OTA handler.")
            sys.exit(1)

        sys_manager.register_blob_handler("udmi_module",
                                          udmi_module_update_handler)
        LOGGER.info("Registered 'udmi_module' blob handler.")

        trigger_json = generate_ota_trigger_payload(
            "UDMI_MODULE-V2.5-STABLE-BUILD-99")

        print("\n" + "=" * 80)
        print("OTA MODULE DEMO INSTRUCTIONS:")
        print("1. The device is running.")
        print("2. To simulate an OTA update, publish this JSON to:")
        print(f"   Topic: {TOPIC_PREFIX}{DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print("Copy/Paste for Mosquitto:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{trigger_json}'")
        print("=" * 80 + "\n")

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
