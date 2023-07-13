package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageBase.BundleException;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Base class for UDMIS components.
 */
public abstract class ProcessorBase extends ContainerBase {

  public static final Integer FUNCTIONS_VERSION_MIN = 9;
  public static final Integer FUNCTIONS_VERSION_MAX = 9;
  public static final String UDMI_VERSION = "1.4.1";
  private static final String REFLECT_REGISTRY = "UDMI-REFLECT";

  private final ImmutableList<HandlerSpecification> baseHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );

  protected MessageDispatcher dispatcher;

  /**
   * Create a new instance of the given target class with the provided configuration.
   */
  public static <T extends ProcessorBase> T create(Class<T> clazz, EndpointConfiguration config) {
    try {
      T object = clazz.getDeclaredConstructor().newInstance();
      object.dispatcher = MessageDispatcher.from(config);
      return object;
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName(), e);
    }
  }

  /**
   * The default message handler. Defaults to ignore unexpected message types, but can be overridden
   * to provide component-specific behavior.
   */
  protected void defaultHandler(Object defaultedMessage) {
  }

  /**
   * The default exception handler. Defaults to simply print out exceptions, but can be overridden
   * to provide component-specific behavior.
   */
  protected void exceptionHandler(Exception e) {
    error("Received processing exception: " + friendlyStackTrace(e));
    e.printStackTrace();
  }

  protected void reflectError(SubType subType, BundleException bundleException) {
    Bundle bundle = bundleException.bundle;
    Map<String, String> errorMap = bundle.attributesMap;
    errorMap.put("subType", subType.value());
    errorMap.computeIfAbsent("subFolder", k -> SubFolder.ERROR.value());
    ErrorMessage errorMessage = new ErrorMessage();
    errorMessage.error = (String) bundle.message;
    errorMessage.data = bundle.payload;
    errorMap.put("payload", encodeBase64(stringify(errorMessage)));
    error(format("Reflecting error %s/%s", errorMap.get("subType"), errorMap.get("subFolder")));
    reflectString(errorMap.get("deviceRegistryId"), stringify(errorMap));
  }

  protected void reflectMessage(Envelope envelope, String message) {
    try {
      checkState(envelope.payload == null, "envelope payload is not null");
      envelope.payload = encodeBase64(message);
      reflectString(envelope.deviceRegistryId, stringify(envelope));
    } finally {
      envelope.payload = null;
    }
  }

  private static void reflectString(String deviceRegistryId, String stringify) {
    ifNotNullThen(UdmiServicePod.<IotAccessBase>maybeGetComponent(IOT_ACCESS_COMPONENT),
        iotAccess -> iotAccess.sendCommand(REFLECT_REGISTRY, deviceRegistryId, null,
            stringify));
  }

  /**
   * Register default component handlers. Can be overridden to change underlying behavior.
   */
  protected void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    dispatcher.registerHandlers(messageHandlers);
  }

  /**
   * Register component specific handlers. Should be overridden by subclass to change behaviors.
   */
  protected void registerHandlers() {
  }

  /**
   * Activate this component.
   */
  public void activate() {
    info("Activating " + this.getSimpleName());
    if (dispatcher != null) {
      registerHandlers(baseHandlers);
      registerHandlers();
      dispatcher.activate();
    }
  }

  public int getMessageCount(Class<?> clazz) {
    return dispatcher.getHandlerCount(clazz);
  }

  /**
   * Shutdown the component.
   */
  public void shutdown() {
    if (dispatcher != null) {
      dispatcher.shutdown();
    }
  }

  static class ErrorMessage {

    public String error;
    public String data;
  }

  <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    dispatcher.registerHandler(clazz, handler);
  }

  void publish(Object message) {
    dispatcher.publish(message);
  }

  MessageContinuation getContinuation(Object message) {
    return dispatcher.getContinuation(message);
  }

  @TestOnly
  MessageDispatcher getDispatcher() {
    return dispatcher;
  }
}
