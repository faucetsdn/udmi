package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.REFLECT_REGISTRY;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

  private static void verifyCommand(ArgumentCaptor<String> commandCaptor, String extraField) {
    Map<String, Object> command = toMap(commandCaptor.getValue());
    String payload = (String) command.remove("payload");
    String payloadString = GeneralUtils.decodeBase64(payload);
    Map<String, Object> payloadMap = toMap(payloadString);
    assertEquals(extraField, payloadMap.get(EXTRA_FIELD_KEY), "message " + EXTRA_FIELD_KEY);
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

  @Override
  protected void initializeTestInstance() {
    super.initializeTestInstance();
    getReverseDispatcher().prototypeEnvelope.deviceId = TEST_DEVICE;
    getReverseDispatcher().prototypeEnvelope.deviceRegistryId = TEST_REGISTRY;
  }

  @NotNull
  private Object getTestMessage(boolean isError) {
    return isError ? makeErrorMessage() : new PointsetEvent();
  }

  private Object makeErrorMessage() {
    Map<String, Object> stringObjectMap = toMap(new PointsetEvent());
    stringObjectMap.put(EXTRA_FIELD_KEY, MONGOOSE);
    // Specify these explicitly since the sent object is a (generic) map.
    getReverseDispatcher().prototypeEnvelope.subType = SubType.EVENT;
    getReverseDispatcher().prototypeEnvelope.subFolder = SubFolder.POINTSET;
    return stringObjectMap;
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected defaulted message.
   */
  @Test
  public void errorReceive() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestMessage(true));
    terminateAndWait();

    assertEquals(1, captured.size(), "unexpected received message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
    assertEquals(0, getMessageCount(PointsetEvent.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(eq(REFLECT_REGISTRY),
        eq(TEST_REGISTRY), eq(SubFolder.UDMI), commandCaptor.capture());
    verifyCommand(commandCaptor, MONGOOSE);
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
    verifyCommand(commandCaptor, null);
  }

}