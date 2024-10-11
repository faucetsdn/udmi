package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl.getMessageClassFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.Common.CONDENSER_STRING;
import static com.google.udmi.util.Common.DETAIL_KEY;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.firstNonNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.multiTrim;
import static com.google.udmi.util.GeneralUtils.requireNull;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.mapCast;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.JsonUtil.toObject;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.Resource_type.REGISTRY;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.ModelUpdate;
import com.google.bos.udmi.service.messaging.SiteMetadataUpdate;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MetadataMapKeys;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Entry;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.SetupUdmiState;
import udmi.schema.SystemModel;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiEvents;
import udmi.schema.UdmiState;

/**
 * Handle the reflector processor stream for UDMI utility tool clients.
 */
@ComponentName("reflect")
public class ReflectProcessor extends ProcessorBase {

  public static final String PAYLOAD_KEY = "payload";
  private static final Date START_TIME = new Date();
  private static final String LEGACY_METADATA_STRING = "{ }";

  public ReflectProcessor(EndpointConfiguration config) {
    super(config);
  }

  public static String makeTransactionId() {
    return format("RP:%08x", Objects.hash(System.currentTimeMillis(), Thread.currentThread()));
  }

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope reflect = continuation.getEnvelope();
    Map<String, Object> objectMap = toMap(message);
    try {
      boolean isCommand = objectMap.containsKey(PAYLOAD_KEY);
      if (reflect.subFolder == null && !isCommand) {
        reflectStateHandler(reflect, extractUdmiState(message));
      } else if (reflect.subFolder != SubFolder.UDMI && reflect.subType != SubType.REFLECT) {
        throw new IllegalStateException(format("Neither type %s nor folder %s is udmi",
            reflect.subType, reflect.subFolder));
      } else if (message instanceof UdmiState distributedUpdate) {
        updateAwareness(reflect, distributedUpdate);
      } else if (objectMap.isEmpty()) {
        // Ignore empty messages, used as keep-alive messages.
      } else {
        Object payload = extractMessagePayload(objectMap);
        Envelope envelope = extractMessageEnvelope(objectMap);
        requireNull(envelope.payload, "payload not extracted from message envelope");
        // If the message came from a reflector device, check the registry ID matches
        // the device ID
        if (reflect.deviceId != null) {
          checkState(reflect.deviceId.equals(envelope.deviceRegistryId),
              format("envelope %s/%s registryId %s does not match expected reflector deviceId %s",
                  envelope.subType, envelope.subFolder, envelope.deviceRegistryId,
                  reflect.deviceId));
        }

        envelope.source = reflect.source;
        reflect.transactionId = firstNonNull(envelope.transactionId, reflect.transactionId,
            ReflectProcessor::makeTransactionId);
        processReflection(reflect, envelope, payload);
      }
    } catch (Exception e) {
      processException(reflect, objectMap, e);
    }
  }

  private Boolean checkConfigAckTime(Envelope attributes, StateUpdate stateUpdate) {
    CloudModel cloudModel = iotAccess.fetchDevice(attributes.deviceRegistryId, attributes.deviceId);
    Date lastConfigAck = cleanDate(cloudModel.last_config_ack);
    Date lastConfig = cleanDate(ifNotNullGet(stateUpdate.system, system -> system.last_config));
    debug("Check last config ack %s >= %s", isoConvert(lastConfigAck), isoConvert(lastConfig));
    if (lastConfig == null || lastConfigAck == null) {
      return false;
    }
    return lastConfig.after(START_TIME) && !lastConfigAck.before(lastConfig);
  }

  private void reflectUdmiLog(Envelope attributes, String message) {
    Envelope logging = deepCopy(attributes);
    logging.subType = SubType.EVENTS;
    logging.subFolder = SubFolder.UDMI;
    Entry entry = new Entry();
    entry.message = message;
    entry.level = Level.INFO.value();
    entry.timestamp = new Date();
    UdmiEvents events = new UdmiEvents();
    events.logentries = ImmutableList.of(entry);
    reflectMessage(logging, stringify(events));
  }

  private Object extractModel(CloudModel request) {
    String metadata = catchToNull(() -> request.metadata.get(MetadataMapKeys.UDMI_METADATA));
    if (metadata == null) {
      return null;
    } else if (request.resource_type == REGISTRY) {
      return asSiteMetadataUpdate(metadata);
    } else {
      return asModelUpdate(metadata);
    }
  }

  private SiteMetadataUpdate asSiteMetadataUpdate(String metadataString) {
    SiteMetadataUpdate siteMetadataUpdate =
        fromStringDynamic(SiteMetadataUpdate.class, metadataString);
    // TODO: Remove the LEGACY_METADATA_STRING check once tool deployments have matured.
    if (!LEGACY_METADATA_STRING.equals(metadataString)) {
      requireNonNull(siteMetadataUpdate.name, "missing site model name");
    }
    return siteMetadataUpdate;
  }

  private <T> T fromStringDynamic(Class<T> targetClass, String metadataString) {
    try {
      return fromStringStrict(targetClass, metadataString);
    } catch (Exception e) {
      warn("String conversion failed, trying again with relaxed conversion: ",
          friendlyStackTrace(e));
      return fromString(targetClass, metadataString);
    }
  }

  private Envelope extractMessageEnvelope(Object message) {
    return convertToStrict(Envelope.class, message);
  }

  private Object extractMessagePayload(Map<String, Object> message) {
    String payload = (String) message.remove(PAYLOAD_KEY);
    return ifNotNullGet(payload, data -> toObject(decodeBase64(data)));
  }

  private UdmiState extractUdmiState(Object message) {
    Map<String, Object> stringObjectMap = asMap(message);
    UdmiState udmiState =
        convertToStrict(UdmiState.class, stringObjectMap.get(SubFolder.UDMI.value()));
    requireNonNull(udmiState, "reflector state update missing udmi subfolder");
    udmiState.timestamp = JsonUtil.getDate((String) stringObjectMap.get(TIMESTAMP_KEY));
    return udmiState;
  }

  private CloudModel getReflectionResult(Envelope attributes, Object payload) {
    try {
      SubType subType = ofNullable(attributes.subType).orElse(SubType.EVENTS);
      return switch (subType) {
        case QUERY -> reflectQuery(attributes, mapCast(payload));
        case MODEL -> reflectModel(attributes, convertToStrict(CloudModel.class, payload));
        default -> reflectProcess(attributes, payload);
      };
    } catch (Exception e) {
      throw new RuntimeException("While processing reflect message type " + attributes.subType, e);
    }
  }

  private void processException(Envelope reflection, Map<String, Object> objectMap, Exception e) {
    String transactionId =
        ofNullable((String) objectMap.get(TRANSACTION_KEY)).orElse(reflection.transactionId);
    String stackMessage = friendlyStackTrace(e);
    String detailString = multiTrim(stackTraceString(e), CONDENSER_STRING);
    String registryId = reflection.deviceRegistryId;
    String deviceId = reflection.deviceId;
    warn("Processing exception %s/%s %s: %s", registryId, deviceId, transactionId, stackMessage);
    debug("Stack trace details %s/%s %s: %s", registryId, deviceId, transactionId, detailString);
    Map<String, Object> message = new HashMap<>();
    message.put(ERROR_KEY, stackMessage);
    message.put(DETAIL_KEY, detailString);
    Envelope envelope = new Envelope();
    envelope.subFolder = SubFolder.ERROR;
    envelope.deviceId = (String) objectMap.get(DEVICE_ID_KEY);
    envelope.transactionId = transactionId;
    sendReflectCommand(reflection, envelope, message);
  }

  private void processReflection(Envelope reflection, Envelope envelope,
      Object payload) {
    debug("Processing reflection %s/%s %s %s", envelope.subType, envelope.subFolder,
        isoConvert(envelope.publishTime), envelope.transactionId);
    CloudModel result = getReflectionResult(envelope, payload);
    ifNotNullThen(result,
        v -> debug("Reflection result %s: %s", envelope.transactionId, envelope.subType));
    ifNotNullThen(result, v -> sendReflectCommand(reflection, envelope, result));
  }

  private void processStateUpdate(Envelope envelope, StateUpdate stateUpdate) {
    debug("Updating last_start b/c of device state query %s/%s",
        envelope.deviceRegistryId, envelope.deviceId);
    updateLastStart(envelope, stateUpdate);
  }

  private CloudModel queryCloudDevice(Envelope attributes) {
    return iotAccess.fetchDevice(attributes.deviceRegistryId, attributes.deviceId);
  }

  private CloudModel queryCloudRegistry(Envelope attributes) {
    return iotAccess.listDevices(attributes.deviceRegistryId,
        progress -> reflectUdmiLog(attributes, format("Fetched %d devices...", progress)));
  }

  private CloudModel queryDeviceState(Envelope attributes) {
    try {
      String state = ofNullable(
          iotAccess.fetchState(attributes.deviceRegistryId, attributes.deviceId)).orElse("{}");
      debug(format("Processing device %s state query", attributes.deviceId));
      StateUpdate stateUpdate = fromString(StateUpdate.class, state);
      stateUpdate.configAcked = checkConfigAckTime(attributes, stateUpdate);
      processStateUpdate(attributes, stateUpdate);
      publish(attributes, stateUpdate);
      reflectStateUpdate(attributes, stringify(stateUpdate));
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.FETCH;
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While querying device state " + attributes.deviceId, e);
    }
  }

  private CloudModel reflectModel(Envelope attributes, CloudModel request) {
    ifNotNullThen(extractModel(request), model -> publish(attributes, model));
    if (request.resource_type == REGISTRY) {
      return iotAccess.modelRegistry(attributes.deviceRegistryId, attributes.deviceId, request);
    } else {
      return iotAccess.modelDevice(attributes.deviceRegistryId, attributes.deviceId, request);
    }
  }

  private ModelUpdate asModelUpdate(String modelString) {
    // If it's not a valid JSON object, then fall back to a string description alternate.
    if (modelString == null || !modelString.startsWith(JsonUtil.JSON_OBJECT_LEADER)) {
      ModelUpdate modelUpdate = new ModelUpdate();
      modelUpdate.system = new SystemModel();
      modelUpdate.system.description = modelString;
      return modelUpdate;
    }
    ModelUpdate modelUpdate = fromStringDynamic(ModelUpdate.class, modelString);
    requireNonNull(modelUpdate.system, "missing system block in model message");
    return modelUpdate;
  }

  private CloudModel reflectProcess(Envelope attributes, Object payload) {
    if (payload == null) {
      return null;
    }
    if (attributes.subType == SubType.CONFIG) {
      processConfigChange(attributes, payload, null);
    }
    if (payload instanceof String) {
      return null;
    }
    Class<?> messageClass = getMessageClassFor(attributes, true);
    debug("Propagating message %s: %s", attributes.transactionId, messageClass.getSimpleName());
    publish(attributes, convertTo(messageClass, payload));
    return null;
  }

  private CloudModel reflectQuery(Envelope attributes, Map<String, Object> payload) {
    checkState(payload.size() == 0, "unexpected non-empty message payload");
    attributes.subType = SubType.REPLY;
    switch (attributes.subFolder) {
      case UPDATE:
        return queryDeviceState(attributes);
      case CLOUD:
        return reflectQueryCloud(attributes);
      default:
        throw new RuntimeException("Unknown query folder: " + attributes.subFolder);
    }
  }

  private CloudModel reflectQueryCloud(Envelope attributes) {
    return attributes.deviceId != null
        ? queryCloudDevice(attributes) : queryCloudRegistry(attributes);
  }

  private void reflectStateHandler(Envelope envelope, UdmiState toolState) {
    final String registryId = envelope.deviceRegistryId;
    final String deviceId = envelope.deviceId;

    // Ensure source is encoded in the distribution (not always send in some mechanisms).
    ifNullThen(toolState.setup, () -> toolState.setup = new SetupUdmiState());
    ifNullThen(toolState.setup.msg_source, () -> toolState.setup.msg_source = envelope.source);

    ifNotNullThen(distributor, d -> catchToElse(() -> d.publish(envelope, toolState, containerId),
        e -> error("Error handling update: %s %s", friendlyStackTrace(e), envelope.transactionId)));
    updateAwareness(envelope, toolState);

    UdmiConfig udmiConfig = UdmiServicePod.getUdmiConfig(toolState);

    Map<String, Object> configMap = new HashMap<>();
    configMap.put(SubFolder.UDMI.value(), udmiConfig);
    configMap.put(TIMESTAMP_KEY, isoConvert());
    String contents = stringifyTerse(configMap);
    debug("Setting reflector config %s %s: %s", registryId, deviceId, contents);
    iotAccess.modifyConfig(envelope, previous -> contents);
  }

  private void reflectStateUpdate(Envelope attributes, String state) {
    Envelope envelope = deepCopy(attributes);
    envelope.subType = SubType.STATE;
    envelope.subFolder = UPDATE;
    reflectMessage(envelope, state);
  }

  private void sendReflectCommand(Envelope reflection, Envelope message, Object payload) {
    message.payload = encodeBase64(stringify(payload));
    message.source = reflection.source;

    String deviceRegistry = reflection.deviceId;
    iotAccess.sendCommand(makeReflectEnvelope(deviceRegistry, reflection.source), SubFolder.UDMI,
        stringify(message));
  }

  private void updateProviderAffinity(Envelope envelope, String source) {
    debug("Setting affinity for %s/%s to %s", envelope.deviceRegistryId, envelope.deviceId,
        source);
    iotAccess.setProviderAffinity(envelope.deviceRegistryId, envelope.deviceId, source);
  }

  private void updateRegistryRegions(Map<String, String> regions) {
    debug("Updating registry regions: ",
        regions.entrySet().stream().map(entry -> format("%s:%s", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", ")));
    iotAccess.updateRegistryRegions(regions);
  }

  @Override
  public void activate() {
    debug("Deployment configuration: " + stringifyTerse(UdmiServicePod.getDeployedConfig()));
    super.activate();
  }

  void updateAwareness(Envelope envelope, UdmiState toolState) {
    debug("Processing UdmiState for %s/%s: %s", envelope.deviceRegistryId, envelope.deviceId,
        stringifyTerse(toolState));
    ifNotNullThen(toolState.setup,
        setup -> updateProviderAffinity(envelope, toolState.setup.msg_source));
    ifNotNullThen(toolState.regions, this::updateRegistryRegions);
  }
}
