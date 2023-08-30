package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl.getMessageClassFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.JsonUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Handle the reflector processor stream for UDMI utility tool clients.
 */
@ComponentName("reflect")
public class ReflectProcessor extends ProcessorBase {

  public static final String PAYLOAD_KEY = "payload";

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope reflection = continuation.getEnvelope();
    try {
      if (reflection.subFolder == null) {
        reflectStateHandler(reflection, extractUdmiState(message));
      } else if (reflection.subFolder != SubFolder.UDMI) {
        throw new IllegalStateException("Unexpected reflect subfolder " + reflection.subFolder);
      } else {
        Map<String, Object> stringObjectMap = toMap(message);
        Map<String, Object> payload = extractMessagePayload(stringObjectMap);
        Envelope envelope = extractMessageEnvelope(stringObjectMap);
        reflection.transactionId = envelope.transactionId;
        processReflection(reflection, envelope, payload);
      }
    } catch (Exception e) {
      processException(reflection, e);
    }
  }

  private Boolean checkConfigAckTime(Envelope attributes, String state) {
    // const queries = [
    // iotClient.getDevice(request),
    //     iotClient.listDeviceConfigVersions(request)
    //   ];
    //
    // return Promise.all(queries).then(([device, config]) =>{
    // const stateBinaryData = device[0].state && device[0].state.binaryData;
    // const stateString = stateBinaryData && stateBinaryData.toString();
    // const msgObject = JSON.parse(stateString) || {};
    // const lastConfig = config[0].deviceConfigs[0];
    // const cloudUpdateTime = lastConfig.cloudUpdateTime.seconds;
    // const deviceAckTime = lastConfig.deviceAckTime && lastConfig.deviceAckTime.seconds;
    //   msgObject.configAcked = String(deviceAckTime >= cloudUpdateTime);
    //   return process_state_update(attributes, msgObject);
    // });
    return true;
  }

  private Envelope extractMessageEnvelope(Object message) {
    return convertToStrict(Envelope.class, message);
  }

  private Map<String, Object> extractMessagePayload(Map<String, Object> message) {
    String payload = (String) message.remove(PAYLOAD_KEY);
    return ifNotNullGet(payload, data -> toMap(decodeBase64(data)));
  }

  private UdmiState extractUdmiState(Object message) {
    Map<String, Object> stringObjectMap = JsonUtil.asMap(message);
    UdmiState udmiState =
        convertToStrict(UdmiState.class, stringObjectMap.get(SubFolder.UDMI.value()));
    requireNonNull(udmiState, "missing udmi subfolder");
    udmiState.timestamp = JsonUtil.getDate((String) stringObjectMap.get(TIMESTAMP_KEY));
    return udmiState;
  }

  private CloudModel getReflectionResult(Envelope attributes, Map<String, Object> payload) {
    try {
      switch (attributes.subType) {
        case QUERY:
          return reflectQuery(attributes, payload);
        case MODEL:
          return reflectModel(attributes, convertToStrict(CloudModel.class, payload));
        default:
          return reflectPropagate(attributes, ofNullable(payload).orElseGet(HashMap::new));
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing reflect message type " + attributes.subType, e);
    }
  }

  private void processException(Envelope reflection, Exception e) {
    String stackMessage = friendlyStackTrace(e);
    warn("Processing exception %s: %s", reflection.transactionId, stackMessage);
    Map<String, Object> message = new HashMap<>();
    message.put(ERROR_KEY, stackMessage);
    Envelope envelope = new Envelope();
    envelope.subFolder = SubFolder.ERROR;
    envelope.transactionId = reflection.transactionId;
    sendReflectCommand(reflection, envelope, message);
  }

  private void processReflection(Envelope reflection, Envelope envelope,
      Map<String, Object> payload) {
    debug("Processing reflection %s/%s %s %s", envelope.subType, envelope.subFolder,
        getTimestamp(envelope.publishTime), envelope.transactionId);
    updateProviderAffinity(envelope, reflection.source);
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
    return iotAccess.listDevices(attributes.deviceRegistryId);
  }

  private CloudModel queryDeviceState(Envelope attributes) {
    try {
      String state = ofNullable(
          iotAccess.fetchState(attributes.deviceRegistryId, attributes.deviceId)).orElse("{}");
      debug(format("Processing device %s state query", attributes.deviceId));
      StateUpdate stateUpdate = fromStringStrict(StateUpdate.class, state);
      stateUpdate.configAcked = checkConfigAckTime(attributes, state);
      processStateUpdate(attributes, stateUpdate);
      publish(stateUpdate);
      reflectStateUpdate(attributes, stringify(stateUpdate));
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.FETCH;
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While querying device state " + attributes.deviceId, e);
    }
  }

  private CloudModel reflectModel(Envelope attributes, CloudModel request) {
    return requireNonNull(
        iotAccess.modelDevice(attributes.deviceRegistryId, attributes.deviceId, request),
        "missing reflect model response");
  }

  private CloudModel reflectPropagate(Envelope attributes, Map<String, Object> payload) {
    if (requireNonNull(attributes.subType) == SubType.CONFIG) {
      processConfigChange(attributes, payload, null);
    }
    Class<?> messageClass = getMessageClassFor(attributes);
    debug("Propagating message %s: %s", attributes.transactionId, messageClass.getSimpleName());
    publish(convertTo(messageClass, payload));
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

    ifNotNullThen(distributor, d -> d.distribute(envelope, toolState));
    updateAwareness(envelope, toolState);

    UdmiConfig udmiConfig = UdmiServicePod.getUdmiConfig(toolState);

    Map<String, Object> configMap = new HashMap<>();
    configMap.put(SubFolder.UDMI.value(), udmiConfig);
    configMap.put(TIMESTAMP_KEY, getTimestamp());
    String contents = stringifyTerse(configMap);
    debug("Setting reflector config %s %s: %s", registryId, deviceId, contents);
    iotAccess.modifyConfig(registryId, deviceId, previous -> contents);
  }

  private void reflectStateUpdate(Envelope attributes, String state) {
    Envelope envelope = deepCopy(attributes);
    envelope.subType = SubType.STATE;
    envelope.subFolder = UPDATE;
    reflectMessage(envelope, state);
  }

  private void sendReflectCommand(Envelope reflection, Envelope message, Object payload) {
    String reflectRegistry = reflection.deviceRegistryId;
    String deviceRegistry = reflection.deviceId;
    message.payload = encodeBase64(stringify(payload));
    iotAccess.sendCommand(reflectRegistry, deviceRegistry, SubFolder.UDMI, stringify(message));
  }

  private void updateProviderAffinity(Envelope envelope, String source) {
    debug("Setting affinity for %s/%s to %s", envelope.deviceRegistryId, envelope.deviceId, source);
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
    ifNotNullThen(toolState.setup, setup -> updateProviderAffinity(envelope, envelope.source));
    ifNotNullThen(toolState.regions, this::updateRegistryRegions);
  }
}
