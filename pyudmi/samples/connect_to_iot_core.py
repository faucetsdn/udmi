import logging
import sys

from udmi.core import Device, create_mqtt_device_instance
from udmi.core.auth import JwtAuthProvider
from udmi.schema import EndpointConfiguration

# --- Configuration Constants ---
PROJECT_ID = "gcp-project-id"
REGION = "us-central1"
REGISTRY_ID = "ZZ-TRI-FECTA"
DEVICE_ID = "AHU-1"
MQTT_HOST = "mqtt.bos.goog"
MQTT_PORT = 8883  # secure port, mqtt client will automatically use TLS
ALGORITHM = "RS256"
PRIVATE_KEY_FILE = "/path/to/ZZ-TRI-FECTA/devices/AHU-1/rsa_private.pem"

CLIENT_ID = (
    f"projects/{PROJECT_ID}/locations/{REGION}/"
    f"registries/{REGISTRY_ID}/devices/{DEVICE_ID}"
)

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        auth_provider = JwtAuthProvider(
            project_id=PROJECT_ID,
            private_key_file=PRIVATE_KEY_FILE,
            algorithm=ALGORITHM
        )

        endpoint_config = EndpointConfiguration(
            client_id=CLIENT_ID,
            hostname=MQTT_HOST,
            port=MQTT_PORT
        )

        logging.info("Creating device instance using the factory...")

        device = create_mqtt_device_instance(
            device_class=Device,
            endpoint_config=endpoint_config,
            auth_provider=auth_provider
        )
        device.run()
    except Exception as e:
        logging.error(f"A critical error occurred: {e}", exc_info=True)
        sys.exit(1)

    logging.info("Device shut down gracefully. Exiting.")
