
## blob_update_invalid_hash (PREVIEW)

Validates tamper protection by providing a valid URL but an incorrect SHA-256 hash.

1. Update config trigger blob update for system
    * Add `blobset` = { "blobs": { "system": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `ERROR` category `blobset.blob.parse`
1. Wait for system phase transitions
1. Wait for system phase is FINAL
1. Check that system state indicates error

Test passed.
