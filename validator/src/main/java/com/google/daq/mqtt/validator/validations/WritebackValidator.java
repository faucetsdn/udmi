package com.google.daq.mqtt.validator.validations;

import com.google.daq.mqtt.validator.PointValidator;
import java.util.Objects;
import org.junit.Test;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.TargetTestingMetadata;

/**
 * Validate UDMI writeback capabilities.
 */
public class WritebackValidator extends PointValidator {

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;

  private boolean valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      return false;
    }
    Value_state rawState = deviceState.pointset.points.get(pointName).value_state;
    String valueState = rawState == null ? null : rawState.value();
    boolean equals = Objects.equals(expected, valueState);
    System.err.printf("%s Value state %s equals %s = %s%n",
        getTimestamp(), expected, valueState, equals);
    return equals;
  }

  private String expectedValueState(String pointName, String expectedValue) {
    String targetState = expectedValue == null ? "default (null)" : expectedValue;
    return String.format("point %s to have value_state %s", pointName, targetState);
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
}
