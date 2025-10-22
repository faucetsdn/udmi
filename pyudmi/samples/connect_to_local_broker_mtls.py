import os
import sys
import logging

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from src.udmi.core import Device, create_mqtt_device_instance

from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 8883  # Use the mTLS secure port

CERT_DIR = "/path/to/certs"
CA_CERT_FILE = os.path.join(CERT_DIR, "ca.crt")
CLIENT_CERT_FILE = os.path.join(CERT_DIR, "client.crt")
CLIENT_KEY_FILE = os.path.join(CERT_DIR, "client.key")


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    topic_prefix = "/r/ZZ-TRI-FECTA/d/"
    client_id = f"{topic_prefix}{DEVICE_ID}"

    try:
        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix
        )

        logging.info("Creating mTLS device instance using the factory...")
        device = create_mqtt_device_instance(
            device_class=Device,
            endpoint_config=endpoint_config,
            ca_certs=CA_CERT_FILE,
            cert_file=CLIENT_CERT_FILE,
            key_file=CLIENT_KEY_FILE
        )
        device.run()
    except Exception as e:
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
