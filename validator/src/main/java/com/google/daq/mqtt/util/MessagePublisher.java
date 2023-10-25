package com.google.daq.mqtt.util;

import com.google.bos.iot.core.proxy.MqttPublisher;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess;
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
   * Factory to create an instance with the given provider and handlers.
   *
   * @param iotConfig      configuration to use
   * @param messageHandler received message handler
   * @param errorHandler   error/exception handler
   */
  static MessagePublisher from(ExecutionConfiguration iotConfig,
      BiConsumer<String, String> messageHandler, Consumer<Throwable> errorHandler) {
    if (IotAccess.IotProvider.PUBSUB == iotConfig.iot_provider) {
      return PubSubClient.from(iotConfig, messageHandler, errorHandler);
    }
    return MqttPublisher.from(iotConfig, messageHandler, errorHandler);
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
