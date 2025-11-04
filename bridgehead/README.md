# MQTT Broker Environment Setup

This guide provides instructions for deploying and verifying the **Mosquitto MQTT broker** using Docker Compose.

### Broker access details

- **Broker address**:  The host machine's local IP address (e.g., `localhost` or `127.0.0.1`).
- **Port**: `1883`

The configuration is designed for connections without client credentials.

### Setting up the docker environment

1.  **Install docker engine:** Ensure Docker is installed and running on your system: `https://docs.docker.com/engine/install/`

2.  **Navigate to project directory:** Open your terminal and change the directory to the project root containing the `docker-compose.yml` file (`bridgehead/`).

3.  **Deploy the service:** Execute the following command to build the custom image (if needed) and start the Mosquitto container in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`

4.  **Run connectivity check:** Confirm the broker is up and running by executing `sudo docker compose exec mosquitto /usr/local/bin/check_mqtt.sh`. This script performs an internal publish/subscribe self-test. A successful output confirms the broker is fully operational and accepting client connections.

### Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`
