# Spotter - UDMI Reference Client

Spotter is an on-premise, Python-based reference client for UDMI. It is designed to act as an actual UDMI compliant device and a virtualized test target for the Sequencer CI framework.

Spotter implements an extensible architecture and handles Over-The-Air (OTA) updates using the UDMI `blobset` protocol, utilizing a Git-based update strategy.

## Key Features

1. **Extensible Architecture**: Based on the standard UDMI Python library (`clientlib/python/src/udmi/core`), allowing for easy extension of managers and handlers.
2. **Robust OTA Updates**:
   - Supports out-of-band downloading via standard HTTP(S).
   - Handles resumable downloads via HTTP `Range` requests and implements exponential backoff for transient failures (e.g., HTTP 503).
   - Immediately aborts on fatal authorization/network failures (e.g., HTTP 403, 404).
3. **Git-Based OTA Updates**:
   - The payload specifies the target Git commit hash.
   - Spotter fetches the remote repository and extracts a manifest (`spotter_manifest.json`) directly from the target commit using `git show`.
   - Validates hardware make/model and software dependencies against the downloaded manifest *before* checking out the code.
   - If validation passes, Spotter switches to the target commit and triggers a simulated restart.

## Usage

You can run Spotter using basic MQTT credentials or JWT Authentication.

### Basic Auth
```bash
python -m spotter.spotter.main \
    --client_id projects/my-project/locations/us-central1/registries/reg/devices/AHU-1 \
    --hostname mqtt.googleapis.com \
    --port 8883 \
    --username my_user \
    --password my_password
```

### JWT Auth
```bash
python -m spotter.spotter.main \
    --client_id projects/my-project/locations/us-central1/registries/reg/devices/AHU-1 \
    --hostname mqtt.googleapis.com \
    --port 8883 \
    --jwt_audience my-project \
    --key_file /path/to/rsa_private.pem
```

## Running Tests

To run the unit tests, ensure you have the `udmi` Python client library installed or set in your PYTHONPATH:

```bash
export PYTHONPATH="../clientlib/python/src:../gencode/python:."
cd ../clientlib/python
poetry run pytest ../../spotter/tests/
```
