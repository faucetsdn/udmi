package com.google.bos.udmi.service.messaging.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.LocalnetModel;
import udmi.schema.LocalnetState;

/**
 * Common classes and functions for working with UDMIS unit tests.
 */
public abstract class MessagePipeTestBase extends MessageTestBase {

  private static final long RECEIVE_TIMEOUT_MS = 1000;

  @NotNull
  public static MessageDispatcherImpl getDispatcherFor(EndpointConfiguration reversedTarget) {
    return (MessageDispatcherImpl) MessageDispatcher.from(reversedTarget);
  }

  protected boolean environmentIsEnabled() {
    return true;
  }

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
    if (reverse != null) {
      reverse.resetForTest();
      reverse = null;
    }
  }

  /**
   * Test that receiving a malformed bundle results in a received exception.
   */
  @Test
  void receiveException() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    Object received = publishAndReceive(makeExceptionBundle());
    assertTrue(received instanceof Exception, "Expected received exception");
  }

  @NotNull
  private static Bundle makeExceptionBundle() {
    Bundle bundle = new Bundle();
    bundle.envelope.subType = SubType.EVENT;
    bundle.envelope.subFolder = SubFolder.ERROR;
    return bundle;
  }

  /**
   * Test that a publish/receive pair results in the right type of object.
   */
  @Test
  void receiveMessage() throws InterruptedException {
    Assumptions.assumeTrue(environmentIsEnabled(), "environment is not enabled");
    MessageDispatcher reversed = getReverseDispatcher();
    reversed.activate();
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
