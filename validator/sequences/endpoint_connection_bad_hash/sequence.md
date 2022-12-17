
## endpoint_connection_bad_hash

Failed connection because of bad hash.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `invalid blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Test failed: timeout waiting for blobset phase is final and stateStatus is null
