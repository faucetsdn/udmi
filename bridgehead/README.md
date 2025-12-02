# Core UDMI Services Environment Set Up

This guide provides instructions for deploying the core UDMI services bundle. This bundle includes:
- Mosquitto broker
- etcd server 
- udmis service 
- validator service (includes registrar tool)
- InfluxDB timeseries database
- Grafana visualisation tool

*Note: docker logs display file paths as seen inside the container. These are internal paths and do not match your local machine's file system. Files with local access exist via volume mapping; check your docker-compose.yml for the corresponding host path. Example: The common container path /root/site_model/ exists locally under bridgehead/udmi_site_model*

## Setting up the Docker environment

1. **Install docker engine:** Ensure Docker is installed and running on your system: `https://docs.docker.com/engine/install/`

2. **Navigate to project directory:** Open your terminal and change the directory to the project root containing the `docker-compose.yml` file (`bridgehead/`).

3. **Get default site model:** In you terminal, run `sudo git clone https://github.com/faucetsdn/udmi_site_model.git`.

4. **Edit compose file:** Open the docker-compose.yml file in your chosen editor.
    1. **Add Host IP:** Locate the line `HOST_IP: <YOUR_IP>` inside the **mosquitto** service block. Replace `<YOUR_IP>` with your hosts ip address. You can find this by running `sudo hostname -I`. This is required in order tp allow connections to the broker externally from the docker compose environment. 
    2. **Update InfluxDB Credentials:** Under the **influxdb** service, under the environment variables, set the values of `DOCKER_INFLUXDB_INIT_USERNAME`, `DOCKER_INFLUXDB_INIT_PASSWORD` and `DOCKER_INFLUXDB_INIT_ADMIN_TOKEN`. For `DOCKER_INFLUXDB_INIT_ADMIN_TOKEN`, run `openssl rand -hex 32`.
    3. **Update Grafana credentials:** Under the **grafana** service, set the values of `GF_SECURITY_ADMIN_USER` and `GF_SECURITY_ADMIN_USER`. Update the value of `INFLUXDB_TOKEN` with the token generated in step 2.
    4. **Update udmis credentials:** Under the **udmis** service, set the value of `INFLUXDB_TOKEN` with the token generated in step 2.

5. **Deploy the service:** Execute the following command to build the custom images (if needed) and start the containers in detached mode.
    * **First time/after changes:** Run `sudo docker compose up -d --build`
    * **Standard run:** Run `sudo docker compose up -d`
    
6. **Confirm all containers are running:** Run `sudo docker ps` in the terminal, you should see the following containers in any order:
   - validator
   - udmis
   - mosquitto
   - etcd
   - grafana
   - influxdb

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

You can stop the pubber container by running `sudo docker stop pubber`

### Validator

To run the validator service in the background, execute `sudo docker exec -d validator bin/validator site_model/ //mqtt/mosquitto`

## Discovery

Run the following commands to complete a discovery sequence: 

*Note: Make sure to update <YOUR_HOST_IP> with the ip we set in the compose file.*
```
sudo docker exec validator /bin/bash -c "bin/registrar site_model/ //mqtt/mosquitto -x -d && bin/registrar site_model/ //mqtt/mosquitto GAT-123"

sudo docker run -d --rm --name pubber -v $(realpath udmi_site_model):/root/site_model ghcr.io/faucetsdn/udmi:pubber-latest /bin/bash -c "tail -f /dev/null"

sudo docker exec -d pubber /bin/bash -c "bin/pubber site_model/ //mqtt/<YOUR_HOST_IP> GAT-123 852649" 

sudo docker exec validator /root/discovery.sh

sudo docker exec validator bin/registrar site_model/ //mqtt/mosquitto

sudo docker stop pubber
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

## Diagnostics

### InfluxDB

1. **Access:** Navigate to the InfluxDB endpoint at `http://localhost:8086`.
2. **Login:** Use the credentials specified in the Docker Compose file.
3. **Query:** Go to the Data Explorer (left panel). Locate the bridgehead bucket at the bottom to begin querying data.

### Grafana 

1. **Access:** Navigate to the InfluxDB endpoint at `http://localhost:3000`.
2. **Login:** Use the credentials specified in the Docker Compose file.
3. **View Dashboard:**  Select Dashboards from the left-hand menu. The pre-configured `bridgehead` dashboard includes a basic overview of the running services.

#### Alerts

The following instructions will setup an alert in Grafana for when a container goes down.

1. **Create new contact point:** 
    1. In the left-hand pane go to `Alerting -> Contact points`
    2. Navigate to `https://webhook.site` and copy the URL under "Your unique URL".
    3. In Grafana, click "Create contact point", name it Webhook, set the type to webhook and paste in the URL you just copied. Test your webhook with the test button, you should see a test webhook appear in `https://webhook.site`. Save the new contact point.
2. **Create new alert rule:** 
    1. In the left-hand pane go to `Alerting -> Alert rules`. 
    2. Click on "New alert rule" and name the rule `Container Down`.
    3. Set query data source to be InfluxDB, then paste the following flux query:
    ```
    from(bucket: "home")
    |> range(start: -5m) 
    |> filter(fn: (r) => r["_measurement"] == "docker_container_status")
    |> filter(fn: (r) => r["_field"] == "uptime_ns") 
    |> filter(fn: (r) => r["com.docker.compose.project"] == "bridgehead")
    |> group(columns: ["container_name"]) 
    |> last()
    |> map(fn: (r) => ({
        r with
        alert_value: if r.container_status == "exited" or r.container_status == "dead" then 1.0 else 0.0
    }))
    |> keep(columns: ["container_name", "alert_value"]) 
    |> group()
    ```

    This query summarizes the status of Docker containers. It finds the most recent status for each container and outputs a value of 1.0 if the container is currently 'exited' or 'dead', and 0.0 otherwise. This binary output is then used to trigger the alert.

    4. Set the `Alert condition` to be `WHEN Last OF QUERY IS EQUAL TO 1`. When you click `Preview alert rule condition` you should see each container in a "Normal" state.
    5. In section 3, `Add folder and labels`, create a new folder "Alerts" and select this folder.
    6. in section 4, `Set evaluation behaviour`, create an evaluation group "Bridgehead Alerts" and set the evaluation interval to 30s. Set the pending period to 30s, Keep firing for to 0s.
    7. In section 5, select "Webhook" as the contact point and then save.
   
3. **Edit default notification policy:** By default a notification won’t be sent for 5 min. Update this by going to `Alerting -> Notification policies` edit the default policy so that the Group interval is set to 30s, and update.

4. **Test:** Go back to the bridgehead dashboard and confirm all containers are running. In your terminal, run `sudo docker stop validator`. Keep an eye on the validator container, it should go red and show an uptime of 0s. Navigate to `https://webhook.site`, you should have received a notification with the line `"title": "[FIRING:1] Container Down Alerts (validator)",` (This could take a minute or two).
7. Run validator container again with `sudo docker start validator`

Use the same method to setup any number of alerts, you can find useful information here: https://grafana.com/docs/grafana/latest/alerting/
    

## Shutting down the docker environment

To gracefully stop and remove the container, run: `sudo docker compose down`