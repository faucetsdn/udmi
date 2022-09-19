package com.google.daq.mqtt.sequencer.sequences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.daq.mqtt.sequencer.SequencesTestBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.util.JsonUtil;
import com.google.daq.mqtt.validator.CleanDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequencesTestBase {

  public static final int SCAN_START_DELAY_SEC = 10;
  private static final int SCAN_ITERATIONS = 2;
  private HashMap<String, Date> previousGenerations;
  private Set<String> families;

  @Test
  public void self_enumeration() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      throw new SkipTest("No metadata pointset points defined");
    }
    untilUntrue("enumeration not active", () -> deviceState.discovery.enumeration.active);
    Date startTime = SemanticDate.describe("generation start time", CleanDateFormat.cleanDate());
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumeration = new FamilyDiscoveryConfig();
    deviceConfig.discovery.enumeration.generation = startTime;
    info("Starting enumeration at " + JsonUtil.getTimestamp(startTime));
    updateConfig("discovery generation");
    untilTrue("enumeration generation",
        () -> deviceState.discovery.enumeration.generation.equals(startTime)
    );
    untilUntrue("enumeration still not active", () -> deviceState.discovery.enumeration.active);
    List<DiscoveryEvent> events = getReceivedEvents(DiscoveryEvent.class);
    assertTrue("a few events received", events.size() >= 1 && events.size() <= 2);
    DiscoveryEvent discoveryEvent = events.get(0);
    info("Received discovery generation " + JsonUtil.getTimestamp(discoveryEvent.generation));
    assertEquals("matching event generation", startTime, discoveryEvent.generation);
    int discoveredPoints = discoveryEvent.uniqs == null ? 0 : discoveryEvent.uniqs.size();
    assertEquals("discovered points count", deviceMetadata.pointset.points.size(),
        discoveredPoints);
  }

  @Test
  public void single_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate(
        Date.from(Instant.now().plusSeconds(SCAN_START_DELAY_SEC)));
    boolean shouldEnumerate = true;
    scheduleScan(startTime, null, shouldEnumerate);
    untilTrue("scheduled scan start",
        () -> families.stream().anyMatch(familyScanActivated(startTime))
            || families.stream()
            .anyMatch(family -> !stateGenerationSame(family, previousGenerations))
            || !deviceState.timestamp.before(startTime));
    if (deviceState.timestamp.before(startTime)) {
      warning("scan started before activation: " + deviceState.timestamp + " < " + startTime);
      assertFalse("premature activation",
          families.stream().anyMatch(familyScanActivated(startTime)));
      assertFalse("premature generation",
          families.stream().anyMatch(family -> !stateGenerationSame(family, previousGenerations)));
      fail("unknown reason");
    }
    untilTrue("scan activation", () -> families.stream().allMatch(familyScanActivated(startTime)));
    untilTrue("scan completed", () -> families.stream().allMatch(familyScanComplete(startTime)));
    List<DiscoveryEvent> receivedEvents = getReceivedEvents(
        DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    Set<String> eventFamilies = receivedEvents.stream()
        .flatMap(event -> event.families.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested families present", eventFamilies.containsAll(families));
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

  @Test
  public void periodic_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate();
    boolean shouldEnumerate = true;
    scheduleScan(startTime, SCAN_START_DELAY_SEC, shouldEnumerate);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY_SEC * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    String oneFamily = families.iterator().next();
    Date finishTime = deviceState.discovery.families.get(oneFamily).generation;
    assertTrue("premature termination",
        families.stream().noneMatch(familyScanComplete(finishTime)));
    List<DiscoveryEvent> receivedEvents = getReceivedEvents(DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    int expected = SCAN_ITERATIONS * families.size();
    int received = receivedEvents.size();
    assertTrue("number responses received", received >= expected && received <= expected + 1);
  }

  private void initializeDiscovery() {
    families = catchToNull(() -> deviceMetadata.discovery.families.keySet());
    if (families == null || families.isEmpty()) {
      throw new SkipTest("No discovery families configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    updateConfig();
    untilTrue("all scans not active", () -> families.stream().noneMatch(familyScanActivated(null)));
    previousGenerations = new HashMap<>();
    families.forEach(family -> previousGenerations.put(family, getStateFamilyGeneration(family)));
  }

  private void scheduleScan(Date startTime, Integer scanIntervalSec, boolean enumerate) {
    info("Scan start scheduled for " + startTime);
    families.forEach(family -> {
      getConfigFamily(family).generation = startTime;
      getConfigFamily(family).enumerate = enumerate;
      getConfigFamily(family).scan_interval_sec = scanIntervalSec;
    });
    updateConfig();
    getReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
  }

  private FamilyDiscoveryConfig getConfigFamily(String family) {
    return deviceConfig.discovery.families.computeIfAbsent(family,
        adding -> new FamilyDiscoveryConfig());
  }

  private Date getStateFamilyGeneration(String family) {
    return catchToNull(() -> getStateFamily(family).generation);
  }

  private boolean stateGenerationSame(String family, Map<String, Date> previousGenerations) {
    return Objects.equals(previousGenerations.get(family), getStateFamilyGeneration(family));
  }

  private FamilyDiscoveryState getStateFamily(String family) {
    return deviceState.discovery.families.get(family);
  }

  private Predicate<String> familyScanActive(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        && CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<String> familyScanActivated(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        || CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<? super String> familyScanComplete(Date startTime) {
    return familyScanActivated(startTime).and(familyScanActive(startTime).negate());
  }
}
