package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.parseJson;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.AtomicDouble;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.UdmiConfig;

/**
 * Base class for supporting a variety of messaging interfaces.
 */
public abstract class MessageBase extends ContainerBase implements MessagePipe {

  public static final String INVALID_ENVELOPE_KEY = "invalid";
  public static final int EXECUTION_THREADS = 4;
  public static final String ERROR_MESSAGE_MARKER = "error-mark";
  static final String TERMINATE_MARKER = "terminate";
  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final Set<Object> HANDLED_QUEUES = new HashSet<>();
  private static final long DEFAULT_POLL_TIME_SEC = 1;
  private static final long AWAIT_TERMINATION_SEC = 10;
  private final ExecutorService executor = Executors.newFixedThreadPool(EXECUTION_THREADS);
  private BlockingQueue<QueueEntry> sourceQueue;
  private Consumer<Bundle> dispatcher;
  private boolean activated;
  private final AtomicInteger publishCount = new AtomicInteger();
  private final AtomicDouble publishDuration = new AtomicDouble();

  /**
   * Combine two message configurations together (for applying defaults).
   */
  public static EndpointConfiguration combineConfig(EndpointConfiguration defaults,
      EndpointConfiguration defined) {
    EndpointConfiguration useDefaults = ofNullable(defaults).orElseGet(EndpointConfiguration::new);
    return ifNotNullGet(defined, () -> mergeObject(deepCopy(useDefaults), defined));
  }

  static Bundle extractBundle(String bundleString) {
    return ifNotNullGet(bundleString, b -> fromString(Bundle.class, b));
  }

  static String normalizeNamespace(String configSpace) {
    return ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  protected static String queueIdentifierStatic(BlockingQueue<QueueEntry> queue) {
    return format("%08x", Objects.hash(queue));
  }

  protected synchronized void accumulateDuration(Duration duration) {
    double seconds = duration.getSeconds() + duration.toMillisPart() / 1000.0;
    publishCount.incrementAndGet();
    publishDuration.addAndGet(seconds);
  }

  protected Bundle makeExceptionBundle(Envelope envelope, Exception exception) {
    Bundle bundle = new Bundle(envelope, exception);
    bundle.envelope.subType = SubType.EVENT;
    bundle.envelope.subFolder = SubFolder.ERROR;
    return bundle;
  }

  protected Bundle makeHelloBundle() {
    UdmiConfig udmiConfig = UdmiServicePod.getUdmiConfig(null);
    Bundle bundle = new Bundle(udmiConfig);
    bundle.envelope.subType = SubType.CONFIG;
    bundle.envelope.subFolder = SubFolder.UDMI;
    bundle.envelope.publishTime = new Date();
    return bundle;
  }

  protected String pipeId() {
    return "";
  }

  protected abstract void publishRaw(Bundle bundle);

  protected void pushQueueEntry(BlockingQueue<QueueEntry> queue, String stringBundle) {
    try {
      requireNonNull(stringBundle, "missing queue bundle");
      queue.put(new QueueEntry(grabExecutionContext(), stringBundle));
    } catch (Exception e) {
      throw new RuntimeException("While pushing queue entry", e);
    }
  }

  protected String queueIdentifier() {
    return queueIdentifierStatic(sourceQueue);
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
      sanitizeAttributeMap(attributesMap);
      envelope = convertTo(Envelope.class, attributesMap);
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

  protected void receiveMessage(Envelope envelope, Map<?, ?> messageMap) {
    receiveMessage(toStringMap(envelope), stringify(messageMap));
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
          if (TERMINATE_MARKER.equals(bundle.message)) {
            info("Terminating message loop %s", id);
            return;
          }
          envelope = bundle.envelope;
          debug("Processing %s %s/%s %s", this, envelope.subType, envelope.subFolder,
              envelope.transactionId);
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

  private void receiveBundle(Bundle bundle) {
    receiveBundle(stringify(bundle));
  }

  private void receiveBundle(String stringBundle) {
    ensureSourceQueue();
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

  private void sanitizeAttributeMap(Map<String, String> attributesMap) {
    String subFolderRaw = attributesMap.get(SUBFOLDER_PROPERTY_KEY);
    if (subFolderRaw == null) {
      // Do nothing!
    } else if (subFolderRaw.equals("")) {
      debug("Coerced empty subFolder to undefined");
      attributesMap.remove(SUBFOLDER_PROPERTY_KEY);
    } else {
      SubFolder subFolder = catchToElse(() -> SubFolder.fromValue(subFolderRaw), SubFolder.INVALID);
      if (!subFolder.value().equals(subFolderRaw)) {
        debug("Coerced subFolder " + subFolderRaw + " to " + subFolder.value());
        attributesMap.put(SUBFOLDER_PROPERTY_KEY, subFolder.value());
      }
    }

    String subTypeRaw = attributesMap.get(SUBTYPE_PROPERTY_KEY);
    if (subTypeRaw == null) {
      // Do nothing!
    } else if (subTypeRaw.equals("")) {
      debug("Coerced empty subType to undefined");
      attributesMap.remove(SUBTYPE_PROPERTY_KEY);
    } else if (!Strings.isNullOrEmpty(subTypeRaw)) {
      SubType subType = catchToElse(() -> SubType.fromValue(subTypeRaw), SubType.INVALID);
      if (!subType.value().equals(subTypeRaw)) {
        debug("Coerced subFolder " + subTypeRaw + " to " + subType.value());
        attributesMap.put(SUBTYPE_PROPERTY_KEY, subType.value());
      }
    }
  }

  private void shutdownExecutor() {
    debug("Shutdown of %s", this);
    executor.shutdown();
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    dispatcher = bundleConsumer;
    ensureSourceQueue();
    debug("Handling %s", this);
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
  public synchronized Entry<Integer, Double> extractDuration() {
    return new SimpleEntry<>(publishCount.getAndSet(0), publishDuration.getAndSet(0));
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

  public final void publish(Bundle bundle) {
    Instant start = Instant.now();
    try {
      publishRaw(bundle);
    } finally {
      accumulateDuration(Duration.between(start, Instant.now()));
    }
  }

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
    return format("%s%s %s => %s", getClass().getSimpleName(), pipeId(), queueIdentifier(),
        Objects.hash(dispatcher));
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
