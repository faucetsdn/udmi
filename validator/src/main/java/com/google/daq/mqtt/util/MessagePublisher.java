package com.google.daq.mqtt.util;

import static com.google.udmi.util.Common.getNamespacePrefix;
import static java.util.Optional.ofNullable;
import static udmi.schema.IotAccess.IotProvider.IMPLICIT;
import static udmi.schema.IotAccess.IotProvider.JWT;

import com.google.bos.iot.core.proxy.MqttPublisher;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.PubSubReflector;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.schema.Credential;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.SetupUdmiConfig;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {

  static String getRegistryId(ExecutionConfiguration config) {
    return getNamespacePrefix(config.udmi_namespace) + config.registry_id;
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
    IotProvider iotProvider = ofNullable(iotConfig.iot_provider).orElse(JWT);
    if (iotConfig.reflector_endpoint != null && iotProvider != IMPLICIT) {
      iotConfig.reflector_endpoint = null;
    }
    return switch (iotProvider) {
      case GREF -> PubSubReflector.from(iotConfig, messageHandler, errorHandler);
      case MQTT, JWT, GBOS -> MqttPublisher.from(iotConfig, messageHandler, errorHandler);
      case PUBSUB -> PubSubClient.from(iotConfig, messageHandler, errorHandler);
      default -> throw new RuntimeException("Unsupported iot provider " + iotProvider);
    };
  }

  String publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  void activate();

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
    throw new RuntimeException("getBridgeHost not implemented");
  }

  default Credential getCredential() {
    throw new RuntimeException("getCredential not implemented");
  }

  default String getSessionPrefix() {
    throw new RuntimeException("session prefix not implemented for " + getClass().getSimpleName());
  }

  /**
   * Speed of a query -- how long to wait before a timeout.
   */
  enum QuerySpeed {
    QUICK(1),
    SHORT(15),
    LONG(30),
    SLOW(90),
    ETERNITY(600);

    private final int seconds;

    QuerySpeed(int seconds) {
      this.seconds = seconds;
    }

    public int seconds() {
      return this.seconds;
    }
  }
}
