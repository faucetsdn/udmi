package com.google.bos.udmi.service.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Handle the reflector processor stream for UDMI utility tool clients.
 */
public class ReflectProcessor extends UdmisComponent {

  public static final String PAYLOAD_KEY = "payload";
  static final String DEPLOY_FILE = "var/deployed_version.json";
  private final SetupUdmiConfig deployed =
      loadFileStrictRequired(SetupUdmiConfig.class, new File(DEPLOY_FILE));

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    try {
      requireNonNull(provider, "iot access provider not set");
      Envelope envelope = continuation.getEnvelope();
      if (envelope.subFolder == null) {
        stateHandler(envelope, extractUdmiState(message));
      } else if (envelope.subFolder != SubFolder.UDMI) {
        throw new IllegalStateException("Unexpected reflect subfolder " + envelope.subFolder);
      } else {
        Envelope attributes = extractReflectEnvelope(envelope.projectId, message);
        processReflection(attributes, extractMessagePayload(message));
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing reflect handler", e);
    }
  }

  private Map<String, Object> extractMessagePayload(Object message) {
    String payload = (String) toMap(message).remove(PAYLOAD_KEY);
    return toMap(GeneralUtils.decodeBase64(payload));
  }

  private Envelope extractReflectEnvelope(String projectId, Object message) {
    Envelope envelope = convertToStrict(Envelope.class, message);
    envelope.projectId = projectId;
    envelope.payload = null; // Remove here before payload is handled separately.
    return envelope;
  }

  private UdmiState extractUdmiState(Object message) {
    Map<String, Object> stringObjectMap = JsonUtil.asMap(message);
    UdmiState udmiState =
        convertToStrict(UdmiState.class, stringObjectMap.get(SubFolder.UDMI.value()));
    udmiState.timestamp = JsonUtil.getDate((String) stringObjectMap.get(TIMESTAMP_KEY));
    return udmiState;
  }

  private CloudModel reflectModel(Envelope attributes) {
    throw new RuntimeException("Not yet implemented");
  }

  private CloudModel processReflection(Envelope attributes, Map<String, Object> payload) {
    try {
      switch (attributes.subType) {
        case QUERY:
          return reflectQuery(attributes, payload);
        case MODEL:
          return reflectModel(attributes);
        default:
          return reflectPropagate(attributes);
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing reflect message type " + attributes.subType, e);
    }
  }

  private CloudModel reflectQuery(Envelope attributes, Map<String, Object> payload) {
    checkState(payload.size() == 0, "unexpected non-empty message payload");
    switch (attributes.subFolder) {
      case CLOUD:
        return reflectQueryCLoud(attributes);
      default:
        throw new RuntimeException("Unknown query folder: " + attributes.subFolder);
    }
  }

  private CloudModel reflectQueryCLoud(Envelope attributes) {
    return attributes.deviceId != null
        ? queryCloudDevice(attributes) : queryCloudRegistry(attributes);
  }

  private CloudModel queryCloudRegistry(Envelope attributes) {
    return provider.listRegistryDevices(attributes.deviceRegistryId);
  }

  private CloudModel queryCloudDevice(Envelope attributes) {
    throw new RuntimeException("Not yet implemented");
  }

  private CloudModel reflectPropagate(Envelope attributes) {
    throw new RuntimeException("Not yet implemented");
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
    debug("Setting reflector state %s %s %s", registryId, deviceId, contents);
    provider.updateConfig(registryId, deviceId, contents);
  }
}
