package com.google.bos.udmi.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.stringify;
import static udmi.schema.Envelope.SubFolder.SYSTEM;
import static udmi.schema.Envelope.SubType.STATE;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import java.util.concurrent.BlockingQueue;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
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

  @Test
  public void singleExpansion() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);
    BlockingQueue<String> targetBus = getQueueForScope(getNamespace(), TARGET_BUS_ID);

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

  @Test
  public void multiExpansion() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);
    BlockingQueue<String> targetBus = getQueueForScope(getNamespace(), TARGET_BUS_ID);

    Bundle testStateBundle = getTestStateBundle(true);

    stateBus.put(stringify(testStateBundle));

    Bundle targetBundle = fromString(Bundle.class, targetBus.take());

    drainPipe();

    assertEquals(STATE, targetBundle.envelope.subType, "received message subType mismatch");
    assertNotNull(targetBundle.envelope.subFolder, "received message subFolder is null");
    assertEquals(1, targetBus.size(), "unexpected published message count");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  @Test
  public void stateException() throws InterruptedException {
    initializeTestHandler();
    BlockingQueue<String> stateBus = getQueueForScope(getNamespace(), STATE_BUS_ID);

    stateBus.put(INVALID_MESSAGE);
    drainPipe();
    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
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