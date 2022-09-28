package com.google.daq.mqtt.sequencer.sequences;

import static udmi.schema.Category.BLOBSET_BLOB_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;

import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validation tests for instances that involve blobset config messages.
 */

public class BlobsetSequences extends SequenceBase {

  private static final String ENDPOINT_CONFIG_HOSTNAME_PAYLOAD =
      "{ "
          + "  \"protocol\": \"mqtt\",\n"
          + "  \"client_id\": \"%s\",\n"
          + "  \"hostname\": \"%s\"\n"
          + "}";

  private static final String ENDPOINT_CONFIG_CLIENT_ID =
      "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final String ENDPOINT_CONFIG_HOSTNAME = "mqtt.googleapis.com";

  private String generateEndpointConfigClientId() {
    return String.format(
        ENDPOINT_CONFIG_CLIENT_ID,
        projectId,
        cloudRegion,
        registryId,
        deviceId);
  }

  private String generateEndpointConfigBase64Payload(String hostname) {
    String payload = String.format(
        ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, ENDPOINT_CONFIG_CLIENT_ID, hostname);
    String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
    return SemanticValue.describe("endpoint base64 payload", base64Payload);
  }

  private String generateNonce() {
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);
    String base64Nonce = Base64.getEncoder().encodeToString(nonce);
    return SemanticValue.describe("endpoint nonce", base64Nonce);
  }

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_config_connection_error() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = generateEndpointConfigBase64Payload("localhost");
    config.content_type = "application/json";
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<>();
    deviceConfig.blobset.blobs.put(SystemBlobsets.IOT_ENDPOINT_CONFIG.value(), config);

    untilTrue("blobset entry config status is error", () -> {
      Entry stateStatus = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).status;
      return stateStatus.category.equals(BLOBSET_BLOB_APPLY)
          && stateStatus.level == Level.ERROR.value();
    });
  }

  @Test
  @Description(
      "Push endpoint config message to device that results in successful reconnect to "
          + "the same endpoint.")
  public void endpoint_config_connection_success_reconnect() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = generateEndpointConfigBase64Payload(ENDPOINT_CONFIG_HOSTNAME);
    config.content_type = "application/json";
    config.nonce = generateNonce();
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<>();
    deviceConfig.blobset.blobs.put(SystemBlobsets.IOT_ENDPOINT_CONFIG.value(), config);

    untilTrue("blobset entry config status is success", () -> {
      BlobPhase phase = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).phase;
      Entry stateStatus = deviceState.system.status;
      return phase != null
          && phase.equals(BlobPhase.FINAL)
          && stateStatus.category.equals(SYSTEM_CONFIG_APPLY)
          && stateStatus.level == Level.NOTICE.value();
    });
  }
}
