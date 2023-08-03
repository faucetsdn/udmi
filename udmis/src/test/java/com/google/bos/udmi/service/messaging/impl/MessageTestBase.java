package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.GeneralUtils;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import udmi.schema.EndpointConfiguration;
import udmi.schema.LocalnetModel;

/**
 * Simple base class for many kinds of message-base tests.
 */
public abstract class MessageTestBase extends MessageTestCore {

  protected final AtomicReference<Object> receivedMessage = new AtomicReference<>();
  protected final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::messageHandler),
      messageHandlerFor(LocalnetModel.class, this::messageHandler));
  protected MessageDispatcherImpl dispatcher;
  protected MessageDispatcherImpl reverse;

  protected MessageDispatcherImpl getReverseDispatcher() {
    getTestDispatcher();  // Ensure that the main pipe exists before doing the reverse.
    reverse = Optional.ofNullable(reverse).orElseGet(() -> getTestDispatcher(true));
    return reverse;
  }

  protected MessageDispatcherImpl getTestDispatcher() {
    dispatcher = Optional.ofNullable(dispatcher).orElseGet(() -> getTestDispatcher(false));
    return dispatcher;
  }

  protected MessageDispatcherImpl getTestDispatcher(boolean reversed) {
    EndpointConfiguration configuration = getMessageConfig(reversed);
    MessageDispatcher dispatcher = MessageDispatcher.from(configuration);
    // Don't activate reversed dispatchers b/c the caller might have special things they want to do!
    if (!reversed && !dispatcher.isActive()) {
      dispatcher.registerHandlers(messageHandlers);
      dispatcher.activate();
    }
    return (MessageDispatcherImpl) dispatcher;
  }

  protected void setTestDispatcher(MessageDispatcher dispatcher) {
    this.dispatcher = (MessageDispatcherImpl) dispatcher;
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

  @AfterEach
  public void resetAfter() {
    LocalMessagePipe.resetForTestStatic();
  }
}
