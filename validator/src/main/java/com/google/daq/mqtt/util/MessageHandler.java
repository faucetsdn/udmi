package com.google.daq.mqtt.util;

import java.util.function.BiConsumer;
import udmi.schema.Envelope;

public interface MessageHandler {

  <T> void registerHandler(Class<T> targetClass, HandlerConsumer<T> handlerConsumer);

  void messageLoop();

  void publishMessage(String deviceId, Object message);

  interface HandlerConsumer<T> extends BiConsumer<T, Envelope> { }

  class HandlerSpecification extends Pair<Class<?>, HandlerConsumer<?>> {
    public <T> HandlerSpecification(Class<T> valueOne, HandlerConsumer<T> valueTwo) {
      super(valueOne, valueTwo);
    }
  }

  static <T> HandlerSpecification handlerSpecification(Class<T> clazz, HandlerConsumer<T> func) {
    return new HandlerSpecification(clazz, func);
  }

}
