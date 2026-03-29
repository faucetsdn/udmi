"""
Sample: Custom Point Implementation (Dependency Injection)

This script demonstrates how to create a custom implementation of `BasicPoint`
and inject it into the `PointsetManager` using the `point_factory`.

SCENARIO:
1.  **CustomPoint**: A custom subclass of `BasicPoint` that simulates a "Sine Wave" 
    sensor reading directly within its `get_value()` method.
2.  **Factory Injection**: We provide a custom factory callable to the `PointsetManager`
    which instantiates our `CustomPoint` instead of the default.
3.  **Result**: The application doesn't need an external update loop; 
    the point itself executes its reading logic pulled by the manager.
"""

import logging
import math
import sys
import threading
import time
from typing import Any, Optional

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import AuthProvider, Basic, EndpointConfiguration, PointPointsetModel

# --- Configuration ---
DEVICE_ID = "AHU-2"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("CustomPointSample")


class SineWavePoint(BasicPoint):
    """
    A custom Point that generates a sine wave reading natively.
    Demonstrates encapsulation of data acquisition at the individual point level. 
    By overriding `get_value()`, the point itself becomes responsible for its own 
    state calculation without relying on external loops or global callbacks.
    """
    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        super().__init__(name, model)
        self._start_time = time.time()
        LOGGER.info(f"Custom SineWavePoint '{name}' instantiated.")

    def get_value(self) -> Any:
        # Simulate a sine wave over time
        elapsed = time.time() - self._start_time
        val = 20.0 + 5.0 * math.sin(elapsed / 10.0)
        return round(val, 2)

    def set_value(self, value: Any) -> Any:
        LOGGER.info(f"[{self._name}] Actuation order received applied: {value}")
        return value

    def validate_value(self, value: Any) -> bool:
        return True


def sine_wave_factory(name: str, model: Optional[PointPointsetModel] = None):
    """
    Factory creating SineWavePoint for all points.
    Acts as the Dependency Injection provider for the PointsetManager. When the 
    manager receives a configuration to manage a new point, it uses this factory 
    to instantiate the custom user-defined Point class instead of the default.
    """
    return SineWavePoint(name, model)


if __name__ == "__main__":
    try:
        # 1. Configure Connection
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

        # 2. Get Default Managers with Custom Point Factory
        managers = get_default_managers(point_factory=sine_wave_factory)

        # 4. Create Device
        device = create_device(endpoint, managers)

        # 5. Add Points
        manager = device.get_manager(PointsetManager)
        # These points will now use SineWavePoint implicitly via the factory!
        manager.add_point("supply_air_temp")
        manager.add_point("return_air_temp")

        # 6. Start Device in background
        device_thread = threading.Thread(target=device.run, daemon=True)
        device_thread.start()

        LOGGER.info(f"Device {DEVICE_ID} running using Custom Point Layer.")
        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("1. Observe individual 'SineWavePoint instantiated' logs.")
        print("2. The point values are pulled directly inside get_value().")
        print("3. There is no manual update_sensors loop needed!")
        print("=" * 60 + "\n")

        # Keep alive
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
