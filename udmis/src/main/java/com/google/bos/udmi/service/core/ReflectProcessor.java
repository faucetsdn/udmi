package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT_NAME;
import static com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl.getMessageClassFor;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.util.Objects.requireNonNull;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Handle the reflector processor stream for UDMI utility tool clients.
 */
public class ReflectProcessor extends ProcessorBase {

  public static final String PAYLOAD_KEY = "payload";
  static final String DEPLOY_FILE = "var/deployed_version.json";
  private final SetupUdmiConfig deployed =
      loadFileStrictRequired(SetupUdmiConfig.class, new File(DEPLOY_FILE));

  private IotAccessBase iotAccess;

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope reflection = continuation.getEnvelope();
    try {
      if (reflection.subFolder == null) {
        stateHandler(reflection, extractUdmiState(message));
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
          return reflectPropagate(attributes, payload);
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing reflect message type " + attributes.subType, e);
    }
  }

  private void processException(Envelope reflection, Exception e) {
    debug("Processing exception: " + GeneralUtils.friendlyStackTrace(e));
    Map<String, Object> message = new HashMap<>();
    message.put(ERROR_KEY, stackTraceString(e));
    Envelope envelope = new Envelope();
    envelope.subFolder = SubFolder.ERROR;
    envelope.transactionId = reflection.transactionId;
    sendReflectCommand(reflection, envelope, message);
  }

  private void processReflection(Envelope reflection, Envelope envelope,
      Map<String, Object> payload) {
    iotAccess.setProviderAffinity(envelope.deviceRegistryId, envelope.deviceId, reflection.source);
    CloudModel result = getReflectionResult(envelope, payload);
    envelope.subType = SubType.REPLY;
    sendReflectCommand(reflection, envelope, result);
  }

  private CloudModel queryCloudDevice(Envelope attributes) {
    return iotAccess.fetchDevice(attributes.deviceRegistryId, attributes.deviceId);
  }

  private CloudModel queryCloudRegistry(Envelope attributes) {
    return iotAccess.listDevices(attributes.deviceRegistryId);
  }

  private CloudModel queryDeviceState(Envelope attributes) {
    try {
      String state = iotAccess.fetchState(attributes.deviceRegistryId, attributes.deviceId);
      publish(fromStringStrict(StateUpdate.class, state));
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
    // TODO: Replace this with published config message for a ConfigProcessor handler.
    if (requireNonNull(attributes.subType) == SubType.CONFIG) {
      iotAccess.modifyConfig(attributes.deviceRegistryId, attributes.deviceId, SubFolder.UPDATE,
          stringify(payload));
      return new CloudModel();
    }
    Class<?> messageClass = getMessageClassFor(attributes);
    publish(convertToStrict(messageClass, payload));
    return new CloudModel();
  }

  private CloudModel reflectQuery(Envelope attributes, Map<String, Object> payload) {
    checkState(payload.size() == 0, "unexpected non-empty message payload");
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

  private void sendReflectCommand(Envelope reflection, Envelope message, Object payload) {
    String reflectRegistry = reflection.deviceRegistryId;
    String deviceRegistry = reflection.deviceId;
    message.payload = encodeBase64(stringify(payload));
    iotAccess.sendCommand(reflectRegistry, deviceRegistry, SubFolder.UDMI, stringify(message));
  }

  private void stateHandler(Envelope envelope, UdmiState toolState) {
    final String registryId = envelope.deviceRegistryId;
    final String deviceId = envelope.deviceId;

    UdmiConfig udmiConfig = new UdmiConfig();
    udmiConfig.last_state = toolState.timestamp;
    udmiConfig.setup = new SetupUdmiConfig();
    copyFields(deployed, udmiConfig.setup, false);
    udmiConfig.setup.udmi_version = UDMI_VERSION;
    udmiConfig.setup.functions_min = FUNCTIONS_VERSION_MIN;
    udmiConfig.setup.functions_max = FUNCTIONS_VERSION_MAX;

    Map<String, Object> configMap = new HashMap<>();
    configMap.put(SubFolder.UDMI.value(), udmiConfig);
    String contents = stringify(configMap);
    debug("Setting reflector config %s %s %s", registryId, deviceId, contents);
    iotAccess.setProviderAffinity(registryId, deviceId, envelope.source);
    iotAccess.modifyConfig(registryId, deviceId, UPDATE, contents);
  }

  @Override
  public void activate() {
    debug(stringify(deployed));
    iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT_NAME);
    super.activate();
  }
}
