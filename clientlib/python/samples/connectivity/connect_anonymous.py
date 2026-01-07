"""
Sample: Connect to Generic Broker (Anonymous / No Auth)

This script demonstrates how to connect to a standard MQTT broker
WITHOUT using any authentication (Username/Password, Certificates, or JWT).

Use Case:
- Local testing with a default Mosquitto container (`allow_anonymous true`).
- Internal networks where network-level security is assumed.
- Debugging basic connectivity issues without auth complexity.

Behavior:
- Because the port is 1883 (Standard MQTT), the library infers that TLS
  is NOT required.
- It skips looking for or generating 'rsa_private.pem'.
- It connects over plain TCP.
"""

import logging
import sys

from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883  # 1883 implies Non-Secure / No TLS

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("AnonymousConnect")

    try:
        # 1. Define the Endpoint
        # We use Port 1883 and NO auth_provider.
        # The library detects the insecure port and disables the mTLS auto-generation logic.
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": topic_prefix,
            # "auth_provider": None  <-- Defaults to None, meaning Anonymous on port 1883
        })

        logger.info(
            f"Initializing device {DEVICE_ID} (Anonymous @ {MQTT_HOSTNAME}:{MQTT_PORT})...")

        # 2. Create the Device
        # Factory sees Port 1883 -> Skips SSL Context -> Defaults to Anonymous
        device = create_device(endpoint_config)

        # 3. Start the Loop
        logger.info("Starting main event loop...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Connection failed: {e}", exc_info=True)
        logger.warning(
            "TIP: Ensure your broker is configured with 'allow_anonymous true'.")
        sys.exit(1)

    logger.info("Device shut down gracefully.")