package com.google.daq.mqtt.util;

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
  interface HandlerConsumer<T> extends BiConsumer<T, Envelope> { }

  /**
   * Represent a type-happy consumer into a more generic specification.
   */
  class HandlerSpecification extends Pair<Class<?>, HandlerConsumer<?>> {
    public <T> HandlerSpecification(Class<T> valueOne, HandlerConsumer<T> valueTwo) {
      super(valueOne, valueTwo);
    }
  }

  static <T> HandlerSpecification handlerSpecification(Class<T> clazz, HandlerConsumer<T> func) {
    return new HandlerSpecification(clazz, func);
  }

}
