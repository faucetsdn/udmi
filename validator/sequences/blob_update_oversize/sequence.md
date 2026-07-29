
## blob_update_oversize (PREVIEW)

Validates reporting of an oversized payload fetch failure.

1. Update config trigger blob update for system
    * Add `blobset` = { "blobs": { "system": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `ERROR` category `blobset.blob.fetch`
1. Wait for system phase transitions
1. Wait for system phase is FINAL
1. Check that system state indicates error

Test passed.
