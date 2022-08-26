package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;

import com.google.daq.mqtt.validator.MessageReadingClient.OutputBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
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
    assertEquals("correct devices", 2, finalReport.summary.correct_devices.size());
    assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
    assertEquals("missing devices", 1, finalReport.summary.missing_devices.size());
    assertEquals("error devices", 1, finalReport.summary.error_devices.size());

    assertEquals("device summaries", 1, finalReport.devices.size());
    ValidationEvent deviceReport = forDevice(outputMessages, "AHU-1");
    String missingPointName = deviceReport.pointset.missing.get(0);
    assertEquals("missing point", FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, missingPointName);
    assertEquals("extra points", 0, deviceReport.pointset.extra.size());
    assertEquals("device status", (Integer) Level.ERROR.value(), deviceReport.status.level);
  }

  private ValidationEvent forDevice(List<OutputBundle> outputMessages, String deviceId) {
    for (OutputBundle bundle : outputMessages) {
      if (bundle.deviceId.equals(deviceId)) {
        return asValidationEvent(bundle.message);
      }
    }
    throw new RuntimeException("Event match for device not found " + deviceId);
  }

  @Test
  public void deviceArgs() {
    MessageReadingClient client = validateTrace("simple", TRACE_DEVICES);
    assertEquals("trace message count", 8, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    TreeMap<String, Object> lastMessage = outputMessages.get(outputMessages.size() - 1).message;
    ValidationState finalReport = asValidationState(lastMessage);
    assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
    assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
    assertEquals("missing devices", 2, finalReport.summary.missing_devices.size());
    assertEquals("error devices", 1, finalReport.summary.error_devices.size());
    assertEquals("device summaries", 1, finalReport.devices.size());
  }

  private ValidationState asValidationState(TreeMap<String, Object> message) {
    try {
      String stringValue = OBJECT_MAPPER.writeValueAsString(message);
      return OBJECT_MAPPER.readValue(stringValue, ValidationState.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting message", e);
    }
  }

  private ValidationEvent asValidationEvent(TreeMap<String, Object> message) {
    try {
      String stringValue = OBJECT_MAPPER.writeValueAsString(message);
      return OBJECT_MAPPER.readValue(stringValue, ValidationEvent.class);
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
        "-p", PROJECT_ID,
        "-a", SCHEMA_SPEC,
        "-s", SITE_DIR,
        "-r", tracePath));
    testArgs.addAll(additionalArgs);
    Validator validator = new Validator(testArgs);
    validator.messageLoop();
    return validator.getMessageReadingClient();
  }
}
