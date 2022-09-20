
## endpoint_config_connection_error

Push endpoint config message to device that results in a connection error

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInRlc3RfcHJvamVjdC9kZXZpY2UiLAogICJob3N0bmFtZSI6ICJsb2NhbGhvc3QiCn0=` } } }
1. Wait for blobset entry config status is error
