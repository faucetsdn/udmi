"""
Sample: Endpoint Redirection (Local Broker -> GCP IoT Core)

This script demonstrates the "Dynamic Reconfiguration" feature.

SCENARIO:
1.  **Bootstrap**: The device connects to a Local Broker using Basic Auth.
    (This simulates a factory or staging environment).
2.  **Trigger**: You publish a Config message to the local broker containing
    a `blobset`. This blob contains the JSON configuration for the *target*
    endpoint (GCP IoT Core).
3.  **Redirection**:
    a. The `Device` detects the new `iot_endpoint_config` blob.
    b. It downloads the blob (Data URI) and verifies the SHA256 hash.
    c. It saves the new endpoint to persistence (`.udmi_persistence.json`).
    d. It triggers a `ConnectionReset`.
    e. It restarts the network stack, this time using the NEW endpoint
       and switching from Basic Auth to JWT Auth automatically.

USAGE:
1.  Configure the `GCP_*` constants below to match your real IoT Core registry.
2.  Run the script.
3.  Copy the `mosquitto_pub` command printed to the console and run it in your terminal.
4.  Watch the device disconnect from localhost and connect to Google Cloud.
"""

import base64
import datetime
import hashlib
import json
import logging
import sys
from typing import Any
from typing import Dict

from udmi.constants import IOT_ENDPOINT_CONFIG_BLOB_KEY
from udmi.core.factory import create_device
from udmi.schema import AuthProvider
from udmi.schema import Basic
from udmi.schema import EndpointConfiguration

# --- 1. CONFIGURATION: SOURCE (Local Broker / Factory) ---
LOCAL_DEVICE_ID = "AHU-1"
LOCAL_HOSTNAME = "localhost"
LOCAL_PORT = 1883
LOCAL_USERNAME = "pyudmi-device"
LOCAL_PASSWORD = "somesecureword"
LOCAL_TOPIC_PREFIX = "/r/ZZ-TRI-FECTA/d/"

# --- 2. CONFIGURATION: TARGET (Production / GCP IoT Core) ---
# ! UPDATE THESE TO MATCH YOUR REAL GCP RESOURCES !
GCP_PROJECT_ID = "your-gcp-project"
GCP_REGION = "us-central1"
GCP_REGISTRY = "ZZ-TRI-FECTA"
GCP_DEVICE_ID = "AHU-1"
GCP_HOSTNAME = "mqtt.googleapis.com"
GCP_PORT = 8883

# Path to the key used for JWT generation.
# The library will GENERATE this file if it doesn't exist during startup.
KEY_FILE = "rsa_private.pem"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("RedirectDemo")


def generate_trigger_payload(target_endpoint: Dict[str, Any]) -> str:
    """
    Generates the UDMI Config message containing the blobset
    that points to the new endpoint.
    """
    # 1. Serialize the target config
    target_json = json.dumps(target_endpoint)
    target_bytes = target_json.encode('utf-8')

    # 2. Calculate Hash (Integrity Check)
    sha256_hash = hashlib.sha256(target_bytes).hexdigest()

    # 3. Create Data URI (The "Blob")
    b64_data = base64.b64encode(target_bytes).decode('utf-8')
    data_uri = f"data:application/json;base64,{b64_data}"

    # 4. Construct UDMI Config
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

        # --- GENERATE & PRINT TRIGGER ---
        trigger_json = generate_trigger_payload(target_endpoint_dict)

        print("\n" + "=" * 80)
        print("DEMO INSTRUCTIONS:")
        print("1. The device is connecting to LOCALHOST (Factory Mode).")
        print("2. To trigger the redirect to GCP, publish the following JSON to:")
        print(f"   Topic: {LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config")
        print("-" * 20)
        print(trigger_json)
        print("-" * 20)
        print("Copy/Paste for Mosquitto:")
        print(f"mosquitto_pub -h {LOCAL_HOSTNAME} -p {LOCAL_PORT} -u {LOCAL_USERNAME} -P {LOCAL_PASSWORD} -t '{LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}/config' -m '{trigger_json}'")
        print("=" * 80 + "\n")

        # --- START DEVICE WITH SOURCE CONFIG ---
        source_endpoint = EndpointConfiguration(
            client_id=f"{LOCAL_TOPIC_PREFIX}{LOCAL_DEVICE_ID}",
            hostname=LOCAL_HOSTNAME,
            port=LOCAL_PORT,
            topic_prefix=LOCAL_TOPIC_PREFIX,
            auth_provider=AuthProvider(
                basic=Basic(
                    username=LOCAL_USERNAME,
                    password=LOCAL_PASSWORD
                )
            )
        )

        # CRITICAL: We pass `key_file` here.
        # Even though the INITIAL connection (Basic Auth) doesn't use it,
        # the factory uses this to initialize the CredentialManager.
        # This ensures that when the device switches to the GCP endpoint (JWT),
        # it has the key ready (or generates it now).
        device = create_device(source_endpoint, key_file=KEY_FILE)

        LOGGER.info("Starting Device on Local Broker...")
        device.run()

    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")
    except Exception as e:
        LOGGER.critical(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)
