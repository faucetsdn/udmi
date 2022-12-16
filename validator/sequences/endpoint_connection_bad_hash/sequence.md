
## endpoint_connection_bad_hash

Failed connection because of bad hash.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `2b6b47470d4fa9075f97d3af909088d92b91e444ce7eb2b8cacaead4aa63800e`, "url": {  } } } }
1. Test failed: timeout waiting for device config sync
