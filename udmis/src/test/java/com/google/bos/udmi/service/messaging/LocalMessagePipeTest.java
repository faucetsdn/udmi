package com.google.bos.udmi.service.messaging;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.udmi.util.JsonUtil;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import udmi.schema.MessageConfiguration;

public class LocalMessagePipeTest extends MessageTestBase {

  private static LocalMessagePipe mainPipe;

  @AfterEach
  public void resetForTest() {
    resetForTestStatic();
  }

  public static void resetForTestStatic() {
    mainPipe = null;
    LocalMessagePipe.resetForTest();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishBundle() throws InterruptedException {
    MessagePipe pipe = getTestMessagePipe(false);
    StateUpdate testMessage = new StateUpdate();
    testMessage.version = TEST_VERSION;
    Map<String, Object> received = testSend(pipe, testMessage);
    Map<String, Object> envelope = (Map<String, Object>) received.get("envelope");
    assertEquals("state", envelope.get("subType"), "unexpected subtype");
    assertEquals("update", envelope.get("subFolder"), "unexpected subfolder");
    Map<String, Object> message = (Map<String, Object>) received.get("message");
    assertEquals(TEST_VERSION, message.get("version"));
  }

  @Test
  void publishUntyped() {
    MessagePipe pipe = getTestMessagePipe(false);
    Exception expected = assertThrows(Exception.class,
        () -> testSend(pipe, new Exception()), "Expected exception");
    assertTrue(expected.getMessage().contains("type entry not found"), "unexpected message");
  }

  private Map<String, Object> testSend(MessagePipe pipe, Object message)
      throws InterruptedException {
    BlockingQueue<String> outQueue = getQueueForScope(TEST_NAMESPACE, TEST_DESTINATION);
    pipe.publish(message);
    return JsonUtil.asMap(outQueue.take());
  }

  public LocalMessagePipe getTestMessagePipeCore(boolean reversed) {
    return LocalMessagePipeTest.getTestMessagePipeStatic(reversed);
  }

  public static LocalMessagePipe getTestMessagePipeStatic(boolean reversed) {
    if (reversed) {
      checkState(mainPipe != null, "main pipe not instantiated");
      return new LocalMessagePipe(mainPipe, true);
    }
    LocalMessagePipe localMessagePipe = new LocalMessagePipe(getConfiguration());
    checkState(mainPipe == null, "duplicate main pipe assignment");
    mainPipe = localMessagePipe;
    return localMessagePipe;
  }

  private static MessageConfiguration getConfiguration() {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = TEST_SOURCE;
    messageConfiguration.destination = TEST_DESTINATION;
    return messageConfiguration;
  }
}