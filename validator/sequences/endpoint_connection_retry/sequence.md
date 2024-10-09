
## endpoint_connection_retry (PREVIEW)

Check repeated endpoint with same information gets retried.

1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
