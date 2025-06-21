package com.google.bos.iot.core.bambi.auth;

/**
 * Interface for verifying identity for incoming requests.
 */
public interface IdVerifier {

  String PUBSUB = "pubsub";
  String MQTT = "mqtt";

  /**
   * Get the appropriate Id Verifier depending on the mode.
   *
   * @param mode either of "pubsub" or "mqtt"
   */
  static IdVerifier from(String mode) {
    return switch (mode) {
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
