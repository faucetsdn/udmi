package com.google.daq.mqtt.util;

import com.google.daq.mqtt.validator.Validator.MessageBundle;
import udmi.schema.SetupUdmiConfig;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {

  String publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  boolean isActive();

  MessageBundle takeNextMessage(boolean enableTimeout);

  default SetupUdmiConfig getVersionInformation() {
    throw new RuntimeException("Not implemented for " + this.getClass());
  }
}
