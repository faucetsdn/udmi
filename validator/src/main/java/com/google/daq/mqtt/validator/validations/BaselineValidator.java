package com.google.daq.mqtt.validator.validations;

import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetConfig;
import udmi.schema.TargetTestingMetadata;

public class BaselineValidator extends SequenceValidator {

  public static final String RECALCITRANT_ANGLE = "recalcitrant_angle";
  public static final String FAULTY_FINDING = "faulty_finding";
  public static final String SUPERIMPOSITION_READING = "superimposition_reading";
  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;

  @Before
  public void makePoints() {
    deviceConfig.pointset = new PointsetConfig();
    deviceConfig.pointset.points = new HashMap<>();
    deviceConfig.pointset.points.put(RECALCITRANT_ANGLE, new PointPointsetConfig());
    deviceConfig.pointset.points.put(FAULTY_FINDING, new PointPointsetConfig());
    deviceConfig.pointset.points.put(SUPERIMPOSITION_READING, new PointPointsetConfig());
    untilTrue(this::validSerialNo, "valid serial no");
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
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    Date prevConfig = deviceState.system.last_config;
    updateConfig();
    untilTrue(() -> !prevConfig.equals(deviceState.system.last_config), "last_config " + prevConfig);
    System.err.printf("%s last_config updated from %s to %s%n", getTimestamp(), prevConfig,
        deviceState.system.last_config);
  }

  @Test
  public void writeback_states() {
    TargetTestingMetadata invalidTarget = getTarget("invalid");
    TargetTestingMetadata failureTarget = getTarget("failure");
    TargetTestingMetadata appliedTarget = getTarget("applied");

    String invalidPoint = invalidTarget.target_point;
    String failurePoint = failureTarget.target_point;
    String appliedPoint = appliedTarget.target_point;
    untilTrue(() -> valueStateIs(invalidPoint, DEFAULT_STATE), expectedValueState(invalidPoint, "default (null)"));
    untilTrue(() -> valueStateIs(failurePoint, DEFAULT_STATE), expectedValueState(failurePoint, "default (null)"));
    untilTrue(() -> valueStateIs(appliedPoint, DEFAULT_STATE), expectedValueState(appliedPoint,"default (null)"));
    deviceConfig.pointset.points.get(invalidPoint).set_value = invalidTarget.target_value;
    deviceConfig.pointset.points.get(failurePoint).set_value = failureTarget.target_value;
    deviceConfig.pointset.points.get(appliedPoint).set_value = appliedTarget.target_value;
    updateConfig();
    untilTrue(() -> valueStateIs(invalidPoint, INVALID_STATE), expectedValueState(invalidPoint,"invalid"));
    untilTrue(() -> valueStateIs(failurePoint, FAILURE_STATE), expectedValueState(invalidPoint,"failure"));
    untilTrue(() -> valueStateIs(appliedPoint, APPLIED_STATE), expectedValueState(invalidPoint,"applied"));
  }

  private String expectedValueState(String pointName, String expectedValue) {
    return String.format("point %s to have value_state %s", pointName, expectedValue);
  }

  private TargetTestingMetadata getTarget(String target) {
    if (deviceMetadata.testing == null ||
        deviceMetadata.testing.targets == null ||
        !deviceMetadata.testing.targets.containsKey(target)) {
      throw new SkipTest(String.format("Missing '%s' target specification", target));
    }
    return deviceMetadata.testing.targets.get(target);
  }

}
