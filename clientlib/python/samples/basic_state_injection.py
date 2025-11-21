"""
This sample demonstrates how to inject static device information (Make, Model,
Firmware, etc.) into the UDMI State message using the standard SystemManager.

No custom manager classes are required for this common task.
"""

import logging
import sys

from udmi.core.factory import create_device_with_basic_auth
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration

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
        endpoint = EndpointConfiguration(
            client_id=f"{topic_prefix}{DEVICE_ID}",
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix
        )

        # 2. CONFIGURE SYSTEM MANAGER
        # We pre-configure the standard SystemManager with our device's details.
        # These will automatically appear in the 'system.hardware' and
        # 'system.software' sections of the published State message.
        my_system_manager = SystemManager(
            hardware_info={"make": "GenericDevice", "model": "SomeModel"},
            software_info={"firmware": "v2.4.5-stable", "os": "Linux"}
        )

        # 3. Create Device
        # We pass our configured manager to the 'managers' argument, which
        # replaces the default empty SystemManager.
        device = create_device_with_basic_auth(
            endpoint_config=endpoint,
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD,
            managers=[my_system_manager]
        )

        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
