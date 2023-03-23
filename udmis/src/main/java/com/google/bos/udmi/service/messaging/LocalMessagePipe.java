package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.JsonUtil.stringify;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import udmi.schema.MessageConfiguration;

public class LocalMessagePipe extends MessageBase {

  private static final Map<String, BlockingQueue<String>> GLOBAL_PIPES = new ConcurrentHashMap<>();

  private final String sourceScope;
  private final String destinationScope;
  private final BlockingQueue<String> sourceQueue;
  private final BlockingQueue<String> destinationQueue;

  public LocalMessagePipe(MessageConfiguration config) {
    sourceScope = checkNotNull(config.source, "pipe source is undefined");
    sourceQueue = getQueueForScope(sourceScope);
    destinationScope = checkNotNull(config.destination, "pipe destination is undefined");
    destinationQueue = getQueueForScope(destinationScope);
    info(String.format("Created local pipe from %s to %s", sourceScope, destinationScope));
  }

  public static BlockingQueue<String> getQueueForScope(String scope) {
    return GLOBAL_PIPES.computeIfAbsent(scope, key -> new SynchronousQueue<>());
  }

  static MessagePipe from(MessageConfiguration config) {
    return new LocalMessagePipe(config);
  }

  @Override
  public void activate() {
    handleQueue(sourceQueue);
  }

  @Override
  public void publish(Object message) {
    publishBundle(makeMessageBundle(message));
  }

  public void publishBundle(Bundle messageBundle) {
    try {
      destinationQueue.put(stringify(messageBundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to destination queue", e);
    }
  }
}
