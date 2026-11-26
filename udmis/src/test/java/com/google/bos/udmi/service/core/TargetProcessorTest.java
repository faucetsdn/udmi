package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointsetEvents;

class TargetProcessorTest extends ProcessorTestBase {

  public static final String MONGOOSE = "mongoose";
  public static final String EXTRA_FIELD_KEY = "extraField";
  private final List<String> notices = new CopyOnWriteArrayList<>();

  @Override
  protected ProcessorBase getProcessor(EndpointConfiguration config,
      Class<? extends ProcessorBase> clazz) {
    if (clazz.equals(TargetProcessor.class)) {
      return new TargetProcessor(config) {
        @Override
        public void notice(String message) {
          notices.add(message);
          super.notice(message);
        }
      };
    }
    return super.getProcessor(config, clazz);
  }

  private static void verifyCommand(ArgumentCaptor<String> commandCaptor, boolean isError) {
    Map<String, Object> command = toMap(commandCaptor.getValue());
    assertEquals(SubFolder.POINTSET.value(), command.remove("subFolder"), "subFolder field");
    assertEquals("events", command.remove("subType"), "subType field");
    assertEquals(TEST_DEVICE, command.remove("deviceId"));
    String expectedNamespace = !isError ? null : TEST_NAMESPACE;
    assertEquals(expectedNamespace, command.remove("projectId"));
    assertEquals(TEST_REGISTRY, command.remove("deviceRegistryId"));
  }

  @NotNull
  private Object getTestMessage(boolean isExtra) {
    return isExtra ? makeExtraFieldMessage() : new PointsetEvents();
  }

  private Bundle makeExtraFieldMessage() {
    Map<String, Object> stringObjectMap = toMap(new PointsetEvents());
    stringObjectMap.put(EXTRA_FIELD_KEY, MONGOOSE);
    // Specify these explicitly since the sent object is a (generic) map.
    Bundle bundle = makeMessageBundle(stringObjectMap);
    bundle.envelope.subType = SubType.EVENTS;
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
    assertEquals(0, getMessageCount(PointsetEvents.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(
        any(Envelope.class), eq(SubFolder.UDMI), commandCaptor.capture());
    verifyCommand(commandCaptor, true);
  }

  private void initializeTestInstance() {
    initializeTestInstance(TargetProcessor.class);
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
    assertEquals(1, getMessageCount(PointsetEvents.class), "pointset handler count");

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(
        any(Envelope.class), eq(SubFolder.UDMI), commandCaptor.capture());
    verifyCommand(commandCaptor, false);
  }

  /**
   * Test that provider messages with no deviceId skip reflector forwarding without a drop notice.
   */
  @Test
  public void providerMessageSkippedWithoutNotice() {
    notices.clear();
    initializeTestInstance();

    DiscoveryEvents event = new DiscoveryEvents();
    event.event_no = 0;
    Bundle bundle = new Bundle(event);
    bundle.envelope.deviceRegistryId = TEST_REGISTRY;
    bundle.envelope.deviceId = null;
    bundle.envelope.providerId = "zanzara";
    bundle.envelope.subSetKey = ModelDiscoveryHandler.MODEL_SUBSET_KEY;
    bundle.envelope.subType = SubType.EVENTS;
    bundle.envelope.subFolder = SubFolder.DISCOVERY;

    getReverseDispatcher().publish(bundle);
    terminateAndWait();

    assertEquals(1, captured.size(), "provider message should still be published");
    assertTrue(notices.isEmpty(),
        "Provider message with no deviceId should not emit a drop notice: " + notices);
    verify(provider, times(0)).sendCommand(any(Envelope.class), any(), any());
  }

  /**
   * Test that messages missing both deviceId and providerId still emit a drop notice.
   */
  @Test
  public void missingDeviceIdWithoutProviderLogsNotice() {
    notices.clear();
    initializeTestInstance();

    Bundle bundle = new Bundle(new PointsetEvents());
    bundle.envelope.deviceRegistryId = TEST_REGISTRY;
    bundle.envelope.deviceId = null;
    bundle.envelope.providerId = "";
    bundle.envelope.subType = SubType.EVENTS;
    bundle.envelope.subFolder = SubFolder.POINTSET;

    getReverseDispatcher().publish(bundle);
    terminateAndWait();

    assertEquals(1, captured.size(), "message should still be published");
    assertEquals(1, notices.size(), "expected a single drop notice");
    assertTrue(notices.get(0).contains("Dropping message with no deviceId"));
    verify(provider, times(0)).sendCommand(any(Envelope.class), any(), any());
  }

}
