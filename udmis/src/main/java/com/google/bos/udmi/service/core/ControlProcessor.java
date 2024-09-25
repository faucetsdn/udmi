package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.Map.Entry;
import udmi.schema.CloudQuery;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.UdmiConfig;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

  TargetProcessor targetProcessor;

  public ControlProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  public void activate() {
    super.activate();
    targetProcessor = UdmiServicePod.getComponent(TargetProcessor.class);
  }

  /**
   * Handle a cloud query command.
   */
  @MessageHandler
  public void cloudQueryHandler(CloudQuery query) {
    CloudQueryHandler.processQuery(this, query);
  }

  /**
   * Handle a udmi config command.
   */
  @MessageHandler
  public void udmiConfigHandler(UdmiConfig config) {
    if (config.timestamp != null) {
      debug("Processing UdmiConfig " + isoConvert(config.timestamp));
      UdmiConfig udmiConfig = UdmiServicePod.getUdmiConfig(null);
      config.setup = udmiConfig.setup;
      publish(config);
      Envelope message = new Envelope();
      message.payload = encodeBase64(stringify(config));
      message.subType = SubType.CONFIG;
      message.subFolder = SubFolder.UDMI;
      iotAccess.getActiveConnections().forEach(entry -> {
        debug("Propagate UdmiConfig to " + entry);
        iotAccess.sendCommand(makeEntryEnvelope(entry), SubFolder.UDMI, stringify(message));
      });
    }
  }

  private Envelope makeEntryEnvelope(Entry<String, String> entry) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = entry.getKey();
    envelope.deviceId = entry.getValue();
    return envelope;
  }
}
