package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.JsonUtil.stringify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import udmi.schema.MessageConfiguration;

/**
 * A MessagePipe implementation that only uses local resources (in-process). Useful for testing or
 * other internal coordination. Consists of a global namespace, each supporting a collection of
 * named pipes to be used, ultimately backed by a standard BlockingQueue.
 */
public class LocalMessagePipe extends MessageBase {

  private static final Map<String, Map<String, BlockingQueue<String>>> NAMESPACES =
      new ConcurrentHashMap<>();

  private final String namespace;
  private final String sourceName;
  private final String destinationName;
  private final BlockingQueue<String> destinationQueue;

  /**
   * Create a new local message pipe given a configuration bundle.
   */
  public LocalMessagePipe(MessageConfiguration config) {
    namespace = normalizeNamespace(config.namespace);
    sourceName = config.source;
    sourceQueue = getQueueForScope(sourceName);
    destinationName = config.destination;
    destinationQueue = getQueueForScope(destinationName);
    info(String.format("Created local pipe from %s to %s", sourceName, destinationName));
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  public void resetForTest() {
    NAMESPACES.clear();
  }

  /**
   * Publish a message bundle to this pipe. Simply pushes it into the outgoing queue!
   */
  public void publishBundle(Bundle bundle) {
    try {
      destinationQueue.add(stringify(bundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }

  private BlockingQueue<String> getQueueForScope(String name) {
    checkNotNull(name, "pipe name is null");
    Map<String, BlockingQueue<String>> namedQueues =
        NAMESPACES.computeIfAbsent(namespace, key -> new ConcurrentHashMap<>());
    return namedQueues.computeIfAbsent(name, key -> new LinkedBlockingDeque<>());
  }

  @Override
  public List<Bundle> drainOutput() {
    ArrayList<String> drained = new ArrayList<>();
    destinationQueue.drainTo(drained);
    return drained.stream().map(this::extractBundle).collect(
        Collectors.toList());
  }
}
