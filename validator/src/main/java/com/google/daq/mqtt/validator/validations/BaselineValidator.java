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

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;

  @Before
  public void makePoints() {
    deviceConfig.pointset = new PointsetConfig();
    deviceConfig.pointset.points = new HashMap<>();
    try {
      TargetTestingMetadata invalidTarget = getTarget(INVALID_STATE);
      TargetTestingMetadata failureTarget = getTarget(FAILURE_STATE);
      TargetTestingMetadata appliedTarget = getTarget(APPLIED_STATE);
      deviceConfig.pointset.points.put(invalidTarget.target_point, new PointPointsetConfig());
      deviceConfig.pointset.points.put(failureTarget.target_point, new PointPointsetConfig());
      deviceConfig.pointset.points.put(appliedTarget.target_point, new PointPointsetConfig());
    } catch (SkipTest skipTest) {
      System.err.println("Not setting config points: " + skipTest.getMessage());
    }
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
    TargetTestingMetadata invalidTarget = getTarget(INVALID_STATE);
    TargetTestingMetadata failureTarget = getTarget(FAILURE_STATE);
    TargetTestingMetadata appliedTarget = getTarget(APPLIED_STATE);

    String invalidPoint = invalidTarget.target_point;
    String failurePoint = failureTarget.target_point;
    String appliedPoint = appliedTarget.target_point;
    untilTrue(() -> valueStateIs(invalidPoint, DEFAULT_STATE), expectedValueState(invalidPoint, DEFAULT_STATE));
    untilTrue(() -> valueStateIs(failurePoint, DEFAULT_STATE), expectedValueState(failurePoint, DEFAULT_STATE));
    untilTrue(() -> valueStateIs(appliedPoint, DEFAULT_STATE), expectedValueState(appliedPoint, DEFAULT_STATE));
    deviceConfig.pointset.points.get(invalidPoint).set_value = invalidTarget.target_value;
    deviceConfig.pointset.points.get(failurePoint).set_value = failureTarget.target_value;
    deviceConfig.pointset.points.get(appliedPoint).set_value = appliedTarget.target_value;
    updateConfig();
    untilTrue(() -> valueStateIs(invalidPoint, INVALID_STATE), expectedValueState(invalidPoint,INVALID_STATE));
    untilTrue(() -> valueStateIs(failurePoint, FAILURE_STATE), expectedValueState(invalidPoint,FAILURE_STATE));
    untilTrue(() -> valueStateIs(appliedPoint, APPLIED_STATE), expectedValueState(invalidPoint,APPLIED_STATE));
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
      System.err.println(getTimestamp() + " No metadata pointset points defined, I hope you know what you're doing");
    } else if (!deviceMetadata.pointset.points.containsKey(testingMetadata.target_point)) {
      throw new RuntimeException(String.format("Testing target %s point '%s' not defined in pointset metadata",
          target, testingMetadata.target_point));
    }
    return testingMetadata;
  }
}
