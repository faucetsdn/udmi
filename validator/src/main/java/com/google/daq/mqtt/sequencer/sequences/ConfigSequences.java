package com.google.daq.mqtt.sequencer.sequences;

import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;
import com.google.daq.mqtt.util.SamplingRange;
import com.google.daq.mqtt.sequencer.SequenceBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.PointsetEvent;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigSequences extends SequenceBase {

  @Test()
  @Description("Check that last_update state is correctly set in response to a config update.")
  public void system_last_update() {
    untilTrue("state last_config matches config timestamp", () -> {
      Date expectedConfig = deviceConfig.timestamp;
      Date lastConfig = deviceState.system.last_config;
      return dateEquals(expectedConfig, lastConfig);
    });
  }

  @Test
  @Description("Check that the min log-level config is honored by the device.")
  public void system_min_loglevel() {
    clearLogs();
    Integer savedLevel = deviceConfig.system.min_loglevel;
    deviceConfig.system.min_loglevel = Level.WARNING.value();
    hasNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    deviceConfig.system.min_loglevel = savedLevel;
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test
  @Description("Check that the device MQTT-acknowledges a sent config.")
  public void device_config_acked() {
    untilTrue("config acked", () -> configAcked);
  }

  @Test
  @Description("Check that the device correctly handles a broken (non-json) config message.")
  public void broken_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilFalse("no interesting status", this::hasInterestingStatus);
    untilTrue("clean config/state synced", this::configUpdateComplete);
    Date stableConfig = deviceConfig.timestamp;
    info("initial stable_config " + getTimestamp(stableConfig));
    untilTrue("state synchronized", () -> dateEquals(stableConfig, deviceState.system.last_config));
    info("initial last_config " + getTimestamp(deviceState.system.last_config));
    checkThat("initial stable_config matches last_config",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    clearLogs();
    extraField = "break_json";
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("has interesting status", this::hasInterestingStatus);
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("following stable_config " + getTimestamp(stableConfig));
    info("following last_config " + getTimestamp(deviceState.system.last_config));
    assertTrue("following stable_config matches last_config",
        dateEquals(stableConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    resetConfig(); // clears extra_field
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilFalse("no interesting status", this::hasInterestingStatus);
    untilTrue("last_config updated",
        () -> !dateEquals(stableConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
  }

  private boolean hasInterestingStatus() {
    return deviceState.system.status != null
        && deviceState.system.status.level >= Level.WARNING.value();
  }

  @Test
  @Description("Check that the device correctly handles an extra out-of-schema field")
  public void extra_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilTrue("last_config not null", () -> deviceState.system.last_config != null);
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    clearLogs();
    final Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated", () -> !deviceState.system.last_config.equals(prevConfig));
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    final Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated again",
        () -> !deviceState.system.last_config.equals(updatedConfig)
    );
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test
  @Description("test sample rate")
  public void pointset_sample_rate_sec() {

    int first_sample_rate_sec = 2;
    int second_sample_rate_sec = 10;
    int tolerance = 2;

    // Start small
    deviceConfig.pointset.sample_rate_sec = first_sample_rate_sec;
    getReceivedEvents(PointsetEvent.class);
    // wait 3 because there might be one message stuck in the system
    untilTrue("receive 3 pointset event",
        () -> (countReceivedEvents(PointsetEvent.class) > 4)
    );
    List<PointsetEvent> receivedEvents = getReceivedEvents(PointsetEvent.class);
    List<Long> vector = telemetryTimestampDeltaVector(receivedEvents);
    assertTrue("all values greater than x",
        allValuesLessThan(first_sample_rate_sec, tolerance, vector));

    deviceConfig.pointset.sample_rate_sec = second_sample_rate_sec;
    getReceivedEvents(PointsetEvent.class);
    untilTrue("receive some telemetry events",
        () -> (countReceivedEvents(PointsetEvent.class) > 4)
    );
    receivedEvents = getReceivedEvents(PointsetEvent.class);
    vector = telemetryTimestampDeltaVector(receivedEvents);
    vector.remove(0); // Ignore first

    assertTrue("all values greater than x",
        allValuesLessThan(second_sample_rate_sec, tolerance, vector));
  }

  @Test
  @Description("test sample rate")
  public void pointset_sample_limit_test() {
    SamplingRange samplingWindow = new SamplingRange(1,5,2);

    // Start small
    deviceConfig.pointset.sample_limit_sec = samplingWindow.sampleLimit;
    deviceConfig.pointset.sample_rate_sec = samplingWindow.sampleRate;
    getReceivedEvents(PointsetEvent.class);

    // wait for a few because there might be one message stuck in the system and then ignore the first
    untilTrue("receive 3 pointset event",
        () -> (countReceivedEvents(PointsetEvent.class) > 4)
    );

    List<PointsetEvent> receivedEvents = getReceivedEvents(PointsetEvent.class);
    List<Long> vector = telemetryTimestampDeltaVector(receivedEvents);
    vector.remove(0); // Ignore first
    assertTrue(samplingWindow.valuesInRange(vector));
  }

  private List<Long> telemetryTimestampDeltaVector(List<PointsetEvent> receivedEvents) {
    ArrayList<Long> deltaVector = new ArrayList<>();
    List<Date> events = receivedEvents.stream().map(event -> event.timestamp)
        .collect(Collectors.toList());
    Collections.sort(events);
    for (int i = 1; i < events.size(); i++) {
      deltaVector.add(((events.get(i).getTime() - events.get(i - 1).getTime()) / 1000));
    }
    return deltaVector;
  }

  private boolean allValuesLessThan(double threshold, double tolerance, List<Long> vector) {
    return (vector.stream().filter(x -> x < threshold + tolerance).count() > 0);
  }

  private boolean allValuesInRange(double lower, double upper, double tolerance,
      List<Long> vector) {
    return (vector.stream().filter(x -> (
        x < upper + tolerance && x > lower - tolerance
    )).count() > 0);
  }

}


