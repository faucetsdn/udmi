
## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config before blobset phase is FINAL and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "nonce": `endpoint_nonce`, "url": {  } } } }
1. Wait for blobset phase is FINAL and stateStatus is null
