# Discovery Node

## Notes

`vendor` discovery family is a counter which increments every second

## Standalone

**TODO** - Create `bin/setup` script

1.  Setup a python virtual environment and install the required dependancies

    ```shell
    cd PATH_TO_UDMI/misc/discoverynode/
    python3 -m venv venv
    venv/bin/python3 -m pip install -r src/requirements.txt
    ```

2. Create a JSON configuration file

    Sample Clearblade configuration file

    ```json
    {
        "mqtt": {
            "device_id": "AHU-1",
            "host": "us-central1-mqtt.clearblade.com",
            "port": 8883,
            "registry_id": "discovery",
            "region": "us-central1",
            "project_id": "discovery",
            "key_file": "/workspaces/udmi/sites/udmi_site_model/devices/AHU-1/rsa_private.pem",
            "algorithm": "RS256",
        },
        "udmi":{"discovery": {"ipv4":"false", "ethmac": false, "bacnet": false}},
        "bacnet": {"ip": "192.168.11.251"}
    }
    ```

    Local UDMIS (complete)

    ```json
    {
        "mqtt": {
        "device_id": "GAT-1",
        "host": "localhost",
        "port": 8883,
        "registry_id": "ZZ-TRI-FECTA",
        "region": "us-central1",
        "project_id": "localhost",
        "key_file": "/workspaces/udmi/sites/udmi_site_model/devices/GAT-1/rsa_private.pem",
        "ca_file": "/workspaces/udmi/sites/udmi_site_model/reflector/ca.crt",
        "cert_file": "/workspaces/udmi/sites/udmi_site_model/devices/GAT-1/rsa_private.crt",
        "algorithm": "RS256",
        "authentication_mechanism": "udmi_local"
        },
        "udmi":{"discovery": {"ipv4":"false", "ethmac": false, "bacnet":false}},
        "nmap": {
            "targets": [
                "127.0.0.1"
            ],
          "interface": "eth0"
        },
            "bacnet": {
                "ip": "192.168.11.251"
        }
    }
    ```

3. Launch the discovery node

    ```
    venv/bin/python3 src/main.py --config_file=PATH_TO_CONFIG_FILE
    ```


## Dockerised

TBC

## Daemon

The discovery node can be installed as an always running systemd daemon `udmi_discovery`. This will isntall any requirements

1.  Run the install script and follow the instructions

    ```
    sudo bin/install_daemon install
    ```

2.  Create a gateway device in the site model, using the public key which is printed to the terminal at the end of the script. Use the `discovery` block to configure which dsicovery families are supported when using the UDMI tooling to intiaite discovery and run registrar

    ```json
    {
        "gateway": {
            "proxy_ids": []
        },
        "discovery": {
            "families": {
                "bacnet": {},
                "ipv4": {}
            }
        },
        "cloud": {
            "auth_type": "RS256"
        },
        "version": "1.5.1",
        "timestamp": "2020-05-01T13:39:07Z"

    ```

3. 

## E2E Test

TBC

## Test Suite

- Unit tests - `~/venv/bin/python3 -m pytest tests/`
- Integration tests - TBC
