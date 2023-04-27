package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;

import java.io.File;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

public class ReflectProcessor extends UdmisComponent {

  static final String DEPLOY_FILE = "var/deployed_version.json";
  private final SetupUdmiConfig deployed =
      loadFileStrictRequired(SetupUdmiConfig.class, new File(DEPLOY_FILE));

  @Override
  protected void defaultHandler(Object message) {
    Envelope envelope = getContinuation(message).getEnvelope();
    if (envelope.subFolder == null) {
      stateHandler(envelope, convertToStrict(UdmiState.class, message));
    } else if (envelope.subFolder != SubFolder.UDMI) {
      throw new IllegalStateException("Unexpected reflect subfolder " + envelope.subFolder);
    } else {
      System.err.println("Default handler " + message);
    }
  }

  private void stateHandler(Envelope envelope, UdmiState toolState) {
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
    UdmiConfig config = new UdmiConfig();
    config.udmi = new SetupUdmiConfig();
    copyFields(deployed, config.udmi, false);
    config.udmi.udmi_version = UDMI_VERSION;
    config.udmi.last_state = toolState.timestamp;
    config.udmi.functions_min = FUNCTIONS_VERSION_MIN;
    config.udmi.functions_max = FUNCTIONS_VERSION_MAX;
    debug("Setting reflector state %s %s %s", registryId, deviceId, stringify(config));
    // startTime = currentTimestamp();
    // return modify_device_config(registryId, deviceId, UDMIS_FOLDER, subContents, startTime, null);
    publish(config);
  }
}
