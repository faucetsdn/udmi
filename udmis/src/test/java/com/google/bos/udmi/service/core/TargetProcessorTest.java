package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointsetEvent;

class TargetProcessorTest extends ProcessorTestBase {

  @NotNull
  protected Class<? extends ProcessorBase> getProcessorClass() {
    return TargetProcessor.class;
  }

  @NotNull
  private Object getTestMessage(boolean isError) {
    return isError ? makeErrorMessage() : new PointsetEvent();
  }

  private Object makeErrorMessage() {
    Map<String, Object> stringObjectMap = toMap(new PointsetEvent());
    stringObjectMap.put("extraField", "mongoose");
    // Specify these explicitly since the sent object is a (generic) map.
    getReverseDispatcher().prototypeEnvelope.subType = SubType.EVENT;
    getReverseDispatcher().prototypeEnvelope.subFolder = SubFolder.POINTSET;
    return stringObjectMap;
  }

  @Override
  protected void initializeTestInstance() {
    super.initializeTestInstance();
    getReverseDispatcher().prototypeEnvelope.deviceId = TEST_DEVICE;
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
    verify(provider, times(1)).sendCommand(anyString(), isNull(), isNull(),
        commandCaptor.capture());
    verifyCommand(commandCaptor, SubFolder.POINTSET);
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected defaulted message.
   */
  @Test
  public void errorReceive() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestMessage(true));
    terminateAndWait();

    assertEquals(0, captured.size(), "unexpected received message count");
    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
    assertEquals(0, getMessageCount(PointsetEvent.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(anyString(), isNull(), isNull(),
        commandCaptor.capture());
    verifyCommand(commandCaptor, SubFolder.ERROR);
  }

  private static void verifyCommand(ArgumentCaptor<String> commandCaptor, SubFolder expectedSubFolder) {
    Map<String, Object> command = toMap(commandCaptor.getValue());
    assertNotNull(command.remove("payload"), "message payload");
    assertEquals(expectedSubFolder.value(), command.remove("subFolder"), "subFolder field");
    assertEquals("event", command.remove("subType"), "subType field");
    assertEquals(TEST_DEVICE, command.remove("deviceId"));
    assertEquals(TEST_NAMESPACE, command.remove("projectId"));
    assertEquals(0, command.size(), "remaining fields");
  }

}