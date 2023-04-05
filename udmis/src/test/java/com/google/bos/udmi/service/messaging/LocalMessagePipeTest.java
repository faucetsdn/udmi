package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import udmi.schema.MessageConfiguration;

/**
 * Tests for LocalMessagePipe.
 */
public class LocalMessagePipeTest extends MessageTestBase {

  public MessageBase getTestMessagePipeCore(boolean reversed) {
    return LocalMessagePipeTest.getTestMessagePipeStatic(reversed);
  }

  /**
   * Static version of getting a LocalMessagePipe.
   */
  public static LocalMessagePipe getTestMessagePipeStatic(boolean reversed) {
    LocalMessagePipe mainPipe = LocalMessagePipe.getPipeForNamespace(getConfiguration().namespace);
    if (reversed) {
      checkState(mainPipe != null, "main pipe not instantiated");
      return new LocalMessagePipe(mainPipe, true);
    }
    return Optional.ofNullable(mainPipe).orElseGet(() -> new LocalMessagePipe(getConfiguration()));
  }

  private static MessageConfiguration getConfiguration() {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = TEST_SOURCE;
    messageConfiguration.destination = TEST_DESTINATION;
    return messageConfiguration;
  }

  @Override
  public void resetForTest() {
    resetForTestStatic();
  }

  public static void resetForTestStatic() {
    LocalMessagePipe.resetForTest();
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

  private Map<String, Object> testSend(Object message) {
    getTestMessagePipe().publish(message);
    List<Bundle> bundles = getTestMessagePipe().drainOutput();
    checkState(bundles.size() == 1, "expected 1 drained message, found " + bundles.size());
    return JsonUtil.asMap(bundles.get(0));
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