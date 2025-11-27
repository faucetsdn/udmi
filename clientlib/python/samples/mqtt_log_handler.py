"""
UDMI MQTT Log Handler Sample.

This sample demonstrates how to wire up the UDMIMqttLogHandler to
redirect standard Python logs to the UDMI 'events/system' topic.

Use Cases Demonstrated:
1.  Unified Logging Workflow:
    The application uses standard Python `logging` calls (info, warning, error).
    It does not need to know about MQTT or UDMI JSON structures.

2.  Remote Diagnostics (Alerting):
    By logging warnings or errors (e.g., "High memory usage"), the device
    automatically notifies the cloud. Operators can see these issues on a
    dashboard without needing SSH access to the device.

3.  Audit Trails (Status Tracking):
    By logging info-level milestones (e.g., "Processing batch #1"), the device
    creates a timestamped history of its operations in the cloud, useful for
    verifying behavior or debugging sequence errors.
"""

import logging
import random
import sys
import threading
import time

from udmi.core.factory import create_device
from udmi.core.logging.mqtt_handler import UDMIMqttLogHandler
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"


def simulate_application_activity(logger, stop_event):
    """
    Simulates an application doing work and generating logs.
    """
    logger.info("Application worker thread started.")

    counter = 1
    while not stop_event.is_set():
        # Simulate work
        time.sleep(5)

        # --- USE CASE: Audit Trail / Status Tracking ---
        # We log a milestone. In the cloud, this becomes a permanent record
        # that the device was alive and functioning correctly at this timestamp.
        # UDMI Level: 200 (INFO)
        logger.info(f"Processing batch #{counter}...")

        # --- USE CASE: Remote Diagnostics / Alerting ---
        # We simulate a random issue. In a real scenario, this might be a
        # sensor read failure or an API timeout.
        # By logging this as a WARNING, we flag it for operators in the cloud.
        # UDMI Level: 300 (WARNING)
        if random.random() > 0.7:
            logger.warning(f"High memory usage detected in batch #{counter}!")

        counter += 1

    logger.info("Application worker thread stopping.")


if __name__ == "__main__":
    # Ensures logs still go to the console for local debugging.
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - CONSOLE - %(levelname)s - %(message)s')

    # Create a specific logger for our application logic
    app_logger = logging.getLogger("my_device_app")
    app_logger.setLevel(logging.INFO)

    try:
        # Standard connection setup
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

        logging.info("Initializing UDMI Device...")

        device = create_device(endpoint_config)

        # Retrieve the SystemManager
        # We need this to initialize the log handler because SystemManager
        # owns the 'events/system' topic logic.
        system_manager = device.get_manager(SystemManager)

        if not system_manager:
            logging.error("SystemManager not found! Cannot setup MQTT logging.")
            sys.exit(1)

        logging.info("Attaching UDMI MQTT Log Handler...")

        mqtt_handler = UDMIMqttLogHandler(system_manager)
        # Optional: Set a specific format for the cloud logs
        formatter = logging.Formatter('%(name)s: %(message)s')
        mqtt_handler.setFormatter(formatter)

        # Attach the handler to our application logger.
        # Now, any log sent to 'app_logger' goes to:
        #   a) The Console (via basicConfig)
        #   b) The Cloud (via UDMIMqttLogHandler -> SystemManager -> MQTT)
        app_logger.addHandler(mqtt_handler)

        app_logger.info(
            "This log message goes to Console AND MQTT (as a SystemEvent).")

        # Start a background thread to generate logs
        # Since device.run() blocks, we need a separate thread to make noise.
        stop_event = threading.Event()
        app_thread = threading.Thread(
            target=simulate_application_activity,
            args=(app_logger, stop_event),
            daemon=True
        )
        app_thread.start()

        device.run()

    except KeyboardInterrupt:
        logging.info("Stopping...")
        if 'stop_event' in locals():
            stop_event.set()

    except Exception as e:
        logging.error(f"Critical error: {e}", exc_info=True)
        sys.exit(1)
