package com.google.bos.udmi.service.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Unit tests for CaptureProcessor.
 */
public class CaptureProcessorTest extends ProcessorTestBase {

  @Test
  public void testProcessorInstantiation() {
    CaptureProcessor captureProcessor = initializeTestInstance(CaptureProcessor.class);
    assertNotNull(captureProcessor, "CaptureProcessor instantiated successfully");
  }

  @Test
  public void testPointsetEventsHandling() {
    Envelope envelope = new Envelope();
    envelope.projectId = "test-project";
    envelope.deviceRegistryId = "test-registry";
    envelope.deviceId = "test-device";
    envelope.subFolder = SubFolder.POINTSET;
    envelope.subType = SubType.EVENTS;
    envelope.publishTime = new Date();

    Map<String, Object> payload = Map.of(
        "timestamp", "2026-07-30T12:00:00Z",
        "points", Map.of(
            "temp", Map.of("present_value", 21.5),
            "status", Map.of("present_value", true)
        )
    );

    CaptureProcessor captureProcessor = initializeTestInstance(CaptureProcessor.class);
    assertDoesNotThrow(() -> captureProcessor.saveToInflux(envelope, payload));
  }

  @Test
  public void testStateMessageHandling() {
    Envelope envelope = new Envelope();
    envelope.projectId = "test-project";
    envelope.deviceRegistryId = "test-registry";
    envelope.deviceId = "test-device";
    envelope.subFolder = SubFolder.SYSTEM;
    envelope.subType = SubType.STATE;
    envelope.publishTime = new Date();

    Map<String, Object> payload = Map.of(
        "timestamp", "2026-07-30T12:00:00Z",
        "system", Map.of("operation", Map.of("operational", true))
    );

    CaptureProcessor captureProcessor = initializeTestInstance(CaptureProcessor.class);
    assertDoesNotThrow(() -> captureProcessor.saveToPostgres(envelope, payload));
  }
}
