"""
Sample: Zero-Config mTLS with Auto-Generated Keys

This script demonstrates the "Just-in-Time" Provisioning capability.

SCENARIO:
1.  **Configuration**: We provide an EndpointConfiguration *without* an `auth_provider`.
2.  **Inference**: The library detects the missing auth and infers **mTLS** is required.
3.  **Detection**: It checks the current directory for default keys:
    - `rsa_private.pem` (Private Key)
    - `client_cert.pem` (Self-Signed X.509 Certificate)
4.  **Generation**: If these files are missing, the library **GENERATES** them automatically.
5.  **Connection**: It attempts to connect using these new credentials.

USAGE:
    - Run this script once to generate keys.
    - If connecting to a strict broker (like Mosquitto with `require_certificate true`),
      you must take the generated `client_cert.pem` and register/trust it on the server side.
"""

import logging
import os
import sys

from udmi.core.factory import ClientConfig
from udmi.core.factory import create_device
from udmi.core.messaging import TlsConfig
from udmi.schema import EndpointConfiguration

# --- Configuration ---
DEVICE_ID = "AHU-AUTO-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 8883
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

# Optional: Path to the Broker's CA Certificate.
# Required if the broker uses a self-signed server certificate (common in local tests).
# If connecting to a public broker with a valid CA (like Verisign), this can be None.
CA_CERT_FILE = "./ca.crt"

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("AutoMtls")

    try:
        # 1. Define Endpoint Config (No Auth Provider specified!)
        # The absence of 'auth_provider' triggers the auto-detection logic.
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"

        endpoint = EndpointConfiguration.from_dict({
            "client_id": client_id,
            "hostname": MQTT_HOSTNAME,
            "port": MQTT_PORT,
            "topic_prefix": TOPIC_PREFIX
            # "auth_provider": ...  <-- OMITTED INTENTIONALLY
        })

        logger.info("Initializing device with implicit mTLS configuration...")

        # 2. Configure Client Options (Server Trust)
        # We might need to trust the broker's CA, even if we are generating our own keys.
        client_config = None
        if CA_CERT_FILE and os.path.exists(CA_CERT_FILE):
            logger.info(f"Trusting server CA: {CA_CERT_FILE}")
            client_config = ClientConfig(
                tls_config=TlsConfig(ca_certs=CA_CERT_FILE)
            )
        else:
            logger.warning("No CA cert provided. Connection may fail if broker uses self-signed certs.")

        # 3. Create Device
        # The library will now scan for 'rsa_private.pem' and 'client_cert.pem'.
        # If missing, it will generate them using the DEVICE_ID as the Common Name (CN).
        device = create_device(endpoint, client_config=client_config)

        # 4. Check for Generated Files
        if os.path.exists("rsa_private.pem") and os.path.exists("client_cert.pem"):
            logger.info("Identity verified.")
            logger.info(f"   Private Key: {os.path.abspath('rsa_private.pem')}")
            logger.info(f"   Certificate: {os.path.abspath('client_cert.pem')}")
            logger.info("   (If connection fails, ensure the broker trusts this certificate)")

        # 5. Run
        logger.info("Starting connection loop...")
        device.run()

    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)