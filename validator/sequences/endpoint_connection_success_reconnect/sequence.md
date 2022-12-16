
## endpoint_connection_success_reconnect

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "sha256": `6c41a829dacd5915299df9e69a9421b8ae973b3c8be4421ed7973ff392bce185`, "nonce": `endpoint_nonce`, "url": {  } } } }
1. Wait for blobset phase is final and stateStatus is null
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
