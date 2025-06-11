
## endpoint_connection_bad_hash (PREVIEW)

Failed connection because of bad hash.

1. Update config before blobset status is ERROR
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `invalid blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset status is ERROR

Test passed.
