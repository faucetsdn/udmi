package com.google.daq.mqtt.validator.validations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.validator.CleanDateFormat;
import com.google.daq.mqtt.validator.SequenceValidator;
import com.google.daq.mqtt.validator.SkipTest;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoveryValidator extends SequenceValidator {

  public static final int SCAN_START_DELAY_SEC = 10;

  @Test
  public void self_enumeration() {
    if (!safeTrue(() -> deviceMetadata.pointset.points != null)) {
      throw new SkipTest("No metadata pointset points defined");
    }
    untilUntrue(() -> deviceState.discovery.enumeration.active, "enumeration not active");
    Date startTime = CleanDateFormat.cleanDate();
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumeration = new FamilyDiscoveryConfig();
    deviceConfig.discovery.enumeration.generation = startTime;
    info("Starting enumeration at " + getTimestamp(startTime));
    updateConfig();
    untilTrue(() -> deviceState.discovery.enumeration.generation.equals(startTime),
        "enumeration generation");
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
    if (!safeTrue(() -> deviceMetadata.testing.discovery.families.size() > 0)) {
      throw new SkipTest("No discovery families configured");
    }
    Set<String> families = deviceMetadata.testing.discovery.families.keySet();
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    families.forEach(family ->
        deviceConfig.discovery.families.computeIfAbsent(family,
            adding -> new FamilyDiscoveryConfig()));
    updateConfig();
    untilUntrue(
        () -> deviceState.discovery.families.values().stream().noneMatch(family -> family.active),
        "all scans not active");
    Date startTime = Date.from(Instant.now().plusSeconds(SCAN_START_DELAY_SEC));
    families.forEach(family -> deviceConfig.discovery.families.get(family).generation = startTime);
    updateConfig();
    getReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
    untilTrue(() -> families.stream()
        .allMatch(family -> deviceState.discovery.families.get(family).active &&
            CleanDateFormat.dateEquals(deviceState.discovery.families.get(family).generation,
                startTime)), "all scans active");
    untilTrue(() -> families.stream()
            .noneMatch(family -> deviceState.discovery.families.get(family).active),
        "all scans not active");
    List<DiscoveryEvent> receivedEvents = getReceivedEvents(
        DiscoveryEvent.class);

    Set<String> eventFamilies = receivedEvents.stream()
        .flatMap(event -> event.families.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested families present", eventFamilies.containsAll(families));
  }

  private boolean safeTrue(Supplier<Boolean> evaluator) {
    try {
      return evaluator.get();
    } catch (Exception e) {
      return false;
    }
  }
}
