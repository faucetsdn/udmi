package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.util.List;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.ValidationEvent;

/**
 * Unit test suite for the validator.
 */
public class BasicTest extends TestBase {

  private static final List<String> TEST_ARGS = ImmutableList.of(
      "-n",
      "-p", PROJECT_ID,
      "-a", SCHEMA_SPEC,
      "-s", SITE_DIR);
  private final Validator validator = new Validator(TEST_ARGS);

  @org.junit.Test
  public void emptySystemBlock() {
    MessageBundle bundle = getMessageBundle("event", "pointset", new PointsetEvent());
    bundle.message.remove("system");
    validator.validateMessage(bundle);
    ValidationEvent report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
    assertEquals("One error device", 1, report.devices.size());
  }

  @org.junit.Test
  public void emptyPointsetEvent() {
    MessageBundle bundle = getMessageBundle("event", "pointset", new PointsetEvent());
    validator.validateMessage(bundle);
    ValidationEvent report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
    assertEquals("One error device", 1, report.devices.size());
  }

  @org.junit.Test
  public void validPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    MessageBundle bundle = getMessageBundle("event", "pointset", messageObject);
    validator.validateMessage(bundle);
    ValidationEvent report = getValidationReport();
    assertEquals("No error devices", 0, report.devices.size());
  }

  @org.junit.Test
  public void missingPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle("event", "pointset", messageObject);
    validator.validateMessage(bundle);
    ValidationEvent report = getValidationReport();
    assertEquals("No error devices", 1, report.devices.size());
    List<String> missingPoints = report.devices.get("AHU-1").missing_points;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }

  @org.junit.Test
  public void missingPointsetState() {
    PointsetState messageObject = basePointsetState();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle("state", "pointset", messageObject);
    validator.validateMessage(bundle);
    ValidationEvent report = getValidationReport();
    assertEquals("No error devices", 1, report.devices.size());
    List<String> missingPoints = report.devices.get("AHU-1").missing_points;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }

}
