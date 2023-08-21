package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.REFLECT_REGISTRY;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.GeneralUtils;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointsetEvent;

class TargetProcessorTest extends ProcessorTestBase {

  public static final String MONGOOSE = "mongoose";
  public static final String EXTRA_FIELD_KEY = "extraField";

  private static void verifyCommand(ArgumentCaptor<String> commandCaptor, boolean isError) {
    Map<String, Object> command = toMap(commandCaptor.getValue());
    String payload = (String) command.remove("payload");
    String payloadString = GeneralUtils.decodeBase64(payload);
    Map<String, Object> payloadMap = toMap(payloadString);
    assertEquals(SubFolder.POINTSET.value(), command.remove("subFolder"), "subFolder field");
    assertEquals("event", command.remove("subType"), "subType field");
    assertEquals(TEST_DEVICE, command.remove("deviceId"));
    assertEquals(TEST_NAMESPACE, command.remove("projectId"));
    assertEquals(TEST_REGISTRY, command.remove("deviceRegistryId"));
  }

  @NotNull
  protected Class<? extends ProcessorBase> getProcessorClass() {
    return TargetProcessor.class;
  }

  @NotNull
  private Object getTestMessage(boolean isExtra) {
    return isExtra ? makeExtraFieldMessage() : new PointsetEvent();
  }

  private Bundle makeExtraFieldMessage() {
    Map<String, Object> stringObjectMap = toMap(new PointsetEvent());
    stringObjectMap.put(EXTRA_FIELD_KEY, MONGOOSE);
    // Specify these explicitly since the sent object is a (generic) map.
    Bundle bundle = makeMessageBundle(stringObjectMap);
    bundle.envelope.subType = SubType.EVENT;
    bundle.envelope.subFolder = SubFolder.POINTSET;
    bundle.envelope.projectId = TEST_NAMESPACE;
    return bundle;
  }

  /**
   * Test receiving a message that has an unexpected extra field.
   */
  @Test
  public void unexpectedReceive() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestMessage(true));
    terminateAndWait();

    assertEquals(1, captured.size(), "unexpected received message count");
    assertTrue(captured.get(0) instanceof Map, "unexpected on-map message");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
    assertEquals(0, getMessageCount(PointsetEvent.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(eq(REFLECT_REGISTRY),
        eq(TEST_REGISTRY), eq(SubFolder.UDMI), commandCaptor.capture());
    verifyCommand(commandCaptor, true);
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected defaulted message.
   */
  @Test
  public void simpleReceive() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestMessage(false));
    terminateAndWait();

    assertEquals(1, captured.size(), "unexpected received message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
    assertEquals(1, getMessageCount(PointsetEvent.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(eq(REFLECT_REGISTRY),
        eq(TEST_REGISTRY), eq(SubFolder.UDMI), commandCaptor.capture());
    verifyCommand(commandCaptor, false);
  }

}