package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.bos.udmi.service.messaging.impl.FileMessagePipe;
import com.google.bos.udmi.service.messaging.impl.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.PubSubPipe;
import com.google.bos.udmi.service.messaging.impl.SimpleMqttPipe;
import com.google.bos.udmi.service.messaging.impl.TraceMessagePipe;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;

/**
 * Basic message pipe interface that logically supports an in-source and out-destination. The pipe
 * implementation itself sits between, so in the purest form would take messages from the input and
 * simply push them to the output. Additionally, it includes the facility to semantically distribute
 * messages to typed handlers based on the Java class.
 */
public interface MessagePipe {

  Map<Protocol, Function<EndpointConfiguration, MessagePipe>> IMPLEMENTATIONS = ImmutableMap.of(
      Protocol.LOCAL, LocalMessagePipe::fromConfig,
      Protocol.PUBSUB, PubSubPipe::fromConfig,
      Protocol.FILE, FileMessagePipe::fromConfig,
      Protocol.TRACE, TraceMessagePipe::fromConfig,
      Protocol.MQTT, SimpleMqttPipe::fromConfig);

  /**
   * MessagePipe factory given a message configuration blob.
   */
  static MessagePipe from(EndpointConfiguration config) {
    checkState(IMPLEMENTATIONS.containsKey(config.protocol),
        "unknown message transport type " + config.protocol);
    try {
      return IMPLEMENTATIONS.get(config.protocol).apply(config);
    } catch (Exception e) {
      throw new RuntimeException("While instantiating pipe of type " + config.protocol, e);
    }
  }

  /**
   * Activate the receive loop of the message handler. Usually after handlers are registered!
   */
  void activate(Consumer<Bundle> callback);

  /**
   * Check if this pipe has been activated (and is still active).
   */
  boolean isActive();

  /**
   * Publish an outgoing message bundle.
   */
  void publish(Bundle bundle);

  /**
   * Shutdown an active pipe so that it no longer processes received messages.
   */
  void shutdown();

  /**
   * Atomically extract a count/sum of message statistics.
   */
  Map<String, PipeStats> extractStats();

  /**
   * Simple class to hold pipe statistics values.
   */
  class PipeStats {
    public int count;
    public double latency;
    public double size;
  }
}