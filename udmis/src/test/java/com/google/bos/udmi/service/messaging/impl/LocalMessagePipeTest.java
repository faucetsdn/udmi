package com.google.bos.udmi.service.messaging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;

/**
 * Tests for LocalMessagePipe.
 */
public class LocalMessagePipeTest extends MessagePipeTestBase {

  private Map<String, Object> testSend(Object message) {
    getTestDispatcher().publish(message);
    List<Bundle> bundles = getReverseDispatcher().drain();
    assertEquals(1, bundles.size(), "unexpected received bundle");
    return JsonUtil.asMap(bundles.get(0));
  }

  public void augmentConfig(EndpointConfiguration config, boolean reversed) {
    config.protocol = Protocol.LOCAL;
  }

  private static class BespokeObject {

    public String sillyDilly;
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
    assertThrows(Exception.class,
        () -> testSend(new BespokeObject()), "missing expected exception");
  }
}
