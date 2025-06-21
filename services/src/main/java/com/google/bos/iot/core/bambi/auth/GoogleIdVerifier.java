package com.google.bos.iot.core.bambi.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verify incoming requests from Google Apps Script.
 */
public class GoogleIdVerifier implements IdVerifier {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleIdVerifier.class);

  @Override
  public boolean verify(IdVerificationConfig config) {
    if (config.identityToken() == null || config.identityToken().isEmpty()) {
      LOGGER.warn("Request missing identity token.");
      return false;
    }
    try {
      GoogleIdTokenVerifier.Builder verifier = new GoogleIdTokenVerifier.Builder(
          new NetHttpTransport(), new GsonFactory());
      if (config.audience() != null && !config.audience().isEmpty()) {
        verifier.setAudience(config.audience());
      }

      GoogleIdToken token = verifier.build().verify(config.identityToken());
      if (token != null && token.getPayload().getEmailVerified()) {
        LOGGER.info("Received verified request from {}", token.getPayload().getEmail());
        return true;
      }
      LOGGER.warn("Could not verify identity token; request will not be authorized.");
      return false;
    } catch (GeneralSecurityException | IOException e) {
      LOGGER.error("Exception while verifying identity; request will not be authorized.", e);
      return false;
    }
  }
}
