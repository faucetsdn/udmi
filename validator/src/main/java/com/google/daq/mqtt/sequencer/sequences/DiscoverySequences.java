package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.sequencer.Feature.Stage.ALPHA;
import static com.google.daq.mqtt.sequencer.Feature.Stage.BETA;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static udmi.schema.Bucket.DISCOVERY_SCAN;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_NETWORKS;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.udmi.util.CleanDateFormat;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Enumerate;
import udmi.schema.NetworkDiscoveryConfig;
import udmi.schema.NetworkDiscoveryState;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequenceBase {

  public static final int SCAN_START_DELAY_SEC = 10;
  private static final int SCAN_ITERATIONS = 2;
  private HashMap<String, Date> previousGenerations;
  private Set<String> networks;

  private DiscoveryEvent runEnumeration(Enumerate enumerate) {
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumerate = enumerate;
    untilTrue("enumeration not active", () -> deviceState.discovery.generation == null);

    Date startTime = SemanticDate.describe("generation start time", CleanDateFormat.cleanDate());
    deviceConfig.discovery.generation = startTime;
    info("Starting empty enumeration at " + JsonUtil.getTimestamp(startTime));
    untilTrue("matching enumeration generation",
        () -> deviceState.discovery.generation.equals(startTime));

    deviceConfig.discovery.generation = null;
    untilTrue("cleared enumeration generation", () -> deviceState.discovery.generation == null);

    List<DiscoveryEvent> allEvents = popReceivedEvents(DiscoveryEvent.class);
    // Filter for enumeration events, since there will sometimes be lingering scan events.
    List<DiscoveryEvent> enumEvents = allEvents.stream().filter(event -> event.scan_id == null)
        .collect(Collectors.toList());
    assertEquals("a single discovery event received", 1, enumEvents.size());
    DiscoveryEvent event = enumEvents.get(0);
    info("Received discovery generation " + JsonUtil.getTimestamp(event.generation));
    assertEquals("matching event generation", startTime, event.generation);
    return event;
  }

  private void checkSelfEnumeration(DiscoveryEvent event, Enumerate enumerate) {
    if (isTrue(enumerate.networks)) {
      Set<String> models = Optional.ofNullable(deviceMetadata.localnet)
          .map(localnet -> localnet.networks.keySet()).orElse(null);
      Set<String> events = Optional.ofNullable(event.networks).map(Map::keySet).orElse(null);
      checkThat("network enumeration matches", () -> Objects.equals(models, events));
    } else {
      checkThat("no network enumeration", () -> event.networks == null);
    }

    if (isTrue(enumerate.features)) {
      checkThat("features enumerated", () -> event.features != null);
    } else {
      checkThat("no feature enumeration", () -> event.features == null);
    }

    if (isTrue(enumerate.uniqs)) {
      int expectedSize = Optional.ofNullable(deviceMetadata.pointset.points).map(HashMap::size)
          .orElse(0);
      checkThat("points enumerated " + expectedSize, () -> event.uniqs.size() == expectedSize);
    } else {
      checkThat("no point enumeration", () -> event.uniqs == null);
    }
  }

  private boolean isTrue(Boolean condition) {
    return Optional.ofNullable(condition).orElse(false);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = ALPHA)
  public void empty_enumeration() {
    Enumerate enumerate = new Enumerate();
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = ALPHA)
  public void pointset_enumeration() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      throw new SkipTest("No metadata pointset points defined");
    }
    Enumerate enumerate = new Enumerate();
    enumerate.uniqs = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION_FEATURES, stage = BETA)
  public void feature_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.features = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION_NETWORKS, stage = ALPHA)
  public void network_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.networks = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(stage = ALPHA)
  public void multi_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.networks = true;
    enumerate.features = true;
    enumerate.uniqs = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  public void single_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate(
        Date.from(Instant.now().plusSeconds(SCAN_START_DELAY_SEC)));
    boolean shouldEnumerate = true;
    scheduleScan(startTime, null, shouldEnumerate);
    untilTrue("scheduled scan start",
        () -> networks.stream().anyMatch(networkScanActivated(startTime))
            || networks.stream()
            .anyMatch(network -> !stateGenerationSame(network, previousGenerations))
            || !deviceState.timestamp.before(startTime));
    if (deviceState.timestamp.before(startTime)) {
      warning("scan started before activation: " + deviceState.timestamp + " < " + startTime);
      assertFalse("premature activation",
          networks.stream().anyMatch(networkScanActivated(startTime)));
      assertFalse("premature generation",
          networks.stream()
              .anyMatch(network -> !stateGenerationSame(network, previousGenerations)));
      fail("unknown reason");
    }
    untilTrue("scan activation", () -> networks.stream().allMatch(networkScanActivated(startTime)));
    untilTrue("scan completed", () -> networks.stream().allMatch(networkScanComplete(startTime)));
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(
        DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    Set<String> eventNetworks = receivedEvents.stream()
        .flatMap(event -> event.networks.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested networks present", eventNetworks.containsAll(networks));
  }

  private void checkEnumeration(List<DiscoveryEvent> receivedEvents, boolean shouldEnumerate) {
    Predicate<DiscoveryEvent> hasPoints = event -> event.uniqs != null
        && !event.uniqs.isEmpty();
    if (shouldEnumerate) {
      assertTrue("with enumeration", receivedEvents.stream().allMatch(hasPoints));
    } else {
      assertTrue("sans enumeration", receivedEvents.stream().noneMatch(hasPoints));
    }
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(DISCOVERY_SCAN)
  public void periodic_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate();
    boolean shouldEnumerate = true;
    scheduleScan(startTime, SCAN_START_DELAY_SEC, shouldEnumerate);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY_SEC * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    String oneNetwork = networks.iterator().next();
    Date finishTime = deviceState.discovery.networks.get(oneNetwork).generation;
    assertTrue("premature termination",
        networks.stream().noneMatch(networkScanComplete(finishTime)));
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    int expected = SCAN_ITERATIONS * networks.size();
    int received = receivedEvents.size();
    assertTrue("number responses received", received >= expected && received <= expected + 1);
  }

  private void initializeDiscovery() {
    networks = catchToNull(() -> deviceMetadata.discovery.networks.keySet());
    if (networks == null || networks.isEmpty()) {
      throw new SkipTest("No discovery networks configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.networks = new HashMap<>();
    untilTrue("all scans not active",
        () -> networks.stream().noneMatch(networkScanActivated(null)));
    previousGenerations = new HashMap<>();
    networks.forEach(
        network -> previousGenerations.put(network, getStateNetworkGeneration(network)));
  }

  private void scheduleScan(Date startTime, Integer scanIntervalSec, boolean enumerate) {
    info("Scan start scheduled for " + startTime);
    networks.forEach(network -> {
      getConfigNetwork(network).generation = SemanticDate.describe("network generation", startTime);
      getConfigNetwork(network).enumerate = enumerate;
      getConfigNetwork(network).scan_interval_sec = scanIntervalSec;
    });
    popReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
  }

  private NetworkDiscoveryConfig getConfigNetwork(String network) {
    return deviceConfig.discovery.networks.computeIfAbsent(network,
        adding -> new NetworkDiscoveryConfig());
  }

  private Date getStateNetworkGeneration(String network) {
    return catchToNull(() -> getStateNetwork(network).generation);
  }

  private boolean stateGenerationSame(String network, Map<String, Date> previousGenerations) {
    return Objects.equals(previousGenerations.get(network), getStateNetworkGeneration(network));
  }

  private NetworkDiscoveryState getStateNetwork(String network) {
    return deviceState.discovery.networks.get(network);
  }

  private Predicate<String> networkScanActive(Date startTime) {
    return network -> catchToFalse(() -> getStateNetwork(network).active
        && CleanDateFormat.dateEquals(getStateNetwork(network).generation, startTime));
  }

  private Predicate<String> networkScanActivated(Date startTime) {
    return network -> catchToFalse(() -> getStateNetwork(network).active
        || CleanDateFormat.dateEquals(getStateNetwork(network).generation, startTime));
  }

  private Predicate<? super String> networkScanComplete(Date startTime) {
    return networkScanActivated(startTime).and(networkScanActive(startTime).negate());
  }
}
