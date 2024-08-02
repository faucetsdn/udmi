package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.collect.Sets.difference;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Envelope.SubFolder.POINTSET;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.FeatureDiscovery.FeatureStage;

/**
 * Specific tests for logical gateway devices. This is not the same as proxied devices (devices that
 * are proxied through a gateway).
 */
public class GatewaySequences extends SequenceBase {

  private static final Duration MESSAGE_WAIT_DURATION = Duration.of(30, ChronoUnit.SECONDS);

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
    Set<String> proxyIds = ImmutableSet.copyOf(deviceMetadata.gateway.proxy_ids);
    captureReceivedEventsFor(proxyIds);

    waitFor("All proxy devices received event and state", MESSAGE_WAIT_DURATION,
        () -> {
      Set<String> remainingState = difference(proxyIds, receivedDevices(proxyIds, UPDATE));
      String stateMessage =
          remainingState.isEmpty() ? "" : "Missing state from " + CSV_JOINER.join(remainingState);
      Set<String> remainingEvents = difference(proxyIds, receivedDevices(proxyIds, POINTSET));
      String eventsMessage = remainingEvents.isEmpty() ? ""
          : "Missing events from " + CSV_JOINER.join(remainingEvents);
      boolean hasBoth = !(stateMessage.isBlank() || eventsMessage.isBlank());
      String totalMessage = stateMessage + (hasBoth ? "; " : "") + eventsMessage;
      return totalMessage.isBlank() ? null : totalMessage;
    });

    Set<String> receivedDevices = getReceivedDevices();
    SetView<String> difference = difference(difference(receivedDevices, proxyIds),
        ImmutableSet.of(getDeviceId()));
    assertTrue("unexpected proxy device: " + CSV_JOINER.join(difference), difference.isEmpty());
  }

  private Set<String> receivedDevices(Set<String> proxyIds, SubFolder subFolder) {
    return proxyIds.stream().filter(deviceId -> {
      CaptureMap receivedEvents = getReceivedEvents(deviceId);
      return !ofNullable(receivedEvents.get(subFolder)).map(List::isEmpty).orElse(true);
    }).collect(Collectors.toSet());
  }
}
