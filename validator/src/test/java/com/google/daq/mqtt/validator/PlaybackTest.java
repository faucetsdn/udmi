package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.MessageReadingClient.OutputBundle;
import com.google.udmi.util.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.Level;
import udmi.schema.ValidationEvent;
import udmi.schema.ValidationState;

/**
 * Tests based on validation of message trace playback.
 */
public class PlaybackTest extends TestBase {

  private static final String TRACE_BASE = "../validator/traces/";
  private static final List<String> TRACE_DEVICES = List.of("--", "AHU-22", "SNS-4", "XXX", "YYY");

  @Test
  public void simpleTraceReport() {
    MessageReadingClient client = validateTrace("simple");
    assertEquals("trace message count", 8, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    OutputBundle lastBundle = outputMessages.get(outputMessages.size() - 1);
    ValidationState finalReport = asValidationState(lastBundle.message);
    try {
      assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
      assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
      assertEquals("missing devices", 1, finalReport.summary.missing_devices.size());
      assertEquals("error devices", 2, finalReport.summary.error_devices.size());
      assertEquals("device summaries", 2, finalReport.devices.size());

      List<ValidationEvent> deviceReports = reports(outputMessages, "AHU-1");

      ValidationEvent firstReport = deviceReports.get(0);
      assertEquals("missing points", 1, firstReport.pointset.missing.size());
      String missingPointName = firstReport.pointset.missing.get(0);
      assertEquals("missing point", FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, missingPointName);
      assertEquals("extra points", 0, firstReport.pointset.extra.size());
      assertEquals("device status", (Integer) Level.ERROR.value(), firstReport.status.level);

      ValidationEvent lastReport = deviceReports.get(deviceReports.size() - 1);
      assertEquals("missing points", 0, lastReport.pointset.missing.size());
      assertEquals("extra points", 0, lastReport.pointset.extra.size());
      assertNull("device status", lastReport.status);
    } catch (Throwable e) {
      outputMessages.forEach(message -> System.err.println(JsonUtil.stringify(message)));
      throw e;
    }
  }

  private List<ValidationEvent> reports(List<OutputBundle> outputMessages, String deviceId) {
    return outputMessages.stream()
        .filter(bundle -> bundle.deviceId.equals(deviceId))
        .map(bundle -> asValidationEvent(bundle.message))
        .collect(Collectors.toList());
  }

  @Test
  public void deviceArgs() {
    MessageReadingClient client = validateTrace("simple", TRACE_DEVICES);
    assertEquals("trace message count", 8, client.messageCount);
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

  private ValidationEvent asValidationEvent(TreeMap<String, Object> message) {
    try {
      String stringValue = TestCommon.OBJECT_MAPPER.writeValueAsString(message);
      return TestCommon.OBJECT_MAPPER.readValue(stringValue, ValidationEvent.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting message", e);
    }
  }

  private MessageReadingClient validateTrace(String traceDir) {
    return validateTrace(traceDir, List.of());
  }

  private MessageReadingClient validateTrace(String traceDir, List<String> additionalArgs) {
    String tracePath = TRACE_BASE + traceDir;

    List<String> testArgs = new ArrayList<>();
    testArgs.addAll(List.of(
        "-p", TestCommon.PROJECT_ID,
        "-a", TestCommon.SCHEMA_SPEC,
        "-s", TestCommon.SITE_DIR,
        "-r", tracePath));
    testArgs.addAll(additionalArgs);
    Validator validator = new Validator(testArgs);
    validator.messageLoop();
    return validator.getMessageReadingClient();
  }
}
