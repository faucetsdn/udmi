package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEVICE_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.messaging.impl.MessageBase;
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
import udmi.schema.PointsetEvent;

/**
 * Base class for UDMIS components.
 */
public abstract class ProcessorBase extends ContainerBase {

  public static final Integer FUNCTIONS_VERSION_MIN = 9;
  public static final Integer FUNCTIONS_VERSION_MAX = 9;
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

  private static void reflectString(String deviceRegistryId, String stringify) {
    ifNotNullThen(UdmiServicePod.<IotAccessBase>maybeGetComponent(IOT_ACCESS_COMPONENT),
        iotAccess -> iotAccess.sendCommand(REFLECT_REGISTRY, deviceRegistryId, null,
            stringify));
  }

  /**
   * The default message handler. Defaults to ignore unexpected message types, but can be overridden
   * to provide component-specific behavior.
   */
  protected void defaultHandler(Object defaultedMessage) {
  }

  /**
   * Processing exception handler.
   */
  protected void exceptionHandler(Exception e) {
    if (e instanceof BundleException bundleException) {
      reflectError(getExceptionSubType(), bundleException);
      return;
    }
    Envelope envelope = getContinuation(e).getEnvelope();
    String message = e.getMessage();
    error(format("Received message exception: %s", message));
    String payload = friendlyStackTrace(e);
    BundleException bundleException = new BundleException(message, toStringMap(envelope), payload);
    reflectError(SubType.EVENT, bundleException);
  }

  protected SubType getExceptionSubType() {
    return null;
  }

  protected void reflectError(SubType subType, BundleException bundleException) {
    Bundle bundle = bundleException.bundle;
    Map<String, String> errorMap = bundle.attributesMap;

    if (errorMap.containsKey(MessageBase.INVALID_ENVELOPE_KEY)) {
      reflectInvalidEnvelope(bundleException);
      return;
    }

    errorMap.put(SUBTYPE_PROPERTY_KEY, subType.value());
    errorMap.computeIfAbsent(SUBFOLDER_PROPERTY_KEY, k -> SubFolder.ERROR.value());
    ErrorMessage errorMessage = new ErrorMessage();
    errorMessage.error = (String) bundle.message;
    errorMessage.data = encodeBase64(bundle.payload);
    errorMessage.version = ReflectProcessor.DEPLOYED_CONFIG.udmi_functions;
    errorMessage.timestamp = getTimestamp();
    errorMap.put("payload", encodeBase64(stringify(errorMessage)));
    error(format("Reflecting error %s/%s for %s", errorMap.get(SUBTYPE_PROPERTY_KEY),
        errorMap.get(SUBFOLDER_PROPERTY_KEY),
        errorMap.get(DEVICE_ID_PROPERTY_KEY)));
    reflectString(errorMap.get(REGISTRY_ID_PROPERTY_KEY), stringify(errorMap));
  }

  private void reflectInvalidEnvelope(BundleException bundleException) {
    Map<String, String> envelopeMap = bundleException.bundle.attributesMap;
    error(format("Reflecting invalid %s/%s for %s", envelopeMap.get(SUBTYPE_PROPERTY_KEY),
        envelopeMap.get(SUBFOLDER_PROPERTY_KEY),
        envelopeMap.get(DEVICE_ID_PROPERTY_KEY)));
    String deviceRegistryId = envelopeMap.get(REGISTRY_ID_PROPERTY_KEY);
    envelopeMap.put("payload", encodeBase64(bundleException.bundle.payload));
    reflectString(deviceRegistryId, stringify(envelopeMap));
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

  // TODO: This really should be encapsulated in a proper JSON-schema structure.
  static class ErrorMessage {

    public String timestamp;
    public String version;
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
