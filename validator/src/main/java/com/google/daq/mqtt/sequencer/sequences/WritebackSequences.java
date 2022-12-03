package com.google.daq.mqtt.sequencer.sequences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.sequencer.PointSequencer;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetEvent;
import udmi.schema.TargetTestingModel;

/**
 * Validate UDMI writeback capabilities.
 */
public class WritebackSequences extends PointSequencer {

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String DEFAULT_STATE = null;

  /**
   * Checks `value_state` for the point in the state matches the provided string.
   *
   * @param pointName Target point
   * @param expected Expected `value_state`
   * @return true/false actual matches expected
   */
  private boolean valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      return false;
    }
    Value_state rawState = deviceState.pointset.points.get(pointName).value_state;
    String valueState = rawState == null ? null : rawState.value();
    boolean equals = Objects.equals(expected, valueState);
    debug(String.format("%s Value state %s equals %s = %s%n",
        JsonUtil.getTimestamp(), expected, valueState, equals));
    return equals;
  }

  /** Log string for value_state check. */
  private String expectedValueState(String pointName, String expectedValue) {
    String targetState = expectedValue == null ? "default (null)" : expectedValue;
    return String.format("point %s to have value_state %s", pointName, targetState);
  }

  /** Log string for present_value check. */
  private String expectedPresentValue(String pointName, Object expectedValue) {
    return String.format("point `%s` to have present_value `%s`", pointName, expectedValue);
  }

  /**
   * Checks if the `present_value` for the given point matches the given value.
   *
   * @param pointName Target point
   * @param expectedValue Expected `present_value`
   * @return true/false actual matches expected
   */
  private boolean presentValueIs(String pointName, Object expectedValue) {
    List<PointsetEvent> messages = getReceivedEvents(PointsetEvent.class);
    for (PointsetEvent message : messages) {
      PointsetEvent pointsetEvent = JsonUtil.convertTo(PointsetEvent.class, message);
      if (pointsetEvent.points.get(pointName) != null 
          && pointsetEvent.points.get(pointName).present_value == expectedValue) {
        return true;
      }
    }
    return false;
  }

  protected PointsetEvent latestPointsetEvent() {
    List<PointsetEvent>  events = getReceivedEvents(PointsetEvent.class);
    if (events == null) {
      return null;
    }
    return JsonUtil.convertTo(PointsetEvent.class, events.get(events.size() - 1));
  }

  @Test(timeout = 90000)
  public void writeback_success_apply() {
    TargetTestingModel appliedTarget = getTarget(APPLIED_STATE);
    String appliedPoint = appliedTarget.target_point;
    Object appliedValue = appliedTarget.target_value;
    deviceConfig.pointset.points.get(appliedPoint).set_value = appliedValue;

    untilTrue(expectedPresentValue(appliedPoint, appliedValue),
        () -> presentValueIs(appliedPoint, appliedValue)
    );

  }

  @Test(timeout = 90000)
  public void writeback_success_state() {
    TargetTestingModel appliedTarget = getTarget(APPLIED_STATE);
    String appliedPoint = appliedTarget.target_point;
    Object appliedValue = appliedTarget.target_value;

    untilTrue(expectedValueState(appliedPoint, DEFAULT_STATE),
        () -> valueStateIs(appliedPoint, DEFAULT_STATE)
    );

    deviceConfig.pointset.points.get(appliedPoint).set_value = appliedValue;

    untilTrue(expectedValueState(appliedPoint, APPLIED_STATE),
        () -> valueStateIs(appliedPoint, APPLIED_STATE)
    );
    
    untilTrue(expectedPresentValue(appliedPoint, appliedValue),
        () -> presentValueIs(appliedPoint, appliedValue)
    );
  }

  @Test
  public void writeback_invalid_state() {
    TargetTestingModel invalidTarget = getTarget(INVALID_STATE);
    String invalidPoint = invalidTarget.target_point;
    Object invalidValue = invalidTarget.target_value;

    untilTrue(expectedValueState(invalidPoint, DEFAULT_STATE),
        () -> valueStateIs(invalidPoint, DEFAULT_STATE)
    );

    deviceConfig.pointset.points.get(invalidPoint).set_value = invalidValue;

    untilTrue(expectedValueState(invalidPoint, INVALID_STATE),
        () -> valueStateIs(invalidPoint, INVALID_STATE)
    );
  
  }

  @Test
  public void writeback_failure_state() {
    TargetTestingModel failureTarget = getTarget(FAILURE_STATE);
    String failurePoint = failureTarget.target_point;
    Object failureValue = failureTarget.target_value;

    untilTrue(expectedValueState(failurePoint, DEFAULT_STATE),
        () -> valueStateIs(failurePoint, DEFAULT_STATE)
    );

    deviceConfig.pointset.points.get(failurePoint).set_value = failureValue;

    untilTrue(expectedValueState(failurePoint, FAILURE_STATE),
        () -> valueStateIs(failurePoint, FAILURE_STATE)
    );
  }

}

