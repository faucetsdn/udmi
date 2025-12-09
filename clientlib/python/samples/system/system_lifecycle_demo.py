"""
Sample: System Lifecycle Demo using registered command callbacks.

This script demonstrates how to register callbacks for system commands.

INSTRUCTIONS:
1. Run this script.
2. Send a reboot command: /devices/{DEVICE_ID}/commands/reboot
   -> Observe it logs "SOFT REBOOT".
3. Send a custom command: /devices/{DEVICE_ID}/commands/maintenance_mode
   -> Observe it logs "Entering Maintenance Mode".
"""

import logging
import sys
import threading
import time
from typing import Any, Dict

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("CustomCmdSample")


def reboot_handler(payload: Dict[str, Any]):
    LOGGER.warning("!!! INTERCEPTED REBOOT COMMAND !!!")
    LOGGER.warning("Performing Soft Reboot...")
    time.sleep(1)
    LOGGER.warning("Soft Reboot Complete. Process will NOT exit.")


def maintenance_mode_handler(payload: Dict[str, Any]):
    duration = payload.get("duration_sec", 60)
    LOGGER.info(f"Entering Maintenance Mode for {duration} seconds...")


if __name__ == "__main__":
    try:
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": "/r/ZZ-TRI-FECTA/d/",
            "auth_provider": {
                "basic": {"username": BROKER_USERNAME,
                          "password": BROKER_PASSWORD}
            }
        })

        device = create_device(endpoint)
        sys_manager = device.get_manager(SystemManager)

        if sys_manager:
            sys_manager.register_command_handler("reboot", reboot_handler)
            LOGGER.info("Overridden 'reboot' command handler.")

            sys_manager.register_command_handler("maintenance_mode",
                                                 maintenance_mode_handler)
            LOGGER.info("Registered 'maintenance_mode' command handler.")

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info("Device running. Waiting for commands...")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
