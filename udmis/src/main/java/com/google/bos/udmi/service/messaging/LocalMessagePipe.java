package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import udmi.schema.Envelope;
import udmi.schema.MessageConfiguration;

public class LocalMessagePipe extends MessageBase {

  private static final Map<String, BlockingQueue<Bundle>> GLOBAL_PIPES = new ConcurrentHashMap<>();

  private final String sourceScope;
  private final String destinationScope;
  private final BlockingQueue<Bundle> sourceQueue;
  private final BlockingQueue<Bundle> destinationQueue;

  public LocalMessagePipe(MessageConfiguration config) {
    sourceScope = checkNotNull(config.source, "pipe source is undefined");
    sourceQueue = getQueueForScope(sourceScope);
    destinationScope = checkNotNull(config.destination, "pipe destination is undefined");
    destinationQueue = getQueueForScope(destinationScope);
    info(String.format("Created local pipe from %s to %s", sourceScope, destinationScope));
  }

  public static BlockingQueue<Bundle> getQueueForScope(String scope) {
    return GLOBAL_PIPES.computeIfAbsent(scope, key -> new SynchronousQueue<>());
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  @Override
  public void activate() {
    processQueue(sourceQueue);
  }

  @Override
  public void publish(Envelope envelope, Object message) {
    try {
      destinationQueue.put(makeBundle(envelope, message));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }
}
