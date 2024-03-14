package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
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
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureDiscovery.FeatureStage;

/**
 * Specific tests for logical gateway devices. This is not the same as proxied
 * devices (devices that are proxied through a gateway).
 */
public class GatewaySequences extends SequenceBase {

  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("gateway");
  }

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToTrue(() -> deviceMetadata.gateway.proxy_ids.isEmpty()), "Not a gateway");
    super.setUp();
  }

  @Feature(stage = FeatureStage.ALPHA, bucket = Bucket.GATEWAY)
  @Summary("Check adequate logging for gateway detach, errors, and reattach")
  @Test(timeout = TWO_MINUTES_MS)
  public void gateway_attach_handling() {
    ifTrueSkipTest(true, "Not yet implemented");
    // * Verify that proxied device is sending data.
    // * Remove proxied device from gateway list
    // * Verify that proper detach logging occured
    // * Verify that device is no longer sending data
    // * Add a bad (random) device to the list of proxied devices
    // * Check for status and logging of bsad attach request
    // * Remove bad device, replace with original good device
    // * Check for proper attach logging, and clear status
    // * Verify that device is sending data
  }

  @Feature(stage = FeatureStage.BETA, bucket = Bucket.GATEWAY, nostate = true)
  @Summary("Check that a gateway proxies pointset events for indicated devices")
  @Test(timeout = TWO_MINUTES_MS)
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
