package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.stringify;
import static udmi.schema.Envelope.SubFolder.SYSTEM;
import static udmi.schema.Envelope.SubType.STATE;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import java.util.concurrent.BlockingQueue;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.GatewayState;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;
import udmi.schema.State;
import udmi.schema.SystemState;

public class StateHandlerTest extends TestBase {

  public static final String INVALID_MESSAGE = "invalid message";

  private static final String STATE_BUS_ID = "udmi_state";
  private static final String TARGET_BUS_ID = "udmi_target";
  private static final String TESTING_VERSION = "98.2";

  public void testSingleExpansion() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);
    BlockingQueue<String> targetBus = getQueueForScope(getNamespace(), TARGET_BUS_ID);

    Bundle testStateBundle = getTestStateBundle(false);
    Bundle originalBundle = deepCopy(testStateBundle);

    stateBus.put(stringify(testStateBundle));

    Bundle targetBundle = fromString(Bundle.class, targetBus.take());

    drainPipe();

    assertNull("original envelope was not null", originalBundle.envelope.subType);
    assertEquals("received message subType mismatch", STATE, targetBundle.envelope.subType);
    assertEquals("received message subType mismatch", SYSTEM, targetBundle.envelope.subFolder);
    assertNull("original subType was mutated", testStateBundle.envelope.subType);
    assertEquals("unexpected published message count", 0, targetBus.size());
    assertEquals("exception count", 0, getExceptionCount());
    assertEquals("default handler count", 1, getDefaultCount());
  }

  public void testMultiExpansion() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);
    BlockingQueue<String> targetBus = getQueueForScope(getNamespace(), TARGET_BUS_ID);

    Bundle testStateBundle = getTestStateBundle(true);

    stateBus.put(stringify(testStateBundle));

    Bundle targetBundle = fromString(Bundle.class, targetBus.take());

    drainPipe();

    assertEquals("received message subType mismatch", STATE, targetBundle.envelope.subType);
    assertNotNull("received message subFolder is null", targetBundle.envelope.subFolder);
    assertEquals("unexpected published message count", 1, targetBus.size());
    assertEquals("exception count", 0, getExceptionCount());
    assertEquals("default handler count", 1, getDefaultCount());
  }

  public void testStateException() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);

    stateBus.put(INVALID_MESSAGE);
    drainPipe();
    assertEquals("exception count", 1, getExceptionCount());
    assertEquals("default handler count", 0, getDefaultCount());
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
    stateMessage.version = TESTING_VERSION;
    stateMessage.system = new SystemState();
    stateMessage.gateway = includeGateway ? new GatewayState() : null;
    return stateMessage;
  }

  private StateHandler initializeTestHandler() {
    instanceCount.incrementAndGet();
    MessageConfiguration config = new MessageConfiguration();
    config.transport = Transport.LOCAL;
    config.namespace = getNamespace();
    config.source = STATE_BUS_ID;
    config.destination = TARGET_BUS_ID;
    StateHandler stateHandler = StateHandler.forConfig(config);
    stateHandler.activate();
    stateHandler.info("Using test message namespace " + getNamespace());
    return stateHandler;
  }

}