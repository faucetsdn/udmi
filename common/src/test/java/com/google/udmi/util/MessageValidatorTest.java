package com.google.udmi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Unit tests for MessageValidator.
 */
public class MessageValidatorTest {

  private static final String SCHEMA_ROOT = "../schema";

  @Test
  public void multipleOneOfFailuresPruneSubErrorsPerInstance() {
    MessageValidator validator = new MessageValidator(SCHEMA_ROOT);
    String payload = """
        {
          "version": "1.5.7",
          "timestamp": "2022-07-19T05:04:39Z",
          "logentries": [
            {
              "message": "first entry",
              "timestamp": "2022-07-19T05:04:39Z",
              "level": 200,
              "category": "invalid.category.one"
            },
            {
              "message": "second entry",
              "timestamp": "2022-07-19T05:04:39Z",
              "level": 200,
              "category": "invalid.category.two"
            }
          ]
        }
        """;
    List<String> errors = validator.validateMessage("events_system", payload);
    // Top-level summary + 2 oneOf violations (sub-branch errors pruned per instance location).
    assertEquals(3, errors.size());
    assertEquals("2 schema violations found", errors.get(0));
    assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.logentries[0].category:")));
    assertTrue(errors.stream().anyMatch(e -> e.startsWith("$.logentries[1].category:")));
  }

  @Test
  public void utcTimestampNormalizedWhileNonUtcRejected() {
    MessageValidator validator = new MessageValidator(SCHEMA_ROOT);
    String validUtcOffsetPayload = """
        {
          "version": "1.5.7",
          "timestamp": "2022-07-19T05:04:39+0000"
        }
        """;
    assertTrue(validator.validateMessage("events_system", validUtcOffsetPayload).isEmpty());

    String invalidNonUtcOffsetPayload = """
        {
          "version": "1.5.7",
          "timestamp": "2022-07-19T05:04:39-0700"
        }
        """;
    List<String> errors = validator.validateMessage("events_system", invalidNonUtcOffsetPayload);
    assertEquals(2, errors.size());
    assertTrue(errors.get(1).contains("$.timestamp:"));
  }
}
