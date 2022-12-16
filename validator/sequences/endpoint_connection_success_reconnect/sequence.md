
## endpoint_connection_success_reconnect

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `6c41a829dacd5915299df9e69a9421b8ae973b3c8be4421ed7973ff392bce185`, "url": {  } } } }
1. Test failed: timeout waiting for device config sync
