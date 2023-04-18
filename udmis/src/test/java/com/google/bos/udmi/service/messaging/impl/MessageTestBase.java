package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import udmi.schema.LocalnetModel;
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
  protected final AtomicReference<Object> receivedMessage = new AtomicReference<>();
  protected final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::messageHandler),
      messageHandlerFor(LocalnetModel.class, this::messageHandler));
  private MessageDispatcherImpl dispatcher;

  protected List<Bundle> drainPipes() {
    getTestDispatcher().drainSource();
    return getTestDispatcher().drainOutput();
  }

  protected boolean environmentIsEnabled() {
    return true;
  }

  protected MessageDispatcherImpl getReverseDispatcher() {
    getTestDispatcher();  // Ensure that the main pipe exists before doing the reverse.
    return getTestDispatcher(true);
  }

  protected void setTestDispatcher(MessageDispatcher dispatcher) {
    this.dispatcher = (MessageDispatcherImpl) dispatcher;
  }

  protected MessageDispatcherImpl getTestDispatcher() {
    dispatcher = Optional.ofNullable(dispatcher).orElseGet(() -> getTestDispatcher(false));
    return dispatcher;
  }

  protected MessageDispatcherImpl getTestDispatcher(boolean reversed) {
    MessageDispatcher dispatcher = getTestDispatcherCore(reversed);
    if (!reversed && !dispatcher.isActive()) {
      dispatcher.registerHandlers(messageHandlers);
      dispatcher.activate();
    }
    return (MessageDispatcherImpl) dispatcher;
  }

  /**
   * Get a message pipe as defined by the appropriate pipe type subclass.
   */
  protected abstract MessageDispatcher getTestDispatcherCore(boolean reversed);

  protected Object synchronizedReceive() throws InterruptedException {
    synchronized (this) {
      Object existing = receivedMessage.getAndSet(null);
      if (existing != null) {
        return existing;
      }
      wait(RECEIVE_TIMEOUT_MS);
      return receivedMessage.getAndSet(null);
    }
  }

  private <T> void defaultHandler(T message) {
    // Wrap the message in an AtomicReference as a signal that this was the default handler.
    messageHandler(new AtomicReference<>(message));
  }

  private <T> void messageHandler(T message) {
    synchronized (this) {
      Object previous = receivedMessage.getAndSet(message);
      assertNull(previous, "Unexpected previously received message");
      notify();
    }
  }

  private Object publishAndReceive(Bundle bundle) throws InterruptedException {
    assertNull(receivedMessage.get(), "expected null pre-receive message");
    getReverseDispatcher().publishBundle(bundle);
    return synchronizedReceive();
  }

  @AfterEach
  void resetForTest() {
    if (dispatcher != null) {
      dispatcher.resetForTest();
      dispatcher = null;
    }
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
    MessageDispatcher reversed = getReverseDispatcher();
    reversed.publish(new LocalnetModel());
    Object received = synchronizedReceive();
    assertTrue(received instanceof LocalnetModel, "Expected state update message");
  }

  /**
   * Test that received an unregistered message type (no handler) results in the default Object
   * handler being called.
   */
  @Test
  @SuppressWarnings("unchecked")
  void receiveDefaultMessage() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    MessageDispatcher reversed = getReverseDispatcher();
    reversed.publish(new LocalnetState());
    Object received = synchronizedReceive();
    // The default handler warps the received message in an AtomicReference just as a signal.
    assertTrue(received instanceof AtomicReference, "Expected default handler");
    Object receivedObject = ((AtomicReference<Object>) received).get();
    assertTrue(receivedObject instanceof LocalnetState, "Expected localnet message");
  }
}
