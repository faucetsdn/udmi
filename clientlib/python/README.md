# A Python Library for UDMI

This project is a high-level, extensible Python library designed to simplify the
development of applications for the **Universal Device Management Interface (
UDMI)**. It provides a clean, Pythonic abstraction over the core UDMI
specification, lowering the barrier to entry for developers and device
manufacturers seeking to create UDMI-compliant IoT solutions.

## Key Features

* **Strict Schema Adherence**: Ensures 1:1 compliance with the UDMI data model
  by using Python dataclasses that are auto-generated directly from the official
  UDMI JSON schemas.

* **Abstracted Complexity**: Handles core device functionalities like MQTT
  client communication, authentication (including certificate management), and
  the primary config/state message loop.

* **Extensible by Design**: Uses abstract base classes to allow for easy
  customization and support for future protocols or features.

The library acts as a reference implementation for demonstrating core
functionalities like connectivity, telemetry, and message handling.

---

## Usage Examples

### Local Setup

To install and build the library locally use the build script
```shell
${UDMI_ROOT}/clientlib/python/bin/build
```

---

Here are a few samples demonstrating how to connect a device using different
authentication methods.

### Example 1: Connect to Local MQTT Broker (Basic Auth)

This example demonstrates connecting to a local MQTT broker (like Mosquitto)
using basic username and password authentication.

```python
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
```

### Example 2: Connect to Local Broker (mTLS Auth)

This example shows how to establish a secure connection to a local broker using
mTLS (Mutual Transport Layer Security) with client-side certificates.

```python
import os
import sys
import logging

from udmi.core import Device, create_mqtt_device_instance
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
```

### Example 3: Connect to IoT Core (JWT Auth)

This example shows how to connect to a cloud-based MQTT broker like IoT Core,
which uses JWT (JSON Web Token) for authentication.

```python
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
```