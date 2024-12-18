package com.google.bos.udmi.service.messaging;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.function.Consumer;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

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

  static MessageDispatcher from(EndpointConfiguration config) {
    return ifNotNullGet(config, MessageDispatcherImpl::new);
  }

  static MessageDispatcher from(EndpointConfiguration from, EndpointConfiguration to) {
    throw new IllegalStateException("Not yet implemented");
  }

  /**
   * Static factory method for creating handler specifications.
   */
  static <T> HandlerSpecification messageHandlerFor(Class<T> clazz, Consumer<T> consumer) {
    return new HandlerSpecification(clazz, consumer);
  }

  void activate();

  /**
   * Make a new continuation with the given envelope.
   */
  MessageContinuation withEnvelope(Envelope envelope);

  /**
   * Get a message continuation for the received message.
   */
  MessageContinuation getContinuation(Object message);

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

  static RawString rawString(String rawString) {
    return new RawString(rawString);
  }

  /**
   * Marker class to indicate a string that should be applied/sent directly with no JSON or
   * map interpretation.
   */
  class RawString {
    public final String rawString;

    private RawString(String rawString) {
      this.rawString = rawString;
    }
  }
}
