package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;
import udmi.schema.MessageConfiguration;

/**
 * A MessagePipe implementation that only uses local resources (in-process). Useful for testing or
 * other internal coordination. Consists of a global namespace, each supporting a collection of
 * named pipes to be used, ultimately backed by a standard BlockingQueue.
 */
public class LocalMessagePipe extends MessageBase {

  private static final Map<String, LocalMessagePipe> GLOBAL_PIPES = new ConcurrentHashMap<>();
  public static final String DEFAULT_NAMESPACE = "default-namespace";

  private final Map<String, BlockingQueue<String>> scopedPipes = new ConcurrentHashMap<>();

  private final String sourceScope;
  private final String destinationScope;
  private final BlockingQueue<String> sourceQueue;
  private final BlockingQueue<String> destinationQueue;
  private Future<Void> sourceFuture;

  /**
   * Create a new local message pipe given a configuration bundle.
   */
  public LocalMessagePipe(MessageConfiguration config) {
    String namespace = normalizeNamespace(config.namespace);
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
   * Get a pipe from the global namespace. Only valid after the pipe in question has been
   * instantiated... this is not a factory!
   */
  public static LocalMessagePipe getPipeForNamespace(String namespace) {
    return GLOBAL_PIPES.get(normalizeNamespace(namespace));
  }

  @NotNull
  private static String normalizeNamespace(String configSpace) {
    return Optional.ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  public static BlockingQueue<String> getQueueForScope(String namespace, String scope) {
    return getPipeForNamespace(namespace).scopedPipes
        .computeIfAbsent(scope, key -> new LinkedBlockingDeque<>());
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  public static void resetForTest() {
    GLOBAL_PIPES.clear();
  }

  @Override
  public void activate() {
    checkState(sourceFuture == null, "pipe already activated");
    sourceFuture = handleQueue(sourceQueue);
  }

  public void drainSource() {
    drainQueue(sourceQueue, sourceFuture);
  }

  /**
   * Publish a message bundle to this pipe. Pushes it into the outgoing queue!
   */
  protected void publishBundle(Bundle messageBundle) {
    try {
      destinationQueue.put(stringify(messageBundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }
}
