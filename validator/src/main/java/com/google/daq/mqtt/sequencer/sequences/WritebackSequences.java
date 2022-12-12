package com.google.daq.mqtt.sequencer.sequences;

import com.google.daq.mqtt.sequencer.PointsetBase;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetEvent;
import udmi.schema.TargetTestingModel;

/**
 * Validate UDMI writeback capabilities.
 */
public class WritebackSequences extends PointsetBase {

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;

  /**
   * Checks `value_state` for the point in the state matches the provided string.
   *
   * @param pointName Target point
   * @param expected  Expected `value_state`
   * @return true/false actual matches expected
   */
  private boolean valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      return false;
    }
    Value_state rawState = deviceState.pointset.points.get(pointName).value_state;
    String valueState = rawState == null ? null : rawState.value();
    boolean equals = Objects.equals(expected, valueState);
    debug(String.format("Value state %s equals %s = %s", expected, valueState, equals));
    return equals;
  }

  /**
   * Log string for value_state check.
   */
  private String expectedValueState(String pointName, String expectedValue) {
    String targetState = expectedValue == null ? "default (null)" : expectedValue;
    return String.format("point %s to have value_state %s", pointName, targetState);
  }

  /**
   * Log string for present_value check.
   *
   * @param targetModel Target point model
   */
  private String expectedPresentValue(TargetTestingModel targetModel) {
    return String.format("point `%s` to have present_value `%s`", targetModel.target_point,
        targetModel.target_value);
  }

  /**
   * Checks if the `present_value` for the given point matches the given value.
   *
   * @param targetModel Target point model
   * @return true/false actual matches expected
   */
  private boolean presentValueIs(TargetTestingModel targetModel) {
    String pointName = targetModel.target_point;
    List<PointsetEvent> messages = getReceivedEvents(PointsetEvent.class);
    for (PointsetEvent pointsetEvent : messages) {
      if (pointsetEvent.points.get(pointName) != null
          && pointsetEvent.points.get(pointName).present_value == targetModel.target_value) {
        return true;
      }
    }
    return false;
  }

  @Test(timeout = 90000)
  public void writeback_success() {
    TargetTestingModel targetModel = testTargetState(APPLIED_STATE);
    untilTrue(expectedPresentValue(targetModel), () -> presentValueIs(targetModel));
  }

  private TargetTestingModel testTargetState(String targetState) {
    TargetTestingModel targetModel = getTarget(targetState);
    String targetPoint = targetModel.target_point;
    Object targetValue = targetModel.target_value;

    deviceConfig.pointset.points.get(targetPoint).set_value = null;

    untilTrue(expectedValueState(targetPoint, DEFAULT_STATE),
        () -> valueStateIs(targetPoint, DEFAULT_STATE));

    deviceConfig.pointset.points.get(targetPoint).set_value = targetValue;

    untilTrue(expectedValueState(targetPoint, targetState),
        () -> valueStateIs(targetPoint, targetState));

    return targetModel;
  }

  @Test
  public void writeback_invalid() {
    testTargetState(INVALID_STATE);
  }

  @Test
  public void writeback_failure() {
    testTargetState(FAILURE_STATE);
  }
}

