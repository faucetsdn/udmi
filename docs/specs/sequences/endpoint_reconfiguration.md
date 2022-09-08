[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Endpoint Reconfiguration](#)

# Endpoint Reconfiguration

Endpoint reconfiguration is the reconfiguration of the UDMI endpoint through the
UDMI blob delivery mechanisms via UDMI config messages.

The [endpoint configuration blob](https://github.com/faucetsdn/udmi/blob/master/tests/configuration_endpoint.tests/simple.json) is a JSON object defined by
[configuration_endpoint.json](https://faucetsdn.github.io/udmi/gencode/docs/configuration_endpoint.html), which is base64 encoded in the config message.


## Tests

### Valid Endpoint (Successful) Reconfiguration

```mermaid
sequenceDiagram
    autonumber
    participant E as Original Endpoint
    participant D as Device
    participant E' as New Endpoint
    E->>D:CONFIG MESSAGE<br>blobset.blobs._iot_endpoint.blob = <ENDPOINT><br>blobset.blobs._iot_endpoint.blob.phase = "final"
    D->>E:STATE MESSAGE<BR>blobset.blobs._iot_endpoint.blob.phase = "preparing"
    D-->>E':CONNECTION ATTEMPT
    E'-->>D:SUCCESS
    D->>E':STATE MESSAGE<BR>blobset.blobs._iot_endpoint.blob.phase = "applied"
    note over D: Reboot device
    D-->>E':CONNECTION ATTEMPT
    
```

### Invalid Endpoint (Unsuccessful Reconfiguration)

```mermaid
sequenceDiagram
    autonumber
    participant E as Original Endpoint
    participant D as Device
    participant E' as New Endpoint
    E->>D:CONFIG MESSAGE<br>blobset.blobs._iot_endpoint.blob = <ENDPOINT><br>blobset.blobs._iot_endpoint.blob.phase = "final"
    D->>E:STATE MESSAGE<BR>blobset.blobs._iot_endpoint.blob.phase = "preparing"
    D-->>E':CONNECTION ATTEMPT
    note over D,E': Failure, e.g. endpoint doesn't exist, incorrect credentials, ...
    D-->>E:CONNECTION ATTEMPT
    E-->>D:SUCCESS
    D->>E:STATE MESSAGE<BR>blobset.blobs._iot_endpoint.blob.phase = "failed"
```