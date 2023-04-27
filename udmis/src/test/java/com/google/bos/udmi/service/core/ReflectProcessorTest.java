package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.UdmisComponent.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.UdmisComponent.FUNCTIONS_VERSION_MIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

public class ReflectProcessorTest extends ProcessorTestBase {

  @Override
  protected @NotNull Class<? extends UdmisComponent> getProcessorClass() {
    return ReflectProcessor.class;
  }

  private Bundle getTestReflectBundle(boolean stateMessage) {
    return new Bundle(getTestReflectEnvelope(stateMessage), getTestReflectMessage());
  }

  @NotNull
  private Envelope getTestReflectEnvelope(boolean stateMessage) {
    Envelope envelope = new Envelope();
    envelope.subFolder = stateMessage ? null : SubFolder.UDMI;
    envelope.projectId = TEST_NAMESPACE;
    return envelope;
  }

  @NotNull
  private UdmiState getTestReflectMessage() {
    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = TEST_USER;
    udmiState.timestamp = TEST_TIMESTAMP;
    return udmiState;
  }

  /**
   * Test that the basic udmi-reflect state/config handshake works. When a client connects
   * it updates the state of the device entry, which then should trigger the underlying logic
   * to output an updated config message.
   */
  @Test
  public void stateConfigExchange() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestReflectBundle(true));
    terminateAndWait();

    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
    assertEquals(1, captured.size(), "unexpected received message count");

    UdmiConfig config = (UdmiConfig) captured.get(0);
    assertEquals(FUNCTIONS_VERSION_MIN, config.udmi.functions_min, "min func version");
    assertEquals(FUNCTIONS_VERSION_MAX, config.udmi.functions_max, "min func version");
    assertEquals(TEST_USER, config.udmi.deployed_by, "deployed by user");
    assertEquals(TEST_TIMESTAMP, config.udmi.deployed_at, "deployed at time");
  }

}