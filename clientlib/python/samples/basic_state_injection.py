"""
This sample demonstrates how to inject static device information (Make, Model,
Firmware, etc.) into the UDMI State message using the SystemManager.

Refactored to use the typed SystemState injection instead of the specific
hardware_info and software_info dicts.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.core.factory import get_default_managers
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration
from udmi.schema import StateSystemHardware
from udmi.schema import SystemState

# --- Configuration ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(message)s')
LOGGER = logging.getLogger(__name__)

if __name__ == "__main__":
    try:
        # 1. Configure Connection
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"{topic_prefix}{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": topic_prefix,
            "auth_provider": {
                "basic": {
                    "username": BROKER_USERNAME,
                    "password": BROKER_PASSWORD
                }
            }
        })

        # 2. CONFIGURE SYSTEM MANAGER
        # We create a strongly typed SystemState object.
        # This allows us to define any static field supported by the UDMI schema.
        static_state = SystemState(
            hardware=StateSystemHardware(
                make="GenericDevice",
                model="SomeModel"
            ),
            software={
                "firmware": "v2.4.5-stable",
                "os": "Linux"
            },
            serial_no="SN-88392-X",
        )

        # Initialize the managers with our custom state
        managers = get_default_managers(system_state=static_state)

        # 3. Create Device
        device = create_device(endpoint_config=endpoint,
                               managers=managers)

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
