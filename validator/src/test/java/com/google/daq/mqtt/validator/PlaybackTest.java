package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class PlaybackTest {

  private static final String SCHEMA_SPEC = "../schema";
  private static final String SITE_DIR = "../sites/udmi_site_model";
  private static final String PROJECT_ID = "unit-testing";
  private static final String TRACE_BASE = "../validator/traces/";

  @Test
  public void traceCount() {
   MessageReadingClient client = validateTrace("simple");
    assertEquals("trace message count", 7, client.messageCount);
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
