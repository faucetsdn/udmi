"""
Sample: Localnet Routing & Validation Demo

This script demonstrates how to use the enhanced LocalnetManager to:
1. Register a protocol provider (Mock BACnet).
2. Receive a Localnet configuration from the cloud.
3. Validate the configuration against the provider.
4. Report the status (Active/Error) back to the cloud.

INSTRUCTIONS:
1. Run this script.
2. Publish a valid or invalid localnet config to the device config topic.

   Example Valid Config:
   {
     "localnet": {
       "families": {
         "bacnet": {
           "addr": "192.168.1.10",
           "devices": {
             "ahu-1": "1001",
             "vav-1": "1002"
           }
         }
       }
     }
   }

3. Watch the logs to see the validation logic and state updates.
"""

import logging
import sys
import threading
import time
from typing import Dict, Optional

from udmi.core.factory import create_device
from udmi.core.managers import LocalnetManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import DiscoveryEvents, RefDiscovery
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "GATEWAY-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-gateway"
BROKER_PASSWORD = "somesecureword"

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
        LOGGER.info(f"Validating BACnet address: {addr}")
        # In this mock, we only accept digits (e.g., "1001" is valid, "1001-A" is invalid)
        return addr.isdigit()

    def start_scan(self, discovery_config, publish_func) -> None:
        LOGGER.info("Starting mock BACnet scan...")
        # Simulate finding a device
        time.sleep(1)
        # In a real app, you would publish valid DiscoveryEvents here
        LOGGER.info("Scan complete (simulated).")

    def stop_scan(self) -> None:
        LOGGER.info("Stopping scan.")

    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        return {}

    def scan(self) -> None:
        LOGGER.info("Manual scan triggered via 'discovery' command.")


if __name__ == "__main__":
    try:
        # 1. Configure Connection
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

        LOGGER.info("Device running. Waiting for 'localnet' config...")
        LOGGER.info(
            "Try sending a config with family 'bacnet' (valid) or 'modbus' (invalid/missing provider).")

        # Keep the script alive
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)