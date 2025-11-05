# Core UDMI Services Environment Set Up

This guide provides instructions for deploying the core UDMI services bundle. This bundle includes:
- Mosquitto broker
- etcd server 
- udmis service 
- validator (registrar) tool 
- pubber tool

## Setting up the Docker environment

1. **Install docker engine:** Ensure Docker is installed and running on your system: `https://docs.docker.com/engine/install/`

2. **Navigate to project directory:** Open your terminal and change the directory to the project root containing the `docker-compose.yml` file (`bridgehead/`).

3. **Get default site model:** In you terminal, run `git clone https://github.com/faucetsdn/udmi_site_model.git`.
4. **Deploy the service:** Execute the following command to build the custom images (if needed) and start the containers in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`
5. **Confirm all containers are running:** Run `sudo docker ps` in the terminal, you should see the following containers in any order:
   - pubber
   - validator
   - udmis
   - mosquitto
   - etcd

## Tools

The UDMI tools should only be run after the udmis service has completed setup. You can confirm this by comparing your udmis output to the [sample udmis output](sample_outputs/udmis_output.md). Tools should be run in the same directory as the Docker Compose (`bridgehead/`).

### Registrar (validator)

In your terminal, execute `sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto`. To confirm a successful execution, take a look at the [sample registrar output](sample_outputs/registrar_output.md)

### Pubber

The pubber tool will only have a successful output after the registrar tool has been executed. In your terminal, run `sudo docker exec pubber bin/pubber site_model/ //mqtt/mosquitto AHU-1 123456` (`123456` can be replaced with any serial number). 

Pubber is running successfully if there are no obvious error messages or retries. An **unsuccessful** run will retry multiple times, will see messages like `Attempt #10 failed`. 

A successful run will not end on its own, you can press `Ctrl` + `C` on your keyboard to exit. 

## Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`