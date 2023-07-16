package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.getExceptionMessage;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.parseJson;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

  public static final String INVALID_ENVELOPE_KEY = "invalid";
  static final String TERMINATE_MARKER = "terminate";
  private static final String DEFAULT_NAMESPACE = "default-namespace";
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

  protected Bundle makeExceptionBundle(Envelope envelope, Exception exception) {
    Bundle bundle = new Bundle(envelope, exception);
    bundle.envelope.subFolder = SubFolder.ERROR;
    return bundle;
  }

  private void receiveBundle(Bundle bundle) {
    receiveBundle(stringify(bundle));
  }

  private void receiveBundle(String stringBundle) {
    try {
      ensureSourceQueue();
      sourceQueue.put(requireNonNull(stringBundle));
    } catch (Exception e) {
      throw new RuntimeException("While receiving bundle", e);
    }
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

  protected void setSourceQueue(BlockingQueue<String> queueForScope) {
    sourceQueue = queueForScope;
  }

  protected void terminateHandler() {
    debug("Terminating " + this);
    receiveBundle(new Bundle(TERMINATE_MARKER));
  }

  private void ensureSourceQueue() {
    if (sourceQueue == null) {
      sourceQueue = new LinkedBlockingDeque<>();
    }
  }

  @SuppressWarnings("unchecked")
  private Future<Void> handleQueue() {
    // Keep track of used queues to make sure there's no shenanigans during testing.
    if (!HANDLED_QUEUES.add(System.identityHashCode(sourceQueue))) {
      throw new IllegalStateException("Source queue handled multiple times!");
    }
    return (Future<Void>) executor.submit(this::messageLoop);
  }

  private void messageLoop() {
    try {
      while (true) {
        Envelope envelope = null;
        try {
          Bundle bundle = extractBundle(sourceQueue.take());
          if (bundle.message.equals(TERMINATE_MARKER)) {
            debug("Exiting %s", this);
            info("Message loop terminated");
            return;
          }
          envelope = bundle.envelope;
          debug(format("Processing %s/%s %s %s -> %s", bundle.envelope.subType, bundle.envelope.subFolder,
              bundle.envelope.transactionId, queueIdentifier(), dispatcher));
          dispatcher.accept(bundle);
        } catch (Exception e) {
          handleDispatchException(envelope, e);
        }
      }
    } catch (Exception loopException) {
      error("Message loop exception: " + friendlyStackTrace(loopException));
    }
  }

  private void handleDispatchException(Envelope envelope, Exception e) {
    try {
      error(format("Dispatch exception: " + friendlyStackTrace(e)));
      dispatcher.accept(makeExceptionBundle(envelope, e));
    } catch (Exception e2) {
      error(format("Exception dispatch: " + friendlyStackTrace(e2)));
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

  /**
   * Await the shutdown of the input handler.
   */
  public void awaitShutdown() {
    debug("Awaiting shutdown of %s", this);
    if (sourceFuture == null) {
      return;
    }
    try {
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

  public abstract void publish(Bundle bundle);

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
   * Terminate the output path.
   */
  public void terminate() {
    debug("Terminating %s", this);
    publish(new Bundle(TERMINATE_MARKER));
  }

  protected void receiveMessage(Envelope envelope, Map<?, ?> messageMap) {
    receiveMessage(toStringMap(envelope), stringify(messageMap));
  }

  protected void receiveMessage(Map<String, String> envelopeMap, Map<?, ?> messageMap) {
    receiveMessage(envelopeMap, stringify(messageMap));
  }

  protected void receiveMessage(Map<String, String> attributesMap, String messageString) {
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
      debug(format("Received %s/%s %s -> %s", bundle.envelope.subType, bundle.envelope.subFolder,
          bundle.envelope.transactionId, queueIdentifier()));
      receiveBundle(bundle);
    } catch (Exception e) {
      receiveException(attributesMap, messageString, e, null);
    }
  }

  @Override
  public String toString() {
    return format("MessagePipe %s", queueIdentifier());
  }

  private String queueIdentifier() {
    return format("%08x", Objects.hash(sourceQueue));
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
      this.envelope = Optional.ofNullable(envelope).orElseGet(Envelope::new);
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

  @TestOnly
  void resetForTest() {
    debug("Resetting %s", this);
  }
}
