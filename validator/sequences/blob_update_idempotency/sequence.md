
## blob_update_idempotency (PREVIEW)

Validates that a previously applied blob config is not reapplied.

1. Update config trigger blob update for system
    * Add `blobset` = { "blobs": { "system": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `NOTICE` category `blobset.blob.apply`
1. Wait for system phase transitions
1. Wait for system phase is FINAL
1. Check that system state is success
1. Check that system software version reflects update
1. Check that log level `DEBUG` (or greater) category `blobset.blob.receive` was not logged
1. Check that log level `DEBUG` (or greater) category `blobset.blob.fetch` was not logged
1. Check that log level `INFO` (or greater) category `blobset.blob.apply` was not logged
1. Wait for system phase is FINAL

Test passed.
