
## ota_happy_path (PREVIEW)

1. Update config trigger ota update for pubber_module
    * Add `blobset` = { "blobs": { "pubber_module": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait for pubber_module phase is APPLY
1. Wait until system logs level `NOTICE` category `blobset.blob.apply`
1. Wait for pubber_module phase is FINAL and status is null
1. Check that pubber_module software version reflects update

Test passed.
