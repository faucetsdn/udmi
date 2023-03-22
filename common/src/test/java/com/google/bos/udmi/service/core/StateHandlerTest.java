package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.bos.udmi.service.messaging.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.concurrent.BlockingQueue;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;
import udmi.schema.State;

public class StateHandlerTest extends TestCase {

  private static final String STATE_BUS_ID = "udmi_state";
  private static final String TARGET_BUS_ID = "udmi_target";
  private static final String TESTING_VERSION = "98.2";

  public void testStateExpansion() throws InterruptedException {
    StateHandler handler = getTestHandler();
    handler.activate();
    BlockingQueue<String> stateBus = LocalMessagePipe.getQueueForScope(STATE_BUS_ID);
    BlockingQueue<String> targetBus = LocalMessagePipe.getQueueForScope(TARGET_BUS_ID);
    Bundle testStateBundle = getTestStateBundle();
    Bundle originalBundle = deepCopy(testStateBundle);
    stateBus.put(stringify(testStateBundle));
    Bundle targetBundle = fromString(Bundle.class, targetBus.take());
    assertNull("original envelope was not null", originalBundle.envelope.subType);
    assertEquals("received message subType mismatch", SubType.STATE, targetBundle.envelope.subType);
    assertNull("original subType was mutated", testStateBundle.envelope.subType);
  }

  private Bundle getTestStateBundle() {
    Bundle bundle = new Bundle();
    bundle.envelope = getTestStateEnvelope();
    bundle.message = getTestStateMessage();
    return bundle;
  }

  @NotNull
  private Envelope getTestStateEnvelope() {
    return new Envelope();
  }

  @NotNull
  private State getTestStateMessage() {
    State stateMessage = new State();
    stateMessage.version = TESTING_VERSION;
    return stateMessage;
  }

  private StateHandler getTestHandler() {
    MessageConfiguration config = new MessageConfiguration();
    config.transport = Transport.LOCAL;
    config.source = STATE_BUS_ID;
    config.destination = TARGET_BUS_ID;
    return StateHandler.forConfig(config);
  }
}