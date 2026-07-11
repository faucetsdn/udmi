
## blob_update_invalid_payload (PREVIEW)

Validates format and signature checking by providing a dummy payload.

1. Update config trigger blob update for system
    * Add `blobset` = { "blobs": { "system": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `ERROR` category `blobset.blob.parse`
1. Wait for system phase transitions
1. Wait for system phase is FINAL
1. Check that system state indicates error

Test passed.
