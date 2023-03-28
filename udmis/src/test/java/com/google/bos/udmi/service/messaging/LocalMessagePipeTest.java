package com.google.bos.udmi.service.messaging;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import udmi.schema.LocalnetState;
import udmi.schema.MessageConfiguration;

class LocalMessagePipeTest {

  private static final String MESSAGE_SOURCE = "message_from";
  private static final String MESSAGE_DESTINATION = "message_to";
  private static final String SIMPLE_NAMESPACE = "namespace";
  private static final String TEST_VERSION = "1.32";

  private final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::messageHandler),
      messageHandlerFor(StateUpdate.class, this::messageHandler));

  private final AtomicReference<Object> receivedMessage = new AtomicReference<>();

  @Test
  @SuppressWarnings("unchecked")
  void publishBundle() throws InterruptedException {
    LocalMessagePipe pipe = getTestMessagePipe();
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
    LocalMessagePipe pipe = getTestMessagePipe();
    Exception expected = assertThrows(Exception.class,
        () -> testSend(pipe, new Exception()), "Expected exception");
    assertTrue(expected.getMessage().contains("type entry not found"), "unexpected message");
  }

  @Test
  void receiveException() throws InterruptedException {
    LocalMessagePipe pipe = getTestMessagePipe();
    String messageString = "hello";
    Object received = loopBundle(messageString);
    assertTrue(received instanceof Exception, "Expected received exception");
    pipe.drainSource();
  }

  @Test
  void receiveMessage() throws InterruptedException {
    LocalMessagePipe pipe = getTestMessagePipe();
    LocalMessagePipe reversed = new LocalMessagePipe(pipe, true);
    reversed.publish(new StateUpdate());
    Object received = synchronizedReceive();
    assertTrue(received instanceof StateUpdate, "Expected state update message");
    pipe.drainSource();
  }

  @Test
  @SuppressWarnings("unchecked")
  void receiveDefaultMessage() throws InterruptedException {
    LocalMessagePipe pipe = getTestMessagePipe();
    LocalMessagePipe reversed = new LocalMessagePipe(pipe, true);
    reversed.publish(new LocalnetState());
    Object received = synchronizedReceive();
    // The default handler warps the received message in an AtomicReference just as a signal.
    assertTrue(received instanceof AtomicReference, "Expected default handler");
    Object receivedObject = ((AtomicReference<Object>) received).get();
    assertTrue(receivedObject instanceof LocalnetState, "Expected localnet message");
    pipe.drainSource();
  }

  private Object synchronizedReceive() throws InterruptedException {
    synchronized (LocalMessagePipeTest.class) {
      Object existing = receivedMessage.getAndSet(null);
      if (existing != null) {
        return existing;
      }
      LocalMessagePipeTest.class.wait();
      return receivedMessage.getAndSet(null);
    }
  }

  private Object loopBundle(String bundleString) throws InterruptedException {
    BlockingQueue<String> inQueue = getQueueForScope(SIMPLE_NAMESPACE, MESSAGE_SOURCE);
    assertNull(receivedMessage.get(), "expected null pre-receive message");
    inQueue.put(bundleString);
    return synchronizedReceive();
  }

  private Map<String, Object> testSend(LocalMessagePipe pipe, Object message)
      throws InterruptedException {
    BlockingQueue<String> outQueue = getQueueForScope(SIMPLE_NAMESPACE, MESSAGE_DESTINATION);
    pipe.publish(message);
    return JsonUtil.asMap(outQueue.take());
  }

  private <T> void messageHandler(T message) {
    synchronized (LocalMessagePipeTest.class) {
      Object previous = receivedMessage.getAndSet(message);
      assertNull(previous, "Unexpected previously received message");
      LocalMessagePipeTest.class.notify();
    }
  }

  private <T> void defaultHandler(T message) {
    // Wrap the message in an AtomicReference as a signal that this was the default handler.
    messageHandler(new AtomicReference<>(message));
  }

  @NotNull
  private LocalMessagePipe getTestMessagePipe() {
    LocalMessagePipe.resetForTest();
    LocalMessagePipe localMessagePipe = new LocalMessagePipe(getConfiguration());
    localMessagePipe.registerHandlers(messageHandlers);
    localMessagePipe.activate();
    return localMessagePipe;
  }

  private MessageConfiguration getConfiguration() {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.namespace = SIMPLE_NAMESPACE;
    messageConfiguration.source = MESSAGE_SOURCE;
    messageConfiguration.destination = MESSAGE_DESTINATION;
    return messageConfiguration;
  }
}