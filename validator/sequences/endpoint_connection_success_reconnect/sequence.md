
## endpoint_connection_success_reconnect

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `endpoint_base64_payload`, "nonce": `endpoint_nonce` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
