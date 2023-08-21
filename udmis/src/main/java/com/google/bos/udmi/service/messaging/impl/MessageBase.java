package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.parseJson;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Base class for supporting a variety of messaging interfaces.
 */
public abstract class MessageBase extends ContainerBase implements MessagePipe {

  public static final String INVALID_ENVELOPE_KEY = "invalid";
  static final String TERMINATE_MARKER = "terminate";
  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final Set<Object> HANDLED_QUEUES = new HashSet<>();
  private static final long DEFAULT_POLL_TIME_SEC = 1;
  private static final long AWAIT_TERMINATION_SEC = 10;
  public static final int EXECUTION_THREADS = 4;
  public static final String ERROR_MESSAGE_MARKER = "error-mark";

  private final ExecutorService executor = Executors.newFixedThreadPool(EXECUTION_THREADS);
  private BlockingQueue<QueueEntry> sourceQueue;
  private Consumer<Bundle> dispatcher;
  private int inCount;
  private int outCount;
  private boolean activated;

  /**
   * Combine two message configurations together (for applying defaults).
   */
  public static EndpointConfiguration combineConfig(EndpointConfiguration defaults,
      EndpointConfiguration defined) {
    EndpointConfiguration useDefaults = ofNullable(defaults).orElseGet(
        EndpointConfiguration::new);
    return ifNotNullGet(defined, () -> mergeObject(deepCopy(useDefaults), defined));
  }

  static Bundle extractBundle(String bundleString) {
    return ifNotNullGet(bundleString, b -> fromString(Bundle.class,  b));
  }

  static String normalizeNamespace(String configSpace) {
    return ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  protected Bundle makeExceptionBundle(Envelope envelope, Exception exception) {
    Bundle bundle = new Bundle(envelope, exception);
    bundle.envelope.subType = SubType.EVENT;
    bundle.envelope.subFolder = SubFolder.ERROR;
    return bundle;
  }

  protected void pushQueueEntry(BlockingQueue<QueueEntry> queue, String stringBundle) {
    try {
      requireNonNull(stringBundle, "missing queue bundle");
      queue.put(new QueueEntry(grabExecutionContext(), stringBundle));
    } catch (Exception e) {
      throw new RuntimeException("While pushing queue entry", e);
    }
  }

  protected void receiveMessage(Envelope envelope, Map<?, ?> messageMap) {
    receiveMessage(toStringMap(envelope), stringify(messageMap));
  }

  protected void receiveMessage(Map<String, String> envelopeMap, Map<?, ?> messageMap) {
    receiveMessage(envelopeMap, stringify(messageMap));
  }

  protected void receiveMessage(Map<String, String> attributesMap, String messageString) {
    grabExecutionContext();

    final Object messageObject;
    try {
      messageObject = parseJson(messageString);
    } catch (Exception e) {
      receiveException(attributesMap, messageString, e, SubFolder.ERROR);
      return;
    }
    final Envelope envelope;

    try {
      envelope = convertToStrict(Envelope.class, attributesMap);
    } catch (Exception e) {
      attributesMap.put(INVALID_ENVELOPE_KEY, "true");
      receiveException(attributesMap, messageString, e, null);
      return;
    }

    try {
      Bundle bundle = new Bundle(envelope, messageObject);
      debug("Received %s/%s -> %s %s", bundle.envelope.subType, bundle.envelope.subFolder,
          queueIdentifier(), bundle.envelope.transactionId);
      receiveBundle(bundle);
    } catch (Exception e) {
      receiveException(attributesMap, messageString, e, null);
    }
  }

  protected void setSourceQueue(BlockingQueue<QueueEntry> queueForScope) {
    sourceQueue = queueForScope;
  }

  protected void terminateHandlers() {
    debug("Terminating " + this);
    for (int i = 0; i < EXECUTION_THREADS; i++) {
      receiveBundle(new Bundle(TERMINATE_MARKER));
    }
  }

  private synchronized void ensureSourceQueue() {
    if (sourceQueue == null) {
      sourceQueue = new LinkedBlockingDeque<>();
    }
  }

  @Nullable
  private String getFromSourceQueue() throws InterruptedException {
    QueueEntry poll = sourceQueue.poll(DEFAULT_POLL_TIME_SEC, TimeUnit.SECONDS);
    ifNotNullThen(poll, p -> setExecutionContext(p.context));
    return ifNotNullGet(poll, p -> p.message);
  }

  private void handleDispatchException(Envelope envelope, Exception e) {
    try {
      error(format("Dispatch exception: " + friendlyStackTrace(e)));
      dispatcher.accept(makeExceptionBundle(envelope, e));
    } catch (Exception e2) {
      error(format("Exception dispatch: " + friendlyStackTrace(e2)));
    }
  }

  @SuppressWarnings("unchecked")
  private void handleQueue() {
    // Keep track of used queues to make sure there's no shenanigans during testing.
    if (!HANDLED_QUEUES.add(System.identityHashCode(sourceQueue))) {
      throw new IllegalStateException("Source queue handled multiple times!");
    }
    for (int i = 0; i < EXECUTION_THREADS; i++) {
      String id = format("%s:%02d", queueIdentifier(), i);
      executor.submit(() -> messageLoop(id));
    }
  }

  private void messageLoop(String id) {
    info("Starting message loop %s", id);
    while (true) {
      try {
        grabExecutionContext();
        Envelope envelope = null;
        try {
          final Instant before = Instant.now();
          Bundle bundle = extractBundle(getFromSourceQueue());
          if (bundle == null) {
            continue;
          }
          final Instant start = Instant.now();
          long waiting = Duration.between(before, start).getSeconds();
          debug("Processing waited %ds on message loop %s", waiting, id);
          if (bundle.message.equals(TERMINATE_MARKER)) {
            info("Terminating message loop %s", id);
            terminateHandlers();
            return;
          }
          debug("Handling message %d of %s", outCount++, this);
          envelope = bundle.envelope;
          debug("Processing %s/%s %s %s -> %s", envelope.subType, envelope.subFolder,
              envelope.transactionId, queueIdentifier(), dispatcher);
          if (ERROR_MESSAGE_MARKER.equals(envelope.transactionId)) {
            throw new RuntimeException("Exception due to test-induced error");
          }
          dispatcher.accept(bundle);
          long seconds = Duration.between(start, Instant.now()).getSeconds();
          debug("Processing took %ds for message loop %s", seconds, id);
        } catch (Exception e) {
          warn("Handling dispatch exception: " + friendlyStackTrace(e));
          handleDispatchException(envelope, e);
        }
      } catch (Exception loopException) {
        error("Message loop exception: " + friendlyStackTrace(loopException));
        error(stackTraceString(loopException));
      }
    }
  }

  private void shutdownExecutor() {
    debug("Shutdown of %s", this);
    executor.shutdown();
  }

  protected String queueIdentifier() {
    return queueIdentifier(sourceQueue);
  }

  protected static String queueIdentifier(BlockingQueue<QueueEntry> queue) {
    return format("%08x", Objects.hash(queue));
  }

  private void receiveBundle(Bundle bundle) {
    receiveBundle(stringify(bundle));
  }

  private void receiveBundle(String stringBundle) {
    ensureSourceQueue();
    debug("Received message %d on %s", inCount++, this);
    pushQueueEntry(sourceQueue, stringBundle);
  }

  private void receiveException(Map<String, String> attributesMap, String messageString,
      Exception e, SubFolder forceFolder) {
    Bundle bundle = new Bundle();
    bundle.message = friendlyStackTrace(e);
    bundle.payload = messageString;
    HashMap<String, String> mutableMap = new HashMap<>(attributesMap);
    bundle.attributesMap = mutableMap;
    ifNotNullThen(forceFolder, folder -> mutableMap.put(SUBFOLDER_PROPERTY_KEY, folder.value()));
    receiveBundle(stringify(bundle));
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    dispatcher = bundleConsumer;
    ensureSourceQueue();
    debug("Handling %s to %08x", this, Objects.hash(bundleConsumer));
    handleQueue();
    activated = true;
  }

  /**
   * Await the shutdown of the input handler.
   */
  public void awaitShutdown() {
    try {
      shutdownExecutor();
      debug("Awaiting termination of %s", this);
      executor.awaitTermination(AWAIT_TERMINATION_SEC, TimeUnit.SECONDS);
      activated = false;
      debug("Finished termination of %s", this);
    } catch (Exception e) {
      throw new RuntimeException("While awaiting termination", e);
    }
  }

  @Override
  public boolean isActive() {
    return activated;
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
      return ifNotNullGet(getFromSourceQueue(), MessageBase::extractBundle);
    } catch (Exception e) {
      throw new RuntimeException("While polling queue", e);
    }
  }

  public abstract void publish(Bundle bundle);

  @Override
  public void shutdown() {
    try {
      terminateHandlers();
      awaitShutdown();
    } catch (Exception e) {
      throw new RuntimeException("While processing shutdown", e);
    }
  }

  /**
   * Terminate the output path.
   */
  public void terminate() {
    terminateHandlers();
  }

  @Override
  public String toString() {
    return format("MessagePipe %s", queueIdentifier());
  }

  /**
   * Simple wrapper for a message bundle, including envelope and message.
   */
  public static class Bundle {

    public Envelope envelope;
    public Object message;
    public Map<String, String> attributesMap;
    public String payload;

    public Bundle() {
      this.envelope = new Envelope();
    }

    public Bundle(Object message) {
      this.message = message;
      this.envelope = new Envelope();
    }

    public Bundle(Envelope envelope, Object message) {
      this.envelope = ofNullable(envelope).orElseGet(Envelope::new);
      this.message = message;
    }
  }

  /**
   * Exception used for internal bundle handling.
   */
  public static class BundleException extends Exception {

    public Bundle bundle = new Bundle();

    /**
     * New instance.
     */
    public BundleException(String stringMessage, Map<String, String> attributesMap,
        String payload) {
      bundle.message = stringMessage;
      bundle.attributesMap = attributesMap;
      bundle.payload = payload;
    }
  }

  record QueueEntry(String context, String message) {

  }

  @TestOnly
  void resetForTest() {
    debug("Resetting %s", this);
  }
}
