package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.JsonUtil.convertTo;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.base.Preconditions;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
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
      DEFAULT_HANDLER, Object.class,
      EXCEPTION_KEY, Exception.class
  );
  private static final Map<Class<?>, SimpleEntry<SubType, SubFolder>> CLASS_TYPES = new HashMap<>();
  private static final BiMap<String, Class<?>> TYPE_CLASSES = HashBiMap.create();
  private static final Class<Object> DEFAULT_TYPE = Object.class;

  static {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
    registerMessageClass(SubType.STATE, SubFolder.UPDATE, StateUpdate.class);
  }

  private final MessagePipe messagePipe;
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  private final Map<Class<?>, Consumer<Object>> handlers = new HashMap<>();
  private final Map<Class<?>, AtomicInteger> handlerCounts = new ConcurrentHashMap<>();

  public MessageDispatcherImpl(MessagePipe messagePipe) {
    this.messagePipe = messagePipe;
  }

  private static String getMapKey(SubType subType, SubFolder subFolder) {
    SubType useType = Optional.ofNullable(subType).orElse(SubType.EVENT);
    return String.format("%s/%s", useType, subFolder);
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

  @NotNull
  private static SimpleEntry<SubType, SubFolder> getTypeFolderEntry(SubType type,
      SubFolder folder) {
    return new SimpleEntry<>(type, folder);
  }

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
    SimpleEntry<SubType, SubFolder> messageType =
        MessageDispatcherImpl.CLASS_TYPES.get(message.getClass());
    checkNotNull(messageType, "type entry not found for " + message.getClass());
    bundle.envelope.subType = messageType.getKey();
    bundle.envelope.subFolder = messageType.getValue();
    return bundle;
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

  private void ignoreMessage(Object message) {
  }

  private void processHandler(Envelope envelope, Class<?> handlerType, Object messageObject) {
    try {
      messageEnvelopes.put(messageObject, envelope);
      handlerCounts.computeIfAbsent(handlerType, key -> new AtomicInteger()).incrementAndGet();
      handlers.get(handlerType).accept(messageObject);
    } finally {
      messageEnvelopes.remove(messageObject);
    }
  }

  @Override
  public void activate() {
    messagePipe.activate(this::processMessage);
  }

  @TestOnly
  public List<Bundle> drainOutput() {
    return ((MessageBase) messagePipe).drainOutput();
  }

  @TestOnly
  public void drainSource() {
    ((MessageBase) messagePipe).drainSource();
  }

  @Override
  public int getHandlerCount(Class<?> clazz) {
    return handlerCounts.computeIfAbsent(clazz, key -> new AtomicInteger()).get();
  }

  @Override
  public boolean isActive() {
    return messagePipe.isActive();
  }

  @Override
  public void publish(Object message) {
    messagePipe.publish(makeMessageBundle(message));
  }

  @TestOnly
  public void publishBundle(Bundle bundle) {
    messagePipe.publish(bundle);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    if (handlers.put(clazz, (Consumer<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + clazz.getName());
    }
  }

  @TestOnly
  public void resetForTest() {
    ((MessageBase) messagePipe).resetForTest();
  }

  /**
   * Process a received message bundle.
   */
  void processMessage(Bundle bundle) {
    Envelope envelope = Preconditions.checkNotNull(bundle.envelope, "bundle envelope is null");
    boolean isException = bundle.message instanceof Exception;
    String mapKey = isException ? EXCEPTION_KEY : getMapKey(envelope.subType, envelope.subFolder);
    Class<?> handlerType =
        TYPE_CLASSES.getOrDefault(mapKey, SPECIAL_CLASSES.getOrDefault(mapKey, DEFAULT_TYPE));
    try {
      handlers.computeIfAbsent(handlerType, key -> {
        info("Defaulting messages of type/folder " + handlerType.getName());
        return handlers.getOrDefault(DEFAULT_TYPE, this::ignoreMessage);
      });
      Object messageObject = isException ? bundle.message : convertTo(handlerType, bundle.message);
      processHandler(envelope, handlerType, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

}