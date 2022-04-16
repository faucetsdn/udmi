package com.google.daq.mqtt.validator.validations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.daq.mqtt.validator.CleanDateFormat;
import com.google.daq.mqtt.validator.SequenceValidator;
import com.google.daq.mqtt.validator.SkipTest;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoveryValidator extends SequenceValidator {

  public static final int SCAN_START_DELAY_SEC = 10;

  @Test
  public void self_enumeration() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      throw new SkipTest("No metadata pointset points defined");
    }
    untilUntrue(() -> deviceState.discovery.enumeration.active, "enumeration not active");
    Date startTime = CleanDateFormat.cleanDate();
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumeration = new FamilyDiscoveryConfig();
    deviceConfig.discovery.enumeration.generation = startTime;
    info("Starting enumeration at " + getTimestamp(startTime));
    updateConfig();
    untilTrue("enumeration generation",
        () -> deviceState.discovery.enumeration.generation.equals(startTime)
    );
    untilUntrue(() -> deviceState.discovery.enumeration.active, "enumeration still not active");
    List<DiscoveryEvent> events = getReceivedEvents(DiscoveryEvent.class);
    assertEquals("one event received", 1, events.size());
    DiscoveryEvent discoveryEvent = events.get(0);
    info("Received discovery generation " + getTimestamp(discoveryEvent.generation));
    assertEquals("matching event generation", startTime, discoveryEvent.generation);
    discoveryEvent.points.values().forEach(point -> System.err.println(toJsonString(point)));
    int discoveredPoints = discoveryEvent.points == null ? 0 : discoveryEvent.points.size();
    assertEquals("discovered points count", deviceMetadata.pointset.points.size(),
        discoveredPoints);
  }

  @Test
  public void single_scan() {
    Set<String> families = catchToNull(() -> deviceMetadata.testing.discovery.families.keySet());
    if (families == null || families.isEmpty()) {
      throw new SkipTest("No discovery families configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    updateConfig();
    untilTrue("all scans not active", () -> families.stream().noneMatch(familyScanActivated(null)));
    Map<String, Date> previousGenerations = new HashMap<>();
    families.forEach(family -> previousGenerations.put(family, getStateFamilyGeneration(family)));
    Date startTime = Date.from(Instant.now().plusSeconds(SCAN_START_DELAY_SEC));
    families.forEach(family -> getConfigFamily(family).generation = startTime);
    updateConfig();
    getReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
    untilTrue("scheduled scan start", () -> families.stream().anyMatch(familyScanActivated(startTime))
        || !families.stream().allMatch(family -> stateGenerationSame(family, previousGenerations))
        || !deviceState.timestamp.before(startTime));
    if (deviceState.timestamp.before(startTime)) {
      assertFalse("premature activation", families.stream().anyMatch(familyScanActivated(startTime)));
      assertTrue("premature generation",
          families.stream().allMatch(family -> stateGenerationSame(family, previousGenerations)));
      fail("unknown reason");
    }
    untilTrue("scan activation", () -> families.stream().allMatch(familyScanActivated(startTime)));
    List<DiscoveryEvent> receivedEvents = getReceivedEvents(
        DiscoveryEvent.class);
    Set<String> eventFamilies = receivedEvents.stream()
        .flatMap(event -> event.families.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested families present", eventFamilies.containsAll(families));
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

  private Predicate<String> familyScanActivated(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active ||
        (startTime != null && !getStateFamily(family).generation.before(
            startTime)));
  }

}
