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

  MessageBundle takeNextMessage(QuerySpeed querySpeed);

  /**
   * Get a version structure describing the cloud-side deployment info.
   */
  default SetupUdmiConfig getVersionInformation() {
    SetupUdmiConfig setupUdmiConfig = new SetupUdmiConfig();
    setupUdmiConfig.deployed_by = "Default implementation for " + this.getClass();
    return setupUdmiConfig;
  }

  default String getBridgeHost() {
    throw new RuntimeException("Not implemented");
  }

  /**
   * Speed of a query -- how long to wait before a timeout.
   */
  enum QuerySpeed {
    QUICK(1),
    SHORT(15),
    LONG(30);

    private final int seconds;
    QuerySpeed(int seconds) {
      this.seconds = seconds;
    }

    public int seconds() {
      return this.seconds;
    }
  }
}
