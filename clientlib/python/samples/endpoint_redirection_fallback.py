"""
Sample: Endpoint Redirection Fallback (Local Broker -> GCP IoT Core -> Local Broker)

This script demonstrates what happens when "Dynamic Reconfiguration" fails.
1. Starts connected to a Local Broker (Basic Auth).
2. Prints a "Trigger Payload" (Config message) to the console.
3. When you publish that payload to the local broker, the device will:
   a. Detect the new endpoint configuration in the blobset.
   b. Verify the blob's SHA256 hash.
   c. Disconnect from Local Broker.
   d. Re-initialize the network stack using JWT Auth.
   e. Try to connect to GCP IoT Core.
4. If connection fails after retries, the device falls back to the original
endpoint configuration and connects to the Local Broker.
"""

import base64
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

# --- 2. CONFIGURATION: TARGET (GCP IoT Core) ---
# Update these with your actual GCP details
GCP_PROJECT_ID = "gcp-project-id"
GCP_REGION = "us-central1"
GCP_REGISTRY = "ZZ-TRI-FECTA"
GCP_DEVICE_ID = "AHU-1"
GCP_HOSTNAME = "mqtt.bos.goog"
GCP_PORT = 8883

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("RedirectFallbackDemo")


def generate_trigger_payload(target_endpoint: Dict[str, Any]) -> str:
    """
    Generates the UDMI Config message containing the blobset
    that points to the new endpoint.
    """
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


import datetime

if __name__ == "__main__":
    try:
        # --- PREPARE TARGET CONFIG ---
        # This is the config for GCP IoT Core
        gcp_client_id = f"projects/{GCP_PROJECT_ID}/locations/{GCP_REGION}/registries/{GCP_REGISTRY}/devices/{GCP_DEVICE_ID}"

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

        # --- GENERATE & PRINT TRIGGER ---
        trigger_json = generate_trigger_payload(target_endpoint_dict)

        print("\n" + "=" * 80)
        print("DEMO INSTRUCTIONS:")
        print("1. The device is connecting to LOCALHOST.")
        print(
            "2. To trigger the redirect to GCP, publish the following JSON to:")
        print(f"   Topic: {LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print("You can use mosquitto_pub:")
        print(
            f"mosquitto_pub -h {LOCAL_HOSTNAME} -t '{LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config' -m '{trigger_json}'")
        print("=" * 80 + "\n")

        # --- START DEVICE WITH SOURCE CONFIG ---
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

        # We do not pass key_file - the factory creates a private key for the
        # device but since the public key is not configured in the IoT Core,
        # the connection would fail.
        device = create_device(source_endpoint)

        LOGGER.info("Starting Device on Local Broker...")
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
