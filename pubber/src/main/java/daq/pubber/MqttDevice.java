package daq.pubber;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import udmi.schema.PubberConfiguration;

public class MqttDevice {

  MqttPublisher mqttPublisher;

  public MqttDevice(PubberConfiguration configuration, Consumer<Exception> onError) {
  }

  /**
   * Register a message handler.
   *
   * @param <T>         Param of the message type
   * @param mqttTopic   Mqtt topic
   * @param handler     Message received handler
   * @param messageType Type of the message for this handler
   */
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(String mqttTopic, Consumer<T> handler, Class<T> messageType) {
  }

  public void connect(String deviceId) {
  }

  public void publish(String deviceId, String topicSuffix, Object message, Runnable callback) {
  }

  public boolean isActive() {
    return false;
  }

  public void startupLatchWait(CountDownLatch configLatch, String initial_config_sync) {
  }

  public void close() {
  }
}
