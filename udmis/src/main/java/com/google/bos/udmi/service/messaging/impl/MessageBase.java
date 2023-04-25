package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.udmi.util.Common;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;

/**
 * Base class for supporting a variety of messaging interfaces.
 */
public abstract class MessageBase extends ContainerBase implements MessagePipe {

  private static final String DEFAULT_NAMESPACE = "default-namespace";
  static final String TERMINATE_MARKER = "terminate";

  private static final Set<Object> HANDLED_QUEUES = new HashSet<>();
  private static final long DEFAULT_POLL_TIME_SEC = 1;
  private static final long AWAIT_TERMINATION_SEC = 10;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private BlockingQueue<String> sourceQueue;
  private Future<Void> sourceFuture;
  private Consumer<Bundle> dispatcher;

  /**
   * Combine two message configurations together (for applying defaults).
   */
  public static EndpointConfiguration combineConfig(EndpointConfiguration defaults,
      EndpointConfiguration defined) {
    EndpointConfiguration useDefaults = Optional.ofNullable(defaults).orElseGet(
        EndpointConfiguration::new);
    return ifNotNullGet(defined, () -> mergeObject(deepCopy(useDefaults), defined));
  }

  static Bundle extractBundle(String bundleString) {
    return fromString(Bundle.class, bundleString);
  }

  static String normalizeNamespace(String configSpace) {
    return Optional.ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  protected long getPollTimeSec() {
    return DEFAULT_POLL_TIME_SEC;
  }

  @SuppressWarnings("unchecked")
  private Future<Void> handleQueue() {
    // Keep track of used queues to make sure there's no shenanigans during testing.
    if (!HANDLED_QUEUES.add(System.identityHashCode(sourceQueue))) {
      throw new IllegalStateException("Source queue handled multiple times!");
    }
    return (Future<Void>) executor.submit(this::messageLoop);
  }

  protected Bundle makeExceptionBundle(Exception e) {
    Bundle bundle = new Bundle(e);
    bundle.envelope.subFolder = SubFolder.ERROR;
    return bundle;
  }

  private void messageLoop() {
    try {
      while (true) {
        try {
          final String bundleString;
          bundleString = sourceQueue.take();
          Bundle bundle = extractBundle(bundleString);
          if (bundle.message.equals(TERMINATE_MARKER)) {
            debug("Exiting %s", this);
            info("Message loop terminated");
            return;
          }
          dispatcher.accept(bundle);
        } catch (Exception e) {
          dispatcher.accept(makeExceptionBundle(e));
        }
      }
    } catch (Exception loopException) {
      info("Message loop exception: " + Common.getExceptionMessage(loopException));
    }
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    dispatcher = bundleConsumer;
    checkState(sourceFuture == null, "pipe already activated");
    ensureSourceQueue();
    debug("Handling %s to %08x", this, Objects.hash(bundleConsumer));
    sourceFuture = handleQueue();
  }

  private void ensureSourceQueue() {
    if (sourceQueue == null) {
      sourceQueue = new LinkedBlockingDeque<>();
    }
  }

  /**
   * Await the shutdown of the input handler.
   */
  public void awaitShutdown() {
    try {
      debug("Awaiting shutdown of %s", this);
      sourceFuture.get(AWAIT_TERMINATION_SEC, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("While awaiting termination", e);
    } finally {
      sourceFuture = null;
    }
  }

  @Override
  public boolean isActive() {
    return sourceFuture != null && !sourceFuture.isDone();
  }

  /**
   * Poll for a single received message.
   */
  public Bundle poll() {
    try {
      if (isActive()) {
        throw new RuntimeException("Drain on active pipe");
      }
      debug("Polling on %s", this);
      return ifNotNullGet(sourceQueue.poll(getPollTimeSec(), TimeUnit.SECONDS),
          MessageBase::extractBundle);
    } catch (Exception e) {
      throw new RuntimeException("While polling queue", e);
    }
  }

  protected void terminateHandler() {
    debug("Terminating " + this);
    receiveBundle(new Bundle(TERMINATE_MARKER));
  }

  protected void setSourceQueue(BlockingQueue<String> queueForScope) {
    sourceQueue = queueForScope;
  }

  public abstract void publish(Bundle bundle);

  /**
   * Terminate the output path.
   */
  public void terminate() {
    debug("Terminating %s", this);
    publish(new Bundle(TERMINATE_MARKER));
  }

  @Override
  public void shutdown() {
    try {
      terminateHandler();
      awaitShutdown();
    } catch (Exception e) {
      throw new RuntimeException("While processing shutdown", e);
    }
  }

  /**
   * Simple wrapper for a message bundle, including envelope and message.
   */
  public static class Bundle {

    public Bundle() {
    }

    public Bundle(Object message) {
      this.message = message;
      this.envelope = new Envelope();
    }

    public Bundle(Envelope envelope, Object message) {
      this.envelope = envelope;
      this.message = message;
    }

    public Envelope envelope;
    public Object message;
  }

  protected void receiveBundle(Bundle bundle) {
    receiveBundle(stringify(bundle));
  }

  protected void receiveBundle(String stringBundle) {
    try {
      ensureSourceQueue();
      sourceQueue.put(stringBundle);
    } catch (Exception e) {
      throw new RuntimeException("While receiving bundle", e);
    }
  }

  @Override
  public String toString() {
    return format("MessagePipe %08x", Objects.hash(sourceQueue));
  }

  @TestOnly
  void resetForTest() {
    debug("Resetting %s", this);
  }
}
