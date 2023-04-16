package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.common.collect.ImmutableMap;
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
   * Activate the receive loop of the message handler. Usually after handlers are registered!
   */
  void activate(Consumer<Bundle> callback);

  /**
   * Check if this pipe has been activated (and is still active).
   */
  boolean isActive();

  void publishBundle(Bundle bundle);
}
