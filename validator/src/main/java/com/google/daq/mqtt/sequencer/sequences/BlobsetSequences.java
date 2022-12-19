package com.google.daq.mqtt.sequencer.sequences;

import static udmi.schema.Category.BLOBSET_BLOB_APPLY;

import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Operation.SystemMode;


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
  private static final String GOOGLE_ENDPOINT_HOSTNAME = "mqtt.googleapis.com";

  private String generateEndpointConfigClientId(String registryId) {
    return String.format(
        ENDPOINT_CONFIG_CLIENT_ID,
        projectId,
        cloudRegion,
        registryId,
        getDeviceId());
  }

  private String generateEndpointConfigBase64Payload(String hostname, String registryId) {
    String payload = String.format(
        ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, generateEndpointConfigClientId(registryId), hostname);
    String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
    return SemanticValue.describe("endpoint_base64_payload", base64Payload);
  }

  private String generateNonce() {
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);
    String base64Nonce = Base64.getEncoder().encodeToString(nonce);
    return SemanticValue.describe("endpoint_nonce", base64Nonce);
  }

  private void untilClearedRedirect() {
    deviceConfig.blobset.blobs.remove(SystemBlobsets.IOT_ENDPOINT_CONFIG.value());
    untilTrue("endpoint config blobset state not defined", () -> deviceState.blobset == null
        || deviceState.blobset.blobs.get(SystemBlobsets.IOT_ENDPOINT_CONFIG.value()) == null);
  }

  private void untilSuccessfulRedirect(BlobPhase blobPhase) {
    untilTrue(String.format("blobset phase is %s and stateStatus is null", blobPhase), () -> {
      BlobPhase phase = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).phase;
      // Successful reconnect sends a state message with empty Entry.
      Entry blobStateStatus = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).status;
      return phase != null
          && phase.equals(blobPhase)
          && blobStateStatus == null;
    });
  }

  private void setDeviceConfigEndpointBlob(String googleEndpointHostname, String registryId) {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = generateEndpointConfigBase64Payload(googleEndpointHostname, registryId);
    config.content_type = "application/json";
    config.nonce = generateNonce();
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<>();
    deviceConfig.blobset.blobs.put(SystemBlobsets.IOT_ENDPOINT_CONFIG.value(), config);
  }

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_connection_error() {
    String localhost = "localhost";
    setDeviceConfigEndpointBlob(localhost, registryId);

    untilTrue("blobset entry config status is error", () -> {
      Entry stateStatus = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).status;
      return stateStatus.category.equals(BLOBSET_BLOB_APPLY)
          && stateStatus.level == Level.ERROR.value();
    });

    untilClearedRedirect();
  }

  @Test
  @Description("Check a successful reconnect to the same endpoint.")
  public void endpoint_connection_success_reconnect() {
    setDeviceConfigEndpointBlob(GOOGLE_ENDPOINT_HOSTNAME, registryId);
    untilSuccessfulRedirect(BlobPhase.FINAL);
    untilClearedRedirect();
  }

  @Test
  @Description("Check connection to an alternate project.")
  public void endpoint_connection_success_alternate() {
    if (altRegistry == null) {
      throw new SkipTest("No alternate registry defined");
    }
    // Phase one: initiate connection to alternate registry.
    untilTrue("initial last_config matches config timestamp", this::stateMatchesConfigTimestamp);
    setDeviceConfigEndpointBlob(GOOGLE_ENDPOINT_HOSTNAME, altRegistry);
    untilSuccessfulRedirect(BlobPhase.APPLY);
    mirrorDeviceConfig();

    withAlternateClient(() -> {
      // Phase two: verify connection to alternate registry.
      untilSuccessfulRedirect(BlobPhase.FINAL);
      untilTrue("alternate last_config matches config timestamp",
          this::stateMatchesConfigTimestamp);
      untilClearedRedirect();

      // Phase three: initiate connection back to initial registry.
      // Phase 3/4 test the same thing as phase 1/2, included to restore system to initial state.
      setDeviceConfigEndpointBlob(GOOGLE_ENDPOINT_HOSTNAME, registryId);
      untilSuccessfulRedirect(BlobPhase.APPLY);
      mirrorDeviceConfig();
    });

    // Phase four: verify restoration of initial registry connection.
    whileDoing("restoring main connection", () -> {
      untilSuccessfulRedirect(BlobPhase.FINAL);
      untilTrue("restored last_config matches config timestamp", this::stateMatchesConfigTimestamp);
      untilClearedRedirect();
    });
  }

  @Test
  @Description("Restart and connect to same endpoint and expect it returns.")
  public void system_mode_restart() {
    // Prepare for the restart.
    final Date dateZero = new Date(0);
    untilTrue("last_start is not zero",
        () -> deviceState.system.operation.last_start.after(dateZero));

    deviceConfig.system.operation.mode = SystemMode.ACTIVE;

    untilTrue("deviceState.system.mode == ACTIVE",
        () -> deviceState.system.operation.mode.equals(SystemMode.ACTIVE));

    final Date last_config = deviceState.system.last_config;
    final Date last_start = deviceConfig.system.last_start;

    // Send the restart mode.
    deviceConfig.system.operation.mode = SystemMode.RESTART;

    // Wait for the device to go through the correct states as it restarts.
    untilTrue("deviceState.system.mode == INITIAL",
        () -> deviceState.system.operation.mode.equals(SystemMode.INITIAL));

    deviceConfig.system.operation.mode = SystemMode.ACTIVE;

    untilTrue("deviceState.system.mode == ACTIVE",
        () -> deviceState.system.operation.mode.equals(SystemMode.ACTIVE));

    // Capture error from last_start unexpectedly changing due to restart condition.
    try {
      untilTrue("last_config is newer than previous last_config before abort",
          () -> deviceState.system.last_config.after(last_config));
    } catch (AbortMessageLoop e) {
      info("Squelching aborted message loop: " + e.getMessage());
    }

    untilTrue("last_config is newer than previous last_config after abort",
        () -> deviceState.system.last_config.after(last_config));

    untilTrue("last_start is newer than previous last_start",
        () -> deviceConfig.system.last_start.after(last_start));
  }

}
