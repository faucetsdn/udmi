"""
Sample: Connect using Explicit mTLS Certificates

This script demonstrates how to connect to an MQTT broker using specific,
pre-existing X.509 certificates.

Use Case:
- Connecting to a local aggregator or private broker (Mosquitto/VerneMQ).
- Devices that have certificates provisioned during manufacturing (PKI).
- Scenarios where certificates are stored in non-standard paths.

Prerequisite:
- A valid Certificate Authority (CA) file (ca.crt).
- A client certificate signed by that CA (client.crt).
- The corresponding client private key (client.key).
"""

import logging
import os
import sys

from udmi.core.factory import ClientConfig
from udmi.core.factory import create_device
from udmi.core.messaging import TlsConfig
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 8883
TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

# Paths to your pre-existing certificates
# Ensure these files exist before running!
CERT_DIR = "./certs"
CA_CERT_FILE = os.path.join(CERT_DIR, "ca.crt")
CLIENT_CERT_FILE = os.path.join(CERT_DIR, "client.crt")
CLIENT_KEY_FILE = os.path.join(CERT_DIR, "client.key")

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("MtlsConnect")

    try:
        client_id = f"{TOPIC_PREFIX}{DEVICE_ID}"

        # 1. Define Endpoint (No Auth Provider)
        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=TOPIC_PREFIX
        )

        logger.info("Initializing device with explicit mTLS certificates...")
        logger.info(f"  CA Cert:     {os.path.abspath(CA_CERT_FILE)}")
        logger.info(f"  Client Cert: {os.path.abspath(CLIENT_CERT_FILE)}")
        logger.info(f"  Client Key:  {os.path.abspath(CLIENT_KEY_FILE)}")

        # 2. Configure TLS explicitly
        # This tells the library exactly which files to use for the handshake.
        tls_config = TlsConfig(
            ca_certs=CA_CERT_FILE,
            cert_file=CLIENT_CERT_FILE,
            key_file=CLIENT_KEY_FILE,
        )

        # 3. Create Device
        # Note: We do NOT pass `key_file` as a direct argument to create_device.
        # This prevents the CredentialManager from trying to auto-generate a key.
        # Instead, we pass our explicit `tls_config`.
        device = create_device(
            endpoint_config=endpoint_config,
            client_config=ClientConfig(tls_config=tls_config)
        )

        # 4. Run
        logger.info(f"Connecting to {MQTT_HOSTNAME}:{MQTT_PORT}...")
        device.run()

    except FileNotFoundError as e:
        logger.error(f"Certificate file not found: {e}")
        logger.error("Please ensure you have created the './certs' directory and populated it.")
        sys.exit(1)
    except Exception as e:
        logger.critical(f"Critical error: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Device shut down gracefully.")
