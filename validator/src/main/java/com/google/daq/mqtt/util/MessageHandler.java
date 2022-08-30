package com.google.daq.mqtt.util;

import java.util.AbstractMap.SimpleEntry;
import java.util.function.BiConsumer;
import udmi.schema.Envelope;

/**
 * Interface for a message system that's very type-aware.
 */
public interface MessageHandler {

  <T> void registerHandler(Class<T> targetClass, HandlerConsumer<T> handlerConsumer);

  void messageLoop();

  void publishMessage(String deviceId, Object message);

  /**
   * Represent a type-happy consumer into a more generic specification.
   *
   * @param <T> message type consumed
   */
  interface HandlerConsumer<T> extends BiConsumer<Envelope, T> { }

  /**
   * Represent a type-happy consumer into a more generic specification.
   */
  class HandlerSpecification extends SimpleEntry<Class<?>, HandlerConsumer<?>> {
    public <T> HandlerSpecification(Class<T> clazz, HandlerConsumer<T> consumer) {
      super(clazz, consumer);
    }
  }

  static <T> HandlerSpecification handlerSpecification(Class<T> clazz, HandlerConsumer<T> func) {
    return new HandlerSpecification(clazz, func);
  }

}
