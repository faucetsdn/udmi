"""
Sample: Event Reporting API

This script demonstrates how to use the generic `publish_event` API
to send ad-hoc events (like alarms, logs, or status updates).
"""

import logging
import sys
import threading
import time
from datetime import datetime
from datetime import timezone

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import BaseManager
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration
from udmi.schema import Entry
from udmi.schema import SystemEvents

# --- Config ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger(__name__)


def monitor_for_alarms(manager: BaseManager) -> None:
    """
    Simulates a monitoring loop that triggers events when specific conditions occur.
    """
    LOGGER.info("Starting alarm monitor...")

    while True:
        # Simulate an event happening after a few seconds
        time.sleep(3)

        LOGGER.warning("CONDITION MET: Sending 'Filter Clogged' event!")

        # 1. Create the Event Payload
        # We use the official UDMI SystemEvents schema
        log_entry = Entry(
            message="Filter differential pressure too high",
            level=400,  # warning
            timestamp=datetime.now(timezone.utc).isoformat()
        )

        event = SystemEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            logentries=[log_entry]
        )

        # 2. Publish the Event
        # We use the manager's `publish_event` method.
        # The second argument "system" directs this to the `events/system` topic.
        # You could send to `events/custom` by changing the string.
        manager.publish_event(event, "system")

        LOGGER.info("Event sent.")


if __name__ == "__main__":
    try:
        # 1. Configure Connection
        endpoint = EndpointConfiguration.from_dict({
            "client_id": f"/r/ZZ-TRI-FECTA/{DEVICE_ID}",
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": "/r/ZZ-TRI-FECTA/d/",
            "auth_provider": {
                "basic": {
                    "username": BROKER_USERNAME,
                    "password": BROKER_PASSWORD
                }
            }
        })

        # 2. Create Device
        # We don't need extra managers for this; the default SystemManager
        # is sufficient to send system events.
        device = create_device(endpoint)

        # 3. Get a handle to the manager
        # We need a manager instance to call `publish_event`.
        # SystemManager is available by default.
        sys_manager = device.get_manager(SystemManager)

        # 4. Start Device in a background thread
        device_thread = threading.Thread(target=device.run, daemon=True)
        device_thread.start()

        # 5. Run the application logic
        monitor_for_alarms(sys_manager)

    except KeyboardInterrupt:
        LOGGER.info("Stopping...")
        sys.exit(0)
