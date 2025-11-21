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

3. **Get default site model:** In you terminal, run `sudo git clone https://github.com/faucetsdn/udmi_site_model.git`.

4. **Add your host ip:** Open the docker-compose.yml file and locate the line `HOST_IP: <YOUR_IP>` inside the mosquitto service block. Replace `<YOUR_IP>` with your hosts ip address. You can find this by running `sudo hostname -I`. If you do not plan on using pubber outside of the docker container, you can remove the environment option from the compose file.

5. **Deploy the service:** Execute the following command to build the custom images (if needed) and start the containers in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`
6. **Confirm all containers are running:** Run `sudo docker ps` in the terminal, you should see the following containers in any order:
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

The pubber tool will only have a successful output after the registrar tool has been executed. This is done by default in compose settup. 

#### Local (docker compose) 
- In your terminal, run `sudo docker exec pubber bin/pubber site_model/ //mqtt/mosquitto AHU-1 123456` (`123456` can be replaced with any serial number). 

#### Running pubber on seperate machine
- On your external computer, clone th udmi site model: `https://github.com/faucetsdn/udmi.git`.
- Export your host ip (the same one we set in the docker compose): `EXPORT HOST_IP=<YOUR_HOST_IP>`
- Pull your udmi site model, the default is the same we used earlier: `sudo git clone https://github.com/faucetsdn/udmi_site_model.git`
- Generate keys: `udmi/bin/keygen CA/<YOUR_HOST_IP> udmi_site_model/reflector` and `udmi/bin/keygen CERT/<YOUR_HOST_IP> udmi_site_model/reflector`.
- Run pubber container: `sudo docker run -d --rm --name externalPubber -v $(realpath udmi_site_model):/root/site_model ghcr.io/faucetsdn/udmi:pubber-latest /bin/bash -c "tail -f /dev/null"`
- Run pubber: `sudo docker exec externalPubber bin/pubber site_model/ //mqtt/<YOUR_HOST_IP> AHU-1 123456`

    *Note:* You can name the external pubber container anything, as long is it doesnt match of of your other containers. I this case, its assumed you will still have the pubber container in the docker compose file, therfore the new container cannot also be called pubber.  

Pubber is running successfully if there are no obvious error messages or retries. An **unsuccessful** run will retry multiple times, will see messages like `Attempt #10 failed`. 

A successful run will not end on its own, you can press `Ctrl` + `C` on your keyboard to exit. 

### Discovery

Run the following commands to complete a discovery sequence:
```
sudo docker exec validator /bin/bash -c "bin/registrar site_model/ //mqtt/mosquitto -x -d && bin/registrar site_model/ //mqtt/mosquitto GAT-123"

sudo docker exec -d pubber /bin/bash -c "bin/pubber site_model/ //mqtt/mosquitto GAT-123 852649" 

sudo docker exec validator /root/discovery.sh

sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto
```

A successful run using the default udmi_site_model should produce the following on the final registry output:
```
Summary:
  Device envelope: 1
  Device extra: 6
  Device proxy: 2
  Device status: 4
  Device validation: 1
Out of 4 total.
```

## Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`