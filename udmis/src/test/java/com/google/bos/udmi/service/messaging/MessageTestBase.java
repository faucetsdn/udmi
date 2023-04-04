package com.google.bos.udmi.service.messaging;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
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
  private static MessageBase inPipe;

  /**
   * Get a message pipe as defined by the appropriate pipe type subclass.
   */
  protected abstract MessageBase getTestMessagePipeCore(boolean reversed);

  protected MessageBase getReverseMessagePipe() {
    getTestMessagePipe();  // Ensure that the main pipe exists before doing the reverse.
    return getTestMessagePipe(true);
  }

  protected MessageBase getTestMessagePipe() {
    inPipe = Optional.ofNullable(inPipe).orElseGet(() -> getTestMessagePipe(false));
    return inPipe;
  }

  private MessageBase getTestMessagePipe(boolean reversed) {
    MessageBase messagePipe = getTestMessagePipeCore(reversed);
    if (!messagePipe.isActive()) {
      messagePipe.registerHandlers(messageHandlers);
      messagePipe.activate();
    }
    return messagePipe;
  }

  static MessagePipe getExistingPipe() {
    return inPipe;
  }

  @AfterEach
  public void resetPipe() {
    inPipe = null;
    resetForTest();
  }

  protected boolean environmentIsEnabled() {
    return true;
  }

  protected void resetForTest() {
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

  private Object publishAndReceive(Bundle bundle) throws InterruptedException {
    assertNull(receivedMessage.get(), "expected null pre-receive message");
    getReverseMessagePipe().publishBundle(bundle);
    return synchronizedReceive();
  }

  protected void drainPipe() {
    getTestMessagePipe().drainSource();
    getReverseMessagePipe().drainSource();
  }

  /**
   * Test that receiving a malformed bundle results in a received exception.
   */
  @Test
  void receiveException() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    Object received = publishAndReceive(new Bundle());
    assertTrue(received instanceof Exception, "Expected received exception");
  }

  /**
   * Test that a publish/receive pair results in the right type of object.
   */
  @Test
  void receiveMessage() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    MessagePipe reversed = getReverseMessagePipe();
    reversed.publish(new StateUpdate());
    Object received = synchronizedReceive();
    assertTrue(received instanceof StateUpdate, "Expected state update message");
  }

  /**
   * Test that received an unregistered message type (no handler) results in the default Object
   * handler being called.
   */
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
}
