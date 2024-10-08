package com.google.bos.udmi.service.messaging.impl;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.udmi.util.Common.RAWFOLDER_PROPERTY_KEY;
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
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.udmi.util.JsonUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  public static final String PUBLISH_STATS = "publish";
  public static final String RECEIVE_STATS = "receive";
  public static final double MESSAGE_WARN_THRESHOLD_SEC = 1.0;
  public static final double QUEUE_THROTTLE_MARK = 0.6;
  static final String TERMINATE_MARKER = "terminate";
  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final Set<Object> HANDLED_QUEUES = new HashSet<>();
  private static final long DEFAULT_POLL_TIME_SEC = 1;
  private static final long AWAIT_TERMINATION_SEC = 10;
  private static final int DEFAULT_CAPACITY = 1000;
  protected final int queueCapacity;
  protected final long publishDelaySec;
  private final ExecutorService executor = Executors.newFixedThreadPool(EXECUTION_THREADS);
  private final Entry<AtomicInteger, AtomicDouble> publishStats = makeEmptyStats();
  private final Entry<AtomicInteger, AtomicDouble> receiveStats = makeEmptyStats();
  private final AtomicBoolean subscriptionsThrottled = new AtomicBoolean();
  private BlockingQueue<QueueEntry> sourceQueue;
  private Consumer<Bundle> dispatcher;
  private boolean activated;

  /**
   * Default message base with basic default parameters.
   */
  public MessageBase() {
    queueCapacity = DEFAULT_CAPACITY;
    publishDelaySec = 0;
  }

  /**
   * Create a configuration based instance.
   */
  public MessageBase(EndpointConfiguration configuration) {
    super(configuration);
    queueCapacity = ofNullable(configuration.capacity).orElse(DEFAULT_CAPACITY);
    publishDelaySec = ofNullable(configuration.publish_delay_sec).orElse(0);
    if (publishDelaySec > 0) {
      warn("Artificially delaying message publishing by %ds", publishDelaySec);
    }
  }

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

  protected double getPublishQueueSize() {
    // Marker value for undefined.
    return -1.0;
  }

  protected Bundle makeExceptionBundle(Envelope envelope, Exception exception) {
    Bundle bundle = new Bundle(envelope, exception);
    bundle.envelope.subType = SubType.EVENTS;
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

  protected abstract void publishRaw(Bundle bundle);

  protected void pushQueueEntry(BlockingQueue<QueueEntry> queue, String stringBundle) {
    try {
      requireNonNull(stringBundle, "missing queue bundle");
      throttleQueue();
      randomlyFail();
      queue.add(new QueueEntry(grabExecutionContext(), stringBundle));
    } catch (Exception e) {
      throw new RuntimeException("While adding queue entry", e);
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
    final Instant start = Instant.now();
    try {
      receiveMessageRaw(attributesMap, messageString);
    } finally {
      accumulateStats(RECEIVE_STATS, receiveStats, Duration.between(start, Instant.now()));
    }
  }

  protected void receiveMessage(Map<String, String> envelope, Object object) {
    receiveMessage(envelope, stringify(object));
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

  protected void throttleQueue() {
    double receiveQueueSize = getReceiveQueueSize();
    double publishQueueSize = getPublishQueueSize();

    boolean blockReceiver = receiveQueueSize > QUEUE_THROTTLE_MARK;
    boolean blockPublisher = publishQueueSize > QUEUE_THROTTLE_MARK;
    boolean releaseReceiver = receiveQueueSize < QUEUE_THROTTLE_MARK / 2.0;
    boolean releasePublisher = publishQueueSize < QUEUE_THROTTLE_MARK / 2.0;

    String message = messageQueueMessage();
    if (blockReceiver || blockPublisher) {
      if (!subscriptionsThrottled.getAndSet(true)) {
        warn(message + ", crossing high-water mark");
      }
    } else if (releaseReceiver && releasePublisher) {
      if (subscriptionsThrottled.getAndSet(false)) {
        warn(message + ", below high-water mark");
      }
    }
  }

  private synchronized void accumulateStats(String statsBucket,
      Entry<AtomicInteger, AtomicDouble> stats,
      Duration duration) {
    double seconds = duration.getSeconds() + duration.toMillisPart() / 1000.0;
    stats.getKey().incrementAndGet();
    stats.getValue().addAndGet(seconds);
    if (seconds >= MESSAGE_WARN_THRESHOLD_SEC) {
      warn("Message %s took %.03fs", statsBucket, seconds);
    }
  }

  private synchronized void ensureSourceQueue() {
    if (sourceQueue == null) {
      notice(format("Creating new source queue %s with capacity %s", containerId, queueCapacity));
      sourceQueue = new LinkedBlockingQueue<>(queueCapacity);
    }
  }

  private PipeStats extractStat(Entry<AtomicInteger, AtomicDouble> stats, double size) {
    PipeStats pipeStats = new PipeStats();
    pipeStats.count = stats.getKey().getAndSet(0);
    pipeStats.latency = stats.getValue().getAndSet(0);
    pipeStats.size = size;
    return pipeStats;
  }

  @Nullable
  private String getFromSourceQueue() throws InterruptedException {
    QueueEntry poll = sourceQueue.poll(DEFAULT_POLL_TIME_SEC, TimeUnit.SECONDS);
    throttleQueue();
    ifNotNullThen(poll, p -> setExecutionContext(p.context));
    return ifNotNullGet(poll, p -> p.message);
  }

  private double getReceiveQueueSize() {
    return ofNullable(sourceQueue).map(Collection::size).orElse(0) / (double) queueCapacity;
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

  private Entry<AtomicInteger, AtomicDouble> makeEmptyStats() {
    return new SimpleEntry<>(new AtomicInteger(), new AtomicDouble());
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
          trace("Processing waited %ds on message loop %s", waiting, id);
          if (TERMINATE_MARKER.equals(bundle.message)) {
            info("Terminating message loop %s", id);
            return;
          }
          envelope = bundle.envelope;
          trace("Processing message loop %s %s/%s %s", id, envelope.subType, envelope.subFolder,
              envelope.transactionId);
          if (ERROR_MESSAGE_MARKER.equals(envelope.transactionId)) {
            throw new RuntimeException("Exception due to test-induced error");
          }
          dispatcher.accept(bundle);
          long seconds = Duration.between(start, Instant.now()).getSeconds();
          trace("Processing took %ds for message loop %s", seconds, id);
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

  private String messageQueueMessage() {
    double receiveQueue = getReceiveQueueSize();
    double publishQueue = getPublishQueueSize();
    return format("Message queue %s at %.03f/%.03f", containerId, receiveQueue, publishQueue);
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

  private void receiveMessageRaw(Map<String, String> attributesMap, String messageString) {
    final Object messageObject;
    try {
      messageObject = parseJson(messageString);
    } catch (Exception e) {
      receiveException(attributesMap, messageString, e, SubFolder.ERROR);
      // TODO: Don't make this an error!
    }
    final Envelope envelope;

    try {
      sanitizeAttributeMap(attributesMap);
      envelope = convertTo(Envelope.class, attributesMap);
    } catch (Exception e) {
      attributesMap.put(INVALID_ENVELOPE_KEY, friendlyStackTrace(e));
      receiveException(attributesMap, messageString, e, null);
      return;
    }

    try {
      Bundle bundle = new Bundle(envelope, messageObject);
      debug("Received %s %s/%s -> %s %s", bundle.envelope.deviceRegistryId,
          bundle.envelope.subType, bundle.envelope.subFolder, queueIdentifier(),
          bundle.envelope.transactionId);
      receiveBundle(bundle);
    } catch (Exception e) {
      receiveException(attributesMap, messageString, e, null);
    }
  }

  private void sanitizeAttributeMap(Map<String, String> attributesMap) {
    String subFolderRaw = attributesMap.get(SUBFOLDER_PROPERTY_KEY);
    String rawFolder = attributesMap.get(RAWFOLDER_PROPERTY_KEY);
    checkState(isNull(rawFolder) || "invalid".equals(subFolderRaw),
        "found unexpected rawFolder " + rawFolder);
    if (subFolderRaw == null) {
      // Do nothing!
    } else if (subFolderRaw.equals("")) {
      debug("Coerced empty subFolder to undefined");
      attributesMap.remove(SUBFOLDER_PROPERTY_KEY);
    } else {
      SubFolder subFolder = catchToElse(() -> SubFolder.fromValue(subFolderRaw), SubFolder.INVALID);
      if (!subFolder.value().equals(subFolderRaw)) {
        trace("Coerced subFolder " + subFolderRaw + " to " + subFolder.value());
        attributesMap.put(RAWFOLDER_PROPERTY_KEY, subFolderRaw);
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
        trace("Coerced subType " + subTypeRaw + " to " + subType.value());
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
    debug("Activating message pipe %s as %s => %s", containerId, queueIdentifier(),
        Objects.hash(dispatcher));
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
  public synchronized Map<String, PipeStats> extractStats() {
    double receiveQueue = getReceiveQueueSize();
    double publishQueue = getPublishQueueSize();
    if (subscriptionsThrottled.get()) {
      warn(messageQueueMessage() + ", currently paused");
    }
    return ImmutableMap.of(
        RECEIVE_STATS, extractStat(receiveStats, receiveQueue),
        PUBLISH_STATS, extractStat(publishStats, publishQueue));
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

  @Override
  public final void publish(Bundle bundle) {
    Instant start = Instant.now();
    try {
      publishRaw(bundle);
    } finally {
      Duration between = Duration.between(start, Instant.now());
      accumulateStats(PUBLISH_STATS, publishStats, between);
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
    return containerId;
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

    public Bundle(Map<String, String> attributes, Object message) {
      this.attributesMap = attributes;
      this.message = message;
    }

    /**
     * Get the actual send bytes for this bundle, either from raw payload or message object.
     */
    public byte[] sendBytes() {
      checkState(message != null || payload != null, "no message or payload");
      checkState(message == null || payload == null, "both message and payload");
      return ofNullable(message).map(JsonUtil::stringifyTerse).orElse(payload).getBytes();
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
