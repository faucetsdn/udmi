"""
Sample: Endpoint Redirection Fallback (Local Broker -> GCP IoT Core -> Local Broker)

This script demonstrates the "Safe Migration" capability:
If a device is redirected to a new endpoint but cannot connect, it automatically
reverts to its last known good configuration.

SCENARIO:
1.  **Bootstrap**: Device connects to Local Broker (Factory Mode).
2.  **Trigger**: You publish a Config redirecting it to GCP IoT Core.
3.  **Failure**: The device generates a new key pair (or uses an existing one),
    but because this specific key is NOT registered in the Cloud Console,
    authentication fails.
4.  **Fallback**:
    a. The device attempts to connect to GCP 3 times.
    b. After max retries, it declares the new endpoint "Unreachable".
    c. It clears the new config from persistence.
    d. It reconnects to the Local Broker.

USAGE:
1.  Run the script.
2.  Publish the JSON trigger to the local broker.
3.  Watch the logs:
    - "New endpoint configuration detected..."
    - "Connecting to mqtt.googleapis.com..."
    - "Client disconnected with code: 5" (Auth Error)
    - "Max connection failures reached."
    - "Fallback endpoint will be: localhost"
    - "Connection successful."
"""

import base64
import datetime
import hashlib
import json
import logging
from typing import Any
from typing import Dict

from udmi.constants import IOT_ENDPOINT_CONFIG_BLOB_KEY
from udmi.core.factory import create_device
from udmi.schema import EndpointConfiguration

# --- 1. CONFIGURATION: SOURCE (Local Broker) ---
LOCAL_DEVICE_ID = "AHU-1"
LOCAL_HOSTNAME = "localhost"
LOCAL_PORT = 1883
LOCAL_USERNAME = "pyudmi-device"
LOCAL_PASSWORD = "somesecureword"
LOCAL_TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

# --- 2. CONFIGURATION: TARGET (GCP IoT Core - Intended to Fail) ---
# We use real GCP params so the device *tries* to connect,
# but since we won't upload the key, it will fail auth.
GCP_PROJECT_ID = "your-gcp-project"
GCP_REGION = "us-central1"
GCP_REGISTRY = "ZZ-TRI-FECTA"
GCP_DEVICE_ID = "AHU-1"
GCP_HOSTNAME = "mqtt.googleapis.com"
GCP_PORT = 8883

# We provide a key file so the device CAN initialize the JWT stack.
# Since we won't register this key in the cloud, auth will fail.
KEY_FILE = "rsa_private_fallback.pem"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("RedirectFallbackDemo")


def generate_trigger_payload(target_endpoint: Dict[str, Any]) -> str:
    """Generates the UDMI Config message with the new endpoint blob."""
    target_json = json.dumps(target_endpoint)
    target_bytes = target_json.encode('utf-8')
    sha256_hash = hashlib.sha256(target_bytes).hexdigest()
    b64_data = base64.b64encode(target_bytes).decode('utf-8')
    data_uri = f"data:application/json;base64,{b64_data}"

    config_payload = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "version": "1",
        "blobset": {
            "blobs": {
                IOT_ENDPOINT_CONFIG_BLOB_KEY: {
                    "phase": "final",
                    "url": data_uri,
                    "sha256": sha256_hash,
                    "generation": datetime.datetime.now().isoformat()
                }
            }
        }
    }
    return json.dumps(config_payload, indent=2)


if __name__ == "__main__":
    try:
        # --- PREPARE TARGET CONFIG ---
        gcp_client_id = (
            f"projects/{GCP_PROJECT_ID}/locations/{GCP_REGION}/"
            f"registries/{GCP_REGISTRY}/devices/{GCP_DEVICE_ID}"
        )

        target_endpoint_dict = {
            "client_id": gcp_client_id,
            "hostname": GCP_HOSTNAME,
            "port": GCP_PORT,
            "auth_provider": {
                "jwt": {
                    "audience": GCP_PROJECT_ID
                }
            }
        }

        trigger_json = generate_trigger_payload(target_endpoint_dict)

        print("\n" + "=" * 80)
        print("DEMO INSTRUCTIONS:")
        print("1. The device connects to LOCALHOST.")
        print("2. Publish this trigger to redirect it to GCP (where it will fail):")
        print(f"   Topic: {LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print(f"mosquitto_pub -h {LOCAL_HOSTNAME} -p {LOCAL_PORT} -u {LOCAL_USERNAME} -P {LOCAL_PASSWORD} -t '{LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config' -m '{trigger_json}'")
        print("=" * 80 + "\n")

        # --- START DEVICE ---
        source_endpoint = EndpointConfiguration.from_dict({
            "client_id": f"{LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}",
            "hostname": LOCAL_HOSTNAME,
            "port": LOCAL_PORT,
            "topic_prefix": LOCAL_TOPIC_PREFIX,
            "auth_provider": {
                "basic": {
                    "username": LOCAL_USERNAME,
                    "password": LOCAL_PASSWORD
                }
            }
        })

        # We must provide key_file so the device *can* attempt JWT auth later.
        device = create_device(source_endpoint, key_file=KEY_FILE)

        LOGGER.info("Starting Device on Local Broker...")
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
