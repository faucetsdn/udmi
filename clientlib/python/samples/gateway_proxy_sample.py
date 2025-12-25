"""
Sample: Gateway with Proxy Devices

This script demonstrates how to use the GatewayManager to:
1. Attach a proxy device (Virtual-SubDevice).
2. Publish telemetry on behalf of that proxy.
3. Receive config updates targeted at that proxy.
"""

import logging
import random
import sys
import threading
import time
from datetime import datetime, timezone

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device
from udmi.core.managers import GatewayManager
from udmi.core.managers import SystemManager
from udmi.schema import Config
from udmi.schema import EndpointConfiguration
from udmi.schema import PointsetEvents
from udmi.schema import PointPointsetEvents

# --- CONFIGURATION ---
GATEWAY_ID = "G-1"
PROXY_ID = "P-1"  # The ID of the device we are proxying
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-gateway"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("GatewayDemo")


def proxy_config_handler(device_id: str, config: Config):
    """Callback for when the proxy receives a config update."""
    LOGGER.info(f" >>> Proxy {device_id} received CONFIG update!")
    if config.pointset:
        LOGGER.info(f"     Pointset Config: {config.pointset}")


def proxy_telemetry_loop(gateway_manager: GatewayManager, proxy_id: str):
    """Simulates reading data from the sub-device and publishing to cloud."""
    while True:
        time.sleep(5)
        val = round(random.uniform(20.0, 30.0), 1)

        # Create Pointset Event
        event = PointsetEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            points={"temp_sensor": PointPointsetEvents(present_value=val)}
        )

        LOGGER.info(
            f" <<< Gateway publishing telemetry for {proxy_id}: temp={val}")
        gateway_manager.publish_proxy_event(proxy_id, event, "pointset")


if __name__ == "__main__":
    try:
        # 1. Setup Gateway Connection
        client_id = f"{TOPIC_PREFIX}{GATEWAY_ID}"
        endpoint = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": TOPIC_PREFIX,
            "auth_provider": {
                "basic": {"username": BROKER_USERNAME,
                          "password": BROKER_PASSWORD}
            }
        })

        # 2. Initialize Managers
        sys_manager = SystemManager()
        gateway_manager = GatewayManager()

        # 3. Create Device
        device = create_device(endpoint,
                               managers=[sys_manager, gateway_manager])

        # 4. Register Proxy
        # This will send the 'attach' message once the loop starts
        gateway_manager.add_proxy(PROXY_ID, config_handler=proxy_config_handler)

        # 5. Start Device
        # Run in background so we can run our telemetry loop here
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        # 6. Run Proxy Logic
        proxy_telemetry_loop(gateway_manager, PROXY_ID)

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")