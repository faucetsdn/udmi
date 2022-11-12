package daq.pubber;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import udmi.schema.PubberConfiguration;

/**
 * Encapsulation of a device connection.
 */
public class MqttDevice {

  static final String ATTACH_TOPIC = "attach";
  static final String CONFIG_TOPIC = "config";
  static final String ERRORS_TOPIC = "errors";
  static final String EVENTS_TOPIC = "events";
  static final String STATE_TOPIC = "state";

  private final String deviceId;
  private final MqttPublisher mqttPublisher;

  MqttDevice(PubberConfiguration configuration, Consumer<Exception> onError) {
    deviceId = configuration.deviceId;
    mqttPublisher = new MqttPublisher(configuration, onError);
    if (configuration.endpoint.topic_prefix != null) {
      mqttPublisher.setDeviceTopicPrefix(deviceId, configuration.endpoint.topic_prefix);
    }
  }

  MqttDevice(String deviceId, MqttDevice target) {
    this.deviceId = deviceId;
    mqttPublisher = target.mqttPublisher;
  }

  public <T> void registerHandler(String topicSuffix, Consumer<T> handler, Class<T> messageType) {
    mqttPublisher.registerHandler(deviceId, topicSuffix, handler, messageType);
  }

  public void connect() {
    mqttPublisher.connect(deviceId);
  }

  public void publish(String topicSuffix, Object message, Runnable callback) {
    mqttPublisher.publish(deviceId, topicSuffix, message, callback);
  }

  public boolean isActive() {
    return mqttPublisher.isActive();
  }

  public void startupLatchWait(CountDownLatch configLatch, String message) {
    mqttPublisher.startupLatchWait(configLatch, message);
  }

  public void close() {
    mqttPublisher.close();
  }
}
