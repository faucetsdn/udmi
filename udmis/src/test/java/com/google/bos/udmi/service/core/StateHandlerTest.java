package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static udmi.schema.Envelope.SubFolder.SYSTEM;
import static udmi.schema.Envelope.SubType.STATE;

import com.google.bos.udmi.service.messaging.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.LocalMessagePipeTest;
import com.google.bos.udmi.service.messaging.MessageBase;
import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessageTestBase;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
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
public class StateHandlerTest extends MessageTestBase {

  public static final String INVALID_MESSAGE = "invalid message";
  private StateHandler stateHandler;
  private MessagePipe reversePipe;

  @Override
  protected MessageBase getTestMessagePipeCore(boolean reversed) {
    return LocalMessagePipeTest.getTestMessagePipeStatic(reversed);
  }

  private Bundle getTestStateBundle(boolean includeGateway) {
    Bundle bundle = new Bundle();
    bundle.envelope = getTestStateEnvelope();
    bundle.message = getTestStateMessage(includeGateway);
    return bundle;
  }

  @Override
  protected void resetForTest() {
    LocalMessagePipeTest.resetForTestStatic();
  }

  @NotNull
  private Envelope getTestStateEnvelope() {
    return new Envelope();
  }

  @NotNull
  private State getTestStateMessage(boolean includeGateway) {
    State stateMessage = new State();
    stateMessage.version = TEST_VERSION;
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
    stateHandler = StateHandler.forConfig(config);
    stateHandler.activate();
    reversePipe = getReverseMessagePipe();
  }

  protected int getExceptionCount() {
    return stateHandler.exceptionCount;
  }

  protected int getDefaultCount() {
    return stateHandler.defaultCount;
  }

  /**
   * Test that a state update with one sub-block results in a received message of the proper type.
   */
  @Test
  public void singleExpansion() throws InterruptedException {
    initializeTestInstance();
    BlockingQueue<String> stateBus = getQueueForScope(TEST_NAMESPACE, TEST_SOURCE);
    BlockingQueue<String> targetBus = getQueueForScope(TEST_NAMESPACE, TEST_DESTINATION);

    Bundle testStateBundle = getTestStateBundle(false);
    Bundle originalBundle = deepCopy(testStateBundle);

    stateBus.put(stringify(testStateBundle));

    Bundle targetBundle = fromString(Bundle.class, targetBus.take());

    drainPipe();

    assertNull(originalBundle.envelope.subType, "original envelope was not null");
    assertEquals(STATE, targetBundle.envelope.subType, "received message subType mismatch");
    assertEquals(SYSTEM, targetBundle.envelope.subFolder, "received message subType mismatch");
    assertNull(testStateBundle.envelope.subType, "original subType was mutated");
    assertEquals(0, targetBus.size(), "unexpected published message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected two messages.
   */
  @Test
  public void multiExpansion() throws InterruptedException {
    initializeTestInstance();
    BlockingQueue<String> stateBus = getQueueForScope(TEST_NAMESPACE, TEST_SOURCE);
    BlockingQueue<String> targetBus = getQueueForScope(TEST_NAMESPACE, TEST_DESTINATION);

    Bundle testStateBundle = getTestStateBundle(true);

    stateBus.put(stringify(testStateBundle));

    Bundle targetBundle = fromString(Bundle.class, targetBus.take());

    drainPipe();
    
    assertEquals(STATE, targetBundle.envelope.subType, "received message subType mismatch");
    assertNotNull(targetBundle.envelope.subFolder, "received message subFolder is null");
    System.err.println("Checking queue " + Objects.hash(targetBus));
    assertEquals(1, targetBus.size(), "unexpected remaining message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that receiving an invalid message results in the appropriate exception handler being
   * called.
   */
  @Test
  public void stateException() throws InterruptedException {
    initializeTestInstance();
    BlockingQueue<String> stateBus = getQueueForScope(TEST_NAMESPACE, TEST_SOURCE);

    stateBus.put(INVALID_MESSAGE);

    drainPipe();

    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
  }
}