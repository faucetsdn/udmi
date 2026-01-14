"""
Sample: Gateway with Proxy Devices

This script demonstrates how to use the GatewayManager to:
1.  **Attach** a proxy device (Virtual-SubDevice).
    - This sends an MQTT 'attach' message to the broker.
2.  **Publish Telemetry** on behalf of that proxy.
    - The cloud sees this data as originating from the proxy, not the gateway.
3.  **Receive Config** updates targeted at that proxy.
    - The gateway receives the message, parses it, and routes it to the specific handler.

SCENARIO:
- Gateway 'G-1' connects.
- It registers Proxy 'P-1'.
- It enters a loop sending simulated temperature data for 'P-1'.
- You can manually send a config to 'P-1' to see the handler fire.
"""

import logging
import random
import sys
import threading
import time
from datetime import datetime
from datetime import timezone

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import GatewayManager
from udmi.core.managers import SystemManager
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import Config
from udmi.schema import EndpointConfiguration
from udmi.schema import PointPointsetEvents
from udmi.schema import PointsetEvents

# --- CONFIGURATION ---
GATEWAY_ID = "G-1"
PROXY_ID = "P-1"  # The ID of the device we are proxying
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("GatewayDemo")


def proxy_config_handler(device_id: str, config: Config):
    """
    Callback for when the proxy receives a config update.
    The GatewayManager routes this message specifically to this function.
    """
    LOGGER.info("------------------------------------------------")
    LOGGER.info(f"PROXY CONFIG RECEIVED for {device_id}!")
    if config.pointset:
        LOGGER.info(f"   Pointset Settings: {config.pointset}")
    LOGGER.info("------------------------------------------------")


def proxy_telemetry_loop(gateway_manager: GatewayManager, proxy_id: str):
    """Simulates reading data from the sub-device and publishing to cloud."""
    LOGGER.info(f"Starting telemetry loop for proxy {proxy_id}...")

    while True:
        time.sleep(5)
        val = round(random.uniform(20.0, 30.0), 1)

        # Create Pointset Event (Standard UDMI Schema)
        event = PointsetEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            points={"temp_sensor": PointPointsetEvents(present_value=val)}
        )

        LOGGER.info(
            f"Gateway publishing for {proxy_id}: temp={val}")

        # Publish "on behalf of" the proxy
        gateway_manager.publish_proxy_event(proxy_id, event, "pointset")


if __name__ == "__main__":
    try:
        # 1. Setup Gateway Connection
        client_id = f"{TOPIC_PREFIX}{GATEWAY_ID}"
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

        # 2. Initialize Managers
        # GatewayManager is required to handle the attach/detach/routing logic.
        sys_manager = SystemManager()
        gateway_manager = GatewayManager()

        # 3. Create Device
        device = create_device(endpoint,
                               managers=[sys_manager, gateway_manager])

        # 4. Register Proxy
        # This records the proxy in the manager and queues the 'attach' message.
        # The 'attach' message is sent automatically when the device starts.
        gateway_manager.add_proxy(PROXY_ID, config_handler=proxy_config_handler)

        # 5. Start Device (Background Thread)
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Gateway {GATEWAY_ID} is running.")

        # Print instructions for testing the config handler
        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print(f"1. Telemetry for {PROXY_ID} is being published every 5 seconds.")
        print(f"2. To test the config handler, publish this JSON:")
        print(f"   Topic: {TOPIC_PREFIX}{PROXY_ID}/config")
        print("-" * 20)
        print('{ "pointset": { "sample_rate_sec": 60 } }')
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{PROXY_ID}/config' -m '{{ \"pointset\": {{ \"sample_rate_sec\": 60 }} }}'")
        print("=" * 60 + "\n")

        # 6. Run Proxy Logic (Main Thread)
        proxy_telemetry_loop(gateway_manager, PROXY_ID)

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
