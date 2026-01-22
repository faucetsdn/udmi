"""
Sample: System Lifecycle & Custom Commands

This script demonstrates how to handle:
1.  **Lifecycle Events**: Reboot requests triggered by UDMI Config (`system.operation.mode`).
2.  **Custom Commands**: Ad-hoc commands sent via MQTT (e.g., `maintenance_mode`).

SCENARIO:
- **Reboot**: Can be triggered either by a standard UDMI Config update OR a custom "reboot" command.
  Both trigger the `perform_soft_reboot` function.
- **Maintenance**: A custom command that accepts parameters (`duration_sec`).

INSTRUCTIONS:
1.  Run the script.
2.  Test Custom Command (Maintenance):
    Publish: { "duration_sec": 10 }
    Topic:   .../commands/maintenance_mode
3.  Test Lifecycle (Reboot via Config):
    Publish: { "system": { "operation": { "mode": "restart" } } }
    Topic:   .../config
"""

import logging
import sys
import threading
import time
from typing import Any
from typing import Dict

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = f"/r/{REGISTRY_ID}/d/"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("LifecycleDemo")


def perform_soft_reboot():
    """
    Simulates a device restart.
    In a real app, this might call `os.system("reboot")` or restart a service.
    """
    LOGGER.warning("SYSTEM RESTART REQUESTED")
    LOGGER.info("1. Stopping background threads...")
    time.sleep(1)
    LOGGER.info("2. Flushing caches...")
    time.sleep(1)
    LOGGER.warning("3. REBOOTING NOW... (Simulated)")
    # We don't actually exit here to keep the demo running,
    # but in prod you would sys.exit() or similar.


def maintenance_mode_handler(payload: Dict[str, Any]):
    """
    Handler for the custom 'maintenance_mode' command.
    """
    duration = payload.get("duration_sec", 60)
    LOGGER.info("!" * 50)
    LOGGER.info(f"ENTERING MAINTENANCE MODE")
    LOGGER.info(f"   Duration: {duration} seconds")
    LOGGER.info("!" * 50)


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

        LOGGER.info(f"Initializing device {DEVICE_ID}...")
        device = create_device(endpoint)
        sys_manager = device.get_manager(SystemManager)

        if sys_manager:
            # 1. Register Lifecycle Handler (Config-driven)
            # This handles: { "system": { "operation": { "mode": "restart" } } }
            sys_manager.register_restart_handler(perform_soft_reboot)
            LOGGER.info("Registered Lifecycle Handler: Restart")

            # 2. Register Custom Command (Command-driven)
            # This handles: /devices/{id}/commands/maintenance_mode
            sys_manager.register_command_handler("maintenance_mode", maintenance_mode_handler)
            LOGGER.info("Registered Command Handler: maintenance_mode")

            # 3. (Optional) Alias "reboot" command to the same logic
            # This handles: /devices/{id}/commands/reboot
            sys_manager.register_command_handler("reboot", lambda p: perform_soft_reboot())
            LOGGER.info("Registered Command Handler: reboot")

        else:
            LOGGER.error("SystemManager not found. Exiting.")
            sys.exit(1)

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info("Device is running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. Test Custom Command (Maintenance):")
        print(f"   mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/commands/maintenance_mode' -m '{{ \"duration_sec\": 120 }}'")
        print("\n2. Test Lifecycle Reboot (via Config):")
        print(f"   mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{{ \"system\": {{ \"operation\": {{ \"mode\": \"restart\" }} }} }}'")
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
