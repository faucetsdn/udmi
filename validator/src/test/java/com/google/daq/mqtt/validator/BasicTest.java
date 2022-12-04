package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.util.List;
import org.junit.Test;
import udmi.schema.DeviceValidationEvent;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.PointsetSummary;
import udmi.schema.ValidationEvent;
import udmi.schema.ValidationState;

/**
 * Unit test suite for the validator.
 */
public class BasicTest extends TestBase {

  private static final String EVENT_SUBTYPE = "event";
  private static final String STATE_SUBTYPE = "state";
  private static final String POINTSET_SUBFOLDER = "pointset";
  private static final List<String> TEST_ARGS = ImmutableList.of(
      "-n",
      "-p", SiteModel.MOCK_PROJECT,
      "-a", TestCommon.SCHEMA_SPEC,
      "-s", TestCommon.SITE_DIR);
  private static final String FLUX_READING = "FLUX_READING";
  private final Validator validator = new Validator(TEST_ARGS).prepForMock();

  @Test
  public void emptySystemBlock() {
    Validator.MessageBundle bundle = getMessageBundle(EVENT_SUBTYPE, POINTSET_SUBFOLDER,
        new PointsetEvent());
    bundle.message.remove("system");
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
    assertEquals("One error device", 1, report.devices.size());
  }

  @Test
  public void emptyPointsetEvent() {
    MessageBundle bundle = getMessageBundle(EVENT_SUBTYPE, POINTSET_SUBFOLDER, new PointsetEvent());
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
    assertEquals("One error device", 1, report.devices.size());
  }

  @Test
  public void validPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    MessageBundle bundle = getMessageBundle(EVENT_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("No error devices", 1, report.devices.size());
    DeviceValidationEvent deviceValidationEvent = report.devices.get(TestCommon.DEVICE_ID);
    assertEquals("no report status", null, deviceValidationEvent.status);
    String expected = JsonUtil.getTimestamp(messageObject.timestamp);
    String lastSeen = JsonUtil.getTimestamp(deviceValidationEvent.last_seen);
    assertEquals("status last_seen", expected, lastSeen);
  }

  @Test
  public void missingPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle(EVENT_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error devices", 1, report.devices.size());
    ValidationEvent result = getValidationResult(TestCommon.DEVICE_ID, EVENT_SUBTYPE,
        POINTSET_SUBFOLDER);
    PointsetSummary pointset = result.pointset;
    assertEquals("Missing one point", 1, pointset.missing.size());
    assertTrue("Missing correct point", pointset.missing.contains(FILTER_ALARM_PRESSURE_STATUS));
    assertEquals("No extra points", 0, pointset.extra.size());
  }

  @Test
  public void additionalPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    messageObject.points.put(FLUX_READING, new PointPointsetEvent());
    MessageBundle bundle = getMessageBundle(EVENT_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("No error devices", 1, report.devices.size());
    ValidationEvent result = getValidationResult(TestCommon.DEVICE_ID, EVENT_SUBTYPE,
        POINTSET_SUBFOLDER);
    PointsetSummary points = result.pointset;
    assertEquals("No missing points", 0, points.missing.size());
    assertEquals("One extra point", 1, points.extra.size());
    assertTrue("Missing correct point", points.extra.contains(FLUX_READING));
  }

  @Test
  public void missingPointsetState() {
    PointsetState messageObject = basePointsetState();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle(STATE_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("No error devices", 1, report.devices.size());
    ValidationEvent result = getValidationResult(TestCommon.DEVICE_ID, STATE_SUBTYPE,
        POINTSET_SUBFOLDER);
    List<String> missingPoints = result.pointset.missing;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }


}
