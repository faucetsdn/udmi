
## ota_fetch_failure (PREVIEW)

1. Update config trigger ota update for pubber_module
    * Add `blobset` = { "blobs": { "pubber_module": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `software data` } } }
1. Wait for pubber_module phase is APPLY
1. Wait until system logs level `ERROR` category `blobset.blob.fetch.failure`
1. Wait for pubber_module phase is FINAL and status is not null

Test passed.
