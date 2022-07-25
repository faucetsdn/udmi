package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;

import com.google.daq.mqtt.validator.MessageReadingClient.OutputBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.Test;
import udmi.schema.DeviceValidationEvent;
import udmi.schema.Level;
import udmi.schema.ValidationEvent;

/**
 * Tests based on validation of message trace playback.
 */
public class PlaybackTest extends TestBase {

  private static final String TRACE_BASE = "../validator/traces/";

  @Test
  public void simpleTraceReport() {
    MessageReadingClient client = validateTrace("simple");
    assertEquals("trace message count", 7, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    ValidationEvent finalReport = asValidationEvent(
        outputMessages.get(outputMessages.size() - 1).message);
    assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
    assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
    assertEquals("missing devices", 2, finalReport.summary.missing_devices.size());
    assertEquals("error devices", 1, finalReport.summary.error_devices.size());

    assertEquals("device summaries", 1, finalReport.devices.size());
    DeviceValidationEvent deviceSummary = finalReport.devices.get("AHU-1");
    assertEquals("missing point", FILTER_DIFFERENTIAL_PRESSURE_SETPOINT, deviceSummary.missing_points.get(0));
    assertEquals("device status", (Integer) Level.ERROR.value(), deviceSummary.status.level);
  }

  @Test
  public void deviceArgs() {
    MessageReadingClient client = validateTrace("simple",
        List.of("--", "AHU-22", "SNS-4", "XXX", "YYY"));
    assertEquals("trace message count", 7, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    ValidationEvent finalReport = asValidationEvent(
        outputMessages.get(outputMessages.size() - 1).message);
    assertEquals("correct devices", 1, finalReport.summary.correct_devices.size());
    assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
    assertEquals("missing devices", 3, finalReport.summary.missing_devices.size());
    assertEquals("error devices", 0, finalReport.summary.error_devices.size());
    assertEquals("device summaries", 0, finalReport.devices.size());
  }

  private ValidationEvent asValidationEvent(TreeMap<String, Object> message) {
    try {
      return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(message),
          ValidationEvent.class);
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
