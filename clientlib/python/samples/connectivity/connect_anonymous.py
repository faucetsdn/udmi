"""
Sample: Connect to Generic Broker (Anonymous / No Auth)

This script demonstrates how to connect to a standard MQTT broker
WITHOUT using any authentication (Username/Password, Certificates, or JWT).

SCENARIO:
1.  **Configuration**: Defines an endpoint on Port 1883 (Standard MQTT).
2.  **Explicit No-TLS**: We explicitly disable TLS in the `ClientConfig`.
    * Without this, the factory defaults to mTLS when no Auth Provider is found.
    * Disabling TLS prevents the client from attempting an SSL handshake on a cleartext port.
3.  **Connection**: The device connects using plain TCP.

PREREQUISITE:
- A broker (e.g. Mosquitto) running on localhost:1883.
- The broker must allow anonymous connections (`allow_anonymous true` in mosquitto.conf).
"""

import logging
import sys

from udmi.core.factory import ClientConfig
from udmi.core.factory import create_device
from udmi.core.messaging import TlsConfig
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883  # 1883 implies Non-Secure / No TLS

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("AnonymousConnect")

    try:
        # 1. Define the Endpoint
        # Note: We do NOT provide an 'auth_provider'.
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix
        )

        # 2. Configure Client Options
        # CRITICAL: We must explicitly disable TLS.
        # Otherwise, the library assumes "No Auth Provider" means "Use mTLS"
        # and will try to perform an SSL handshake, which fails on port 1883.
        client_config = ClientConfig(
            tls_config=TlsConfig(enable_tls=False)
        )

        logger.info(
            f"Initializing device {DEVICE_ID} (Anonymous @ {MQTT_HOSTNAME}:{MQTT_PORT})...")

        # 3. Create the Device
        device = create_device(endpoint_config, client_config=client_config)

        # 4. Start the Loop
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