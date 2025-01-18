package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.access.IotAccessBase.MAX_CONFIG_LENGTH;
import static com.google.bos.udmi.service.core.ReflectProcessor.PAYLOAD_KEY;
import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;
import static com.google.bos.udmi.service.pod.UdmiServicePod.UDMI_VERSION;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.compressJsonString;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.getSubMapDefault;
import static com.google.udmi.util.GeneralUtils.getSubMapNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.isNullOrTruthy;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.mapCast;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.ConfigUpdate;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageBase.BundleException;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.SimpleHandler;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Base class for UDMIS components.
 */
public abstract class ProcessorBase extends ContainerBase implements SimpleHandler {

  public static final String IOT_ACCESS_COMPONENT = "iot-access";
  private static final String RESET_CONFIG_VALUE = "reset_config";
  private static final String BREAK_CONFIG_VALUE = "break_json";
  private static final String EXTRA_FIELD_KEY = "extra_field";
  private static final String BROKEN_CONFIG_JSON =
      format("{ broken by %s == %s", EXTRA_FIELD_KEY, BREAK_CONFIG_VALUE);
  private static final String JSON_EMPTY_STRING = "\"\"";
  protected final MessageDispatcher dispatcher;
  private final MessageDispatcher sidecar;
  private final boolean isEnabled;
  protected IotAccessBase iotAccess;
  private final ImmutableList<HandlerSpecification> baseHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );
  protected DistributorPipe distributor;

  /**
   * Create a new configured component.
   */
  public ProcessorBase(EndpointConfiguration config) {
    super(config);
    isEnabled = isNullOrTruthy(variableSubstitution(config.enabled));
    ifNotTrueThen(isEnabled, () -> debug("Processor %s is disabled", containerId));
    dispatcher = ifTrueGet(isEnabled, () -> MessageDispatcher.from(config));
    sidecar = ifTrueGet(isEnabled, () -> MessageDispatcher.from(makeSidecarConfig(config)));
  }

  /**
   * Create a new instance of the given target class with the provided configuration.
   */
  public static <T extends ProcessorBase> T create(Class<T> clazz, EndpointConfiguration config) {
    try {
      return clazz.getDeclaredConstructor(EndpointConfiguration.class).newInstance(config);
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName(), e);
    }
  }

  private static EndpointConfiguration makeSidecarConfig(EndpointConfiguration config) {
    if (config.side_id == null) {
      return null;
    }
    EndpointConfiguration sidecar = deepCopy(config);
    sidecar.recv_id = null;
    sidecar.send_id = sidecar.side_id;
    sidecar.side_id = null;
    return sidecar;
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
    e.printStackTrace();
    BundleException bundleException = new BundleException(message, toStringMap(envelope), payload);
    reflectError(SubType.EVENTS, bundleException);
  }

  protected SubType getExceptionSubType() {
    return null;
  }

  protected String processConfigChange(Envelope envelope, Object payload, Date newLastStart) {
    // TODO: This should really be pushed down to ReflectProcessor, not sure why it's here.
    SubFolder subFolder = envelope.subFolder;
    debug(format("Modifying device config %s/%s/%s %s", envelope.deviceRegistryId,
        envelope.deviceId, subFolder, envelope.transactionId));

    String configUpdate = iotAccess.modifyConfig(
        envelope, previous -> updateConfig(previous, envelope, payload, newLastStart));

    if (configUpdate == null) {
      return null;
    }
    Envelope useAttributes = deepCopy(envelope);
    ifNotNullThen(newLastStart, start -> useAttributes.subType = SubType.CONFIG);
    useAttributes.subFolder = UPDATE;
    checkState(useAttributes.subType == SubType.CONFIG);
    debug("Acknowledging config/%s %s %s", subFolder, useAttributes.transactionId,
        isoConvert(newLastStart));
    reflectMessage(useAttributes, configUpdate);
    return configUpdate;
  }

  protected void reflectError(SubType subType, BundleException bundleException) {
    Bundle bundle = bundleException.bundle;
    Map<String, String> errorMap = bundle.attributesMap;

    String invalid = errorMap.get(MessageBase.INVALID_ENVELOPE_KEY);
    if (invalid != null) {
      reflectInvalidEnvelope(bundleException, invalid);
      return;
    }

    // If the error comes from the reflect registry, then don't use the registry as the device,
    // so revert the default behavior (otherwise the message goes nowhere!).
    String registryId = errorMap.get(REGISTRY_ID_PROPERTY_KEY);
    if (reflectRegistry.equals(registryId)) {
      errorMap.put(REGISTRY_ID_PROPERTY_KEY, errorMap.get(DEVICE_ID_KEY));
      errorMap.put(DEVICE_ID_KEY, null);
    }

    errorMap.put(SUBTYPE_PROPERTY_KEY, ifNotNullGet(subType, SubType::value));
    errorMap.computeIfAbsent(SUBFOLDER_PROPERTY_KEY, k -> SubFolder.ERROR.value());
    ErrorMessage errorMessage = new ErrorMessage();
    errorMessage.error = (String) bundle.message;
    errorMessage.data = encodeBase64(bundle.payload);
    errorMessage.version = UdmiServicePod.getDeployedConfig().udmi_version;
    errorMessage.timestamp = isoConvert();
    errorMap.put(PAYLOAD_KEY, encodeBase64(stringify(errorMessage)));
    error(format("Reflecting error %s/%s for %s", errorMap.get(SUBTYPE_PROPERTY_KEY),
        errorMap.get(SUBFOLDER_PROPERTY_KEY),
        errorMap.get(DEVICE_ID_KEY)));
    reflectString(makeReflectEnvelope(registryId, null), stringify(errorMap));
  }

  protected void reflectMessage(Envelope envelope, String message) {
    String deviceRegistryId = envelope.deviceRegistryId;

    if (deviceRegistryId == null) {
      return;
    }

    try {
      checkState(envelope.payload == null, "envelope payload is not null");
      String jsonEncoded = message.isEmpty() ? JSON_EMPTY_STRING : message;
      envelope.payload = encodeBase64(jsonEncoded);
      reflectString(makeReflectEnvelope(deviceRegistryId, envelope.source), stringify(envelope));
    } catch (Exception e) {
      error(format("Message reflection error %s", friendlyStackTrace(e)));
    } finally {
      envelope.payload = null;
    }
  }

  /**
   * Register component specific handlers. Default implementation will scan methods for the
   * `@ProcessMessage` annotation.
   */
  protected void registerHandlers() {
    Arrays.stream(getClass().getMethods()).forEach(method -> {
      MessageHandler annotation = method.getAnnotation(MessageHandler.class);
      if (annotation != null) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        checkState(parameterTypes.length == 1,
            "dispatch handlers should have exactly one argument");
        Class<?> messageType = parameterTypes[0];
        dispatcher.registerHandler(messageType, message -> {
          try {
            method.invoke(this, messageType.cast(message));
          } catch (Exception e) {
            throw new RuntimeException(
                "While invoking message annotation on " + getClass().getSimpleName(), e);
          }
        });
      }
    });
  }

  /**
   * Register default component handlers. Can be overridden to change underlying behavior.
   */
  protected void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    dispatcher.registerHandlers(messageHandlers);
  }

  protected void sideProcess(Envelope envelope, Object message) {
    ifNotNullThen(sidecar, car -> car.withEnvelope(envelope).publish(message),
        () -> processMessage(envelope, message));
  }

  private void mungeConfigDebug(Envelope attributes, Object lastConfig, String reason) {
    debug("Munge config %s, %s/%s last_config %s %s", reason,
        attributes.deviceRegistryId, attributes.deviceId, lastConfig, attributes.transactionId);
  }

  private void reflectInvalidEnvelope(BundleException bundleException, String invalid) {
    Map<String, String> envelopeMap = bundleException.bundle.attributesMap;
    error(format("Reflecting invalid %s/%s for %s: %s", envelopeMap.get(SUBTYPE_PROPERTY_KEY),
        envelopeMap.get(SUBFOLDER_PROPERTY_KEY), envelopeMap.get(DEVICE_ID_KEY), invalid));
    String deviceRegistryId = envelopeMap.get(REGISTRY_ID_PROPERTY_KEY);
    envelopeMap.put("payload", encodeBase64(bundleException.bundle.payload));
    reflectString(makeReflectEnvelope(deviceRegistryId, null), stringify(envelopeMap));
  }

  private void reflectString(Envelope envelope, String commandString) {
    ifNotNullThen(iotAccess, () ->
        iotAccess.sendCommand(envelope, SubFolder.UDMI, commandString));
  }

  protected Envelope makeReflectEnvelope(String registryId, String source) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = reflectRegistry;
    envelope.deviceId = registryId;
    envelope.source = source;
    return envelope;
  }

  private String updateConfig(Entry<Long, String> previous, Envelope attributes,
      Object rawPayload, Date newLastStart) {
    if (rawPayload instanceof String stringPayload) {
      return stringPayload;
    }
    Map<String, Object> updatePayload = mapCast(rawPayload);
    Object extraField = ifNotNullGet(updatePayload, p -> p.remove(EXTRA_FIELD_KEY));
    boolean resetConfig = RESET_CONFIG_VALUE.equals(extraField);
    boolean breakConfig = BREAK_CONFIG_VALUE.equals(extraField);
    final Map<String, Object> payload;

    final String reason;

    if (resetConfig) {
      reason = Objects.toString(extraField);
      payload = new HashMap<>();
    } else if (breakConfig) {
      mungeConfigDebug(attributes, "undefined", (String) extraField);
      return BROKEN_CONFIG_JSON;
    } else if (newLastStart != null) {
      payload = asMap(ofNullable(previous.getValue()).orElse(EMPTY_JSON));
      augmentPayload(payload, attributes.transactionId, previous.getKey());
      String update = updateWithLastStart(payload, newLastStart);
      ifNotNullThen(update,
          () -> mungeConfigDebug(attributes, payload.get(TIMESTAMP_KEY), "last_start"));
      return update;
    } else if (attributes.subFolder == UPDATE) {
      reason = "update";
      payload = new HashMap<>(updatePayload);
    } else {
      ifNotNullThen(extraField,
          field -> warn(format("Ignoring unknown %s value %s", EXTRA_FIELD_KEY, extraField)));
      try {
        payload = asMap(ofNullable(previous.getValue()).orElse(EMPTY_JSON));
        reason = ifNotNullGet(attributes.subFolder, SubFolder::value, null);
      } catch (Exception e) {
        throw new PreviousParseException("parsing previous config", e);
      }
    }

    ifNotNullThen(updatePayload, p -> updatePayload.remove(TIMESTAMP_KEY));
    ifNotNullThen(updatePayload, p -> updatePayload.remove(VERSION_KEY));

    if (attributes.subFolder != null && attributes.subFolder != UPDATE) {
      payload.put(attributes.subFolder.value(), updatePayload);
    }

    String updateTimestamp = isoConvert();
    payload.put(TIMESTAMP_KEY, updateTimestamp);
    payload.put(VERSION_KEY, UDMI_VERSION);

    augmentPayload(payload, attributes.transactionId, previous.getKey());
    mungeConfigDebug(attributes, updateTimestamp, reason);
    return compressJsonString(payload, MAX_CONFIG_LENGTH);
  }

  private void augmentPayload(Map<String, Object> payload, String transactionId, Long version) {
    try {
      Map<String, Object> asMap = getSubMapNull(getSubMapNull(payload, "system"), "testing");
      ifNotNullThen(asMap, map -> map.put("transaction_id", transactionId));
      ifNotNullThen(asMap, map -> map.put("config_base", version));
    } catch (Exception e) {
      warn("Could not augment config with transactionId %s", transactionId);
    }
  }

  private String updateWithLastStart(Map<String, Object> oldPayload, Date newLastStart) {
    Map<String, Object> oldSystem = getSubMapNull(oldPayload, "system");
    Map<String, Object> oldOperation = getSubMapNull(oldSystem, "operation");
    if (oldOperation == null) {
      return null;
    }

    Date oldLastStart = getDate((String) oldOperation.get("last_start"));
    boolean shouldUpdate = oldLastStart == null || oldLastStart.before(newLastStart);
    debug("Last start was %s, now %s, updating %s", isoConvert(oldLastStart),
        isoConvert(newLastStart), shouldUpdate);
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
    super.activate();
    iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
    distributor = UdmiServicePod.maybeGetComponent(DistributorPipe.class);
    if (dispatcher != null) {
      registerHandlers(baseHandlers);
      registerHandlers();
      dispatcher.activate();
    }
  }

  public int getMessageCount(Class<?> clazz) {
    return dispatcher.getHandlerCount(clazz);
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void processMessage(Envelope envelope, Object message) {
    debug(format("Process message %s %s", stringifyTerse(envelope), stringifyTerse(message)));
    ((MessageDispatcherImpl) dispatcher).processMessage(envelope, message);
  }

  /**
   * Shutdown the component.
   */
  public void shutdown() {
    if (dispatcher != null) {
      dispatcher.shutdown();
    }
  }

  /**
   * Simple exception indicator that a parse error occurred, so it wasn't something about the new
   * config, but the previous config, so should essentially be retried.
   */
  public static class PreviousParseException extends RuntimeException {

    public PreviousParseException(String message, Exception cause) {
      super(message, cause);
    }
  }

  // TODO: This really should be encapsulated in a proper JSON-schema structure.
  static class ErrorMessage {

    public String timestamp;
    public String version;
    public String error;
    public String data;
  }

  void publish(Envelope attributes, Object message) {
    dispatcher.withEnvelope(attributes).publish(message);
  }

  /**
   * Publish a message (using the internal dispatcher).
   */
  void publish(Object message) {
    dispatcher.publish(message);
  }

  <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    dispatcher.registerHandler(clazz, handler);
  }

  public MessageContinuation getContinuation(Object message) {
    return dispatcher.getContinuation(message);
  }

  @TestOnly
  public MessageDispatcher getDispatcher() {
    return dispatcher;
  }

  void updateLastStart(Envelope envelope, StateUpdate message) {
    if (message == null || message.system == null || message.system.operation == null
        || message.system.operation.last_start == null) {
      return;
    }

    try {
      Date newLastStart = message.system.operation.last_start;
      String config = processConfigChange(envelope, new HashMap<>(), newLastStart);
      debug("Config last_start update for %s/%s against state last_start %s, updated %s",
          envelope.deviceRegistryId, envelope.deviceId, isoConvert(newLastStart), config != null);
      ifNotNullThen(config,
          newConfig -> dispatcher.publish(fromJsonString(newConfig, ConfigUpdate.class)));
    } catch (Exception e) {
      debug("Could not process config last_state update, skipping: "
          + friendlyStackTrace(e));
    }
  }
}
