# MQTT to Pub/Sub Bridge

This is a standalone tool to bridge messages from an MQTT broker to a Google Cloud Pub/Sub topic.

## Running the Bridge

To run the bridge in this open-source setup, you can use the `shadowJar` task to create a fat jar and then execute the class directly.

1.  **Build the shadow jar**:
    Run this command from the `udmis` directory:
    ```bash
    ./gradlew shadowJar
    ```

2.  **Run the bridge**:
    Execute the following command from the `udmis` directory (example with TLS and authentication):
    ```bash
    java -cp build/libs/udmis-1.0-SNAPSHOT-all.jar com.google.bos.udmi.service.bridge.MqttToPubSubBridge \
      --mqtt_broker_url=ssl://localhost:8883 \
      --mqtt_subscription_topic="/r/+/d/+/events/#" \
      --gcp_project_id=bos-platform-dev \
      --pubsub_topic_id=test-mqtt-to-pubsub \
      --mqtt_tls \
      --mqtt_ca_path=/path/to/ca.crt \
      --mqtt_client_cert_path=/path/to/rsa_private.crt \
      --mqtt_client_key_path=/path/to/rsa_private.pem \
      --mqtt_username=rocket \
      --mqtt_password=monkey
    ```

### Arguments

-   `--mqtt_broker_url`: MQTT broker URL (default: `tcp://localhost:1883`).
-   `--mqtt_subscription_topic`: MQTT subscription topic (default: `/r/+/d/#`).
-   `--gcp_project_id`: Google Cloud Project ID (required).
-   `--pubsub_topic_id`: Google Cloud Pub/Sub topic ID (required).
-   `--mqtt_tls`: Enable TLS for MQTT connection (flag, no value needed).
-   `--mqtt_ca_path`: Path to CA certificate for TLS.
-   `--mqtt_username`: MQTT username for authentication.
-   `--mqtt_password`: MQTT password for authentication.
-   `--mqtt_client_cert_path`: Path to client certificate for TLS.
-   `--mqtt_client_key_path`: Path to client private key for TLS.

## Verification

You can verify that messages are flowing by checking the Pub/Sub subscription in the GCP console:
https://pantheon.corp.google.com/cloudpubsub/subscription/detail/<subscription-id>?project=<project-id>&tab=messages
