package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.MessagePipe;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import udmi.schema.EndpointConfiguration;

/**
 * A MessagePipe implementation that only uses local resources (in-process). Useful for testing or
 * other internal coordination. Consists of a global namespace, each supporting a collection of
 * named pipes to be used, ultimately backed by a standard BlockingQueue.
 */
public class LocalMessagePipe extends MessageBase {

  private static final Map<String, Map<String, BlockingQueue<QueueEntry>>> NAMESPACES =
      new ConcurrentHashMap<>();

  private final String namespace;
  private final String sourceName;
  private final String destinationName;
  private final BlockingQueue<QueueEntry> destinationQueue;

  /**
   * Create a new local message pipe given a configuration bundle.
   */
  public LocalMessagePipe(EndpointConfiguration config) {
    namespace = normalizeNamespace(config.hostname);
    sourceName = config.recv_id;
    setSourceQueue(getQueueForScope(sourceName));
    destinationName = config.send_id;
    destinationQueue = getQueueForScope(destinationName);
    info(
        format("Created local pipe from %s to %s as %s", sourceName, destinationName, this));
  }

  public static MessagePipe fromConfig(EndpointConfiguration config) {
    return new LocalMessagePipe(config);
  }

  public static void resetForTestStatic() {
    NAMESPACES.clear();
  }

  private BlockingQueue<QueueEntry> getQueueForScope(String name) {
    checkNotNull(name, "pipe name is null");
    Map<String, BlockingQueue<QueueEntry>> namedQueues =
        NAMESPACES.computeIfAbsent(namespace, key -> new ConcurrentHashMap<>());
    return namedQueues.computeIfAbsent(name, trackedQueue(name));
  }

  @NotNull
  private Function<String, BlockingQueue<QueueEntry>> trackedQueue(String name) {
    return key -> new LinkedBlockingQueue<>(queueCapacity);
  }

  /**
   * Publish a message bundle to this pipe. Simply pushes it into the outgoing queue!
   */
  protected void publishRaw(Bundle bundle) {
    try {
      debug("Publishing bundle to %s", this);
      pushQueueEntry(destinationQueue, stringify(bundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }

  @Override
  public void resetForTest() {
    resetForTestStatic();
  }

  @Override
  public String toString() {
    String isActive = isActive() ? "*" : "O";
    return format("%s >-%s-> %s", super.toString(), isActive,
        queueIdentifierStatic(destinationQueue));
  }
}
