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

  /**
   * Get a version structure describing the cloud-side deployment info.
   */
  default SetupUdmiConfig getVersionInformation() {
    SetupUdmiConfig setupUdmiConfig = new SetupUdmiConfig();
    setupUdmiConfig.deployed_by = "Default implementation for " + this.getClass();
    return setupUdmiConfig;
  }
}
