package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

public class MessageDispatcherImpl extends ContainerBase implements MessageDispatcher {

  private static final Map<SimpleEntry<SubType, SubFolder>, Class<?>> SPECIAL_TYPES;
  private static final Map<Class<?>, String> specialClasses;
  private static final String DEFAULT_HANDLER = "event/null";
  static final String EXCEPTION_HANDLER = "exception_handler";
  private final MessagePipe messagePipe;
  private final Map<Object, Envelope> messageEnvelopes = new ConcurrentHashMap<>();
  static final Map<Class<?>, SimpleEntry<SubType, SubFolder>> CLASS_TYPES =
      new HashMap<Class<?>, SimpleEntry<SubType, SubFolder>>();
  static final BiMap<String, Class<?>> TYPE_CLASSES = HashBiMap.create();
  final Map<String, MessageHandler<Object>> handlers =
      new HashMap<String, MessageHandler<Object>>();

  static {
    SPECIAL_TYPES =
        ImmutableMap.of(getTypeFolderEntry(SubType.STATE, SubFolder.UPDATE),
            StateUpdate.class);
    specialClasses = ImmutableMap.of(
        Object.class, DEFAULT_HANDLER,
        Exception.class, EXCEPTION_HANDLER
    );
  }

  public MessageDispatcherImpl(MessagePipe messagePipe) {
    this.messagePipe = messagePipe;
  }

  @Override
  public void activate() {
    messagePipe.activate(this::processMessage);
  }

  void processMessage(Envelope envelope, String mapKey, Object messageObject) {
    try {
      messageEnvelopes.put(messageObject, envelope);
      handlers.get(mapKey).accept(messageObject);
    } finally {
      messageEnvelopes.remove(messageObject);
    }
  }

  @NotNull
  private static SimpleEntry<SubType, SubFolder> getTypeFolderEntry(SubType type,
      SubFolder folder) {
    return new SimpleEntry<>(type, folder);
  }

  private static void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
    MessageDispatcherImpl.SPECIAL_TYPES.forEach(
        (entry, clazz) -> registerMessageClass(entry.getKey(), entry.getValue(), clazz));
  }

  void processMessage(Bundle bundle) {
    Envelope envelope = Preconditions.checkNotNull(bundle.envelope, "bundle envelope is null");
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      handlers.computeIfAbsent(mapKey, key -> {
        info("Defaulting messages of type/folder " + mapKey);
        return handlers.getOrDefault(DEFAULT_HANDLER, this::ignoreMessage);
      });
      Class<?> handlerType = TYPE_CLASSES.getOrDefault(mapKey, Object.class);
      Object messageObject = JsonUtil.convertTo(handlerType, bundle.message);
      processMessage(envelope, mapKey, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  @Override
  public void publish(Object message) {
    messagePipe.publishBundle(makeMessageBundle(message));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, MessageHandler<T> handler) {
    String mapKey = specialClasses.getOrDefault(clazz, TYPE_CLASSES.inverse().get(clazz));
    if (handlers.put(mapKey, (MessageHandler<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + mapKey);
    }
  }

  private void ignoreMessage(Object message) {
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
    SimpleEntry<SubType, SubFolder> messageType = MessageDispatcherImpl.CLASS_TYPES.get(message.getClass());
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

}