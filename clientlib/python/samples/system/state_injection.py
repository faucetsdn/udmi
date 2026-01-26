"""
Sample: System State Injection

This script demonstrates how to inject static device identity information
into the UDMI 'system' state block.

The SystemManager merges this static data with dynamic runtime data
(like restart_count, status, and timestamp) before publishing to the cloud.

SCENARIO:
1.  **Identity Definition**: We create a `SystemState` object with the device's
    Serial Number, Hardware Make/Model, and Software versions.
2.  **Injection**: We pass this object to the manager factory.
3.  **Publication**: When the device connects, it publishes a state message
    containing BOTH this static info and the dynamic startup info.

INSTRUCTIONS:
1.  Run the script.
2.  Subscribe to the state topic using the command below.
3.  Observe that your serial number and firmware version are present in the JSON.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration
from udmi.schema import StateSystemHardware
from udmi.schema import SystemState

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = f"/r/{REGISTRY_ID}/d/"

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
    logger = logging.getLogger("StateInjection")

    try:
        # 1. Define Static Identity (The "Digital Nameplate")
        static_identity = SystemState(
            serial_no="SN-998877-XY",
            hardware=StateSystemHardware(
                make="Delta Controls",
                model="Red5 Controller"
            ),
            software={
                "firmware": "v2.4.5-stable",
                "os": "Linux 5.10",
                "bootloader": "u-boot-2023"
            }
        )

        logger.info("Configuring device identity:")
        logger.info(f"  Serial: {static_identity.serial_no}")
        logger.info(f"  Model:  {static_identity.hardware.model}")

        # 2. Configure Connection
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

        # 3. Initialize Managers with Injected State
        # The factory passes 'system_state' into the SystemManager constructor.
        managers = get_default_managers(system_state=static_identity)

        # 4. Create & Run
        device = create_device(endpoint, managers=managers)

        logger.info(f"Device {DEVICE_ID} starting...")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. The device will publish its state immediately upon connection.")
        print("2. Verify the 'hardware' and 'software' fields in the output.")
        print("-" * 20)
        print("mosquitto_sub command:")
        print(f"mosquitto_sub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/state'")
        print("=" * 60 + "\n")

        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
