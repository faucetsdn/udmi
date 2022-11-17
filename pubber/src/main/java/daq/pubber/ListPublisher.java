package daq.pubber;

import com.google.udmi.util.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import udmi.schema.PubberConfiguration;

/**
 * Publishes message to an in-memory list.
 */
public class ListPublisher implements Publisher {

  private final ExecutorService publisherExecutor = Executors.newFixedThreadPool(1);
  private List<String> messages = new ArrayList<>();
  private String usePrefix;

  public ListPublisher(PubberConfiguration configuration, Consumer<Exception> onError) {
  }

  /**
   * Get messages that have been mocked-published.
   *
   * @return list of published messages
   */
  public synchronized List<String> getMessages() {
    List<String> returnMessages = messages;
    messages = new ArrayList<>();
    return returnMessages;
  }

  @Override
  public void setDeviceTopicPrefix(String deviceId, String topicPrefix) {
    usePrefix = topicPrefix + "/" + deviceId;
  }

  @Override
  public <T> void registerHandler(String deviceId, String topicSuffix, Consumer<T> handler,
      Class<T> messageType) {

  }

  @Override
  public void connect(String deviceId) {

  }

  @Override
  public void publish(String deviceId, String topicSuffix, Object message, Runnable callback) {
    String useTopic = usePrefix + "/" + topicSuffix;
    messages.add(getMessageString(deviceId, useTopic, message));
    publisherExecutor.submit(callback);
  }

  static String getMessageString(String deviceId, String topic, Object message) {
    return String.format("%s/%s/%s", deviceId, topic, JsonUtil.stringify(message));
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public void startupLatchWait(CountDownLatch configLatch, String message) {

  }

  @Override
  public void close() {

  }
}
