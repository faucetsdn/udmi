package com.google.daq.mqtt.validator;

import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.JsonUtil.getInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Level.INFO;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.SiteModel;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Config;
import udmi.schema.DeviceValidationEvents;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetState;
import udmi.schema.PointsetSummary;
import udmi.schema.SystemModel;
import udmi.schema.ValidationEvents;
import udmi.schema.ValidationState;

/**
 * Unit test suite for the validator.
 */
public class BasicTest extends TestBase {

  private static final String EVENTS_SUBTYPE = "events";
  private static final String CONFIG_SUBTYPE = "config";
  private static final String STATE_SUBTYPE = "state";
  private static final String MODEL_SUBTYPE = "model";
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
    Validator.MessageBundle bundle = getMessageBundle(EVENTS_SUBTYPE, POINTSET_SUBFOLDER,
        new PointsetEvents());
    bundle.message.remove("system");
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
  }

  @Test
  public void emptyPointsetEvents() {
    MessageBundle bundle = getMessageBundle(EVENTS_SUBTYPE, POINTSET_SUBFOLDER,
        new PointsetEvents());
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error summary", 1, report.summary.error_devices.size());
  }

  @Test
  public void validPointsetEvents() {
    PointsetEvents messageObject = basePointsetEvents();
    MessageBundle bundle = getMessageBundle(EVENTS_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("No error devices", 0, report.summary.error_devices.size());
    DeviceValidationEvents deviceValidationEvents = report.devices.get(TestCommon.DEVICE_ID);
    assertEquals("report status level", (Object) INFO.value(),
        deviceValidationEvents.status.level);
    String expected = isoConvert(messageObject.timestamp);
    String lastSeen = isoConvert(deviceValidationEvents.last_seen);
    assertEquals("status last_seen", expected, lastSeen);
  }

  @Test
  public void missingPointsetEvents() {
    PointsetEvents messageObject = basePointsetEvents();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle(EVENTS_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error devices", 1, report.summary.error_devices.size());
    ValidationEvents result = getValidationResult(TestCommon.DEVICE_ID, EVENTS_SUBTYPE,
        POINTSET_SUBFOLDER);
    PointsetSummary pointset = result.pointset;
    assertEquals("Missing one point", 1, pointset.missing.size());
    assertTrue("Missing correct point", pointset.missing.contains(FILTER_ALARM_PRESSURE_STATUS));
    assertEquals("No extra points", 0, pointset.extra.size());
  }

  @Test
  public void additionalPointsetEvents() {
    PointsetEvents messageObject = basePointsetEvents();
    messageObject.points.put(FLUX_READING, new PointPointsetEvents());
    MessageBundle bundle = getMessageBundle(EVENTS_SUBTYPE, POINTSET_SUBFOLDER, messageObject);
    validator.validateMessage(bundle);
    ValidationState report = getValidationReport();
    assertEquals("One error devices", 1, report.summary.error_devices.size());
    ValidationEvents result = getValidationResult(TestCommon.DEVICE_ID, EVENTS_SUBTYPE,
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
    assertEquals("One error devices", 1, report.summary.error_devices.size());
    ValidationEvents result = getValidationResult(TestCommon.DEVICE_ID, STATE_SUBTYPE,
        POINTSET_SUBFOLDER);
    List<String> missingPoints = result.pointset.missing;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }

  @Test
  public void lastSeenUpdate() {
    Validator.MessageBundle eventBundle = getMessageBundle(EVENTS_SUBTYPE, DISCOVERY_SUBFOLDER,
        new DiscoveryEvents());
    validator.validateMessage(eventBundle);

    advanceClockSec(10);

    Validator.MessageBundle configBundle = getMessageBundle(CONFIG_SUBTYPE, UPDATE_SUBFOLDER,
        new Config());
    validator.validateMessage(configBundle);

    // Only the event should update the last seen, since config is not from the device.
    ValidationState report = getValidationReport();
    DeviceValidationEvents deviceValidationEvents = report.devices.get(TestCommon.DEVICE_ID);
    Date lastSeen = deviceValidationEvents.last_seen;
    Instant parse = getInstant((String) eventBundle.message.get(TIMESTAMP_KEY));
    assertEquals("device last seen", isoConvert(Date.from(parse)), isoConvert(lastSeen));
  }

  @Test
  public void deviceMetadataUpdate() {
    Metadata messageObject = new Metadata();
    messageObject.system = new SystemModel();
    messageObject.system.description = "Updated description";
    MessageBundle messageBundle = getMessageBundle(MODEL_SUBTYPE, UPDATE_SUBFOLDER, messageObject);

    validator.validateMessage(messageBundle);

    Map<String, ReportingDevice> reportingDevices = validator.getReportingDevices();
    assertEquals(messageObject.system.description,
        reportingDevices.get(TestCommon.DEVICE_ID).getMetadata().system.description);
  }

  @Test
  public void deviceDeleteMetadataUpdate() {
    Metadata messageObject = new Metadata();
    messageObject.cloud = new CloudModel();
    messageObject.cloud.operation = Operation.DELETE;
    MessageBundle messageBundle = getMessageBundle(MODEL_SUBTYPE, UPDATE_SUBFOLDER, messageObject);

    validator.validateMessage(messageBundle);

    Map<String, ReportingDevice> reportingDevices = validator.getReportingDevices();
    assertNull(reportingDevices.get(TestCommon.DEVICE_ID));
  }

}
