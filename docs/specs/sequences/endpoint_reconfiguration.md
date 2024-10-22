[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Endpoint Reconfiguration](#)

# Endpoint Reconfiguration

Endpoint reconfiguration is the reconfiguration of the UDMI endpoint through the
UDMI blob delivery mechanisms via UDMI config messages.

The [endpoint configuration blob](https://github.com/faucetsdn/udmi/blob/master/tests/schemas/configuration_endpoint/simple.json) is a JSON object defined by
[configuration_endpoint.json](https://faucetsdn.github.io/udmi/gencode/docs/configuration_endpoint.html), which is base64 encoded in the config message.


## Tests

### Valid Endpoint (Successful Reconfiguration)

**Notes**
- `<NEW_ENDPOINT>` is a **base64** encoded endpoint object
- `blobset.blobs._iot_endpoint_config` is present in a device's state message if, and only if,   the last received config message has a `blobset.blobs._iot_endpoint_config` block.

```mermaid
%%{wrap}%%
sequenceDiagram
    autonumber
    participant D as Device
    participant E as Original Endpoint
    participant E' as New Endpoint
    E->>D:CONFIG MESSAGE<br/>blobset.blobs._iot_endpoint_config.base64 = <NEW_ENDPOINT><br/>blobset.blobs._iot_endpoint_config.phase = "final"
    D->>E:STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "apply"
    loop Total duration < 30 seconds
    D-->>E':CONNECTION ATTEMPT
    end
    E'->>D:CONFIG MESSAGE<br/>blobset.blobs._iot_endpoint_config.base64 = <NEW_ENDPOINT><br/>blobset.blobs._iot_endpoint_config.phase = "final"
    D->>E':STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "final"
    note over E': system.last_update in state matches timestamp of config from new endpoint
    E'->>D:CONFIG MESSAGE<br/>blobset.blobs._iot_endpoint_config = null
    D->>E':STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config = null
```

### Invalid Endpoint (Unsuccessful Reconfiguration)

```mermaid
%%{wrap}%%
sequenceDiagram
    autonumber
    participant D as Device
    participant E as Original Endpoint
    participant E' as New Endpoint
    E->>D:CONFIG MESSAGE<br/>blobset.blobs._iot_endpoint_config.blob = <NEW_ENDPOINT><br>blobset.blobs._iot_endpoint_config.blob.phase = "final"
    D->>E:STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "apply"
    loop Total duration < 30 seconds
    D-->>E':CONNECTION ATTEMPT
    note over D: Failure, e.g. endpoint cannot be reached, incorrect credentials...
    end
    D-->>E:CONNECTION ATTEMPT
    E->>D:CONFIG MESSAGE
    D->>E:STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "final"<br/>blobset.blobs._iot_endpoint_config.status.level = 500 (ERROR)<br/>blobset.blobs._iot_endpoint_config.status.category = "blobset.blob.apply"

```

### Commit Reconfiguration (Successful reconfiguration, persists restart)

```mermaid
%%{wrap}%%
sequenceDiagram
    autonumber
    participant D as Device
    participant E as Original Endpoint
    participant E' as New Endpoint
    D->>E':STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "final"
    note over D,E': Restart sequence from New Endpoint
    D->>E':STATE MESSAGE<br/>blobset.blobs._iot_endpoint_config.phase = "final"
    note right of E': New endpoint connection is remembered by the device after a system restart
```

## Message Examples

Config message to initiate Reconfiguration (sequence #1 in diagrams above)
<!--example:config/endpoint_reconfiguration.json-->
```json
{
  "version": "1.5.2",
  "blobset": {
    "blobs": {
      "_iot_endpoint_config": {
        "phase": "final",
        "url": "data:application/json;base64,ewogICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzL2Jvcy1zbm9yay1kZXYvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvWlotVFJJLUZFQ1RBL2RldmljZXMvQUhVLTEiLAogICJob3N0bmFtZSI6ICJtcXR0Lmdvb2dsZWFwaXMuY29tIgp9",
        "sha256": "9c8423ac2e707a40c239fce4ce52b8c05ae8c32b163927b9350c97d0f64a8cf7",
        "generation": "2022-07-13T12:00:00.000Z"
      }
    }
  },
  "timestamp": "2022-07-13T12:00:00.000Z"
}
```

The base64 encoded value decodes to:
<!--example:configuration_endpoint/simple.json-->
```json
{
  "protocol": "mqtt",
  "client_id": "projects/bos-snork-dev/locations/us-central1/registries/ZZ-TRI-FECTA/devices/AHU-1",
  "hostname": "mqtt.googleapis.com"
}
```

Example successful state message sent to the new endpoint from device following
a successful reconfiguration
<!--example:state/endpoint_reconfiguration.json-->
```json
{
  "version": "1.5.2",
  "timestamp": "2022-07-13T12:00:10.000Z",
  "system": {
    "hardware": {
      "make": "ACME",
      "model": "Bird Trap"
    },
    "software": {
      "firmware": "1.2"
    },
    "serial_no": "000000",
    "last_config": "2022-07-13T12:00:00.000Z",
    "operation": {
      "operational": true
    }
  },
  "blobset": {
    "blobs": {
      "_iot_endpoint_config": {
        "phase": "final"
      }
    }
  }
}
```

This is an example of the state message sent to the original endpoint after a failure
<!--example:state/endpoint_reconfiguration_failed.json-->
```json
{
  "version": "1.5.2",
  "timestamp": "2022-07-13T12:00:11.000Z",
  "system": {
    "hardware": {
      "make": "ACME",
      "model": "Bird Trap"
    },
    "software": {
      "firmware": "1.2"
    },
    "serial_no": "000000",
    "last_config": "2022-07-13T12:00:00.000Z",
    "operation": {
      "operational": true
    }
  },
  "blobset": {
    "blobs": {
      "_iot_endpoint_config": {
        "phase": "final",
        "status": {
          "message": "Could not connect to new endpoint",
          "category": "blobset.blob.apply",
          "timestamp": "2022-07-13T12:00:11.000Z",
          "level": 500
        }
      }
    }
  }
}
```
