package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.validator.MessageReadingClient.OutputBundle;
import java.util.List;
import java.util.TreeMap;
import org.junit.Test;
import udmi.schema.ValidationEvent;

/**
 * Tests based on validation of message trace playback.
 */
public class PlaybackTest {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);

  private static final String SCHEMA_SPEC = "../schema";
  private static final String SITE_DIR = "../sites/udmi_site_model";
  private static final String PROJECT_ID = "unit-testing";
  private static final String TRACE_BASE = "../validator/traces/";

  @Test
  public void simpleTraceReport() {
    MessageReadingClient client = validateTrace("simple");
    assertEquals("trace message count", 7, client.messageCount);
    List<OutputBundle> outputMessages = client.getOutputMessages();
    ValidationEvent finalReport = asValidationEvent(
        outputMessages.get(outputMessages.size() - 1).message);
    assertEquals("extra devices", 0, finalReport.summary.extra_devices.size());
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
    String tracePath = TRACE_BASE + traceDir;
    List<String> testArgs = ImmutableList.of(
        "-p", PROJECT_ID,
        "-a", SCHEMA_SPEC,
        "-s", SITE_DIR,
        "-r", tracePath);
    Validator validator = new Validator(testArgs);
    validator.messageLoop();
    return validator.getMessageReadingClient();
  }
}
