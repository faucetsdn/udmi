package com.google.daq.mqtt.sequencer.sequences;

import static udmi.schema.Category.BLOBSET_BLOB_APPLY;

import com.google.daq.mqtt.sequencer.SequenceRunner;
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

  @Test
  @Description("Push endpoint config message to device that results in a connection error.")
  public void endpoint_config_connection_error() {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.phase = BlobPhase.FINAL;
    // { protocol=mqtt, client_id=test_project/device; hostname=localhost }
    config.base64 = "ewogICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInRlc3RfcHJvamVjdC9kZXZp"
        + "Y2UiLAogICJob3N0bmFtZSI6ICJsb2NhbGhvc3QiCn0K";
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
