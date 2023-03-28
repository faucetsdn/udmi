package com.google.bos.udmi.service.messaging;

import static com.google.bos.udmi.service.messaging.LocalMessagePipe.getQueueForScope;
import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import udmi.schema.LocalnetState;

/**
 * Common classes and functions for working with UDMIS unit tests.
 */
public abstract class MessageTestBase {

  protected static final String TEST_NAMESPACE = "test_namespace";
  protected static final String TEST_SOURCE = "message_from";
  protected static final String TEST_DESTINATION = "message_to";
  protected static final String TEST_VERSION = "1.32";
  private static final long RECEIVE_TIMEOUT_MS = 1000;
  protected static AtomicInteger instanceCount = new AtomicInteger();
  protected final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::messageHandler),
      messageHandlerFor(StateUpdate.class, this::messageHandler));
  protected final AtomicReference<Object> receivedMessage = new AtomicReference<>();

  protected int getExceptionCount() {
    LocalMessagePipe messagePipe = LocalMessagePipe.getPipeForNamespace(TEST_NAMESPACE);
    Map<String, AtomicInteger> handlerCounts = messagePipe.handlerCounts;
    return handlerCounts.getOrDefault(MessageBase.EXCEPTION_HANDLER, new AtomicInteger()).get();
  }

  protected int getDefaultCount() {
    LocalMessagePipe messagePipe = LocalMessagePipe.getPipeForNamespace(TEST_NAMESPACE);
    Map<String, AtomicInteger> handlerCounts = messagePipe.handlerCounts;
    return handlerCounts.getOrDefault(MessageBase.DEFAULT_HANDLER, new AtomicInteger()).get();
  }

  protected void drainPipe() {
    LocalMessagePipe.getPipeForNamespace(TEST_NAMESPACE).drainSource();
  }

  protected abstract MessagePipe getTestMessagePipeCore(boolean reversed);

  protected MessagePipe getReverseMessagePipe() {
    getTestMessagePipe(false);
    return getTestMessagePipe(true);
  }

  protected MessagePipe getTestMessagePipe(boolean reversed) {
    MessagePipe messagePipe = getTestMessagePipeCore(reversed);
    if (messagePipe != null) {
      messagePipe.registerHandlers(messageHandlers);
      messagePipe.activate();
    }
    return messagePipe;
  }

  @AfterEach
  public void resetPipe() {
    resetForTest();
  }

  protected boolean environmentIsEnabled() {
    return true;
  }

  protected void resetForTest() {
  }

  @Test
  void receiveException() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    getTestMessagePipe(false);
    String messageString = "hello";
    Object received = loopBundle(messageString);
    assertTrue(received instanceof Exception, "Expected received exception");
  }

  @Test
  void receiveMessage() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    MessagePipe reversed = getReverseMessagePipe();
    reversed.publish(new StateUpdate());
    Object received = synchronizedReceive();
    assertTrue(received instanceof StateUpdate, "Expected state update message");
  }

  @Test
  @SuppressWarnings("unchecked")
  void receiveDefaultMessage() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    MessagePipe reversed = getReverseMessagePipe();
    reversed.publish(new LocalnetState());
    Object received = synchronizedReceive();
    // The default handler warps the received message in an AtomicReference just as a signal.
    assertTrue(received instanceof AtomicReference, "Expected default handler");
    Object receivedObject = ((AtomicReference<Object>) received).get();
    assertTrue(receivedObject instanceof LocalnetState, "Expected localnet message");
  }

  protected Object synchronizedReceive() throws InterruptedException {
    synchronized (LocalMessagePipeTest.class) {
      Object existing = receivedMessage.getAndSet(null);
      if (existing != null) {
        return existing;
      }
      LocalMessagePipeTest.class.wait(RECEIVE_TIMEOUT_MS);
      return receivedMessage.getAndSet(null);
    }
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

  private Object loopBundle(String bundleString) throws InterruptedException {
    BlockingQueue<String> inQueue = getQueueForScope(TEST_NAMESPACE, TEST_SOURCE);
    assertNull(receivedMessage.get(), "expected null pre-receive message");
    inQueue.put(bundleString);
    return synchronizedReceive();
  }
}
