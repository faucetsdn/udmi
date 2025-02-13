# Discovery Node

## Notes

`vendor` discovery family is actually sequential number generator at one second increments.

## Running

**NOTE** Below commands are run from within the `discoverynode` directory

1. Setup

```
bin/setup
```

2. Run 

```
bin/run SITE_MODEL TARGET DEVICE_ID
```

- SITE_MODEL - Path to site model
- TARGET - e.g. `//mqtt/localhost` or `//gbos/bos-platform-testing`
- DEVICE_ID - device ID from site model

3. (Optional) Running with UDMIS Locally

**NOTE** This can be destructive to the site model, and file permissions may change
```
bin/container build 
IMAGE_TAG=udmis:latest udmis/bin/actualize ../sites/udmi_site_model
sudo bin/keygen CERT sites/udmi_site_model/devices/AHU-1
bin/registrar sites/udmi_site_model //mqtt/localhost
```

### Troubleshooting


#### `ssl.SSLError: [SSL] PEM lib (_ssl.c:3874)` 

The device certificate was not signed by the CA certificate. This occurs if UDMIS is restarted without regenerating certificates,
because the UDMIS script (bin/setup_ca) recreates the CA certificate.

To fix, run `sudo bin/keygen CERT sites/udmi_site_model/devices/GAT-1`

Run the script as root

## Standalone (advanced)



1.  Setup a python virtual environment and install the required dependencies

    ```shell
    cd PATH_TO_UDMI/misc/discoverynode/
    python3 -m venv venv
    venv/bin/python3 -m pip install -r src/requirements.txt
    ```

2. Create a JSON configuration file

    Sample ClearBlade configuration file

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
        "udmi":{"discovery": {"ipv4":"false", "ether": false, "bacnet": false}},
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
        "udmi":{"discovery": {"ipv4":"false", "ether": false, "bacnet":false}},
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


## Dockerized

TODO

## Daemon

The discovery node can be installed as an always running systemd daemon `udmi_discovery`. This will install any requirements

1.  Run the install script and follow the instructions

    ```
    sudo bin/install_daemon install
    ```

2.  Create a gateway device in the site model, using the public key which is printed to the terminal at the end of the script. Use the `discovery` block to configure which discovery families are supported when using the UDMI tooling to initiate discovery and run registrar

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
    }
    ```

### Useful Commands

```
# sudo if necesary:
journalctl -f -xeu udmi_discovery

```

## Integration Test

```
misc/discoverynode/testing/integration/integration_test.sh
```

This will load a docker container, however with a dedicated testing wrapper 
`misc/discoverynode/src/tests/test_integration.py` 
as the entrypoint.

This can be used to validate that discovery actually discovers, message formats, etc.

## E2E Test

Refer to Github CI test. 

## Test Suite

- Unit tests - `~/venv/bin/python3 -m pytest tests/`
- Integration tests - TODO

## Building binary

Use `bin/build_binary`
