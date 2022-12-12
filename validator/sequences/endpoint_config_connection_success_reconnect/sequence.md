
## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config before blobset phase is FINAL and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `endpoint_base64_payload`, "nonce": `endpoint_nonce` } } }
1. Wait for blobset phase is FINAL and stateStatus is null
