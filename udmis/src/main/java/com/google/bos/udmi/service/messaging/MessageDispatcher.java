package com.google.bos.udmi.service.messaging;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.function.Consumer;
import udmi.schema.EndpointConfiguration;

/**
 * Strongly typed interface for working with message pipes.
 */
public interface MessageDispatcher {

  /**
   * Marker class to indicate a defaulted unknown message type.
   */
  Class<?> DEFAULT_CLASS = Object.class;

  /**
   * Marker class to use for exception handling.
   */
  Class<?> EXCEPTION_CLASS = Exception.class;

  static MessageDispatcher from(EndpointConfiguration configuration) {
    return new MessageDispatcherImpl(MessagePipe.from(configuration));
  }

  /**
   * Static factory method for creating handler specifications.
   */
  static <T> HandlerSpecification messageHandlerFor(Class<T> clazz, Consumer<T> consumer) {
    return new HandlerSpecification(clazz, consumer);
  }

  void activate();

  boolean isActive();

  /**
   * Publish a message to the outgoing channel of this pipe. The type of the message object is
   * extracted at runtime and used to properly reconstruct on the receiving end.
   */
  void publish(Object message);


  void shutdown();

  /**
   * Return a count of the number of times the handler for the indicated class has been called.
   */
  int getHandlerCount(Class<?> clazz);

  /**
   * Register a class message handler with the dispatcher.
   */
  <T> void registerHandler(Class<T> targetClass, Consumer<T> handler);

  /**
   * Convenience function to register an entire collection of handler specifications.
   */
  default void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    ifNotNullThen(messageHandlers,
        () -> messageHandlers.forEach(handler -> handler.registerWith(this)));
  }

  /**
   * Represent a type-happy consumer into a more generic specification. No actual logic, just makes
   * calling code cleaner and less cluttered with java-type crazyness.
   */
  class HandlerSpecification extends SimpleEntry<Class<?>, Consumer<?>> {

    public <T> HandlerSpecification(Class<T> clazz, Consumer<T> handler) {
      super(clazz, handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void registerWith(MessageDispatcher dispatcher) {
      dispatcher.registerHandler((Class<T>) getKey(), (Consumer<T>) getValue());
    }
  }
}
