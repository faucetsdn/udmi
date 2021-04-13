package com.google.daq.mqtt.validator.validations;

import com.google.daq.mqtt.registrar.UdmiSchema.PointConfig;
import com.google.daq.mqtt.registrar.UdmiSchema.PointsetConfig;
import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

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
    deviceConfig.pointset.points.put(RECALCITRANT_ANGLE, new PointConfig());
    deviceConfig.pointset.points.put(FAULTY_FINDING, new PointConfig());
    deviceConfig.pointset.points.put(SUPERIMPOSITION_READING, new PointConfig());
    untilPointsetStateEtagIsUpdated();
    untilTrue(this::validSerialNo, "valid serial no");
  }

  public boolean pointsetStateEtagIs(String expected) {
    if (deviceState.pointset == null) {
      return false;
    }
    return Objects.equals(expected, deviceState.pointset.config_etag);
  }

  private boolean valueStateIs(String pointName, String expected) {
    if (deviceState.pointset == null || !deviceState.pointset.points.containsKey(pointName)) {
      return false;
    }
    return Objects.equals(expected, deviceState.pointset.points.get(pointName).value_state);
  }

  private void untilPointsetStateEtagIsUpdated() {
    String timestamp = Objects.toString(System.currentTimeMillis());
    deviceConfig.pointset.config_etag = timestamp;
    updateConfig();
    untilTrue(() -> pointsetStateEtagIs(timestamp), "etag " + timestamp);
  }

  @Test
  public void pointset_etag() {
    untilPointsetStateEtagIsUpdated();
    untilPointsetStateEtagIsUpdated();
  }

  @Test
  public void writeback_states() {
    untilPointsetStateEtagIsUpdated();
    untilTrue(() -> valueStateIs(RECALCITRANT_ANGLE, DEFAULT_STATE), "default value_state");
    untilTrue(() -> valueStateIs(FAULTY_FINDING, DEFAULT_STATE), "default value_state");
    untilTrue(() -> valueStateIs(SUPERIMPOSITION_READING, DEFAULT_STATE), "default value_state");
    deviceConfig.pointset.points.get(RECALCITRANT_ANGLE).set_value = 20;
    deviceConfig.pointset.points.get(FAULTY_FINDING).set_value = true;
    deviceConfig.pointset.points.get(SUPERIMPOSITION_READING).set_value = 10;
    untilPointsetStateEtagIsUpdated();
    untilTrue(() -> valueStateIs(RECALCITRANT_ANGLE, INVALID_STATE), "invalid value_state");
    untilTrue(() -> valueStateIs(FAULTY_FINDING, FAILURE_STATE), "failure value_state");
    untilTrue(() -> valueStateIs(SUPERIMPOSITION_READING, APPLIED_STATE), "applied value_state");
  }

}
