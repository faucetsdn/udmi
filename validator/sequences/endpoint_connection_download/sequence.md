
## endpoint_connection_download (ALPHA)

Check endpoint redirection via download/blobset channel.

1. Update config before blobset phase is final and stateStatus is null
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint download url` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.
