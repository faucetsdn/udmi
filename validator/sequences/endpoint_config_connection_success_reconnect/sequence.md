
## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": _endpoint base64 payload_, "nonce": _endpoint nonce_ } } }
1. Wait for blobset entry config status is success
