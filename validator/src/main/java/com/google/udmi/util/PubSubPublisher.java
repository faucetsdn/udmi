package com.google.udmi.util;

import com.google.daq.mqtt.util.MessagePublisher;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.schema.ExecutionConfiguration;

public class PubSubPublisher {

  public static MessagePublisher from(ExecutionConfiguration iotConfig,
      BiConsumer<String, String> messageHandler, Consumer<Throwable> errorHandler) {
    throw new RuntimeException("Not yet implemented");
  }
}
