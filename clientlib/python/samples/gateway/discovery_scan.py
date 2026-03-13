"""
Sample: Gateway Discovery Scan

This script simulates a Gateway device performing a discovery scan on a local network.

SCENARIO:
1.  **Setup**: A Gateway connects and registers a "Mock BACnet" provider.
2.  **Trigger**: The operator sends a `discovery` command (via MQTT).
3.  **Scan**: The Provider "scans" (simulated delay) and finds two devices.
4.  **Report**: The Gateway publishes `DiscoveryEvents` for each found device.

INSTRUCTIONS:
1.  Run the script.
2.  Publish a discovery command to:
    Topic: /devices/{GATEWAY_ID}/commands/discovery
    Payload: { "families": ["bacnet"] }
3.  Watch the logs for "Discovery event received" and the published messages.
"""

import logging
import sys
import threading
import time
from typing import Dict

from udmi.core.factory import create_device
from udmi.core.managers import DiscoveryManager
from udmi.core.managers import LocalnetManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import DiscoveryEvents
from udmi.schema import EndpointConfiguration
from udmi.schema import RefDiscovery
from udmi.schema import StateSystemHardware
from udmi.schema import System

# --- CONFIGURATION ---
GATEWAY_ID = "G-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883
BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecureword"
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("DiscoveryDemo")


class MockBacnetProvider(FamilyProvider):
    """
    Simulates a BACnet driver finding devices on a local bus.
    """
    def start_scan(self, discovery_config, publish_func) -> None:
        LOGGER.info("Starting BACnet scan (simulated 3s)...")
        time.sleep(1)

        # Simulate finding Device A
        LOGGER.info("   Found Device: BACnet-1001")

        # We construct the event using the strict dataclasses
        event_a = DiscoveryEvents(
            addr="1001",
            system=System(
                serial_no="SN-A-111",
                hardware=StateSystemHardware(
                    make="Delta",
                    model="DSC-1212"
                )
            ),
            refs={
                "zone_temp": RefDiscovery(point="ai_0"),
                "setpoint": RefDiscovery(point="av_1", writable=True)
            }
        )
        # Report back to the manager
        # The manager will wrap this in the envelope and publish to 'events/discovery'
        publish_func("BACnet-1001", event_a)

        time.sleep(1)

        # Simulate finding Device B
        LOGGER.info("   Found Device: BACnet-2002")
        event_b = DiscoveryEvents(
            addr="2002",
            system=System(
                serial_no="SN-B-222",
                hardware=StateSystemHardware(
                    make="Viconics",
                    model="VT7000"
                )
            )
        )
        publish_func("BACnet-2002", event_b)

        time.sleep(1)
        LOGGER.info("BACnet scan complete.")

    def stop_scan(self) -> None:
        LOGGER.info("Stopping scan.")

    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        return {}


if __name__ == "__main__":
    try:
        # 1. Endpoint
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

        # 2. Managers
        # We need LocalnetManager to hold the providers,
        # and DiscoveryManager to orchestrate the scan.
        localnet = LocalnetManager()
        discovery = DiscoveryManager()

        # Register our mock provider
        localnet.register_provider("bacnet", MockBacnetProvider())

        # 3. Create Device
        device = create_device(endpoint, managers=[localnet, discovery])

        # 4. Start
        t = threading.Thread(target=device.run, daemon=True)
        t.start()

        LOGGER.info(f"Gateway {GATEWAY_ID} running.")
        LOGGER.info("Waiting for discovery command...")

        print("\n" + "=" * 60)
        print("DEMO INSTRUCTIONS:")
        print(f"To trigger a scan, publish this JSON to: {TOPIC_PREFIX}{GATEWAY_ID}/commands/discovery")
        print("-" * 20)
        print('{ "families": ["bacnet"] }')
        print("-" * 20)
        print("mosquitto_pub command:")
        print(f"mosquitto_pub -h {MQTT_HOSTNAME} -p {MQTT_PORT} -u {BROKER_USERNAME} -P {BROKER_PASSWORD} -t '{TOPIC_PREFIX}{GATEWAY_ID}/commands/discovery' -m '{{ \"families\": [\"bacnet\"] }}'")
        print("=" * 60 + "\n")

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
