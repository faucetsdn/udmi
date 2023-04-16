package com.google.bos.udmi.service.messaging;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.function.Consumer;
import udmi.schema.MessageConfiguration;

public interface MessageDispatcher {

  static MessageDispatcher from(MessageConfiguration configuration) {
    return new MessageDispatcherImpl(MessagePipe.from(configuration));
  }

  void activate();

  /**
   * Publish a message to the outgoing channel of this pipe.
   */
  void publish(Object message);

  <T> void registerHandler(Class<T> targetClass, MessageHandler<T> handler);

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
    public <T> void registerWith(MessageDispatcher dispatcher) {
      dispatcher.registerHandler((Class<T>) getKey(), (MessageHandler<T>) getValue());
    }
  }
}
