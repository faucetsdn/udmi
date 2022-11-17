package daq.pubber;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import udmi.schema.PubberConfiguration;

/**
 * Encapsulation of a device connection.
 */
public class MqttDevice {

  public static final String TEST_PROJECT = "test-project";
  static final String ATTACH_TOPIC = "attach";
  static final String CONFIG_TOPIC = "config";
  static final String ERRORS_TOPIC = "errors";
  static final String EVENTS_TOPIC = "events";
  static final String STATE_TOPIC = "state";

  private final String deviceId;
  private final Publisher publisher;

  MqttDevice(PubberConfiguration configuration, Consumer<Exception> onError) {
    deviceId = configuration.deviceId;
    publisher = getPublisher(configuration, onError);
    if (configuration.endpoint.topic_prefix != null) {
      publisher.setDeviceTopicPrefix(deviceId, configuration.endpoint.topic_prefix);
    }
  }

  MqttDevice(String deviceId, MqttDevice target) {
    this.deviceId = deviceId;
    publisher = target.publisher;
  }

  Publisher getPublisher(PubberConfiguration configuration,
      Consumer<Exception> onError) {
    return TEST_PROJECT.equals(configuration.projectId) ? new ListPublisher(configuration, onError)
        : new MqttPublisher(configuration, onError);
  }

  public <T> void registerHandler(String topicSuffix, Consumer<T> handler, Class<T> messageType) {
    publisher.registerHandler(deviceId, topicSuffix, handler, messageType);
  }

  public void connect() {
    publisher.connect(deviceId);
  }

  public void publish(String topicSuffix, Object message, Runnable callback) {
    publisher.publish(deviceId, topicSuffix, message, callback);
  }

  public boolean isActive() {
    return publisher.isActive();
  }

  public void startupLatchWait(CountDownLatch configLatch, String message) {
    publisher.startupLatchWait(configLatch, message);
  }

  public void close() {
    publisher.close();
  }

  public ListPublisher getMockPublisher() {
    return (ListPublisher) publisher;
  }
}
