package com.google.bos.iot.core.bambi.auth;

/**
 * Specify how BAMBI requests should be verified when service is run locally.
 */
public class LocalIdVerifier implements IdVerifier {

  @Override
  public boolean verify(IdVerificationConfig config) {
    return true;
  }

}
