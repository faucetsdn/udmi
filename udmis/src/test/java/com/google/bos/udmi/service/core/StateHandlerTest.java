package com.google.bos.udmi.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.impl.LocalMessagePipeTest;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.GatewayState;
import udmi.schema.State;
import udmi.schema.SystemState;

/**
 * Tests for the StateHandler class, used by UDMIS to process device state updates.
 */
public class StateHandlerTest extends LocalMessagePipeTest {

  private StateHandler stateHandler;
  private final List<Object> captured = new ArrayList<>();

  private int getDefaultCount() {
    return stateHandler.getMessageCount(Object.class);
  }

  private int getExceptionCount() {
    return stateHandler.getMessageCount(Exception.class);
  }

  private Bundle getTestStateBundle(boolean includeGateway) {
    Bundle bundle = new Bundle();
    bundle.envelope = getTestStateEnvelope();
    bundle.message = getTestStateMessage(includeGateway);
    return bundle;
  }

  @NotNull
  private Envelope getTestStateEnvelope() {
    return new Envelope();
  }

  @NotNull
  private State getTestStateMessage(boolean includeGateway) {
    State stateMessage = new State();
    stateMessage.version = TEST_VERSION + "x";
    stateMessage.system = new SystemState();
    stateMessage.gateway = includeGateway ? new GatewayState() : null;
    return stateMessage;
  }

  private void initializeTestInstance() {
    instanceCount.incrementAndGet();
    EndpointConfiguration config = new EndpointConfiguration();
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = TEST_SOURCE;
    config.send_id = TEST_DESTINATION;
    stateHandler = UdmisComponent.create(StateHandler.class, config);
    setTestDispatcher(stateHandler.getDispatcher());
    MessageDispatcherImpl reverseDispatcher = getReverseDispatcher();
    reverseDispatcher.registerHandler(Object.class, this::resultHandler);
    reverseDispatcher.activate();
  }

  private void resultHandler(Object message) {
    captured.add(message);
  }

  private void terminateAndWait() {
    getReverseDispatcher().terminate();
    getTestDispatcher().awaitShutdown();
    getTestDispatcher().terminate();
    getReverseDispatcher().awaitShutdown();
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected two messages.
   */
  @Test
  public void multiExpansion() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestStateBundle(true));
    terminateAndWait();

    assertEquals(2, captured.size(), "unexpected received message count");
    assertTrue(captured.stream().anyMatch(message -> message instanceof SystemState),
        "has SystemState");
    assertTrue(captured.stream().anyMatch(message -> message instanceof GatewayState),
        "has GatewayState");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that a state update with one sub-block results in a received message of the proper type.
   */
  @Test
  public void singleExpansion() {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestStateBundle(false));
    terminateAndWait();

    assertEquals(1, captured.size(), "unexpected received message count");
    Object received = captured.get(0);
    assertTrue(received instanceof SystemState, "expected SystemState message");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that receiving an invalid message results in the appropriate exception handler being
   * called.
   */
  @Test
  public void stateException() {
    initializeTestInstance();
    getReverseDispatcher().publish(null);
    terminateAndWait();

    assertEquals(0, captured.size(), "unexpected received message count");
    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
  }
}
