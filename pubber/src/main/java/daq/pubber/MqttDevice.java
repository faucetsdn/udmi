package daq.pubber;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.udmi.util.CertManager;
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
  private final CertManager certManager;

  MqttDevice(PubberConfiguration configuration, Consumer<Exception> onError,
      CertManager certManager) {
    this.certManager = certManager;
    deviceId = configuration.deviceId;
    publisher = getPublisher(configuration, onError);
    ifNotNullThen(configuration.endpoint.topic_prefix,
        prefix -> publisher.setDeviceTopicPrefix(deviceId, prefix));
  }

  MqttDevice(String deviceId, MqttDevice target) {
    this.deviceId = deviceId;
    publisher = target.publisher;
    certManager = null;
  }

  Publisher getPublisher(PubberConfiguration configuration,
      Consumer<Exception> onError) {
    return TEST_PROJECT.equals(configuration.iotProject) ? new ListPublisher(configuration, onError)
        : new MqttPublisher(configuration, onError, certManager);
  }

  public <T> void registerHandler(String topicSuffix, Consumer<T> handler, Class<T> messageType) {
    publisher.registerHandler(deviceId, topicSuffix, handler, messageType);
  }

  public void connect() {
    publisher.connect(deviceId, true);
  }

  public void connect(String targetId) {
    publisher.connect(targetId, false);
  }

  public void publish(String deviceId, String topicSuffix, Object message, Runnable callback) {
    publisher.publish(deviceId, topicSuffix, message, callback);
  }

  public boolean isActive() {
    return publisher.isActive();
  }

  public void close() {
    publisher.close();
  }

  public void shutdown() {
    publisher.shutdown();
  }

  public ListPublisher getMockPublisher() {
    return (ListPublisher) publisher;
  }
}
