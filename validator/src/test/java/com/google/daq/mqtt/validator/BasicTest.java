package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.Common.TIMESTAMP_PROPERTY_KEY;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.SiteModel;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import udmi.schema.Config;
import udmi.schema.DeviceValidationEvent;
import udmi.schema.DiscoveryEvent;
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
  private static final String CONFIG_SUBTYPE = "config";
  private static final String STATE_SUBTYPE = "state";
  private static final String POINTSET_SUBFOLDER = "pointset";
  private static final String DISCOVERY_SUBFOLDER = "discovery";
  private static final String UPDATE_SUBFOLDER = "update";
  private static final List<String> TEST_ARGS = ImmutableList.of(
      "-n",
      "-p", SiteModel.MOCK_PROJECT,
      "-a", TestCommon.SCHEMA_SPEC,
      "-s", TestCommon.SITE_DIR);
  private static final String FLUX_READING = "FLUX_READING";
  private static final long TWO_SECONDS_MS = 1000 * 2;
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
    String expected = getTimestamp(messageObject.timestamp);
    String lastSeen = getTimestamp(deviceValidationEvent.last_seen);
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

  @Test
  public void lastSeenUpdate() {
    Validator.MessageBundle eventBundle = getMessageBundle(EVENT_SUBTYPE, DISCOVERY_SUBFOLDER,
        new DiscoveryEvent());
    validator.validateMessage(eventBundle);

    // Add enough of a delay to ensure that a seconds-based timestamp is different.
    safeSleep(TWO_SECONDS_MS);
    Validator.MessageBundle configBundle = getMessageBundle(CONFIG_SUBTYPE, UPDATE_SUBFOLDER,
        new Config());
    validator.validateMessage(configBundle);

    // Only the event should update the last seen, since config is not from the device.
    ValidationState report = getValidationReport();
    DeviceValidationEvent deviceValidationEvent = report.devices.get(TestCommon.DEVICE_ID);
    Date lastSeen = deviceValidationEvent.last_seen;
    Instant parse = Instant.parse((String) eventBundle.message.get(TIMESTAMP_PROPERTY_KEY));
    assertEquals("device last seen", getTimestamp(Date.from(parse)), getTimestamp(lastSeen));
  }

}
