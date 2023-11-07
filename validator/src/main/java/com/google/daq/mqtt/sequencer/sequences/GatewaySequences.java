package com.google.daq.mqtt.sequencer.sequences;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration.FeatureStage;

public class GatewaySequences extends SequenceBase {

  @Feature(stage = FeatureStage.ALPHA, bucket = Bucket.GATEWAY)
  @Summary("Check that a gateway proxies pointsets for indicated devices")
  @Test
  public void gateway_proxy_events() {

  }

}
