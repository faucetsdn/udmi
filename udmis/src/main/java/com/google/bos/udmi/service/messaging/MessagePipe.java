package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
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

  void registerHandler(Consumer<Bundle> handler, SubType type, SubFolder folder);

  void activate();

  void publish(Bundle message);

  class Bundle {

    public Map<String, String> attributes;
    Object message;
  }
}
