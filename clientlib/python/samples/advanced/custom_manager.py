"""
Custom Manager with event publish support.

This sample demonstrates how to create a completely new, custom manager
that runs its own background task to publish ad-hoc UDMI events.

Key Features Demonstrated:
1.  Inheriting from `BaseManager`.
2.  Using `start_periodic_task` for background logic.
3.  Publishing custom events using `publish_event`.
4.  Handling configuration updates for the custom manager.
"""

import logging
import sys
from datetime import datetime
from datetime import timezone

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import BaseManager
from udmi.core.managers import SystemManager
from udmi.schema import Config
from udmi.schema import EndpointConfiguration
from udmi.schema import Entry
from udmi.schema import State
from udmi.schema import SystemEvents

# --- Configuration Constants ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"

LOGGER = logging.getLogger("CustomManagerSample")


# --- 1. Define a Custom Manager ---

class HeartbeatManager(BaseManager):
    """
    A custom manager that logs a 'heartbeat' event periodically.

    This demonstrates adding new, periodic logic to the device.
    """
    DEFAULT_INTERVAL_SEC = 10

    @property
    def model_field_name(self) -> str:
        # If we wanted to read metadata, we would define the field name here.
        # e.g. return "heartbeat_config"
        return "custom"

    def __init__(self, interval_sec=DEFAULT_INTERVAL_SEC):
        super().__init__()
        self.interval_sec = interval_sec
        # We store a reference to the wake event so we can trigger immediate runs
        self._wake_event = None

    def start(self) -> None:
        """
        Lifecycle hook: Called by the Device when `device.run()` is initiated.
        """
        LOGGER.info("HeartbeatManager starting...")

        # Use the BaseManager's helper to run a periodic task.
        # This handles threading, exception catching, and graceful shutdown automatically.
        self._wake_event = self.start_periodic_task(
            interval_getter=lambda: self.interval_sec,
            task=self._send_heartbeat,
            name="HeartbeatTask"
        )

    def _send_heartbeat(self):
        """
        The task function. Builds and publishes a UDMI SystemEvents message.
        """
        LOGGER.info(f"Sending heartbeat (Interval: {self.interval_sec}s)...")

        # Create a UDMI-compliant log entry
        log_entry = Entry(
            message="Heartbeat OK",
            level=200,  # INFO
            timestamp=datetime.now(timezone.utc).isoformat()
        )

        # Create the SystemEvents message
        event = SystemEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            logentries=[log_entry]
        )

        # Use the BaseManager's publish_event method.
        # This handles topic formatting ("events/system") and JSON serialization.
        self.publish_event(event, "system")

    # --- Handling Config Updates ---

    def handle_config(self, config: Config) -> None:
        """
        Optional: React to configuration changes.
        Here we demonstrate checking a custom field in the 'system' config
        to dynamically change the heartbeat rate.
        """
        # In a real scenario, you might have your own config block (e.g. config.heartbeat)
        # For this demo, we'll check system.metrics_rate_sec as a proxy,
        # or just log that we saw a config.
        if config.system and config.system.metrics_rate_sec:
            new_rate = config.system.metrics_rate_sec
            if new_rate != self.interval_sec:
                LOGGER.info(f"Updating heartbeat interval: {self.interval_sec} -> {new_rate}")
                self.interval_sec = new_rate
                # Wake up the thread immediately to apply the new rate
                if self._wake_event:
                    self._wake_event.set()

    # --- Required Abstract Methods ---

    def handle_command(self, command_name: str, payload: dict) -> None:
        """This manager doesn't handle any commands."""
        pass

    def update_state(self, state: State) -> None:
        """This manager doesn't contribute to the device state."""
        pass


# --- End Custom Manager ---


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        # Standard setup for a local broker
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": client_id,
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

        LOGGER.info("Creating device with SystemManager + HeartbeatManager...")

        # --- 2. Create Manager List ---
        # We explicitly list the managers we want.
        # We include SystemManager for basic device health/lifecycle.
        # We include our HeartbeatManager for the custom logic.
        custom_managers_list = [
            SystemManager(),
            HeartbeatManager(interval_sec=5)
        ]

        # 3. Create the Device
        device = create_device(
            endpoint_config=endpoint_config,
            managers=custom_managers_list,
        )

        # 4. Start the device
        # This will start the device's main loop, which in turn calls
        # `start()` on all registered managers.
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    LOGGER.info("Device shut down gracefully. Exiting.")
