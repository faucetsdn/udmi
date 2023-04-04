package com.google.bos.udmi.service.messaging;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.bos.udmi.service.messaging.MessageBase.normalizeNamespace;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.udmi.util.JsonUtil;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import udmi.schema.MessageConfiguration;

/**
 * Tests for LocalMessagePipe.
 */
public class LocalMessagePipeTest extends MessageTestBase {

  @Override
  public void resetForTest() {
    resetForTestStatic();
  }

  public static void resetForTestStatic() {
    LocalMessagePipe.resetForTest();
  }

  private Map<String, Object> testSend(MessagePipe pipe, Object message)
      throws InterruptedException {
    BlockingQueue<String> outQueue = getQueueForScope(TEST_NAMESPACE, TEST_DESTINATION);
    pipe.publish(message);
    return JsonUtil.asMap(outQueue.take());
  }

  public MessageBase getTestMessagePipeCore(boolean reversed) {
    return LocalMessagePipeTest.getTestMessagePipeStatic(reversed);
  }

  /**
   * Static version of getting a LocalMessagePipe.
   */
  public static LocalMessagePipe getTestMessagePipeStatic(boolean reversed) {
    LocalMessagePipe mainPipe = LocalMessagePipe.existing(getConfiguration());
    if (reversed) {
      checkState(mainPipe != null, "main pipe not instantiated");
      return new LocalMessagePipe((LocalMessagePipe) mainPipe, true);
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

  private static class BespokeObject {
  }

  /**
   * Test that publishing a message results in a properly constructed bundle (envelope and
   * message).
   */
  @Test
  @SuppressWarnings("unchecked")
  void publishedMessageBundle() throws InterruptedException {
    MessagePipe pipe = getTestMessagePipe();
    StateUpdate testMessage = new StateUpdate();
    testMessage.version = TEST_VERSION;
    Map<String, Object> received = testSend(pipe, testMessage);
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
    MessagePipe pipe = getTestMessagePipe();
    Exception expected = assertThrows(Exception.class,
        () -> testSend(pipe, new BespokeObject()), "Expected exception");
    assertTrue(expected.getMessage().contains("type entry not found"), "unexpected message");
  }
}