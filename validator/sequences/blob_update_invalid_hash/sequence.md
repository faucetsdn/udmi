
## blob_update_invalid_hash (PREVIEW)

Validates tamper protection by providing a valid URL but an incorrect SHA-256 hash.

1. Update config trigger blob update for pubber_module
    * Add `blobset` = { "blobs": { "pubber_module": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait for pubber_module phase transitions
1. Wait for pubber_module phase is FINAL
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `ERROR` category `blobset.blob.parse.corrupt`
1. Check that pubber_module state indicates error

Test passed.
