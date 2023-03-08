
## endpoint_failure_and_restart (ALPHA)

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE:
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL:
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Test failed: timeout waiting for system mode is INITIAL
