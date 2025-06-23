package com.google.bos.iot.core.bambi.auth;

import udmi.schema.IotAccess.IotProvider;

/**
 * Interface for verifying identity for incoming requests.
 */
public interface IdVerifier {

  /**
   * Get the appropriate Id Verifier depending on the mode.
   *
   * @param mode either of "pubsub" or "mqtt"
   */
  static IdVerifier from(String mode) {
    IotProvider provider;
    try {
      provider = IotProvider.fromValue(mode);
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException(
          "Unsupported mode for Identity Verification " + mode, e);
    }

    return switch (provider) {
      case PUBSUB -> new GoogleIdVerifier();
      case MQTT -> new LocalIdVerifier();
      default -> throw new UnsupportedOperationException(
          "Unsupported mode for Identity Verification " + mode);
    };
  }

  /**
   * Verifies the provided identity string.
   *
   * @param config The identity verification config.
   * @return true if the identity is verified, false otherwise.
   */
  boolean verify(IdVerificationConfig config);
}