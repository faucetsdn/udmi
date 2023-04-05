package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
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

  private static final Map<String, LocalMessagePipe> GLOBAL_PIPES = new ConcurrentHashMap<>();

  private final Map<String, BlockingQueue<String>> scopedQueues = new ConcurrentHashMap<>();

  private final String namespace;
  private final String sourceScope;
  private final String destinationScope;
  private final BlockingQueue<String> destinationQueue;

  /**
   * Create a new local message pipe given a configuration bundle.
   */
  public LocalMessagePipe(MessageConfiguration config) {
    namespace = normalizeNamespace(config.namespace);
    checkState(!GLOBAL_PIPES.containsKey(namespace),
        "can not create duplicate pipe in namespace " + namespace);
    GLOBAL_PIPES.put(namespace, this);
    sourceScope = config.source;
    sourceQueue = getQueueForScope(namespace, sourceScope);
    destinationScope = config.destination;
    destinationQueue = getQueueForScope(namespace, destinationScope);
    info(String.format("Created local pipe from %s to %s", sourceScope, destinationScope));
  }

  /**
   * Create a new local message pipe that's possible the reverse of the original. This intentionally
   * skips the already-initialized checks that would prevent a normal pipe from being created
   * twice.
   */
  public LocalMessagePipe(LocalMessagePipe original, boolean reverse) {
    namespace = original.namespace;
    sourceScope = reverse ? original.destinationScope : original.sourceScope;
    sourceQueue = getQueueForScope(namespace, sourceScope);
    destinationScope = reverse ? original.sourceScope : original.destinationScope;
    destinationQueue = getQueueForScope(namespace, destinationScope);
    info(String.format("Created mirror pipe from %s to %s", sourceScope, destinationScope));
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  /**
   * Get a pipe from the global namespace. Only valid after the pipe in question has been
   * instantiated... this is not a factory!
   */
  public static LocalMessagePipe getPipeForNamespace(String namespace) {
    return GLOBAL_PIPES.get(normalizeNamespace(namespace));
  }

  public static void resetForTest() {
    GLOBAL_PIPES.clear();
  }

  /**
   * Publish a message bundle to this pipe. Simply pushes it into the outgoing queue!
   */
  protected void publishBundle(Bundle messageBundle) {
    try {
      destinationQueue.add(stringify(messageBundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }

  private BlockingQueue<String> getQueueForScope(String namespace, String scope) {
    checkNotNull(scope, "pipe scope is null");
    return getPipeForNamespace(namespace).scopedQueues
        .computeIfAbsent(scope, key -> new LinkedBlockingDeque<>());
  }

  @Override
  public List<Bundle> drainOutput() {
    ArrayList<String> drained = new ArrayList<>();
    destinationQueue.drainTo(drained);
    return drained.stream().map(this::extractBundle).collect(
        Collectors.toList());
  }
}
