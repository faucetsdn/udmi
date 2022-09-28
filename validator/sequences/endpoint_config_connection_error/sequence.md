
## endpoint_config_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": _endpoint base64 payload_ } } }
1. Wait for blobset entry config status is error
