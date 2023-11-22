package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Envelope.SubFolder.POINTSET;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration.FeatureStage;

/**
 * Specific tests for gateway functionality.
 */
public class GatewaySequences extends SequenceBase {

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToTrue(() -> deviceMetadata.gateway.proxy_ids.isEmpty()), "Not a gateway");
    super.setUp();
  }

  @Feature(stage = FeatureStage.ALPHA, bucket = Bucket.GATEWAY)
  @Summary("Check that a gateway proxies pointsets for indicated devices")
  @Test(timeout = ONE_MINUTE_MS)
  public void gateway_proxy_events() {
    Set<String> remaining = new HashSet<>(deviceMetadata.gateway.proxy_ids);
    Set<String> original = ImmutableSet.copyOf(remaining);

    untilTrue("All proxy devices received data", () -> {
      remaining.stream().filter(this::hasReceivedPointset).toList().forEach(remaining::remove);
      return remaining.isEmpty();
    }, () -> "Missing data from " + CSV_JOINER.join(remaining));

    Set<String> receivedDevices = getReceivedDevices();
    SetView<String> difference =
        Sets.difference(Sets.difference(receivedDevices, original), ImmutableSet.of(getDeviceId()));
    assertTrue("unexpected proxy device: " + CSV_JOINER.join(difference), difference.isEmpty());
  }

  private boolean hasReceivedPointset(String deviceId) {
    return !getReceivedEvents(deviceId, POINTSET).isEmpty();
  }

}
