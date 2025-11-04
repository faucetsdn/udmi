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
        self._last_heartbeat = 0.0
        self._stop_event = threading.Event()
        self._thread = threading.Thread(target=self.run, daemon=True)

    def start(self) -> None:
        """
        Called by the Device.start(), so we start our thread.
        """
        LOGGER.info("HeartbeatManager starting its own thread...")
        self._thread.start()

    def stop(self) -> None:
        """
        Called by the Device.stop(), so we stop our thread.
        """
        LOGGER.info("HeartbeatManager stopping...")
        self._stop_event.set()
        self._thread.join(timeout=2)

    def run(self) -> None:
        """
        This is the main loop for our manager, running in its own thread.
        """
        while not self._stop_event.is_set():
            try:
                self._send_heartbeat()
                self._stop_event.wait(self.HEARTBEAT_INTERVAL_SEC)
            except Exception as e:
                LOGGER.error(f"Error in HeartbeatManager loop: {e}")
                time.sleep(self.HEARTBEAT_INTERVAL_SEC)

    def _send_heartbeat(self):
        LOGGER.info("Sending device heartbeat event...")
        try:
            log_entry = Entry(
                message="Device heartbeat operational",
                level=200,  # INFO
                timestamp=datetime.now(timezone.utc).isoformat()
            )
            event = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                logentries=[log_entry]
            )
            self.publish_event(event, "system")
        except Exception as e:
            LOGGER.error(f"Failed to send heartbeat: {e}")

    def handle_config(self, config: Config) -> None:
        pass  # This manager doesn't care about config

    def handle_command(self, command_name: str, payload: dict) -> None:
        pass  # This manager doesn't handle commands

    def update_state(self, state: State) -> None:
        pass  # This manager does not update state

# --- End Custom Manager ---


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
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
        managers_list = [
            SystemManager(),  # The default manager
            HeartbeatManager()  # Our new custom manager
        ]
        # --- End Manager List ---

        # 3. Pass the list to the factory
        device = create_device_with_basic_auth(
            endpoint_config=endpoint_config,
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD,
            managers=managers_list
        )
        device.run()
    except Exception as e:
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")