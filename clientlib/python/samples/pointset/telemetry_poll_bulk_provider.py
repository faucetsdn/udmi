"""
Sample: Bulk Telemetry Provider

This script demonstrates the modern approach for supplying hardware-read values 
to the PointsetManager immediately before telemetry is published. By registering
a `BulkPointProvider`, the internal values of managed points are batched-updated.
"""

import logging
import sys
import threading
import time
from typing import Any, Dict

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.core.managers.point.bulk_provider import BulkPointProvider
from udmi.schema import AuthProvider, Basic, EndpointConfiguration

DEVICE_ID = "AHU-1"
REGISTRY_ID = "ZZ-TRI-FECTA"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')
LOGGER = logging.getLogger("BulkProviderSample")


class MockModbusProvider(BulkPointProvider):
    """
    A mock class simulating a connection to Modbus hardware that provides 
    all point data in a single batch read.

    Acts as the bridge between the physical hardware layer and the UDMI software 
    layer. It abstracts the I/O complexity (like serial bus polling) so the PointsetManager 
    receives data efficiently in bulk.
    """
    def __init__(self):
        self._mock_data = {
            "temperature": 22.5,
            "humidity": 45.0,
            "pressure": 1013.2
        }

    def read_points(self) -> Dict[str, Any]:
        """
        Provides the batch telemetry for UDMI.
        
        Executes the physical read against sensors during the start of every telemetry 
        transmission window. Returns a dictionary mapping point names to their values.
        """
        LOGGER.info(f"Modbus batch read yielding: {self._mock_data}")
        self._mock_data["temperature"] += 0.1
        return self._mock_data


if __name__ == "__main__":
    try:
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"
        endpoint = EndpointConfiguration(
            client_id=client_id,
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

        managers = get_default_managers()
        device = create_device(endpoint, managers)
        pointset_manager = device.get_manager(PointsetManager)

        # Register the provider
        modbus_hw = MockModbusProvider()
        pointset_manager.register_bulk_provider(modbus_hw)

        # For the sake of the sample, we add the points explicitly.
        # Normally these would be provisioned via cloud `config.pointset`.
        pointset_manager.add_point("temperature")
        pointset_manager.add_point("humidity")
        pointset_manager.add_point("pressure")

        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Device {DEVICE_ID} is running.")
        LOGGER.info("The application will read from the provider automatically based on sample_rate_sec.")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
