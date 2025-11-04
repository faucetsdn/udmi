"""
Advanced example script for creating a custom UDMI device manager.

This script demonstrates the extensibility of the `udmi` library by
defining a new manager (`HeartbeatManager`) that inherits from `BaseManager`.

Key concepts illustrated:

1.  Inheritance: Creating a class that implements the `BaseManager` interface.
2.  Lifecycle Hooks: Using the `start()` and `stop()` methods, which are
    called by the main `Device` orchestrator.
3.  Custom Logic: Running a separate thread for periodic tasks
    (in this case, publishing a heartbeat event).
4.  Event Publishing: Using the manager's `publish_event` method to send
    UDMI-compliant event messages.
5.  Factory Integration: Passing a custom list of managers to the factory,
    showing how to combine default and custom behaviors.
"""

import logging
import sys
import threading
import time
from datetime import datetime
from datetime import timezone

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device_with_basic_auth
from udmi.core.managers import BaseManager
from udmi.core.managers import SystemManager
from udmi.core.managers.base_manager import LOGGER
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


# --- 1. Define a Custom Manager ---
class HeartbeatManager(BaseManager):
    """
    A custom manager that logs a 'heartbeat' event every 60 seconds.
    This demonstrates adding new, periodic logic to the device.
    """
    HEARTBEAT_INTERVAL_SEC = 60

    def __init__(self):
        super().__init__()
        self._stop_event = threading.Event()
        # We create a thread to run our long-running logic without
        # blocking the main device loop.
        self._thread = threading.Thread(target=self.run, daemon=True)

    def start(self) -> None:
        """
        Lifecycle hook: Called by the Device when `device.run()` is initiated.
        This is where we start our manager's background thread.
        """
        LOGGER.info("HeartbeatManager starting its own thread...")
        self._thread.start()

    def stop(self) -> None:
        """
        Lifecycle hook: Called by the Device when `device.stop()` is initiated.
        This signals our thread to stop and waits for it to exit.
        """
        LOGGER.info("HeartbeatManager stopping...")
        self._stop_event.set()
        self._thread.join(timeout=2)

    def run(self) -> None:
        """
        This is the main loop for our manager, running in its own thread.
        It will loop until the `_stop_event` is set by the `stop()` method.
        """
        while not self._stop_event.is_set():
            try:
                # Perform our periodic action
                self._send_heartbeat()
                # Wait for the interval, or until `stop()` is called
                self._stop_event.wait(self.HEARTBEAT_INTERVAL_SEC)
            except Exception as e:
                LOGGER.error(f"Error in HeartbeatManager loop: {e}")
                # Wait a bit before retrying to avoid spamming errors
                time.sleep(self.HEARTBEAT_INTERVAL_SEC)

    def _send_heartbeat(self):
        """
        Builds and publishes a UDMI SystemEvents message.
        """
        LOGGER.info("Sending device heartbeat event...")
        try:
            # Create a UDMI-compliant log entry
            log_entry = Entry(
                message="Device heartbeat operational",
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
            # This handles topic formatting and JSON serialization.
            self.publish_event(event, "system")
        except Exception as e:
            # Don't crash the loop if publishing fails (e.g., disconnected)
            LOGGER.error(f"Failed to send heartbeat: {e}")

    # --- Required BaseManager Methods ---
    # We must implement these abstract methods, even if we don't use them.

    def handle_config(self, config: Config) -> None:
        """This manager doesn't act on config updates."""
        pass

    def handle_command(self, command_name: str, payload: dict) -> None:
        """This manager doesn't handle any commands."""
        pass

    def update_state(self, state: State) -> None:
        """This manager doesn't contribute to the device state."""
        pass

# --- End Custom Manager ---


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        # Standard setup for a local broker
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix
        )

        logging.info("Creating device with SystemManager + HeartbeatManager...")

        # --- 2. Create Manager List ---
        # This is the key step. Instead of letting the factory create the
        # default [SystemManager()], we provide our own list.
        # We include SystemManager to keep all the default UDMI behaviors
        # (like config/state handling) and add our new manager.
        managers_list = [
            SystemManager(),    # Keeps default UDMI state/config logic
            HeartbeatManager()  # Adds our custom heartbeat logic
        ]
        # --- End Manager List ---

        # 3. Pass the list to the factory.
        # The factory will give each manager a reference to the dispatcher
        # and then call `start()` on each one.
        device = create_device_with_basic_auth(
            endpoint_config=endpoint_config,
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD,
            managers=managers_list  # Pass our custom list here
        )

        # 4. Start the device
        # This will start the device's main loop, which in turn calls
        # `start()` on both SystemManager and HeartbeatManager.
        device.run()
    except Exception as e:
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")