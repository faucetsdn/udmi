package udmi.lib.intf;

import java.util.function.Consumer;

/**
 * Basic interface for publishing/receiving messages.
 */
public interface Publisher {

  /**
   * Sets the device and prefix for published messages.
   *
   * @param deviceId    the device id to use
   * @param topicPrefix the topic prefix to use
   */
  void setDeviceTopicPrefix(String deviceId, String topicPrefix);

  /**
   * Register a handler for receiving a message.
   *
   * @param deviceId    device to handle
   * @param topicSuffix topic to handle
   * @param handler     handler to consume messages
   * @param messageType the type of messages
   * @param <T>         type of message
   */
  <T> void registerHandler(String deviceId, String topicSuffix, Consumer<T> handler,
      Class<T> messageType);

  /**
   * Connect the given device id.
   *
   * @param deviceId device to connect
   * @param clean    true to clean previous connections
   */
  void connect(String deviceId, boolean clean);

  void publish(String deviceId, String topicSuffix, Object message, Runnable callback);

  boolean isActive();

  void close();

  void shutdown();
}
