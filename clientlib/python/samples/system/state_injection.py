"""
Sample: System State Injection

This script demonstrates how to inject static device identity information
into the UDMI 'system' state block.

The SystemManager merges this static data with dynamic runtime data
(like restart_count, status, and timestamp) before publishing to the cloud.

Key Concepts:
1.  **SystemState**: A typed data model representing the device's identity.
2.  **Hardware/Software**: Standardized fields for Make, Model, OS, Firmware.
3.  **Injection**: Passing this state object to the manager factory.
"""

import logging
import sys

from udmi.core.factory import create_device, get_default_managers
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration, SystemState, StateSystemHardware

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
    logger = logging.getLogger("StateInjection")

    try:
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

        endpoint = EndpointConfiguration(
            client_id=f"/r/ZZ-TRI-FECTA/d/{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix="/r/ZZ-TRI-FECTA/d/",
            auth_provider=AuthProvider(
                basic=Basic(
                    username=BROKER_USERNAME,
                    password=BROKER_PASSWORD
                )
            )
        )

        managers = get_default_managers(system_state=static_identity)

        device = create_device(endpoint, managers=managers)

        logger.info("Device starting. Watch the 'state' topic on your broker.")
        logger.info(
            "You should see the injected fields appear in the system block.")

        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
