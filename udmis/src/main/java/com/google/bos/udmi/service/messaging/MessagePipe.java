package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.schema.Envelope;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

public interface MessagePipe {

  Map<Transport, Function<MessageConfiguration, MessagePipe>> IMPLEMENTATIONS = ImmutableMap.of(
      Transport.LOCAL, LocalMessagePipe::from);

  static MessagePipe from(MessageConfiguration config) {
    checkState(IMPLEMENTATIONS.containsKey(config.transport),
        "unknown message transport type " + config.transport);
    return IMPLEMENTATIONS.get(config.transport).apply(config);
  }

  <T> void registerHandler(Class<T> targetClass, MessageHandler<T> handler);

  void activate();

  void publish(Object message);

  Envelope getEnvelopeFor(Object message);

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

  /**
   * Static factory method for creating handler specifications.
   */
  static <T> HandlerSpecification messageHandlerFor(Class<T> clazz, MessageHandler<T> consumer) {
    return new HandlerSpecification(clazz, consumer);
  }

  /**
   * Convenience function to register an entire collection of handler specifications.
   */
  default void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    messageHandlers.forEach(handler -> handler.registerWith(this));
  }
}
