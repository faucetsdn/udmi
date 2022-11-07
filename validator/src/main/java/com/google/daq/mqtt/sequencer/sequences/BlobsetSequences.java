package com.google.daq.mqtt.sequencer.sequences;

import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static udmi.schema.Category.BLOBSET_BLOB_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;

import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metrics;
import udmi.schema.SystemConfig.SystemMode;
import udmi.schema.SystemEvent;

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

  private String generateEndpointConfigDeviceIdClientId(String deviceId) {
    return String.format(
        ENDPOINT_CONFIG_CLIENT_ID,
        projectId,
        cloudRegion,
        registryId,
        deviceId);
  }
  private String generateEndpointConfigBase64Payload(String hostname) {
  String payload = String.format(
      ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, generateEndpointConfigClientId(), ENDPOINT_CONFIG_HOSTNAME);
  String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
    return SemanticValue.describe("endpoint_base64_payload", base64Payload);
  }

  private String generateEndpointConfigBase64HostnamePayload(String hostname) {
    String payload = String.format(
        ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, generateEndpointConfigClientId(), hostname);
    String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
    return SemanticValue.describe("endpoint_base64_payload", base64Payload);
  }

  private String generateEndpointConfigBase64DeviceIdPayload(String deviceId) {
    String payload = String.format(
        ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, generateEndpointConfigDeviceIdClientId(deviceId), ENDPOINT_CONFIG_HOSTNAME);
    info("============PAYLOAD " + payload);
    String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
    return SemanticValue.describe("endpoint_base64_payload", base64Payload);
  }

  private String generateNonce() {
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);
    String base64Nonce = Base64.getEncoder().encodeToString(nonce);
    return SemanticValue.describe("endpoint_nonce", base64Nonce);
  }

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_config_connection_error() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = generateEndpointConfigBase64HostnamePayload("localhost");
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
    config.base64 = generateEndpointConfigBase64HostnamePayload(ENDPOINT_CONFIG_HOSTNAME);
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

  @Test
  @Description("Reset and connect to same endpoint and expect it returns")
  public void endpoint_config_connection_success_reset() {
    info("======================RESET1");

    // The initial last_start is epoch 0. Wait until a more meaningful value is available.
    final Date dateZero = new Date(0);
    untilTrue("last_start is not zero", () -> deviceState.system.last_start.after(dateZero));

    if (!deviceState.system.last_start.after(dateZero)) {
      fail("Can't get past dateZero");
    } else {
      info("============== last_start > 0");
    }

    deviceConfig.system.mode = SystemMode.ACTIVE;
    updateConfig();

    untilTrue("deviceState.system.mode == ACTIVE", () -> {
      return deviceState.system.mode.equals(SystemMode.ACTIVE); } );

    final Date last_config = deviceState.system.last_config;
    final Date last_start = deviceConfig.system.last_start;

    deviceConfig.system.mode = SystemMode.RESTART;
    updateConfig();

    untilTrue("deviceState.system.mode == INITIAL", () -> {
      return deviceState.system.mode.equals(SystemMode.INITIAL); } );

    deviceConfig.system.mode = SystemMode.ACTIVE;
    updateConfig();

    untilTrue("deviceState.system.mode == ACTIVE", () -> {
      return deviceState.system.mode.equals(SystemMode.ACTIVE); } );

    info("================1 previous last_config = " + getTimestamp(last_config));
    info("================1 current last_config = " + getTimestamp(deviceState.system.last_config));

    untilTrue("last_config is newer than previous last_config", () -> {
      info("============ waiting for " +
          "current last_config " + getTimestamp(deviceState.system.last_config) + " to be after " +
          "previous last_config " + getTimestamp(last_config));
      return deviceState.system.last_config.after(last_config);
    });

    info("================2 previous last_config = " + getTimestamp(last_config));
    info("================2 current last_config = " + getTimestamp(deviceState.system.last_config));

    untilTrue("last_start is newer than previous last_start " + getTimestamp(last_start), () -> {
      info("============ waiting for " +
          "current last_start " + getTimestamp(deviceState.system.last_start) + " to be after " +
          "previous last_start " + getTimestamp(last_start));
      return deviceConfig.system.last_start.after(last_start);
    });
  }

  @Test
  @Description("Redirect to a different endpoint")
  public void endpoint_config_connection_success_redirect() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = generateEndpointConfigBase64DeviceIdPayload("AHU-99");
    config.content_type = "application/json";
    config.nonce = generateNonce();
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<>();
    deviceConfig.blobset.blobs.put(SystemBlobsets.IOT_ENDPOINT_CONFIG.value(), config);
    updateConfig();

    untilTrue("blobset entry config status is success", () -> {
      BlobPhase phase = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).phase;
      Entry stateStatus = deviceState.system.status;
      if (phase != null) {
        info("phase = " + phase);
      }
      return phase != null
          && phase.equals(BlobPhase.FINAL)
          && stateStatus.category.equals(SYSTEM_CONFIG_APPLY)
          && stateStatus.level == Level.NOTICE.value();
    });
  }
}
