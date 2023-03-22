package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.MessageBase;
import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.udmi.util.GeneralUtils;
import java.util.concurrent.BlockingQueue;
import junit.framework.TestCase;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

public class StateHandlerTest extends TestCase {

  private static final String STATE_BUS_ID = "udmi_state";
  private static final String TARGET_BUS_ID = "udmi_target";

  public void testStateExpansion() throws InterruptedException {
    StateHandler handler = getTestHandler();
    handler.activate();
    BlockingQueue<Bundle> stateBus = LocalMessagePipe.getQueueForScope(STATE_BUS_ID);
    BlockingQueue<Bundle> targetBus = LocalMessagePipe.getQueueForScope(TARGET_BUS_ID);
    Bundle testStateBundle = getTestStateBundle();
    Bundle originalBundle = GeneralUtils.deepCopy(testStateBundle);
    stateBus.put(testStateBundle);
    Bundle targetBundle = targetBus.take();
    assertNull("original envelope was not null", originalBundle.envelope.subType);
    assertNull("original subType was changed", testStateBundle.envelope.subType);
    assertEquals("received message subType", SubType.STATE, targetBundle.envelope.subType);
  }

  private Bundle getTestStateBundle() {
    Object message = "hello";
    Envelope envelope = new Envelope();
    return MessageBase.makeBundle(envelope, message);
  }

  private StateHandler getTestHandler() {
    MessageConfiguration config = new MessageConfiguration();
    config.transport = Transport.LOCAL;
    config.source = STATE_BUS_ID;
    config.destination = TARGET_BUS_ID;
    return StateHandler.forConfig(config);
  }
}