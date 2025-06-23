package com.google.bos.iot.core.bambi.auth;

import java.util.List;

/**
 * Id Verification Config. The current parameters are specific to Google Id Verifier, but we can
 * expand as needed.
 *
 * @param identityToken token received from an App Script.
 * @param audience the client ids which are allowed to send requests; may be null to allow from
 *     any App Script.
 */
public record IdVerificationConfig(
    String identityToken,
    List<String> audience
) {

}
