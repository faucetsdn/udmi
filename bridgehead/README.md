# Core UDMI Services Environment Set Up

This guide provides instructions for deploying the core UDMI services bundle. This bundle includes:
- Mosquitto broker
- etcd server 
- udmis service 
- validator service (includes registrar tool)

## Setting up the Docker environment

1. **Install docker engine:** Ensure Docker is installed and running on your system: `https://docs.docker.com/engine/install/`

2. **Navigate to project directory:** Open your terminal and change the directory to the project root containing the `docker-compose.yml` file (`bridgehead/`).

3. **Get default site model:** In you terminal, run `sudo git clone https://github.com/faucetsdn/udmi_site_model.git`.

4. **Add your host ip:** Open the docker-compose.yml file and locate the line `HOST_IP: <YOUR_IP>` inside the mosquitto service block. Replace `<YOUR_IP>` with your hosts ip address. You can find this by running `sudo hostname -I`. This is required in order tp allow connections to the broker externally from the docker compose environment. 

5. **Deploy the service:** Execute the following command to build the custom images (if needed) and start the containers in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`
6. **Confirm all containers are running:** Run `sudo docker ps` in the terminal, you should see the following containers in any order:
   - validator
   - udmis
   - mosquitto
   - etcd

## Tools

The UDMI tools should only be run after the udmis service has completed setup. You can confirm this by comparing your udmis output to the [sample udmis output](sample_outputs/udmis_output.md).

### Registrar

The following commands should be run in the same directory as the Docker Compose (`bridgehead/`).

In your terminal, execute `sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto`. To confirm a successful execution, take a look at the [sample registrar output](sample_outputs/registrar_output.md)

### Pubber

The pubber tool will only have a successful output after the registrar tool has been executed. This is done by default in compose setup. 

#### Setup if running pubber on separate machine

Pubber requires access to the site model. There are 2 ways to get pubber setup with a working site model.

1. The quickest setup is to copy across the site model from the `bridgehead/` directory after setup as this will already have all the necessary keys. (alternatively, push the changes to your own repository and pull this on the external computer)

2. Manually create the necessary keys:  *Note: these instructions are assuming you are using the default udmi_site_model*
    - On your external computer, clone the udmi site model and udmi: `sudo git clone https://github.com/faucetsdn/udmi_site_model.git`, `sudo git clone https://github.com/faucetsdn/udmi.git`.
    - Export your docker compose host ip (the same one we set in the docker compose): `export HOST_IP=<YOUR_HOST_IP>`
    - Generate keys: `sudo udmi/bin/keygen CA/<YOUR_HOST_IP> udmi_site_model/reflector` and `sudo udmi/bin/keygen CERT/<YOUR_HOST_IP> udmi_site_model/reflector`.

#### Run tool

Start pubber container: `sudo docker run -d --rm --name pubber -v $(realpath udmi_site_model):/root/site_model ghcr.io/faucetsdn/udmi:pubber-latest /bin/bash -c "tail -f /dev/null"`

Run pubber: `sudo docker exec pubber bin/pubber site_model/ //mqtt/<YOUR_HOST_IP> AHU-1 123456`

Pubber is running successfully if there are no obvious error messages or retries. An **unsuccessful** run will retry multiple times, will see messages like `Attempt #10 failed`. 

A successful run will not end on its own, you can press `Ctrl` + `C` on your keyboard to exit. 
## Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`

