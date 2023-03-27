
## endpoint_connection_success_alternate (ALPHA)

Check connection to an alternate project.

1. Wait for initial last_config matches config timestamp
1. Update config mirroring config false:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Update config mirroring config true:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
