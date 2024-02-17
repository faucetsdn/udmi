package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration.FeatureStage;

/**
 * Validate pointset related functionality for proxied devices.
 */
public class ProxiedSequences extends PointsetBase {

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToTrue(() -> deviceMetadata.gateway.gateway_id == null),
        "Not a proxied device");
    super.setUp();
  }

  @Feature(stage = FeatureStage.ALPHA, bucket = Bucket.GATEWAY, nostate = true)
  @Summary("Basic check for proxied device proxying points")
  @Test(timeout = ONE_MINUTE_MS)
  public void proxied_setup() {
  }
}
