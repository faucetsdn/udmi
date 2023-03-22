package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.function.BiConsumer;
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

  <T> void registerHandler(HandlerSpecification messageConsumer);

  void activate();

  void publish(Envelope envelope, Object message);

  /**
   * Represent a type-happy consumer into a more generic specification.
   *
   * @param <T> message type consumed
   */
  interface MessageConsumer<T> extends BiConsumer<Envelope, T> {

  }

  /**
   * Represent a type-happy consumer into a more generic specification.
   */
  class HandlerSpecification extends SimpleEntry<Class<?>, MessageConsumer<?>> {

    public <T> HandlerSpecification(Class<T> clazz, MessageConsumer<T> consumer) {
      super(clazz, consumer);
    }
  }

  static <T> HandlerSpecification handlerSpecification(Class<T> clazz, MessageConsumer<T> consumer) {
    return new HandlerSpecification(clazz, consumer);
  }

}
