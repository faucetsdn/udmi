package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.daq.mqtt.validator.Validator.MetadataReport;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Test;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;

/**
 * Unit test suite for the validator.
 */
public class ValidatorTest {

  public static final String FILTER_ALARM_PRESSURE_STATUS = "filter_alarm_pressure_status";
  public static final String FILTER_DIFFERENTIAL_PRESSURE_SETPOINT =
      "filter_differential_pressure_setpoint";
  public static final String FILTER_DIFFERENTIAL_PRESSURE_SENSOR =
      "filter_differential_pressure_sensor";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String SCHEMA_SPEC = "../schema";
  private static final String SITE_DIR = "../sites/udmi_site_model";
  private static final String PROJECT_ID = "unit-testing";
  private static final String DEVICE_ID = "AHU-1";
  private static final String UDMI_VERSION = "1.3.14";
  private static final File REPORT_FILE = new File(SITE_DIR + "/out/validation_report.json");
  private static final String DEVICE_NUM_ID = "97216312321";
  private static final String REGISTRY_ID = "ZZ-TRI-FECTA";
  private static final List<String> testArgs = ImmutableList.of(
      "-p", PROJECT_ID,
      "-a", SCHEMA_SPEC,
      "-s", SITE_DIR);
  private final Validator validator = new Validator(testArgs);

  @Test
  public void emptySystemBlock() {
    MessageBundle bundle = getMessageBundle("event", "pointset", new PointsetEvent());
    bundle.message.remove("system");
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("One error device", 1, report.errorDevices.size());
  }

  @Test
  public void emptyPointsetEvent() {
    MessageBundle bundle = getMessageBundle("event", "pointset", new PointsetEvent());
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("One error device", 1, report.errorDevices.size());
  }

  @Test
  public void validPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    MessageBundle bundle = getMessageBundle("event", "pointset", messageObject);
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("No error devices", 0, report.errorDevices.size());
  }

  @Test
  public void missingPointsetEvent() {
    PointsetEvent messageObject = basePointsetEvent();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle("event", "pointset", messageObject);
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("No error devices", 1, report.errorDevices.size());
    Set<String> missingPoints = report.errorDevices.get("AHU-1").missingPoints;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }

  @Test
  public void missingPointsetState() {
    PointsetState messageObject = basePointsetState();
    messageObject.points.remove(FILTER_ALARM_PRESSURE_STATUS);
    MessageBundle bundle = getMessageBundle("state", "pointset", messageObject);
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("No error devices", 1, report.errorDevices.size());
    Set<String> missingPoints = report.errorDevices.get("AHU-1").missingPoints;
    assertEquals("Missing one point", 1, missingPoints.size());
    assertTrue("Missing correct point", missingPoints.contains(FILTER_ALARM_PRESSURE_STATUS));
  }

  private PointsetEvent basePointsetEvent() {
    PointsetEvent pointsetEvent = new PointsetEvent();
    pointsetEvent.timestamp = new Date();
    pointsetEvent.version = UDMI_VERSION;
    HashMap<String, PointPointsetEvent> points = new HashMap<>();
    points.put(FILTER_ALARM_PRESSURE_STATUS, pointsetEventPoint(Boolean.TRUE));
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, pointsetEventPoint(20));
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SENSOR, pointsetEventPoint("yes"));
    pointsetEvent.points = points;
    return pointsetEvent;
  }

  private PointsetState basePointsetState() {
    PointsetState pointsetState = new PointsetState();
    pointsetState.timestamp = new Date();
    pointsetState.version = UDMI_VERSION;
    HashMap<String, PointPointsetState> points = new HashMap<>();
    pointsetState.points = points;
    points.put(FILTER_ALARM_PRESSURE_STATUS, pointsetStatePoint());
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, pointsetStatePoint());
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SENSOR, pointsetStatePoint());
    return pointsetState;
  }

  private PointPointsetState pointsetStatePoint() {
    return new PointPointsetState();
  }

  @SuppressWarnings("ParameterName")
  private PointPointsetEvent pointsetEventPoint(Object present_value) {
    PointPointsetEvent pointPointsetEvent = new PointPointsetEvent();
    pointPointsetEvent.present_value = present_value;
    return pointPointsetEvent;
  }

  @SuppressWarnings("unchecked")
  private MessageBundle getMessageBundle(String subType, String subFolder, Object messageObject) {
    MessageBundle bundle = new MessageBundle();
    bundle.attributes = testMessageAttributes(subType, subFolder);
    try {
      String messageStr = OBJECT_MAPPER.writeValueAsString(messageObject);
      bundle.message = OBJECT_MAPPER.readValue(messageStr, TreeMap.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting message bundle object", e);
    }
    return bundle;
  }

  private MetadataReport getMetadataReport() {
    try {
      return OBJECT_MAPPER.readValue(REPORT_FILE, MetadataReport.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  private Map<String, String> testMessageAttributes(String subType, String subFolder) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("deviceRegistryId", REGISTRY_ID);
    attributes.put("deviceId", DEVICE_ID);
    attributes.put("subFolder", subFolder);
    attributes.put("subType", subType);
    attributes.put("deviceNumId", DEVICE_NUM_ID);
    attributes.put("projectId", PROJECT_ID);
    return attributes;
  }
}
