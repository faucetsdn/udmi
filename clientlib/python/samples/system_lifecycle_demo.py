"""
Sample: System Lifecycle (Reboot/Shutdown)

This script demonstrates how the SystemManager handles lifecycle commands.
It listens for UDMI commands and exits the process with specific status codes.

UDMI Lifecycle Mapping:
- Command: 'terminate' -> Exit Code: 193 (Orchestrator should terminate)
- Command: 'reboot'    -> Exit Code: 192 (Orchestrator should restart)
- Command: 'shutdown'  -> Exit Code: 0   (Orchestrator should stop)
"""

import logging
import sys
import time

from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("LifecycleDemo")

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

        cmd_topic = f"{TOPIC_PREFIX}{DEVICE_ID}/commands"

        print("\n" + "=" * 80)
        print("SYSTEM LIFECYCLE DEMO INSTRUCTIONS:")
        print("1. The device is running.")
        print("2. Open a separate terminal to send commands.")
        print("-" * 20)
        print(f"TO TEST TERMINATE (Expect Exit Code 193):")
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -t '{cmd_topic}/terminate' -m '{{}}'")
        print("-" * 20)
        print(f"TO TEST REBOOT (Expect Exit Code 192):")
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -t '{cmd_topic}/reboot' -m '{{}}'")
        print("-" * 20)
        print(f"TO TEST SHUTDOWN (Expect Exit Code 0):")
        print(
            f"mosquitto_pub -h {MQTT_HOSTNAME} -t '{cmd_topic}/shutdown' -m '{{}}'")
        print("=" * 80 + "\n")

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)