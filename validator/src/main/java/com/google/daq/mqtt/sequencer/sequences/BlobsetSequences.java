package com.google.daq.mqtt.sequencer.sequences;

import static udmi.schema.Category.BLOBSET_BLOB_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;

import com.google.daq.mqtt.sequencer.SequenceBase;
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

  // TODO: Build this from the site model config.
  private static final String ENDPOINT_CONFIG_CLIENT_ID =
      "projects/bos-johnrandolph-dev/locations/us-central1/registries/ZZ-TRI-FECTA/devices/AHU-1";
  private static final String ENDPOINT_CONFIG_HOSTNAME = "mqtt.googleapis.com";

  private String endpointConfigPayloadBase64(String hostname) {
    String payload = String.format(ENDPOINT_CONFIG_HOSTNAME_PAYLOAD, ENDPOINT_CONFIG_CLIENT_ID, hostname);
    return Base64.getEncoder().encodeToString(payload.getBytes());
  }

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_config_connection_error() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = endpointConfigPayloadBase64("localhost");
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
  @Description("Push endpoint config message to device that results in success.")
  public void endpoint_config_connection_success() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = endpointConfigPayloadBase64(ENDPOINT_CONFIG_HOSTNAME);
    config.content_type = "application/json";
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<>();
    deviceConfig.blobset.blobs.put(SystemBlobsets.IOT_ENDPOINT_CONFIG.value(), config);

    untilTrue("blobset entry config status is success", () -> {
      BlobPhase phase = deviceState.blobset.blobs.get(
          SystemBlobsets.IOT_ENDPOINT_CONFIG.value()).phase;
      Entry stateStatus = deviceState.system.status;
      if (phase==null) info("A");
      if (stateStatus==null) info("B");
      return phase != null
          && phase.equals(BlobPhase.FINAL)
          && stateStatus.category.equals(SYSTEM_CONFIG_APPLY)
          && stateStatus.level == Level.NOTICE.value();
    });
  }
}
