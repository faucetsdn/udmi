package com.google.daq.mqtt.util;

import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.util.function.Consumer;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {

  void publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  boolean isActive();

  void processMessage(Consumer<MessageBundle> validator);

  MessageBundle takeNextMessage();
}
