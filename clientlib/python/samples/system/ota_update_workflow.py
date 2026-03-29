"""
Sample: UDMI Module OTA Update Workflow

This script demonstrates the secure two-stage pipeline for self-updates.

SCENARIO:
1.  **Trigger**: Cloud sends a blobset config for key `ota_module_loader`.
2.  **Stage 1 (Process)**: The `process_module` callback writes the bytes to disk.
3.  **Intermediate**: The library automatically updates the state to 'final' and flushes it to MQTT.
4.  **Stage 2 (Post-Process)**: The `restart_device` callback is invoked to actually kill/restart the app.

INSTRUCTIONS:
1.  Run this script.
2.  Publish the JSON config payload below.
3.  Observe the logs verify the "State Flush" happens BEFORE the "Restart".
"""
import datetime
import logging
import os
import sys
import time
from typing import Any

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration, AuthProvider, Basic

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = f"/r/{REGISTRY_ID}/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("OTASample")


def process_module(blob_key: str, data: bytes) -> str:
    """
    STAGE 1: PROCESS
    This runs while the device is still 'Online' and 'Apply'.

    Task: Validate content, write to disk, prepare for switch.
    Returns: Data to pass to the post-processor (e.g. file path).
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"STAGE 1: PROCESSING BLOB '{blob_key}'")
    LOGGER.info(f"   Size: {len(data)} bytes")

    # Simulate writing to a temporary staging area
    staging_path = "/tmp/new_module.bin"
    with open(staging_path, "wb") as f:
        f.write(data)

    LOGGER.info(f"   Written to: {staging_path}")
    LOGGER.info("   Verifying integrity... OK.")
    LOGGER.info("------------------------------------------------")

    # Return the path so the next stage knows what to install
    return staging_path


def restart_device(blob_key: str, staging_path: Any):
    """
    STAGE 2: POST-PROCESS (The "Point of No Return")

    This runs ONLY after the library has successfully published
    the 'final' success state to the cloud.

    Task: Swap files and Restart.
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"STAGE 2: POST-PROCESS (State has been flushed!)")
    LOGGER.info(f"   Installing from: {staging_path}")

    LOGGER.info("   Backing up current module...")
    time.sleep(1)

    LOGGER.info("   Swapping binary...")
    time.sleep(1)

    LOGGER.warning("INITIATING SYSTEM RESTART...")
    LOGGER.warning("RESTART COMPLETED.")


if __name__ == "__main__":
    try:
        endpoint = EndpointConfiguration(
            client_id=f"{TOPIC_PREFIX}{DEVICE_ID}",
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

        device = create_device(endpoint)
        sys_manager = device.get_manager(SystemManager)

        # REGISTER THE TWO-STAGE HANDLER
        # 'process': Doing the work.
        # 'post_process': Restarting the device.
        sys_manager.register_blob_handler(
            "ota_module_loader",
            process=process_module,
            post_process=restart_device
        )

        LOGGER.info(f"Device {DEVICE_ID} running. Waiting for OTA...")
        gen_timestamp = datetime.datetime.now(datetime.timezone.utc).strftime(
            "%Y%m%d%H%M%S")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("To trigger the OTA workflow, publish this blobset:")
        print("-" * 20)
        # Using a simple base64 "Hello Update" payload
        json_payload = (f'{{ "blobset": {{ "blobs": {{ "ota_module_loader": {{ '
                        f'"phase": "final", '
                        f'"url": "data:text/plain;base64,SGVsbG8gVXBkYXRl", '
                        f'"sha256": "9f93504214cd6cc2a8e95923cd745d4f903abbadfd19161489fcc286f119ff68", '
                        f'"generation": "{gen_timestamp}" }} }} }} }}')
        print(json_payload)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{json_payload}'")
        print("=" * 60 + "\n")

        device.run()

    except SystemExit:
        LOGGER.warning("Device shutdown successfully (Simulated Restart).")
    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")