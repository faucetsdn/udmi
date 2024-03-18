package com.google.bos.udmi.service.core;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ignoreValue;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.schema.CloudModel.Operation.BIND;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
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

  private final Map<String, CloudModel> scanAgent = new ConcurrentHashMap<>();

  public MappingAgent(EndpointConfiguration config) {
    super(config);
  }

  private void bindDeviceToGateway(String registryId, String proxyId, String gatewayId) {
    CloudModel model = new CloudModel();
    model.operation = BIND;
    model.device_ids = new HashMap<>();
    model.device_ids.put(proxyId, new CloudModel());
    iotAccess.modelResource(registryId, gatewayId, model);
  }

  private void createDeviceEntry(String registryId, String expectedId) {
    CloudModel deviceModel = new CloudModel();
    deviceModel.operation = Operation.CREATE;
    deviceModel.blocked = true;
    catchToElse(ignoreValue(iotAccess.modelResource(registryId, expectedId, deviceModel)),
        e -> error("Error creating device (exists but not bound?): " + friendlyStackTrace(e)));
  }

  private CloudModel getCloudModel(String deviceRegistryId, String gatewayId) {
    String gatewayKey = format(EXPECTED_DEVICE_FORMAT, deviceRegistryId, gatewayId);
    return scanAgent.computeIfAbsent(gatewayKey, key -> new CloudModel());
  }

  private Map<String, CloudModel> refreshModelDevices(String deviceRegistryId, String gatewayId,
      Date generation) {
    CloudModel cloudModel = getCloudModel(deviceRegistryId, gatewayId);
    if (!generation.equals(cloudModel.timestamp)) {
      String gatewayKey = format(EXPECTED_DEVICE_FORMAT, deviceRegistryId, gatewayId);
      info("New scan %s generation %s, fetching current model...", gatewayKey, generation);
      CloudModel fetchedModel = iotAccess.fetchDevice(deviceRegistryId, gatewayId);
      checkState(fetchedModel.resource_type == Resource_type.GATEWAY, "Device is not a gateway");
      cloudModel.device_ids = fetchedModel.device_ids;
      cloudModel.timestamp = generation;
    }
    return cloudModel.device_ids;
  }

  /**
   * Process any discovery events floating around.
   */
  @MessageHandler
  public void discoveryEvent(DiscoveryEvent discoveryEvent) {
    Envelope envelope = getContinuation(discoveryEvent).getEnvelope();
    String registryId = envelope.deviceRegistryId;
    String gatewayId = envelope.deviceId;
    Date generation = discoveryEvent.generation;
    Map<String, CloudModel> deviceIds = refreshModelDevices(registryId, gatewayId, generation);
    ProtocolFamily family = requireNonNull(discoveryEvent.scan_family, "missing scan_family");
    String addr = requireNonNull(discoveryEvent.scan_addr, "missing scan_addr");
    String expectedId = format(EXPECTED_DEVICE_FORMAT, family, addr);
    if (deviceIds.containsKey(expectedId)) {
      debug("Scan %s/%s device %s already registered", registryId, gatewayId, expectedId);
    } else {
      notice("Scan %s/%s device %s missing, creating", registryId, expectedId, gatewayId);
      createDeviceEntry(registryId, expectedId);
      bindDeviceToGateway(registryId, expectedId, gatewayId);
      deviceIds.put(expectedId, new CloudModel());
    }
  }
}
