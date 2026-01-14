"""
Sample: Large File Firmware Update (Streaming to Disk)

This script demonstrates handling large blob updates that exceed available RAM.
Instead of receiving the raw bytes in memory, the application receives a
FILE PATH to the verified blob on disk.

KEY DIFFERENCE:
- bytes_sample: `register_blob_handler(..., expects_file=False)` -> Callback gets `bytes`.
- stream_sample: `register_blob_handler(..., expects_file=True)` -> Callback gets `str` (path).

SCENARIO:
1.  Device registers a handler with `expects_file=True`.
2.  Cloud sends a config pointing to a large file (simulated via Data URI).
3.  Library streams the download to a temp file, verifying the hash on the fly.
4.  Library calls the handler with the path: `/tmp/blob_xyz`.
5.  Handler "installs" from the file (e.g., by reading chunks).
6.  Library automatically deletes the temp file after the handler returns.
"""

import base64
import datetime
import hashlib
import json
import logging
import os
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
LOGGER = logging.getLogger("FirmwareStreamDemo")


def large_firmware_handler(blob_key: str, file_path: str):
    """
    Callback for processing a large blob from disk.

    Args:
        blob_key: The config key (e.g. "firmware").
        file_path: The absolute path to the verified file on disk.
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"LARGE UPDATE RECEIVED for key: '{blob_key}'")
    LOGGER.info(f"File Path: {file_path}")

    # Check file size to demonstrate it exists on disk
    try:
        size = os.path.getsize(file_path)
        LOGGER.info(f"File Size on Disk: {size} bytes")

        # Simulate processing a large file line-by-line (streaming read)
        LOGGER.info("verifying header (simulated streaming read)...")
        with open(file_path, 'r') as f:
            header = f.read(50)  # Read just the first 50 chars
            LOGGER.info(f"   Header: {header}...")

    except OSError as e:
        LOGGER.error(f"Failed to access file: {e}")
        return

    LOGGER.info("Simulating installation from disk... (3 seconds)")
    time.sleep(3)
    LOGGER.info("FIRMWARE INSTALLED SUCCESSFULLY.")
    LOGGER.info("------------------------------------------------")


def generate_large_payload() -> str:
    """
    Generates a Config with a larger payload.
    """
    # Create a dummy "large" content (e.g. ~10KB for this demo)
    # In a real scenario, the URL would point to a multi-MB file on a server.
    content = "FIRMWARE_BINARY_DATA_" * 500
    data_bytes = content.encode('utf-8')
    sha256_hash = hashlib.sha256(data_bytes).hexdigest()
    b64_data = base64.b64encode(data_bytes).decode('utf-8')
    data_uri = f"data:application/octet-stream;base64,{b64_data}"

    generation_id = datetime.datetime.now().strftime("%Y%m%d%H%M%S")

    payload = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "version": UDMI_VERSION,
        "blobset": {
            "blobs": {
                "firmware_large": {
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

        # --- REGISTER HANDLER (Streaming Mode) ---
        sys_manager = device.get_manager(SystemManager)
        if not sys_manager:
            LOGGER.error("SystemManager not found.")
            sys.exit(1)

        # CRITICAL: expects_file=True tells the library to stream to disk
        sys_manager.register_blob_handler(
            "firmware_large",
            large_firmware_handler,
            expects_file=True
        )
        LOGGER.info("Registered 'firmware_large' blob handler (File Mode).")

        trigger_json = generate_large_payload()

        print("\n" + "=" * 80)
        print("STREAMING FIRMWARE DEMO INSTRUCTIONS:")
        print("1. The device is running.")
        print("2. Publish this JSON to:")
        print(f"   Topic: {TOPIC_PREFIX}{DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{trigger_json}'")
        print("=" * 80 + "\n")

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
