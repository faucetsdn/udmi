import sys
import logging

from udmi.core import Device, create_mqtt_device_instance
from udmi.core.auth import BasicAuthProvider
from udmi.schema import EndpointConfiguration

DEVICE_ID = "AHU-1"
MQTT_HOSTNAME = "localhost"
MQTT_PORT = 1883

BROKER_USERNAME = "pyudmi-device"
BROKER_PASSWORD = "somesecurepassword"

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    try:
        auth_provider = BasicAuthProvider(
            username=BROKER_USERNAME,
            password=BROKER_PASSWORD
        )

        topic_prefix = "/r/ZZ-TRI-FECTA/d/"
        client_id = f"{topic_prefix}{DEVICE_ID}"

        endpoint_config = EndpointConfiguration(
            client_id=client_id,
            hostname=MQTT_HOSTNAME,
            port=MQTT_PORT,
            topic_prefix=topic_prefix
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
