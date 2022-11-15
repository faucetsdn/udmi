
## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config before blobset entry config status is success:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzL2Jvcy1wZXJpbmdrbmlmZS1kZXYvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvWlotVFJJLUZFQ1RBL2RldmljZXMvQUhVLTEiLAogICJob3N0bmFtZSI6ICJtcXR0Lmdvb2dsZWFwaXMuY29tIgp9`, "nonce": `du1qKbJpEWvJiuJ4kanBljJgaUyQRe71FVT2b+i2MCA=` } } }
1. Wait for blobset entry config status is success
