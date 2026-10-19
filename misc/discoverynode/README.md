# Discovery Node

## Notes

`vendor` discovery family is actually sequential number generator at one second increments.
When a scan config sets `addrs`, those values are emitted instead.

## Scan configuration

Scans are driven by the `discovery` block of the UDMI config. Two forms are
supported and may be combined in the same config:

- **Legacy** `discovery.families.<family>`: one scan per family, keyed by the
  family name. Reported under `state.discovery.families.<family>`.
- **Named** `discovery.scans.<name>`: any number of independently scheduled
  scans, each declaring its `family`. Several scans may share a family (e.g. an
  hourly `ether`/`ping` and a daily `ether`/`ports`). Reported under
  `state.discovery.scans.<name>`.

Each named scan uses the same fields as a family scan (`generation`,
`scan_interval_sec`, `scan_duration_sec`, `depth`, `addrs`) plus `family`.
Unsupported fields are dropped with a warning. Scan names must match
`^[a-z][a-z0-9]*(_[a-z0-9]+)*$`.

Families: `ipv4` (passive sniffer), `ether` (`depth`: `ping`, `ports`,
`services`), `bacnet` (`depth`: `system`, `refs`), `vendor`.

`bacnet` scans are **exclusive**: at most one bacnet scan (legacy or named)
runs at a time. A bacnet scan whose start overlaps another is skipped for that
generation and reports a `status` entry naming the running scan.

Example: passive hourly, ping hourly, ports daily, bacnet daily.

```json
{
  "discovery": {
    "scans": {
      "hourly_passive": {
        "family": "ipv4",
        "generation": "2026-09-08T00:00:00Z",
        "scan_interval_sec": 3600,
        "scan_duration_sec": 300
      },
      "hourly_ping": {
        "family": "ether",
        "depth": "ping",
        "addrs": ["192.168.1.0/24"],
        "generation": "2026-09-08T00:00:00Z",
        "scan_interval_sec": 3600
      },
      "nightly_ports": {
        "family": "ether",
        "depth": "ports",
        "addrs": ["192.168.1.0/24"],
        "generation": "2026-09-08T01:00:00Z",
        "scan_interval_sec": 86400
      },
      "nightly_bacnet": {
        "family": "bacnet",
        "depth": "refs",
        "generation": "2026-09-08T02:00:00Z",
        "scan_interval_sec": 86400
      }
    }
  }
}
```

Log lines from a scan are prefixed with its id, e.g. `[scans/hourly_ping]` or
`[families/bacnet]`.

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
as the entry point.

This can be used to validate that discovery actually discovers, message formats, etc.

## E2E Test

Refer to Github CI test. 

## Test Suite

- Unit tests - `~/venv/bin/python3 -m pytest tests/`
- Integration tests - TODO

## Building binary

Use `bin/build_binary`
