
## endpoint_failure_and_restart (PREVIEW)

1. Update config before blobset entry config status is error
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Update config before system mode is ACTIVE
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
