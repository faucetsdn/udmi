# MQTT Broker Environment Setup

This guide provides instructions for deploying the core udmi services bundle. This bundle includes:
- Mosquitto broker
- etcd server 
- udmis service 
- validator (registrar) tool 
- pubber tool

### Setting up the docker environment

1. **Install docker engine:** Ensure Docker is installed and running on your system: `https://docs.docker.com/engine/install/`

2. **Navigate to project directory:** Open your terminal and change the directory to the project root containing the `docker-compose.yml` file (`bridgehead/`).

3. **Get default site model:** In you terminal, run `git clone https://github.com/faucetsdn/udmi_site_model.git`.
4. **Deploy the service:** Execute the following command to build the custom image (if needed) and start the Mosquitto container in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`
5. **Confirm all containers are running:** Run `docker ps` in the terminal, you should see the following containers in any order:
   - pubber
   - validator
   - udmis
   - mosquitto
   - etcd

### Running tools

You should wait at least 30s before running these commands to ensure the services are up and running first.

- **Registrar (validator)**
  - In your terminal, execute `sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto`
    - [sample registrar output](../docs/udmis/registrar_output.md)

- **Pubber**
  - In your terminal, execute `sudo docker exec pubber bin/pubber site_model/ //mqtt/mosquitto AHU-1 123456`
    - [sample pubber output](../docs/udmis/pubber_output.md)

### Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`
