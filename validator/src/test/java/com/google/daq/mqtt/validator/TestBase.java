package com.google.daq.mqtt.validator;

import com.google.daq.mqtt.TestCommon;
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
  private static final File REPORT_BASE = new File(TestCommon.SITE_DIR, "/out");
  private static final File REPORT_FILE = new File(REPORT_BASE, "validation_report.json");

  protected PointsetEvent basePointsetEvent() {
    PointsetEvent pointsetEvent = new PointsetEvent();
    pointsetEvent.timestamp = new Date();
    pointsetEvent.version = TestCommon.UDMI_VERSION;
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
    pointsetState.version = TestCommon.UDMI_VERSION;
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
  protected Validator.MessageBundle getMessageBundle(String subType, String subFolder,
      Object messageObject) {
    Validator.MessageBundle bundle = new MessageBundle();
    bundle.attributes = messageAttributes(subType, subFolder);
    try {
      String messageStr = TestCommon.OBJECT_MAPPER.writeValueAsString(messageObject);
      bundle.message = TestCommon.OBJECT_MAPPER.readValue(messageStr, TreeMap.class);
      bundle.message.put("timestamp", Instant.now().toString());
    } catch (Exception e) {
      throw new RuntimeException("While converting message bundle object", e);
    }
    return bundle;
  }

  protected ValidationState getValidationReport() {
    try {
      return TestCommon.OBJECT_MAPPER.readValue(REPORT_FILE, ValidationState.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  protected ValidationEvent getValidationResult(String deviceId, String subType, String subFolder) {
    try {
      File resultFile = new File(REPORT_BASE,
          String.format("devices/%s/%s_%s.out", deviceId, subType, subFolder));
      return TestCommon.OBJECT_MAPPER.readValue(resultFile, ValidationEvent.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  private Map<String, String> messageAttributes(String subType, String subFolder) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("deviceRegistryId", TestCommon.REGISTRY_ID);
    attributes.put("deviceId", TestCommon.DEVICE_ID);
    attributes.put("subFolder", subFolder);
    attributes.put("subType", subType);
    attributes.put("deviceNumId", TestCommon.DEVICE_NUM_ID);
    attributes.put("projectId", TestCommon.PROJECT_ID);
    return attributes;
  }
}
