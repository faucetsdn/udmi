
## endpoint_config_connection_success_redirect

Redirect to a different endpoint

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": _endpoint_base64_payload_, "nonce": _endpoint_nonce_ } } }
1. Test failed: timeout nothing
