package com.google.daq.mqtt.sequencer.sequences;

import static udmi.schema.Category.BLOBSET_BLOB_APPLY;

import com.google.daq.mqtt.sequencer.SequenceRunner;
import java.util.Base64;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validation tests for instances that involve blobconfig messages.
 */

public class BlobsetSequences extends SequenceRunner {

  private static final String ENDPOINT_CONFIG_CONNECTION_ERROR_PAYLOAD =
      "{ "
      + "  \"protocol\": \"mqtt\",\n"
      + "  \"client_id\": \"test_project/device\",\n"
      + "  \"hostname\": \"localhost\"\n"
      + "}";

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_config_connection_error() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    config.base64 = String.valueOf(
        Base64.getEncoder().encode(ENDPOINT_CONFIG_CONNECTION_ERROR_PAYLOAD.getBytes()));
    config.content_type = "application/json";
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<String, BlobBlobsetConfig>();
    deviceConfig.blobset.blobs.put("_iot_endpoint_config", config);

    updateConfig();
    untilTrue("device tried endpoint config which resulted in connection error", () -> {
      Entry stateStatus = deviceState.blobset.blobs.get("_iot_endpoint_config").status;
      return stateStatus.category.equals(BLOBSET_BLOB_APPLY)
          && stateStatus.level == Level.ERROR.value();
    });
  }

}
