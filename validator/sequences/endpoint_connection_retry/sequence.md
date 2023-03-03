
## endpoint_connection_retry (ALPHA)

Check repeated endpoint with same information gets retried.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Update config before blobset entry config status is error:
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
