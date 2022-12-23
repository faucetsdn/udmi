
## endpoint_connection_success_alternate

Check connection to an alternate project.

1. Wait for initial last_config matches config timestamp
1. Update config before blobset phase is apply and stateStatus is null:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Update config before blobset phase is final and stateStatus is null:
    * Add `system.testing.endpoint_type` = `alternate`
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Update config before blobset phase is apply and stateStatus is null:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Update config before blobset phase is final and stateStatus is null:
    * Remove `system.testing.endpoint_type`
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
