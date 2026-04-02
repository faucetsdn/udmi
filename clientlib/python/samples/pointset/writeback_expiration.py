"""
Sample: Pointset Writeback Expiration (set_value_expiry)

This script demonstrates the UDMI writeback expiration functionality.
When a point's value is overriden via a `set_value` config command, 
the cloud can optionally provide a `set_value_expiry` timestamp. If the 
cloud does not refresh the configuration before this timestamp, the point 
will automatically revert to its base state.

SCENARIO:
1.  **CustomPoint**: A simple subclass of `BasicPoint` that prints when its value changes.
2.  **Actuation**: You publish a config setting 'override_point' to 100 with an expiry in 5 seconds.
3.  **Expiration**: The UDMI `PointsetManager` detects the expiration and automatically 
    clears the state, reverting the point back to its default reading.
"""

import logging
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from udmi.core.factory import create_device, get_default_managers
from udmi.core.managers import PointsetManager
from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import AuthProvider, Basic, EndpointConfiguration, PointPointsetModel

# --- Configuration ---
DEVICE_ID = "AHU-3"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("WritebackExpirationSample")


class ExpiringPoint(BasicPoint):
    """
    A custom Point that defaults to 0 but can be overriden by writebacks.
    """
    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        super().__init__(name, model)
        self._writable = True  # Override basic_point to make it always writable for the sample

    def get_value(self) -> Any:
        return 0.0

    def set_value(self, value: Any) -> Any:
        LOGGER.info(f"[{self._name}] Actuation order received: Setting to {value}")
        return value

    def validate_value(self, value: Any) -> bool:
        return True

def expiring_point_factory(name: str, model: Optional[PointPointsetModel] = None):
    return ExpiringPoint(name, model)

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
        managers = get_default_managers(point_factory=expiring_point_factory)

        # 3. Create Device
        device = create_device(endpoint, managers)

        # 4. Add Points
        manager = device.get_manager(PointsetManager)
        manager.add_point("override_point")

        # 5. Start Device
        device_thread = threading.Thread(target=device.run, daemon=True)
        device_thread.start()

        LOGGER.info(f"Device {DEVICE_ID} running.")
        
        # Calculate timestamps for the demo
        now = datetime.now(timezone.utc)
        config_ts = now.isoformat(timespec='seconds')
        expiry_ts = (now + timedelta(seconds=10)).isoformat(timespec='seconds')
        
        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print("Publish the following JSON config using mosquitto_pub to trigger a 10s expiration override:")
        print("-" * 20)
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{DEVICE_ID}/config' -m \\")
        print(f"  '{{ \"timestamp\": \"{config_ts}\", \"pointset\": {{ \"set_value_expiry\": \"{expiry_ts}\", \"points\": {{ \"override_point\": {{ \"set_value\": 100.0 }} }} }} }}'")
        print("-" * 20)
        print("Once sent, watch the logs. In 10 seconds, the point will automatically revert.")
        print("=" * 60 + "\n")

        # Keep alive
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
