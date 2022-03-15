package com.google.daq.mqtt.validator.validations;

import static org.junit.Assert.assertEquals;

import com.google.daq.mqtt.validator.CleanDateFormat;
import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoveryValidator extends SequenceValidator {

  @Test
  public void self_enumeration() {
    untilUntrue(() -> deviceState.discovery.enumeration.active, "enumeration not active");
    Date startTime = CleanDateFormat.cleanDate();
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumeration = new FamilyDiscoveryConfig();
    deviceConfig.discovery.enumeration.generation = startTime;
    info("Starting enumeration at " + getTimestamp(startTime));
    updateConfig();
    untilTrue(() -> deviceState.discovery.enumeration.generation.equals(startTime),
        "enumeration generation");
    untilUntrue(() -> deviceState.discovery.enumeration.active, "enumeration also not active");
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

}
