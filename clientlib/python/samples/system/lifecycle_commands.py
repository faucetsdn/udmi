"""
Sample: System Lifecycle Demo using registered command callbacks.

This script demonstrates how to register callbacks for system commands.

INSTRUCTIONS:
1. Run this script.
2. Send a reboot command via MQTT:
   Topic:   /devices/{DEVICE_ID}/commands/reboot
   Payload: {}
   -> Observe it logs "SOFT REBOOT".

3. Send a custom command via MQTT:
   Topic:   /devices/{DEVICE_ID}/commands/maintenance_mode
   Payload: {"duration_sec": 120}
   -> Observe it logs "Entering Maintenance Mode".
"""

import logging
import sys
import threading
import time
from typing import Any, Dict

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
REGION = "us-central1"
PROJECT_ID = "demos"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("LifecycleDemo")


def reboot_handler(_payload: Dict[str, Any]):
    """
    Overrides the default system reboot behavior.
    Instead of killing the process, we perform a 'soft' reset logic here.
    """
    LOGGER.warning("!!! INTERCEPTED REBOOT COMMAND !!!")
    LOGGER.warning("Performing Soft Reboot...")
    time.sleep(1.5)
    LOGGER.warning("Soft Reboot Complete. Process state reset.")


def maintenance_mode_handler(payload: Dict[str, Any]):
    """
    Custom handler for a non-standard command 'maintenance_mode'.
    Payload expects: {'duration_sec': int}
    """
    duration = payload.get("duration_sec", 60)
    LOGGER.info(f">>> ENTERING MAINTENANCE MODE (Duration: {duration}s) <<<")


if __name__ == "__main__":
    try:
        topic_prefix = f"/r/{REGISTRY_ID}/d/"

        endpoint = EndpointConfiguration(
            client_id=f"{topic_prefix}{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix,
            auth_provider=AuthProvider(
                basic=Basic(
                    username=BROKER_USERNAME,
                    password=BROKER_PASSWORD
                )
            )
        )

        LOGGER.info(f"Initializing device {DEVICE_ID}...")
        device = create_device(endpoint)
        sys_manager = device.get_manager(SystemManager)

        if sys_manager:
            sys_manager.register_command_handler("reboot", reboot_handler)
            LOGGER.info("Registered custom handler for 'reboot'.")

            sys_manager.register_command_handler("maintenance_mode",
                                                 maintenance_mode_handler)
            LOGGER.info("Registered custom handler for 'maintenance_mode'.")
        else:
            LOGGER.error(
                "SystemManager not found. Callbacks could not be registered.")
            sys.exit(1)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info("Device is running. Listening for commands...")
        LOGGER.info(f"Test Topic: {topic_prefix}{DEVICE_ID}/commands/reboot")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
