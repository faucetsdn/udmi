package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEVICE_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.Common.getExceptionMessage;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.getSubMap;
import static com.google.udmi.util.GeneralUtils.getSubMapDefault;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.Envelope.SubFolder.UDMI;
import static udmi.schema.Envelope.SubFolder.UPDATE;

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
import com.google.udmi.util.Common;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
  static final String REFLECT_REGISTRY = "UDMI-REFLECT";
  static final String UDMI_VERSION =
      requireNonNull(ReflectProcessor.DEPLOYED_CONFIG.udmi_functions);
  private static final String RESET_CONFIG_VALUE = "reset_config";
  private static final String BREAK_CONFIG_VALUE = "break_json";
  private static final String EXTRA_FIELD_KEY = "extra_field";
  private static final String BROKEN_CONFIG_JSON =
      format("{ broken by %s == %s", EXTRA_FIELD_KEY, BREAK_CONFIG_VALUE);
  public static final String EMPTY_JSON = "{}";
  protected MessageDispatcher dispatcher;
  private final ImmutableList<HandlerSpecification> baseHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );
  protected IotAccessBase iotAccess;

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
    String message = Common.getExceptionMessage(e);
    String payload = friendlyStackTrace(e);
    error(format("Received message exception: %s", payload));
    BundleException bundleException = new BundleException(message, toStringMap(envelope), payload);
    reflectError(SubType.EVENT, bundleException);
  }

  protected SubType getExceptionSubType() {
    return null;
  }

  protected void processConfigChange(Envelope envelope, Map<String, Object> payload,
      Date newLastStart) {
    SubFolder subFolder = envelope.subFolder;
    debug(format("Modifying device config %s/%s/%s %s", envelope.deviceRegistryId,
        envelope.deviceId, subFolder, envelope.transactionId));

    if (payload != null) {
      payload.put("timestamp", getTimestamp());
      payload.put("version", UDMI_VERSION);
    }

    String configUpdate = iotAccess.modifyConfig(envelope.deviceRegistryId,
        envelope.deviceId, previous -> updateConfig(previous, envelope, payload, newLastStart));

    if (configUpdate == null) {
      return;
    }
    Envelope useAttributes = deepCopy(envelope);
    ifNotNullThen(newLastStart, start -> useAttributes.subType = SubType.CONFIG);
    useAttributes.subFolder = UPDATE;
    checkState(useAttributes.subType == SubType.CONFIG);
    debug("Acknowledging config/%s %s %s", subFolder, useAttributes.transactionId,
        getTimestamp(newLastStart));
    reflectMessage(useAttributes, configUpdate);
  }

  protected void reflectError(SubType subType, BundleException bundleException) {
    Bundle bundle = bundleException.bundle;
    Map<String, String> errorMap = bundle.attributesMap;

    if (errorMap.containsKey(MessageBase.INVALID_ENVELOPE_KEY)) {
      reflectInvalidEnvelope(bundleException);
      return;
    }

    // If the error comes from the reflect registry, then don't use the registry as the device,
    // so revert the default behavior (otherwise the message goes nowhere!).
    if (errorMap.get(REGISTRY_ID_PROPERTY_KEY).equals(REFLECT_REGISTRY)) {
      errorMap.put(REGISTRY_ID_PROPERTY_KEY, errorMap.get(DEVICE_ID_PROPERTY_KEY));
      errorMap.put(DEVICE_ID_PROPERTY_KEY, null);
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

  protected void reflectMessage(Envelope envelope, String message) {
    try {
      checkState(envelope.payload == null, "envelope payload is not null");
      envelope.payload = encodeBase64(message);
      reflectString(envelope.deviceRegistryId, stringify(envelope));
    } catch (Exception e) {
      error(format("Error reflecting message: %s", getExceptionMessage(e)));
    } finally {
      envelope.payload = null;
    }
  }

  /**
   * Register component specific handlers. Should be overridden by subclass to change behaviors.
   */
  protected void registerHandlers() {
  }

  /**
   * Register default component handlers. Can be overridden to change underlying behavior.
   */
  protected void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    dispatcher.registerHandlers(messageHandlers);
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

  private String updateConfig(String previous, Envelope attributes, Map<String, Object> updatePayload, Date newLastStart) {
    Map<String, Object> payload = ofNullable(asMap(previous)).orElseGet(HashMap::new);
    Object extraField = ifNotNullGet(updatePayload, p -> p.remove(EXTRA_FIELD_KEY));
    boolean resetConfig = RESET_CONFIG_VALUE.equals(extraField);
    boolean breakConfig = BREAK_CONFIG_VALUE.equals(extraField);
    if (resetConfig) {
      debug("Resetting config due to %s value %s", EXTRA_FIELD_KEY, extraField);
      payload.clear();
    } else if (breakConfig) {
      debug("Breaking config due to %s value %s", EXTRA_FIELD_KEY, extraField);
      return BROKEN_CONFIG_JSON;
    } else if (extraField != null) {
      warn(format("Ignoring unknown %s value %s", EXTRA_FIELD_KEY, extraField));
    }

    if (newLastStart != null) {
      return updateWithLastStart(payload, newLastStart);
    } else if (attributes.subFolder == UPDATE) {
      payload = updatePayload;
    } else {
      updatePayload.remove(TIMESTAMP_KEY);
      updatePayload.remove(VERSION_KEY);
      payload.put(attributes.subFolder.value(), updatePayload);
    }

    payload.put(TIMESTAMP_KEY, getTimestamp());
    payload.put(VERSION_KEY, UDMI_VERSION);

    return stringify(payload);
  }

  private String updateWithLastStart(Map<String, Object> oldPayload, Date newLastStart) {
    Map<String, Object> oldSystem = getSubMap(oldPayload, "system");
    Map<String, Object> oldOperation = getSubMap(oldSystem, "operation");
    if (oldOperation == null) {
      return null;
    }

    Date oldLastStart = getDate((String) oldOperation.get("last_start"));
    boolean shouldUpdate = oldLastStart == null || oldLastStart.before(newLastStart);
    debug("Last start was %s, now %s, updating %s", getTimestamp(oldLastStart),
        getTimestamp(newLastStart), shouldUpdate);
    if (!shouldUpdate) {
      return null;
    }

    Map<String, Object> newSystem = getSubMapDefault(oldPayload, "system");
    Map<String, Object> newOperation = getSubMapDefault(newSystem, "operation");
    newOperation.put("last_start", newLastStart);
    return stringify(oldPayload);
  }

  /**
   * Activate this component.
   */
  public void activate() {
    info("Activating");
    iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
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
