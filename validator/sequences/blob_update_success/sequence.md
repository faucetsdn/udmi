
## blob_update_success (PREVIEW)

Validates a successful blob update where the device fetches, applies, and reports the new version.

1. Update config trigger blob update for pubber_module
    * Add `blobset` = { "blobs": { "pubber_module": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait for pubber_module phase transitions
1. Wait for pubber_module phase is FINAL
1. Wait until system logs level `DEBUG` category `blobset.blob.receive`
1. Wait until system logs level `DEBUG` category `blobset.blob.fetch`
1. Wait until system logs level `NOTICE` category `blobset.blob.apply`
1. Check that pubber_module state is success
1. Check that pubber_module software version reflects update

Test passed.
