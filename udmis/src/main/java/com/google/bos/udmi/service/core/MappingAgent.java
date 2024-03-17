package com.google.bos.udmi.service.core;

import static java.util.Objects.requireNonNull;

import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple agent to process discovery events and provision the iot provider.
 */
@ComponentName("mapping")
public class MappingAgent extends ProcessorBase {

  private static final String EXPECTED_DEVICE_FORMAT = "%s-%s";

  public MappingAgent(EndpointConfiguration config) {
    super(config);
  }

  /**
   * Process any discovery events floating around.
   */
  @MessageHandler
  public void discoveryEvent(DiscoveryEvent discoveryEvent) {
    if (!shouldProcessEvent(discoveryEvent)) {
      return;
    }
    Envelope envelope = getContinuation(discoveryEvent).getEnvelope();
    String registryId = envelope.deviceRegistryId;
    String gatewayId = envelope.deviceId;
    ProtocolFamily family = requireNonNull(discoveryEvent.scan_family, "missing scan_family");
    String addr = requireNonNull(discoveryEvent.scan_addr, "missing scan_addr");
    String expectedId = String.format(EXPECTED_DEVICE_FORMAT, family, addr);
    try {
      CloudModel cloudModel = iotAccess.fetchDevice(registryId, expectedId);
      debug("Found already existing device");
    } catch (Exception e) {
      notice("Creating missing device %s/%s through %s...", registryId, expectedId, gatewayId);
      CloudModel deviceModel = new CloudModel();
      deviceModel.operation = Operation.CREATE;
      deviceModel.blocked = true;
      iotAccess.modelResource(registryId, expectedId, deviceModel);
    }
  }

  private boolean shouldProcessEvent(DiscoveryEvent discoveryEvent) {
    return false;
  }
}
