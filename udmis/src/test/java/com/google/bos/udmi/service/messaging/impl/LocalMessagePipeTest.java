package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

/**
 * Tests for LocalMessagePipe.
 */
public class LocalMessagePipeTest extends MessageTestBase {

  private MessageConfiguration getConfiguration(boolean reversed) {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.transport = Transport.LOCAL;
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = reversed ? TEST_DESTINATION : TEST_SOURCE;
    messageConfiguration.destination = reversed ? TEST_SOURCE : TEST_DESTINATION;
    return messageConfiguration;
  }

  private Map<String, Object> testSend(Object message) {
    getTestDispatcher().publish(message);
    List<Bundle> bundles = getTestDispatcher().drainOutput();
    checkState(bundles.size() == 1, "expected 1 drained message, found " + bundles.size());
    return JsonUtil.asMap(bundles.get(0));
  }

  public MessageDispatcher getTestDispatcherCore(boolean reversed) {
    return MessageDispatcher.from(getConfiguration(reversed));
  }

  private static class BespokeObject {

  }

  /**
   * Test that publishing a message results in a properly constructed bundle (envelope and
   * message).
   */
  @Test
  @SuppressWarnings("unchecked")
  void publishedMessageBundle() {
    StateUpdate testMessage = new StateUpdate();
    testMessage.version = TEST_VERSION;
    Map<String, Object> received = testSend(testMessage);
    Map<String, Object> envelope = (Map<String, Object>) received.get("envelope");
    assertEquals("state", envelope.get("subType"), "unexpected subtype");
    assertEquals("update", envelope.get("subFolder"), "unexpected subfolder");
    Map<String, Object> message = (Map<String, Object>) received.get("message");
    assertEquals(TEST_VERSION, message.get("version"));
  }

  /**
   * Test that publishing an unexpected type of object results in an appropriate exception.
   */
  @Test
  void publishUntyped() {
    Exception expected = assertThrows(Exception.class,
        () -> testSend(new BespokeObject()), "Expected exception");
    assertTrue(expected.getMessage().contains("type entry not found"), "unexpected message");
  }
}