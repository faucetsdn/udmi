
## endpoint_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "sha256": `47124552daef9c5f833ac04c8923b05048c88051387541b2fd0fd00904d00b20`, "nonce": `endpoint_nonce`, "url": {  } } } }
1. Wait for blobset entry config status is error
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
