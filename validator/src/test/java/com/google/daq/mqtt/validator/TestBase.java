package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.io.File;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.ValidationEvent;
import udmi.schema.ValidationState;

/**
 * Base class for validator tests.
 */
public class TestBase {

  static final String FILTER_ALARM_PRESSURE_STATUS = "filter_alarm_pressure_status";
  static final String FILTER_DIFFERENTIAL_PRESSURE_SETPOINT =
      "filter_differential_pressure_setpoint";
  static final String FILTER_DIFFERENTIAL_PRESSURE_SENSOR =
      "filter_differential_pressure_sensor";
  static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  static final String PROJECT_ID = "unit-testing";
  static final String DEVICE_ID = "AHU-1";
  static final String UDMI_VERSION = "1.3.14";
  static final String DEVICE_NUM_ID = "97216312321";
  static final String REGISTRY_ID = "ZZ-TRI-FECTA";
  static final String SCHEMA_SPEC = "../schema";
  static final String SITE_DIR = "../sites/udmi_site_model";
  private static final File REPORT_BASE = new File(SITE_DIR, "/out");
  private static final File REPORT_FILE = new File(REPORT_BASE, "validation_report.json");

  protected PointsetEvent basePointsetEvent() {
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

  protected PointsetState basePointsetState() {
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
  protected MessageBundle getMessageBundle(String subType, String subFolder, Object messageObject) {
    MessageBundle bundle = new MessageBundle();
    bundle.attributes = messageAttributes(subType, subFolder);
    try {
      String messageStr = OBJECT_MAPPER.writeValueAsString(messageObject);
      bundle.message = OBJECT_MAPPER.readValue(messageStr, TreeMap.class);
      bundle.message.put("timestamp", Instant.now().toString());
    } catch (Exception e) {
      throw new RuntimeException("While converting message bundle object", e);
    }
    return bundle;
  }

  protected ValidationState getValidationReport() {
    try {
      return OBJECT_MAPPER.readValue(REPORT_FILE, ValidationState.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  protected ValidationEvent getValidationResult(String deviceId, String subType, String subFolder) {
    try {
      File resultFile = new File(REPORT_BASE,
          String.format("devices/%s/%s_%s.out", deviceId, subType, subFolder));
      return OBJECT_MAPPER.readValue(resultFile, ValidationEvent.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  private Map<String, String> messageAttributes(String subType, String subFolder) {
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
