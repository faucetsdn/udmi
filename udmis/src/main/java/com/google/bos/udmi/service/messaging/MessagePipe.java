package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

/**
 * Basic message pipe interface that logically supports an in-source and out-destination. The pipe
 * implementation itself sits between, so in the purest form would take messages from the input and
 * simply push them to the output. Additionally, it includes the facility to semantically distribute
 * messages to typed handlers based on the Java class.
 */
public interface MessagePipe {

  Map<Transport, Function<MessageConfiguration, MessagePipe>> IMPLEMENTATIONS = ImmutableMap.of(
      Transport.LOCAL, LocalMessagePipe::from,
      Transport.MQTT, SimpleMqttPipe::from);

  /**
   * MessagePipe factory given a message configuration blob.
   */
  static MessagePipe from(MessageConfiguration config) {
    checkState(IMPLEMENTATIONS.containsKey(config.transport),
        "unknown message transport type " + config.transport);
    return IMPLEMENTATIONS.get(config.transport).apply(config);
  }

  /**
   * Static factory method for creating handler specifications.
   */
  static <T> HandlerSpecification messageHandlerFor(Class<T> clazz, MessageHandler<T> consumer) {
    return new HandlerSpecification(clazz, consumer);
  }

  /**
   * Activate the receive loop of the message handler. Usually after handlers are registered!
   */
  void activate();

  /**
   * Check if this pipe has been activated (and is still active).
   */
  boolean isActive();

  /**
   * Publish a message to the outgoing channel of this pipe.
   */
  void publish(Object message);

  <T> void registerHandler(Class<T> targetClass, MessageHandler<T> handler);

  /**
   * Convenience function to register an entire collection of handler specifications.
   */
  default void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    messageHandlers.forEach(handler -> handler.registerWith(this));
  }

  /**
   * Represent a type-happy consumer into a more generic functional specification.
   */
  interface MessageHandler<T> extends Consumer<T> {

  }

  /**
   * Represent a type-happy consumer into a more generic specification. No actual logic, just makes
   * calling code cleaner and less cluttered with java-type crazyness.
   */
  class HandlerSpecification extends SimpleEntry<Class<?>, MessageHandler<?>> {

    public <T> HandlerSpecification(Class<T> clazz, MessageHandler<T> handler) {
      super(clazz, handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void registerWith(MessagePipe pipe) {
      pipe.registerHandler((Class<T>) getKey(), (MessageHandler<T>) getValue());
    }
  }
}
