package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.collect.Sets.difference;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static java.lang.String.format;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Config;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.PointsetConfig;

/**
 * Specific tests for logical gateway devices. This is not the same as proxied devices (devices that
 * are proxied through a gateway).
 */
public class GatewaySequences extends SequenceBase {

  private static final Duration MESSAGE_WAIT_DURATION = Duration.of(45, ChronoUnit.SECONDS);
  private static final int POINTSET_SAMPLE_RATE_SEC = 10;
  private Map<String, Config> proxyConfigs = new HashMap<>();

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
    waitForProxyMessages(POINTSET);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY, nostate = true)
  @Summary("Check that a gateway proxies state updates for indicated devices")
  @Test(timeout = TWO_MINUTES_MS)
  public void gateway_proxy_state() {
    waitForProxyMessages(UPDATE);
  }

  private void waitForProxyMessages(SubFolder subFolder) {
    Set<String> proxyIds = ImmutableSet.copyOf(deviceMetadata.gateway.proxy_ids);
    captureReceivedEventsFor(proxyIds);

    proxyIds.forEach(this::updateProxyConfig);

    String description = format("All proxy devices received %s", subFolder.value());
    waitUntil(description, MESSAGE_WAIT_DURATION, () -> {
      Set<String> remainingTargets = difference(proxyIds, receivedDevices(proxyIds, subFolder));
      return remainingTargets.isEmpty() ? null
          : format("Missing %s from %s", subFolder.value(), CSV_JOINER.join(remainingTargets));
    });

    Set<String> receivedDevices = getReceivedDevices();
    SetView<String> difference = difference(difference(receivedDevices, proxyIds),
        ImmutableSet.of(getDeviceId()));
    assertTrue("unexpected proxy device: " + CSV_JOINER.join(difference), difference.isEmpty());
  }

  private void updateProxyConfig(String proxyId) {
    Config config = proxyConfigs.computeIfAbsent(proxyId, this::makeDefaultConfig);
    config.timestamp = new Date();
    config.pointset.sample_rate_sec = POINTSET_SAMPLE_RATE_SEC;
    updateProxyConfig(proxyId, config);
  }

  private Config makeDefaultConfig(String id) {
    Config config = new Config();
    config.pointset = new PointsetConfig();
    return config;
  }

  private Set<String> receivedDevices(Set<String> proxyIds, SubFolder subFolder) {
    return proxyIds.stream().filter(deviceId -> {
      CaptureMap receivedEvents = getReceivedEvents(deviceId);
      return !ofNullable(receivedEvents.get(subFolder)).map(List::isEmpty).orElse(true);
    }).collect(Collectors.toSet());
  }
}
