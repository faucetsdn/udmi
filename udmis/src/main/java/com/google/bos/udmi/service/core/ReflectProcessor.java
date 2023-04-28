package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.util.Objects.requireNonNull;

import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Handle the reflector processor stream for UDMI utility tool clients.
 */
public class ReflectProcessor extends UdmisComponent {

  static final String DEPLOY_FILE = "var/deployed_version.json";
  private final SetupUdmiConfig deployed =
      loadFileStrictRequired(SetupUdmiConfig.class, new File(DEPLOY_FILE));

  @Override
  protected void defaultHandler(Object message) {
    requireNonNull(provider, "iot access provider not set");
    Envelope envelope = getContinuation(message).getEnvelope();
    if (envelope.subFolder == null) {
      stateHandler(envelope, convertToStrict(UdmiState.class, extractUdmiState(message)));
    } else if (envelope.subFolder == SubFolder.UDMI && envelope.subType == SubType.STATE) {
      stateHandler(envelope, convertToStrict(UdmiState.class, message));
    } else if (envelope.subFolder != SubFolder.UDMI) {
      throw new IllegalStateException("Unexpected reflect subfolder " + envelope.subFolder);
    } else {
      System.err.println("Default handler " + message);
    }
  }

  private Object extractUdmiState(Object message) {
    return JsonUtil.asMap(message).get(SubFolder.UDMI.value());
  }

  private void stateHandler(Envelope envelope, UdmiState toolState) {
    final String registryId = envelope.deviceRegistryId;
    final String deviceId = envelope.deviceId;

    UdmiConfig udmiConfig = new UdmiConfig();
    udmiConfig.setup = new SetupUdmiConfig();
    copyFields(deployed, udmiConfig.setup, false);
    udmiConfig.setup.udmi_version = UDMI_VERSION;
    udmiConfig.setup.last_state = toolState.timestamp;
    udmiConfig.setup.functions_min = FUNCTIONS_VERSION_MIN;
    udmiConfig.setup.functions_max = FUNCTIONS_VERSION_MAX;

    Map<String, Object> configMap = new HashMap<>();
    configMap.put(SubFolder.UDMI.value(), udmiConfig);
    String contents = stringify(configMap);
    debug("Setting reflector state %s %s %s", registryId, deviceId, contents);
    provider.updateConfig(registryId, deviceId, contents);
  }
}
