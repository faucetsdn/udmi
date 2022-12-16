
## endpoint_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `endpoint_base64_payload`, "nonce": `endpoint_nonce` } } }
1. Wait for blobset entry config status is error
