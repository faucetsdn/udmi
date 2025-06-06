package udmi.lib.base;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.util.Objects.requireNonNull;
import static udmi.lib.base.MqttPublisher.TEST_PREFIX;

import com.google.udmi.util.CertManager;
import java.util.function.Consumer;
import udmi.lib.intf.Publisher;
import udmi.schema.EndpointConfiguration;

/**
 * Encapsulation of a device connection.
 */
public class MqttDevice {

  public static final String TEST_PROJECT = "test-project";
  public static final String ATTACH_TOPIC = "attach";
  public static final String CONFIG_TOPIC = "config";
  public static final String ERRORS_TOPIC = "errors";
  public static final String EVENTS_TOPIC = "events";
  public static final String STATE_TOPIC = "state";

  private final String deviceId;
  private final Publisher publisher;
  private final CertManager certManager;
  private final boolean msTimestamp;

  /**
   * Builds a MQTT device.
   */
  public MqttDevice(EndpointConfiguration configuration, Consumer<Exception> onError,
      CertManager certManager, boolean msTimestamp) {
    this.certManager = certManager;
    this.msTimestamp = msTimestamp;
    deviceId = requireNonNull(configuration.deviceId, "deviceId not specified");
    publisher = getPublisher(configuration, onError);
    ifNotNullThen(configuration.topic_prefix, p -> publisher.setDeviceTopicPrefix(deviceId, p));
  }

  /**
   * Constructs a new MqttDevice with the specified device ID and copies the publisher from the
   * given target device.
   * The certificate manager is set to null for this device.
   *
   * @param deviceId the unique identifier for the device
   * @param target   the MqttDevice to copy the publisher from
   */
  public MqttDevice(String deviceId, MqttDevice target) {
    this.deviceId = deviceId;
    publisher = target.publisher;
    certManager = null;
    msTimestamp = false;
  }

  Publisher getPublisher(EndpointConfiguration configuration, Consumer<Exception> onError) {
    return TEST_PREFIX.equals(configuration.topic_prefix)
        ? new ListPublisher(onError)
        : new MqttPublisher(configuration, onError, certManager, msTimestamp);
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
