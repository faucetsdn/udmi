package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.UdmisComponent.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.UdmisComponent.FUNCTIONS_VERSION_MIN;
import static com.google.bos.udmi.service.messaging.impl.TraceMessagePipeTest.TEST_DEVICE;
import static com.google.bos.udmi.service.messaging.impl.TraceMessagePipeTest.TEST_REGISTRY;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Tests for the reflect processor function.
 */
public class ReflectProcessorTest extends ProcessorTestBase {

  @Override
  protected @NotNull Class<? extends UdmisComponent> getProcessorClass() {
    return ReflectProcessor.class;
  }

  private Bundle getTestReflectBundle(SubType subType, SubFolder subFolder) {
    return new Bundle(getTestReflectEnvelope(subType, subFolder),
        getTestReflectMessage(subType, subFolder));
  }

  @NotNull
  private Envelope getTestReflectEnvelope(SubType subType, SubFolder subFolder) {
    Envelope envelope = new Envelope();
    envelope.subType = subType;
    envelope.subFolder = subFolder;
    envelope.projectId = TEST_NAMESPACE;
    envelope.deviceId = TEST_DEVICE;
    envelope.deviceRegistryId = TEST_REGISTRY;
    return envelope;
  }

  @NotNull
  private Object getTestReflectMessage(SubType subType, SubFolder subFolder) {
    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = TEST_USER;
    udmiState.timestamp = TEST_TIMESTAMP;
    return subFolder == null ? ImmutableMap.of(SubFolder.UDMI, udmiState) : udmiState;
  }

  private void validateUdmiConfig(UdmiConfig config) {
    assertEquals(FUNCTIONS_VERSION_MIN, config.setup.functions_min, "min func version");
    assertEquals(FUNCTIONS_VERSION_MAX, config.setup.functions_max, "min func version");
    assertEquals(TEST_USER, config.setup.deployed_by, "deployed by user");
    assertEquals(TEST_TIMESTAMP, config.setup.deployed_at, "deployed at time");
  }

  /**
   * Test that the basic udmi-reflect state/config handshake works. When a client connects it
   * updates the state of the device entry, which then should trigger the underlying logic to output
   * an updated config message.
   */
  @Test
  public void stateConfigExchange() {
    initializeTestInstance();

    Bundle rawBundle = getTestReflectBundle(null, null);
    getReverseDispatcher().publish(rawBundle);
    Bundle subBundle = getTestReflectBundle(SubType.STATE, SubFolder.UDMI);
    getReverseDispatcher().publish(subBundle);

    terminateAndWait();

    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
    assertEquals(1, getMessageCount(UdmiState.class), "udmi state processed count");

    verify(provider, times(1)).activate();

    ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).updateConfig(eq(TEST_REGISTRY), eq(TEST_DEVICE),
        configCaptor.capture());
    Map<String, Object> stringObjectMap = toMap(configCaptor.getValue());
    UdmiConfig udmi =
        convertToStrict(UdmiConfig.class, stringObjectMap.get(SubFolder.UDMI.value()));
    assertEquals(getTimestamp(ReflectProcessorTest.TEST_TIMESTAMP),
        getTimestamp(udmi.setup.deployed_at), "unexpected deploy timestamp");
  }

}