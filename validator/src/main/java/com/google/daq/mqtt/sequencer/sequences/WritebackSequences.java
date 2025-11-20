package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static java.lang.String.format;
import static udmi.schema.Bucket.WRITEBACK;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetEvents;
import udmi.schema.TargetTestingModel;

/**
 * Validate UDMI writeback capabilities.
 */
public class WritebackSequences extends PointsetBase {

  public static final String DEFAULT_STATE = null;
  private Object lastPresentValue;
  private static final Duration UPDATING_WAIT_DURATION = Duration.ofSeconds(4);
  private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(20);

  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("pointset.points.");
  }

  /**
   * Checks `value_state` for the point in the state matches the provided string.
   *
   * @param pointName Target point
   * @param expected  Expected `value_state`
   * @return true/false actual matches expected
   */
  private String valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      throw new AbortMessageLoop("Missing pointset point " + pointName);
    }
    Value_state rawState = deviceState.pointset.points.get(pointName).value_state;
    String valueState = rawState == null ? null : rawState.value();
    boolean equals = Objects.equals(expected, valueState);
    debug(format("Value state %s == %s (%s)", valueState, expected, equals));
    return equals ? null : format("point %s is %s, expected %s", pointName, valueState, expected);
  }

  /**
   * Log string for value_state check.
   */
  private String expectedValueState(String expectedValue) {
    String targetState = expectedValue == null ? "default (null)" : expectedValue;
    return format("target point has value_state %s", targetState);
  }

  /**
   * Checks if the `present_value` for the target point matches the target value.
   */
  private String presentValueIs(TargetTestingModel targetModel) {
    String pointName = targetModel.target_point;
    Object targetValue = targetModel.target_value;
    List<PointsetEvents> messages = popReceivedEvents(PointsetEvents.class);
    for (PointsetEvents pointsetEvent : messages) {
      if (pointsetEvent.points.get(pointName) != null) {
        lastPresentValue = pointsetEvent.points.get(pointName).present_value;
        if (targetValue.equals(lastPresentValue)) {
          return null;
        }
      }
    }
    return format("Point %s has present value %s, not target %s", pointName, lastPresentValue,
        targetValue);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = WRITEBACK)
  @Summary("Implements UDMI writeback and can successfully writeback to a point")
  public void writeback_success() {
    deviceConfig.pointset.sample_rate_sec = 10;
    TargetTestingModel targetModel = testTargetState(APPLIED_STATE);
    waitUntil("target point to have target expected value", MAX_WAIT_TIME,
        () -> presentValueIs(targetModel));
  }

  private TargetTestingModel testTargetState(String targetState) {
    TargetTestingModel targetModel = getTarget(targetState);
    String targetPoint = targetModel.target_point;
    Object targetValue = targetModel.target_value;

    deviceConfig.pointset.points.get(targetPoint).set_value = null;

    waitUntil(expectedValueState(DEFAULT_STATE), () -> valueStateIs(targetPoint, DEFAULT_STATE));

    deviceConfig.pointset.points.get(targetPoint).set_value = targetValue;

    waitUntil(expectedValueState(targetState), MAX_WAIT_TIME,
        () -> valueStateIs(targetPoint, targetState));

    return targetModel;
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = WRITEBACK)
  public void writeback_invalid() {
    testTargetState(INVALID_STATE);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = WRITEBACK)
  public void writeback_failure() {
    testTargetState(FAILURE_STATE);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = WRITEBACK)
  @Summary("Tests intermediate UPDATING state of a writeback operation")
  public void writeback_operation() {
    deviceConfig.pointset.sample_rate_sec = 10;
    TargetTestingModel targetModel = getTarget(APPLIED_STATE);
    String targetPoint = targetModel.target_point;
    Object targetValue = targetModel.target_value;

    deviceConfig.pointset.points.get(targetPoint).set_value = null;
    waitUntil(expectedValueState(DEFAULT_STATE), () -> valueStateIs(targetPoint, DEFAULT_STATE));

    deviceConfig.pointset.points.get(targetPoint).set_value = targetValue;
    // Wait until the intermediate UPDATING state. In other cases, this should:
    // 1. Skip if it ends up APPLIED too quickly.
    // 2. Error out if it takes too long to get to UPDATING.
    waitUntil(expectedValueState(UPDATING_STATE), UPDATING_WAIT_DURATION, () -> {
      String appliedStateCheck = valueStateIs(targetPoint, APPLIED_STATE);
      String appliedValueCheck = presentValueIs(targetModel);
      ifTrueSkipTest(appliedStateCheck == null && appliedValueCheck == null,
          "operation completed quickly");

      return valueStateIs(targetPoint, UPDATING_STATE);
    });
    waitUntil(expectedValueState(APPLIED_STATE), () -> valueStateIs(targetPoint, APPLIED_STATE));
  }
}

