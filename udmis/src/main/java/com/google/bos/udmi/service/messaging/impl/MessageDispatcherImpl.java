package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.PUBLISH_STATS;
import static com.google.bos.udmi.service.messaging.impl.MessageBase.RECEIVE_STATS;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.ConfigUpdate;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.PipeStats;
import com.google.bos.udmi.service.messaging.ModelUpdate;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageBase.BundleException;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

/**
 * Implementation of the typed message dispatcher interface.
 */
public class MessageDispatcherImpl extends ContainerBase implements MessageDispatcher {

  private static final String DEFAULT_HANDLER = "event/null";
  private static final String EXCEPTION_KEY = "exception_handler";
  private static final Map<String, Class<?>> SPECIAL_CLASSES = ImmutableMap.of(
      DEFAULT_HANDLER, DEFAULT_CLASS,
      EXCEPTION_KEY, EXCEPTION_CLASS
  );
  private static final Map<Class<?>, SimpleEntry<SubType, SubFolder>> CLASS_TYPES = new HashMap<>();
  private static final BiMap<String, Class<?>> TYPE_CLASSES = HashBiMap.create();
  private static final long HANDLER_TIMEOUT_MS = 2000;
  private static final double LATENCY_WARNING_THRESHOLD = 1.0;
  private static final double SIZE_WARNING_THRESHOLD = 0.5;

  static {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
    registerMessageClass(SubType.STATE, SubFolder.UPDATE, StateUpdate.class);
    registerMessageClass(SubType.CONFIG, SubFolder.UPDATE, ConfigUpdate.class);
    registerMessageClass(SubType.MODEL, SubFolder.UPDATE, ModelUpdate.class);
  }

  private final MessagePipe messagePipe;
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  private final Map<Class<?>, Consumer<Object>> handlers = new ConcurrentHashMap<>();
  private final Map<Class<?>, AtomicInteger> handlerCounts = new ConcurrentHashMap<>();
  private final String projectId;
  private final ThreadLocal<Envelope> threadEnvelope = new ThreadLocal<>();

  /**
   * Create a new instance of the message dispatcher.
   */
  public MessageDispatcherImpl(EndpointConfiguration configuration) {
    super(configuration);
    messagePipe = MessagePipe.from(configuration);
    projectId = variableSubstitution(configuration.hostname, "project_id/hostname not defined");
  }

  @Nullable
  private static Object convertStrictOrObject(Class<?> handlerType, Object message) {
    try {
      return convertToStrict(handlerType, message);
    } catch (Exception e) {
      return toMap(message);
    }
  }

  private static String getMapKey(SubType subType, SubFolder subFolder) {
    SubType useType = ofNullable(subType).orElse(SubType.EVENT);
    return format("%s/%s", useType, subFolder);
  }

  private static Class<?> getMessageClass(SubType type, SubFolder folder) {
    String typeName = Common.capitalize(folder.value()) + Common.capitalize(type.value());
    String className = SystemState.class.getPackageName() + "." + typeName;
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  /**
   * Get the associated message class for the indicated envelope.
   */
  public static Class<?> getMessageClassFor(Envelope envelope, boolean allowDefault) {
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    checkState(allowDefault || TYPE_CLASSES.containsKey(mapKey), "missing class for " + mapKey);
    return TYPE_CLASSES.getOrDefault(mapKey, SPECIAL_CLASSES.getOrDefault(mapKey, DEFAULT_CLASS));
  }

  @NotNull
  private static SimpleEntry<SubType, SubFolder> getTypeFolderEntry(SubType type,
      SubFolder folder) {
    return new SimpleEntry<>(type, folder);
  }

  private static void registerHandlerType(SubType type, SubFolder folder) {
    Class<?> messageClass = getMessageClass(type, folder);
    registerMessageClass(type, folder, messageClass);
  }

  private static void registerMessageClass(SubType type, SubFolder folder, Class<?> messageClass) {
    String mapKey = getMapKey(type, folder);
    if (messageClass != null) {
      MessageDispatcherImpl.TYPE_CLASSES.put(mapKey, messageClass);
      MessageDispatcherImpl.CLASS_TYPES.put(messageClass, getTypeFolderEntry(type, folder));
    }
  }

  @VisibleForTesting
  protected void devNullHandler(Object message) {
  }

  @Override
  protected void periodicTask() {
    Map<String, PipeStats> countSum = messagePipe.extractStats();
    extractAndLog(countSum, RECEIVE_STATS);
    extractAndLog(countSum, PUBLISH_STATS);
  }

  private void executeHandler(Class<?> handlerType, Object messageObject) {
    try {
      handlers.get(handlerType).accept(messageObject);
      synchronized (handlerCounts) {
        handlerCounts.computeIfAbsent(handlerType, key -> new AtomicInteger()).incrementAndGet();
        handlerCounts.notify();
      }
    } catch (Exception e) {
      throw new RuntimeException("While evaluating handler type " + handlerType.getSimpleName(), e);
    }
  }

  private void extractAndLog(Map<String, PipeStats> countSum, String key) {
    PipeStats stats = countSum.get(key);
    double rate = stats.count / (double) periodicSec;
    double average = stats.latency / stats.count;
    String message = format("Pipe %s %s count %.3f/s latency %.03fs, queue %.03f",
        messagePipe, key, rate, average, stats.size);
    boolean asWarn = average >= LATENCY_WARNING_THRESHOLD || stats.size >= SIZE_WARNING_THRESHOLD;
    Consumer<String> logger = asWarn ? this::warn : this::debug;
    logger.accept(message);
  }

  private Envelope getThreadEnvelope() {
    return requireNonNull(threadEnvelope.get(), "thread envelope not defined");
  }

  /**
   * Set the message envelope to use for published messages from the current thread.
   */
  public void setThreadEnvelope(Envelope envelope) {
    Envelope previous = threadEnvelope.get();
    threadEnvelope.set(envelope);
    if (previous != null && envelope != null) {
      throw new RuntimeException("Overwriting existing thread envelope");
    }
  }

  private void processHandler(Envelope envelope, Class<?> handlerType, Object messageObject) {
    withEnvelopeFor(envelope, messageObject, () -> executeHandler(handlerType, messageObject));
  }

  /**
   * Process a received message.
   */
  public void processMessage(Envelope envelope, Object message) {
    Envelope savedEnvelope = threadEnvelope.get();
    try {
      threadEnvelope.set(null);
      processMessage(makeMessageBundle(envelope, message));
    } finally {
      threadEnvelope.set(savedEnvelope);
    }
  }

  private void processMessage(Bundle bundle) {
    Envelope envelope = Preconditions.checkNotNull(bundle.envelope, "bundle envelope is null");
    Object message = bundle.message;
    if (bundle.payload != null) {
      message = new BundleException((String) message, bundle.attributesMap, bundle.payload);
    }
    boolean isException = message instanceof Exception;
    Class<?> handlerType = isException ? EXCEPTION_CLASS : getMessageClassFor(envelope, true);
    try {
      handlers.computeIfAbsent(handlerType, key -> {
        notice("Defaulting messages of type/folder " + key.getName());
        return handlers.getOrDefault(DEFAULT_CLASS, this::devNullHandler);
      });
      Object messageObject = isException ? message : convertStrictOrObject(handlerType, message);
      if (messageObject instanceof Map) {
        handlerType = Object.class;
      }
      trace("Processing %s from %s in %s", handlerType.getSimpleName(), messagePipe, this);
      processHandler(envelope, handlerType, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message " + stringify(envelope), e);
    }
  }

  @Override
  public void activate() {
    super.activate();
    Consumer<Bundle> processMessage = this::processMessage;
    info(format("%s activating %s with %08x", this, messagePipe, Objects.hash(processMessage)));
    messagePipe.activate(processMessage);
  }

  @TestOnly
  public void awaitShutdown() {
    ((MessageBase) messagePipe).awaitShutdown();
  }

  /**
   * Drain the message pipe into a list.
   */
  @TestOnly
  public List<Bundle> drain() {
    List<Bundle> messages = new ArrayList<>();
    try {
      while (true) {
        Bundle message = ((MessageBase) messagePipe).poll();
        if (message == null) {
          return messages;
        }
        messages.add(message);
      }
    } catch (Exception e) {
      throw new RuntimeException("Exception during drain", e);
    }
  }

  @Override
  public MessageContinuation getContinuation(Object message) {
    Envelope messageEnvelope = messageEnvelopes.get(message);
    Envelope envelope = messageEnvelope == null ? getThreadEnvelope() : messageEnvelope;
    final Envelope continuationEnvelope =
        requireNonNull(deepCopy(envelope), "missing message envelope");

    return new MessageContinuation() {
      private boolean available = true;

      @Override
      public Envelope getEnvelope() {
        checkState(available, "message envelope already extracted");
        available = false;
        return continuationEnvelope;
      }

      @Override
      public void publish(Object message) {
        publishBundle(makeMessageBundle(continuationEnvelope, message));
      }
    };
  }

  @Override
  public int getHandlerCount(Class<?> clazz) {
    return handlerCounts.computeIfAbsent(clazz, key -> new AtomicInteger()).get();
  }

  @Override
  public boolean isActive() {
    return messagePipe.isActive();
  }

  /**
   * Make a new message bundle for the given object, inferring the type and folder from the class
   * itself (using the predefined lookup map).
   */
  public Bundle makeMessageBundle(Envelope envelope, Object message) {
    if (message instanceof Bundle || message == null) {
      return (Bundle) message;
    }

    Bundle bundle = new Bundle(deepCopy(envelope), message);

    if (message instanceof Exception || message instanceof String) {
      bundle.envelope.subType = SubType.EVENT;
      bundle.envelope.subFolder = SubFolder.ERROR;
      return bundle;
    }

    if (!(message instanceof Map)) {
      SimpleEntry<SubType, SubFolder> messageType = CLASS_TYPES.get(message.getClass());
      requireNonNull(messageType, "unknown message type for " + message.getClass());
      bundle.envelope.subType = messageType.getKey();
      bundle.envelope.subFolder = messageType.getValue();
    }
    return bundle;
  }

  @Override
  public void publish(Object message) {
    Bundle messageBundle = message instanceof Bundle ? (Bundle) message : makeMessageBundle(
        requireNonNull(getContinuation(message), "no continuation found for message").getEnvelope(),
        message);
    publishBundle(messageBundle);
  }

  /**
   * Publish the given bundle to the outgoing message pipe.
   */
  @VisibleForTesting
  public void publishBundle(Bundle bundle) {
    messagePipe.publish(bundle);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    debug("Registering handler for %s in %s", clazz.getSimpleName(), this);
    if (handlers.put(clazz, (Consumer<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + clazz.getName());
    }
  }

  @TestOnly
  public void resetForTest() {
    ((MessageBase) messagePipe).resetForTest();
  }

  @Override
  public void shutdown() {
    messagePipe.shutdown();
    super.shutdown();
  }

  public void terminate() {
    ((MessageBase) messagePipe).terminate();
  }

  @Override
  public String toString() {
    return format("dispatcher/%s", containerId);
  }

  /**
   * Wait for a message of the given handler type to be processed. Primarily for testing.
   */
  public void waitForMessageProcessed(Class<?> clazz) {
    synchronized (handlerCounts) {
      try {
        Instant endTime = Instant.now().plusMillis(HANDLER_TIMEOUT_MS);
        do {
          handlerCounts.wait(HANDLER_TIMEOUT_MS);
        } while (getHandlerCount(clazz) == 0 && Instant.now().isBefore(endTime));
      } catch (InterruptedException e) {
        throw new RuntimeException("While waiting for handler count update", e);
      }
    }
  }

  @Override
  public MessageContinuation withEnvelope(Envelope envelope) {
    return new MessageContinuation() {
      @Override
      public Envelope getEnvelope() {
        return envelope;
      }

      @Override
      public void publish(Object message) {
        publishBundle(makeMessageBundle(envelope, message));
      }
    };
  }

  /**
   * Execute the runnable with the envelope mapped for the message.
   */
  @VisibleForTesting
  public void withEnvelopeFor(Envelope envelope, Object message, Runnable run) {
    try {
      messageEnvelopes.put(message, envelope);
      setThreadEnvelope(envelope);
      run.run();
    } finally {
      messageEnvelopes.remove(message);
      setThreadEnvelope(null);
    }
  }

}