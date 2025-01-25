package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.FileDataSink.REPORT_JSON_FILENAME;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.isoConvert;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetState;
import udmi.schema.ValidationEvents;
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
  private static final File REPORT_FILE = new File(REPORT_BASE, REPORT_JSON_FILENAME);
  private final AtomicLong testTime = new AtomicLong(298374214L);

  protected PointsetEvents basePointsetEvents() {
    PointsetEvents pointsetEvent = new PointsetEvents();
    pointsetEvent.timestamp = getDate(getTestTimestamp());
    pointsetEvent.version = TestCommon.UDMI_VERSION;
    HashMap<String, PointPointsetEvents> points = new HashMap<>();
    points.put(FILTER_ALARM_PRESSURE_STATUS, pointsetEventsPoint(Boolean.TRUE));
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, pointsetEventsPoint(20));
    points.put(FILTER_DIFFERENTIAL_PRESSURE_SENSOR, pointsetEventsPoint("yes"));
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
  private PointPointsetEvents pointsetEventsPoint(Object present_value) {
    PointPointsetEvents pointPointsetEvents = new PointPointsetEvents();
    pointPointsetEvents.present_value = present_value;
    return pointPointsetEvents;
  }

  @SuppressWarnings("unchecked")
  protected Validator.MessageBundle getMessageBundle(String subType, String subFolder,
      Object messageObject) {
    Validator.MessageBundle bundle = new MessageBundle();
    bundle.attributes = messageAttributes(subType, subFolder);
    try {
      String messageStr = TestCommon.OBJECT_MAPPER.writeValueAsString(messageObject);
      bundle.message = TestCommon.OBJECT_MAPPER.readValue(messageStr, TreeMap.class);
      bundle.message.put("timestamp", getTestTimestamp());
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

  protected ValidationEvents getValidationResult(String deviceId, String subType,
      String subFolder) {
    try {
      File resultFile = new File(REPORT_BASE,
          String.format("devices/%s/%s_%s.out", deviceId, subType, subFolder));
      return TestCommon.OBJECT_MAPPER.readValue(resultFile, ValidationEvents.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  protected void advanceClockSec(int seconds) {
    testTime.addAndGet(seconds);
  }

  private Map<String, String> messageAttributes(String subType, String subFolder) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("deviceRegistryId", TestCommon.REGISTRY_ID);
    attributes.put("deviceId", TestCommon.DEVICE_ID);
    attributes.put("subFolder", subFolder);
    attributes.put("subType", subType);
    attributes.put("deviceNumId", TestCommon.DEVICE_NUM_ID);
    attributes.put(Common.PROJECT_ID_PROPERTY_KEY, SiteModel.MOCK_PROJECT);
    attributes.put(PUBLISH_TIME_KEY, getTestTimestamp());
    return attributes;
  }

  @NotNull
  private String getTestTimestamp() {
    return isoConvert(new Date(testTime.get()));
  }
}
