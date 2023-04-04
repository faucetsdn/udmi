package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.JsonUtil.stringify;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
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
    sourceScope = checkNotNull(config.source, "pipe source is undefined");
    sourceQueue = getQueueForScope(namespace, sourceScope);
    destinationScope = checkNotNull(config.destination, "pipe destination is undefined");
    destinationQueue = getQueueForScope(namespace, destinationScope);
    info(String.format("Created local pipe from %s to %s", sourceScope, destinationScope));
  }

  /**
   * Create a new local message pipe that's possible the reverse of the original. This intentionally
   * avoids some of the duplicate checks that would prevent a normal pipe from being created twice.
   */
  public LocalMessagePipe(LocalMessagePipe original, boolean reverse) {
    namespace = original.namespace;
    sourceScope = reverse ? original.destinationScope : original.sourceScope;
    sourceQueue = getQueueForScope(namespace, sourceScope);
    destinationScope = reverse ? original.sourceScope : original.destinationScope;
    destinationQueue = getQueueForScope(namespace, destinationScope);
    info(String.format("Created mirror pipe from %s to %s", sourceScope, destinationScope));
  }

  /**
   * Get a pipe from the global namespace. Only valid after the pipe in question has been
   * instantiated... this is not a factory!
   */
  public static LocalMessagePipe getPipeForNamespace(String namespace) {
    return GLOBAL_PIPES.get(normalizeNamespace(namespace));
  }

  public static BlockingQueue<String> getQueueForScope(String namespace, String scope) {
    return getPipeForNamespace(namespace).scopedQueues
        .computeIfAbsent(scope, key -> new LinkedBlockingDeque<>());
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  public static void resetForTest() {
    GLOBAL_PIPES.clear();
  }

  public static LocalMessagePipe existing(MessageConfiguration configuration) {
    return GLOBAL_PIPES.get(normalizeNamespace(configuration.namespace));
  }

  /**
   * Publish a message bundle to this pipe. Pushes it into the outgoing queue!
   */
  protected void publishBundle(Bundle messageBundle) {
    try {
      destinationQueue.add(stringify(messageBundle));
      System.err.println("Publishing to queue " + Objects.hash(destinationQueue) + " size " + destinationQueue.size());
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }
}
