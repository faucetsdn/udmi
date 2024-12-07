package udmi.lib.base;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import udmi.lib.intf.Publisher;
import udmi.schema.Config;

/**
 * Publishes message to an in-memory list.
 */
public class ListPublisher implements Publisher {

  private final ExecutorService publisherExecutor = Executors.newFixedThreadPool(1);
  private List<String> messages = new ArrayList<>();
  private String usePrefix;
  private final Map<String, Entry<Consumer<Object>, Class<Object>>> handlers = new HashMap<>();

  public ListPublisher(Consumer<Exception> onError) {
  }

  public static String getMessageString(String deviceId, String topic, Object message) {
    return String.format("%s/%s/%s", deviceId, topic, JsonUtil.stringify(message));
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
    checkState(topicPrefix.endsWith(deviceId), "topic prefix does not end with device id");
    usePrefix = topicPrefix;
  }

  @Override
  public <T> void registerHandler(String deviceId, String topicSuffix,
      Consumer<T> handler, Class<T> messageType) {
    Consumer<Object> foo = (Consumer<Object>) handler;
    Class<Object> clazz = (Class<Object>) messageType;
    handlers.put(topicSuffix, new SimpleEntry<>(foo, clazz));
  }

  @Override
  public void unregisterHandlers() {
    handlers.clear();
  }

  @Override
  public void connect(String deviceId, boolean clean) {
    Consumer<Object> handler = handlers.get("config").getKey();
    handler.accept(new Config());
  }

  @Override
  public void publish(String deviceId, String topicSuffix, Object message, Runnable callback) {
    String useTopic = usePrefix + "/" + topicSuffix;
    messages.add(getMessageString(deviceId, useTopic, message));
    ifNotNullThen(callback, () -> publisherExecutor.submit(callback));
  }

  @Override
  public boolean isActive() {
    return !publisherExecutor.isShutdown();
  }

  @Override
  public void close() {

  }

  @Override
  public void shutdown() {
    if (isActive()) {
      publisherExecutor.shutdown();
    }
  }
}
