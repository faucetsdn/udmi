"""
Sample: Localnet Routing & Validation Demo

This script demonstrates how to use the enhanced LocalnetManager to:
1.  **Register** a protocol provider (Mock BACnet).
2.  **Receive** a Localnet configuration from the cloud.
3.  **Validate** the configuration against the provider.
4.  **Report** the status (Active/Error) back to the cloud.

SCENARIO:
- A Gateway device starts up.
- It registers a driver for the "bacnet" protocol family.
- The driver enforces that addresses must be numeric digits.
- You send a config with valid and invalid addresses to see the validation logic.

USAGE:
1.  Run the script.
2.  Publish the JSON config payload printed in the instructions.
3.  Watch the logs to see validation success/failure.
"""

import logging
import sys
import threading
import time
from typing import Dict

from udmi.core.factory import create_device
from udmi.core.managers import LocalnetManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import EndpointConfiguration
from udmi.schema import RefDiscovery

# --- CONFIGURATION ---
DEVICE_ID = "GATEWAY-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(message)s')
LOGGER = logging.getLogger("LocalnetDemo")


# --- 1. Define a Mock Provider ---
class MockBacnetProvider(FamilyProvider):
    """
    Simulates a BACnet driver that implements the new validation hook.
    """

    def validate_address(self, addr: str) -> bool:
        """
        Validates that the physical address is a numeric ID string.
        Real BACnet logic would be more complex (e.g., checking range).
        """
        LOGGER.info(f"Validating BACnet address: '{addr}'")
        # In this mock, we only accept digits (e.g., "1001" is valid, "1001-A" is invalid)
        if addr.isdigit():
            LOGGER.info("  -> Valid")
            return True
        else:
            LOGGER.warning("  -> Invalid (Must be numeric)")
            return False

    def start_scan(self, discovery_config, publish_func) -> None:
        pass

    def stop_scan(self) -> None:
        pass

    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        return {}


if __name__ == "__main__":
    try:
        # 1. Configure Connection
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"
        endpoint = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": TOPIC_PREFIX,
            "auth_provider": {
                "basic": {"username": BROKER_USERNAME,
                          "password": BROKER_PASSWORD}
            }
        })

        # 2. Initialize Managers
        # We explicitly create LocalnetManager so we can register our provider.
        localnet_manager = LocalnetManager()

        # Register our mock provider for the 'bacnet' family
        # This tells the manager: "I know how to handle 'bacnet' configurations"
        localnet_manager.register_provider("bacnet", MockBacnetProvider())

        # 3. Create Device
        # Pass our configured manager to the factory
        device = create_device(endpoint, managers=[localnet_manager])

        # 4. Start Device
        # Run the device loop in a background thread so the main thread waits
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Gateway {DEVICE_ID} running.")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. To test validation, publish this 'localnet' config:")
        print("-" * 20)
        json_payload = ('{ "localnet": { "families": { "bacnet": { "addr": "192.168.1.10", '
                        '"devices": { "ahu-valid": "1001", "vav-invalid": "2002-A" } } } } }')
        print(json_payload)
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m '{json_payload}'")
        print("=" * 60 + "\n")

        # Keep the script alive
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
