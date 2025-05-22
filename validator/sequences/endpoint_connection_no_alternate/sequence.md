
## endpoint_connection_no_alternate (ALPHA)

Failed connection never uses alternate registry.

1. Wait until initial last_config matches config timestamp
1. Update config before blobset phase is final and stateStatus is not null
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is final and stateStatus is not null
1. Wait for blobset phase is final and stateStatus is not null
1. Wait until alternate client connect delay
1. Wait for blobset phase is final and stateStatus is null
1. Wait until restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.
