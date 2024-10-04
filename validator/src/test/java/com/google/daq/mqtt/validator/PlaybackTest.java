package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static udmi.schema.Level.INFO;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.MessageReadingClient.OutputBundle;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.Category;
import udmi.schema.Level;
import udmi.schema.ValidationEvents;
import udmi.schema.ValidationState;

/**
 * Tests based on validation of message trace playback.
 */
public class PlaybackTest extends TestBase {

  public static final String SIMPLE_TRACE_DIR = "../tests/traces/simple";
  public static final String LENGTHY_TRACE_DIR = "../tests/traces/lengthy";
  private static final List<String> TRACE_DEVICES = List.of("--", "AHU-22", "SNS-4", "XXX", "YYY");

  @Test
  public void simpleTraceReport() {
    MessageReadingClient client = validateTrace(SIMPLE_TRACE_DIR);
    assertEquals("trace message count", 13, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    OutputBundle lastBundle = outputMessages.get(outputMessages.size() - 1);
    ValidationState finalReport = asValidationState(lastBundle.message);
    try {
      assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
      assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
      assertEquals("missing devices", 0, finalReport.summary.missing_devices.size());
      assertEquals("error devices", 3, finalReport.summary.error_devices.size());
      assertEquals("device summaries", 4, finalReport.devices.size());
      assertEquals("AHU-1 status level", (Object) INFO.value(),
          finalReport.devices.get("AHU-1").status.level);
      assertEquals("AHU-22 status category", Category.VALIDATION_DEVICE_SCHEMA,
          finalReport.devices.get("AHU-22").status.category);
      assertEquals("SNS-4 status", Category.VALIDATION_DEVICE_MULTIPLE,
          finalReport.devices.get("SNS-4").status.category);

      List<ValidationEvents> deviceReports = reports(outputMessages, "AHU-1");

      ValidationEvents firstReport = deviceReports.get(0);
      assertEquals("missing points", 1, firstReport.pointset.missing.size());
      String missingPointName = firstReport.pointset.missing.get(0);
      assertEquals("missing point", FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, missingPointName);
      assertEquals("extra points", 0, firstReport.pointset.extra.size());
      assertEquals("device status", (Integer) Level.ERROR.value(), firstReport.status.level);

      ValidationEvents lastReport = deviceReports.get(deviceReports.size() - 1);
      assertEquals("missing points", 0, lastReport.pointset.missing.size());
      assertEquals("extra points", 0, lastReport.pointset.extra.size());
      assertEquals("device status level", (Object) INFO.value(), lastReport.status.level);
    } catch (Throwable e) {
      outputMessages.forEach(message -> System.err.println(JsonUtil.stringify(message)));
      throw e;
    }
  }

  private List<ValidationEvents> reports(List<OutputBundle> outputMessages, String deviceId) {
    return outputMessages.stream()
        .filter(bundle -> bundle.deviceId.equals(deviceId))
        .map(bundle -> asValidationEvents(bundle.message))
        .collect(Collectors.toList());
  }

  @Test
  public void notMissingDevice() {
    MessageReadingClient client = validateTrace(LENGTHY_TRACE_DIR);
    assertEquals("trace message count", 3, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    OutputBundle lastBundle = outputMessages.get(outputMessages.size() - 1);
    ValidationState finalReport = asValidationState(lastBundle.message);
    try {
      assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
      assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
      assertEquals("missing devices", 3, finalReport.summary.missing_devices.size());
      assertEquals("error devices", 0, finalReport.summary.error_devices.size());
      assertEquals("device summaries", 1, finalReport.devices.size());
    } catch (Throwable e) {
      outputMessages.forEach(message -> System.err.println(JsonUtil.stringify(message)));
      throw e;
    }
  }

  @Test
  public void deviceArgs() {
    MessageReadingClient client = validateTrace(SIMPLE_TRACE_DIR, TRACE_DEVICES);
    assertEquals("trace message count", 13, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    TreeMap<String, Object> lastMessage = outputMessages.get(outputMessages.size() - 1).message;
    ValidationState finalReport = asValidationState(lastMessage);
    try {
      assertEquals("correct devices", 0, finalReport.summary.correct_devices.size());
      assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
      assertEquals("missing devices", 2, finalReport.summary.missing_devices.size());
      assertEquals("error devices", 2, finalReport.summary.error_devices.size());
      assertEquals("device summaries", 2, finalReport.devices.size());
    } catch (Throwable e) {
      outputMessages.forEach(message -> System.err.println(JsonUtil.stringify(message)));
      throw e;
    }
  }

  private ValidationState asValidationState(TreeMap<String, Object> message) {
    try {
      String stringValue = TestCommon.OBJECT_MAPPER.writeValueAsString(message);
      return TestCommon.OBJECT_MAPPER.readValue(stringValue, ValidationState.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting message", e);
    }
  }

  private ValidationEvents asValidationEvents(TreeMap<String, Object> message) {
    try {
      String stringValue = TestCommon.OBJECT_MAPPER.writeValueAsString(message);
      return TestCommon.OBJECT_MAPPER.readValue(stringValue, ValidationEvents.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting message", e);
    }
  }

  private MessageReadingClient validateTrace(String traceDir) {
    return validateTrace(traceDir, List.of());
  }

  private MessageReadingClient validateTrace(String traceDir, List<String> additionalArgs) {
    List<String> testArgs = new ArrayList<>();
    testArgs.addAll(List.of(
        "-p", SiteModel.MOCK_PROJECT,
        "-a", TestCommon.SCHEMA_SPEC,
        "-s", TestCommon.SITE_DIR,
        "-r", traceDir));
    testArgs.addAll(additionalArgs);
    Validator validator = new Validator(testArgs);
    validator.messageLoop();
    return validator.getMessageReadingClient();
  }
}
