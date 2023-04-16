package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.JsonUtil.fromString;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.udmi.util.Common;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import udmi.schema.Envelope;

/**
 * Base class for supporting a variety of messaging interfaces.
 */
public abstract class MessageBase extends ContainerBase implements MessagePipe {

  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final String LOOP_EXIT_MARK = "loop-exit";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  BlockingQueue<String> sourceQueue;
  private Future<Void> sourceFuture;
  private Consumer<Bundle> dispatcher;

  static String normalizeNamespace(String configSpace) {
    return Optional.ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  void messageLoop(BlockingQueue<String> queue) {
    try {
      while (true) {
        try {
          final String bundleString;
          bundleString = queue.take();
          if (LOOP_EXIT_MARK.equals(bundleString)) {
            info("Message loop terminated");
            return;
          }
          Bundle bundle = extractBundle(bundleString);
          dispatcher.accept(bundle);
        } catch (Exception e) {
          dispatcher.accept(makeExceptionBundle(e));
        }
      }
    } catch (Exception loopException) {
      info("Message loop exception: " + Common.getExceptionMessage(loopException));
    }
  }

  private Bundle makeExceptionBundle(Exception e) {
    Bundle bundle = new Bundle();
    bundle.envelope = new Envelope();
    bundle.message = e;
    return bundle;
  }

  @SuppressWarnings("unchecked")
  protected Future<Void> handleQueue(BlockingQueue<String> queue) {
    return (Future<Void>) executor.submit(() -> messageLoop(queue));
  }

  public abstract void publishBundle(Bundle bundle);

  @Override
  public void activate(Consumer<Bundle> messageDispatcher) {
    dispatcher = messageDispatcher;
    checkState(sourceFuture == null, "pipe already activated");
    if (sourceQueue == null) {
      sourceQueue = new LinkedBlockingDeque<>();
    }
    sourceFuture = handleQueue(sourceQueue);
  }

  public abstract List<Bundle> drainOutput();

  public void drainSource() {
    drainQueue(sourceQueue, sourceFuture);
  }

  @Override
  public boolean isActive() {
    return sourceFuture != null && !sourceFuture.isDone();
  }

  /**
   * Simple wrapper for a message bundle, including envelope and message.
   */
  public static class Bundle {

    public Envelope envelope;
    public Object message;
  }

  Bundle extractBundle(String bundleString) {
    return fromString(Bundle.class, bundleString);
  }

  void drainQueue(BlockingQueue<String> queue, Future<Void> queueFuture) {
    try {
      queue.put(LOOP_EXIT_MARK);
      queueFuture.get();
    } catch (Exception e) {
      throw new RuntimeException("While draining queue", e);
    }
  }
}
