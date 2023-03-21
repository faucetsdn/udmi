package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.MessageBase;
import com.google.bos.udmi.service.messaging.MessagePipe.Bundle;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import junit.framework.TestCase;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

public class StateHandlerTest extends TestCase {

  private static final String STATE_BUS_ID = "udmi_state";
  private static final String TARGET_BUS_ID = "udmi_target";

  public void testStateExpansion() {
    StateHandler handler = getTestHandler();
    handler.activate();
    BlockingQueue<Bundle> stateBus = LocalMessagePipe.getQueueForScope(STATE_BUS_ID);
    BlockingQueue<Bundle> targetBus = LocalMessagePipe.getQueueForScope(TARGET_BUS_ID);
    try {
      stateBus.put(getTestStateBundle());
      Bundle targetBundle = targetBus.take();
      assertEquals("message subType", SubType.STATE.value(),
          targetBundle.attributes.get("subType"));
    } catch (Exception e) {
      throw new RuntimeException("While testing message bus", e);
    }
  }

  private Bundle getTestStateBundle() {
    Object message = "hello";
    Map<String, String> attributes = ImmutableMap.of();
    return MessageBase.getBundle(message, attributes);
  }

  private StateHandler getTestHandler() {
    MessageConfiguration config = new MessageConfiguration();
    config.transport = Transport.LOCAL;
    config.source = STATE_BUS_ID;
    config.destination = TARGET_BUS_ID;
    return StateHandler.forConfig(config);
  }
}