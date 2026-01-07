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

from udmi.core.factory import ClientConfig, create_device
from udmi.core.messaging import TlsConfig
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 8883

CERT_DIR = "./certs"
CA_CERT_FILE = os.path.join(CERT_DIR, "ca.crt")
CLIENT_CERT_FILE = os.path.join(CERT_DIR, "client.crt")
CLIENT_KEY_FILE = os.path.join(CERT_DIR, "client.key")

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger("MtlsConnect")

    try:
        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix,
        )

        logger.info("Initializing device with explicit mTLS certificates...")

        tls_config = TlsConfig(
            ca_certs=CA_CERT_FILE,
            cert_file=CLIENT_CERT_FILE,
            key_file=CLIENT_KEY_FILE,
        )

        device = create_device(
            endpoint_config=endpoint_config,
            client_config=ClientConfig(tls_config=tls_config)
        )

        logger.info(f"Connecting to {MQTT_HOSTNAME}:{MQTT_PORT}...")
        device.run()

    except FileNotFoundError as e:
        logger.error("Certificate file not found!")
        sys.exit(1)
    except Exception as e:
        logger.critical(f"Critical error: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Device shut down gracefully.")
