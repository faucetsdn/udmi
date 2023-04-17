package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static udmi.schema.Envelope.SubFolder.SYSTEM;
import static udmi.schema.Envelope.SubType.STATE;

import com.google.bos.udmi.service.messaging.impl.LocalMessagePipeTest;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.GatewayState;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;
import udmi.schema.State;
import udmi.schema.SystemState;

/**
 * Tests for the StateHandler class, used by UDMIS to process device state updates.
 */
public class StateHandlerTest extends LocalMessagePipeTest {

  private StateHandler stateHandler;

  private int getDefaultCount() {
    return stateHandler.defaultCount;
  }

  private int getExceptionCount() {
    return stateHandler.exceptionCount;
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
    MessageConfiguration config = new MessageConfiguration();
    config.transport = Transport.LOCAL;
    config.namespace = TEST_NAMESPACE;
    config.source = TEST_SOURCE;
    config.destination = TEST_DESTINATION;
    stateHandler = UdmisComponent.create(StateHandler.class, config);
    setTestDispatcher(stateHandler.getDispatcher());
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected two messages.
   */
  @Test
  public void multiExpansion() {
    initializeTestInstance();

    Bundle testStateBundle = getTestStateBundle(true);

    getReverseDispatcher().publish(testStateBundle);

    List<Bundle> bundles = drainPipes();

    Bundle targetBundle = bundles.remove(0);

    assertEquals(STATE, targetBundle.envelope.subType, "received message subType mismatch");
    assertNotNull(targetBundle.envelope.subFolder, "received message subFolder is null");
    assertEquals(1, bundles.size(), "unexpected remaining message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that a state update with one sub-block results in a received message of the proper type.
   */
  @Test
  public void singleExpansion() {
    initializeTestInstance();

    Bundle testStateBundle = getTestStateBundle(false);
    Bundle originalBundle = deepCopy(testStateBundle);

    getReverseDispatcher().publish(testStateBundle);

    List<Bundle> bundles = drainPipes();
    Bundle targetBundle = bundles.remove(0);

    assertNull(originalBundle.envelope.subType, "original envelope was not null");
    assertEquals(STATE, targetBundle.envelope.subType, "received message subType mismatch");
    assertEquals(SYSTEM, targetBundle.envelope.subFolder, "received message subType mismatch");
    assertNull(testStateBundle.envelope.subType, "original subType was mutated");
    assertEquals(0, bundles.size(), "unexpected published message count");
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

    drainPipes();

    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
  }
}