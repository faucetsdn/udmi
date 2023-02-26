package com.google.daq.mqtt.util;

import com.google.daq.mqtt.validator.Validator.MessageBundle;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {

  String publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  boolean isActive();

  MessageBundle takeNextMessage();
}
