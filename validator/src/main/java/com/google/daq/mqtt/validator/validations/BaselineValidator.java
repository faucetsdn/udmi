package com.google.daq.mqtt.validator.validations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetConfig;
import udmi.schema.TargetTestingMetadata;

public class BaselineValidator extends SequenceValidator {

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;
  public static final String BASE_CONFIG_RECEIVE = "base.config.receive";
  public static final String BASE_CONFIG_PARSE = "base.config.parse";
  public static final String BASE_CONFIG_APPLY = "base.config.apply";

  @Before
  public void makePoints() {
    deviceConfig.pointset = Optional.ofNullable(deviceConfig.pointset).orElse(new PointsetConfig());
    deviceConfig.pointset.points = Optional.ofNullable(deviceConfig.pointset.points)
        .orElse(new HashMap<>());
    try {
      ensurePointConfig(INVALID_STATE);
      ensurePointConfig(FAILURE_STATE);
      ensurePointConfig(APPLIED_STATE);
    } catch (SkipTest skipTest) {
      info("Not setting config points: " + skipTest.getMessage());
    }
    untilTrue(this::validSerialNo, "valid serial no " + serial_no);
  }

  private void ensurePointConfig(String target) {
    String target_point = getTarget(target).target_point;
    if (!deviceConfig.pointset.points.containsKey(target_point)) {
      deviceConfig.pointset.points.put(target_point, new PointPointsetConfig());
    }
  }

  private boolean valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      return false;
    }
    Value_state value_state = deviceState.pointset.points.get(pointName).value_state;
    String valueState = value_state == null ? null : value_state.value();
    boolean equals = Objects.equals(expected, valueState);
    System.err.printf("%s Value state %s equals %s = %s%n",
        getTimestamp(), expected, valueState, equals);
    return equals;
  }

  @Test
  public void system_last_update() {
    deviceConfig.system.min_loglevel = 400;
    clearLogs();
    Date expectedConfig = syncConfig();
    System.err.printf("%s expecting config %s%n", getTimestamp(), getTimestamp(expectedConfig));
    hasLogged(BASE_CONFIG_RECEIVE, Level.INFO);
    hasLogged(BASE_CONFIG_PARSE, Level.INFO);
    untilTrue(() -> expectedConfig.equals(deviceState.system.last_config), "state last_config match");
    hasLogged(BASE_CONFIG_APPLY, Level.INFO);
    System.err.printf("%s last_config match %s%n", getTimestamp(), getTimestamp(expectedConfig));
  }

  @Test
  public void broken_config() {
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    clearLogs();
    Date prevConfig = syncConfig();
    untilTrue(() -> prevConfig.equals(deviceState.system.last_config), "previous config synced");
    info("saved last_config " + getTimestamp(prevConfig));
    extraField = "break_json";
    updateConfig();
    hasLogged(BASE_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> deviceState.system.statuses.containsKey("config"), "statuses has config");
    Entry configStatus = deviceState.system.statuses.get("config");
    assertEquals(BASE_CONFIG_PARSE, configStatus.category);
    assertEquals(Level.ERROR, configStatus.level);
    assertEquals(prevConfig, deviceState.system.last_config);
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(BASE_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(BASE_CONFIG_APPLY, Level.INFO);
    extraField = null;
    updateConfig();
    hasLogged(BASE_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses not config");
    untilTrue(() -> !deviceState.system.last_config.equals(prevConfig), "last_config updated");
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(BASE_CONFIG_PARSE, Level.INFO);
    hasLogged(BASE_CONFIG_APPLY, Level.INFO);
  }

  @Test
  public void extra_config() {
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    clearLogs();
    Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    updateConfig();
    hasLogged(BASE_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(prevConfig), "last_config updated");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    hasLogged(BASE_CONFIG_PARSE, Level.INFO);
    hasLogged(BASE_CONFIG_APPLY, Level.INFO);
    Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    updateConfig();
    hasLogged(BASE_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(updatedConfig), "last_config updated again");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    hasLogged(BASE_CONFIG_PARSE, Level.INFO);
    hasLogged(BASE_CONFIG_APPLY, Level.INFO);
  }

  @Test
  public void writeback_states() {
    TargetTestingMetadata invalidTarget = getTarget(INVALID_STATE);
    TargetTestingMetadata failureTarget = getTarget(FAILURE_STATE);
    TargetTestingMetadata appliedTarget = getTarget(APPLIED_STATE);

    String invalidPoint = invalidTarget.target_point;
    String failurePoint = failureTarget.target_point;
    String appliedPoint = appliedTarget.target_point;
    untilTrue(() -> valueStateIs(invalidPoint, DEFAULT_STATE),
        expectedValueState(invalidPoint, DEFAULT_STATE));
    untilTrue(() -> valueStateIs(failurePoint, DEFAULT_STATE),
        expectedValueState(failurePoint, DEFAULT_STATE));
    untilTrue(() -> valueStateIs(appliedPoint, DEFAULT_STATE),
        expectedValueState(appliedPoint, DEFAULT_STATE));
    deviceConfig.pointset.points.get(invalidPoint).set_value = invalidTarget.target_value;
    deviceConfig.pointset.points.get(failurePoint).set_value = failureTarget.target_value;
    deviceConfig.pointset.points.get(appliedPoint).set_value = appliedTarget.target_value;
    updateConfig();
    untilTrue(() -> valueStateIs(invalidPoint, INVALID_STATE),
        expectedValueState(invalidPoint, INVALID_STATE));
    untilTrue(() -> valueStateIs(failurePoint, FAILURE_STATE),
        expectedValueState(invalidPoint, FAILURE_STATE));
    untilTrue(() -> valueStateIs(appliedPoint, APPLIED_STATE),
        expectedValueState(invalidPoint, APPLIED_STATE));
  }

  private String expectedValueState(String pointName, String expectedValue) {
    String targetState = expectedValue == null ? "default (null)" : expectedValue;
    return String.format("point %s to have value_state %s", pointName, targetState);
  }

  private TargetTestingMetadata getTarget(String target) {
    if (deviceMetadata.testing == null ||
        deviceMetadata.testing.targets == null ||
        !deviceMetadata.testing.targets.containsKey(target)) {
      throw new SkipTest(String.format("Missing '%s' target specification", target));
    }
    TargetTestingMetadata testingMetadata = deviceMetadata.testing.targets.get(target);
    if (deviceMetadata.pointset == null || deviceMetadata.pointset.points == null) {
      info("No metadata pointset points defined, I hope you know what you're doing");
    } else if (!deviceMetadata.pointset.points.containsKey(testingMetadata.target_point)) {
      throw new RuntimeException(
          String.format("Testing target %s point '%s' not defined in pointset metadata",
              target, testingMetadata.target_point));
    }
    return testingMetadata;
  }
}
