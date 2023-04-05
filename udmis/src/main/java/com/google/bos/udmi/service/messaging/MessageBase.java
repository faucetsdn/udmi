package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.fromString;

import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

/**
 * Base class for supporting a variety of messaging interfaces.
 */
public abstract class MessageBase extends ComponentBase implements MessagePipe {

  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final String LOOP_EXIT_MARK = "loop-exit";
  private static final String DEFAULT_HANDLER = "event/null";
  private static final String EXCEPTION_HANDLER = "exception_handler";
  private static final Map<SimpleEntry<SubType, SubFolder>, Class<?>> SPECIAL_TYPES =
      ImmutableMap.of(getTypeFolderEntry(SubType.STATE, SubFolder.UPDATE), StateUpdate.class);
  private static final Map<Class<?>, SimpleEntry<SubType, SubFolder>> CLASS_TYPES = new HashMap<>();
  private static final BiMap<String, Class<?>> TYPE_CLASSES = HashBiMap.create();

  static {
    initializeHandlerTypes();
  }

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Map<String, MessageHandler<Object>> handlers = new HashMap<>();
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  private final Map<Class<?>, String> specialClasses = ImmutableMap.of(
      Object.class, DEFAULT_HANDLER,
      Exception.class, EXCEPTION_HANDLER
  );
  private Future<Void> sourceFuture;
  BlockingQueue<String> sourceQueue;

  /**
   * Make a new message bundle for the given object, inferring the type and folder from the class
   * itself (using the predefined lookup map).
   */
  public static Bundle makeMessageBundle(Object message) {
    if (message instanceof Bundle || message == null) {
      return (Bundle) message;
    }
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = new Envelope();
    SimpleEntry<SubType, SubFolder> messageType = CLASS_TYPES.get(message.getClass());
    checkNotNull(messageType, "type entry not found for " + message.getClass());
    bundle.envelope.subType = messageType.getKey();
    bundle.envelope.subFolder = messageType.getValue();
    return bundle;
  }

  static String normalizeNamespace(String configSpace) {
    return Optional.ofNullable(configSpace).orElse(DEFAULT_NAMESPACE);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, MessageHandler<T> handler) {
    String mapKey = specialClasses.getOrDefault(clazz, TYPE_CLASSES.inverse().get(clazz));
    if (handlers.put(mapKey, (MessageHandler<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + mapKey);
    }
  }

  @Override
  public void publish(Object message) {
    publishBundle(makeMessageBundle(message));
  }

  @Override
  public void activate() {
    checkState(sourceFuture == null, "pipe already activated");
    if (sourceQueue == null) {
      sourceQueue = new LinkedBlockingDeque<>();
    }
    sourceFuture = handleQueue(sourceQueue);
  }

  @Override
  public boolean isActive() {
    return sourceFuture != null && !sourceFuture.isDone();
  }

  @SuppressWarnings("unchecked")
  protected Future<Void> handleQueue(BlockingQueue<String> queue) {
    return (Future<Void>) executor.submit(() -> this.messageLoop(queue));
  }

  private void messageLoop(BlockingQueue<String> queue) {
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
          processMessage(bundle);
        } catch (Exception e) {
          processMessage(makeExceptionEnvelope(), EXCEPTION_HANDLER, e);
        }
      }
    } catch (Exception loopException) {
      info("Message loop exception: " + Common.getExceptionMessage(loopException));
    }
  }

  Bundle extractBundle(String bundleString) {
    return fromString(Bundle.class, bundleString);
  }

  public void drainSource() {
    drainQueue(sourceQueue, sourceFuture);
  }

  void drainQueue(BlockingQueue<String> queue, Future<Void> queueFuture) {
    try {
      queue.put(LOOP_EXIT_MARK);
      queueFuture.get();
    } catch (Exception e) {
      throw new RuntimeException("While draining queue", e);
    }
  }

  public abstract List<Bundle> drainOutput();

  private Envelope makeExceptionEnvelope() {
    return new Envelope();
  }

  private void ignoreMessage(Object message) {
  }

  void processMessage(Bundle bundle) {
    Envelope envelope = checkNotNull(bundle.envelope, "bundle envelope is null");
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      handlers.computeIfAbsent(mapKey, key -> {
        info("Defaulting messages of type/folder " + mapKey);
        return handlers.getOrDefault(DEFAULT_HANDLER, this::ignoreMessage);
      });
      Class<?> handlerType = TYPE_CLASSES.getOrDefault(mapKey, Object.class);
      Object messageObject = convertTo(handlerType, bundle.message);
      processMessage(envelope, mapKey, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  private void processMessage(Envelope envelope, String mapKey, Object messageObject) {
    try {
      messageEnvelopes.put(messageObject, envelope);
      handlers.get(mapKey).accept(messageObject);
    } finally {
      messageEnvelopes.remove(messageObject);
    }
  }

  protected abstract void publishBundle(Bundle messageBundle);

  public MessageContinuation getContinuation(Object message) {
    return new MessageContinuation(this, messageEnvelopes.get(message), message);
  }

  private static void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
    SPECIAL_TYPES.forEach(
        (entry, clazz) -> registerMessageClass(entry.getKey(), entry.getValue(), clazz));
  }

  private static String getMapKey(SubType subType, SubFolder subFolder) {
    SubType useType = Optional.ofNullable(subType).orElse(SubType.EVENT);
    return String.format("%s/%s", useType, subFolder);
  }

  private static void registerHandlerType(SubType type, SubFolder folder) {
    Class<?> messageClass = getMessageClass(type, folder);
    registerMessageClass(type, folder, messageClass);
  }

  private static void registerMessageClass(SubType type, SubFolder folder, Class<?> messageClass) {
    String mapKey = getMapKey(type, folder);
    if (messageClass != null) {
      TYPE_CLASSES.put(mapKey, messageClass);
      CLASS_TYPES.put(messageClass, getTypeFolderEntry(type, folder));
    }
  }

  @NotNull
  private static SimpleEntry<SubType, SubFolder> getTypeFolderEntry(SubType type,
      SubFolder folder) {
    return new SimpleEntry<>(type, folder);
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
   * Simple wrapper for a message bundle, including envelope and message.
   */
  public static class Bundle {

    public Envelope envelope;
    public Object message;
  }
}
