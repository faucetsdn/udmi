"""
Sample: Custom Persistence Backend

This script demonstrates how to override the default file-based persistence.

Use this if your device needs to store state (like restart counts or endpoint info)
in a specific location, such as:
 - BACnet Objects (AV/BV)
 - Non-Volatile Memory (NVM) / EEPROM
 - A Database (Redis, SQLite)
 - A secure hardware element

SCENARIO:
1.  **Define**: Create a class that implements `PersistenceBackend`.
2.  **Inject**: Pass an instance of this class to `create_device`.
3.  **Run**: The library will now call your `load`/`save` methods whenever
    it needs to persist critical state (e.g., incrementing restart count).

INSTRUCTIONS:
1.  Run the script.
2.  Observe the "[CustomBackend]" logs showing the library reading/writing
    data to our mock in-memory storage instead of a file.
"""

import logging
import sys
from typing import Any
from typing import Dict
from typing import Optional

from udmi.core.factory import create_device
from udmi.core.persistence.backend import PersistenceBackend
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- CONFIGURATION ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("CustomPersistence")


# --- 1. Define Custom Backend ---
class MockBacnetPersistence(PersistenceBackend):
    """
    Simulates storing data in BACnet objects or NVM.
    In a real implementation, 'self._storage' would be your hardware API.
    """

    def __init__(self):
        # Simulate existing state (e.g. from a previous boot)
        self._storage: Dict[str, Any] = {
            "restart_count": 99  # Simulate that we've restarted 99 times
        }
        LOGGER.info("--- Custom Backend Initialized (Mocking NVM) ---")

    def load(self, key: str) -> Optional[Any]:
        """Called by the library to read a value."""
        val = self._storage.get(key)
        LOGGER.info(f"[CustomBackend] LOAD key='{key}' -> returning: {val}")
        return val

    def save(self, key: str, value: Any) -> None:
        """Called by the library to write a value."""
        LOGGER.info(f"[CustomBackend] SAVE key='{key}' <- value: {value}")
        self._storage[key] = value
        # In a real app: flush to EEPROM / Write to BACnet Object here.

    def delete(self, key: str) -> None:
        LOGGER.info(f"[CustomBackend] DELETE key='{key}'")
        if key in self._storage:
            del self._storage[key]


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

        # 2. Instantiate Custom Backend
        my_backend = MockBacnetPersistence()

        # 3. Inject into Device Factory
        LOGGER.info("Creating device with custom persistence...")
        device = create_device(endpoint, persistence_backend=my_backend)

        LOGGER.info("Starting device (Check logs for persistence operations)...")
        # When device.run() starts, the SystemManager will:
        # 1. persistence.get("restart_count") -> Should return 99 (from our mock)
        # 2. persistence.set("restart_count", 100) -> Should log a SAVE
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)