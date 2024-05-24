package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ignoreValue;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_FROM;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_WITH;
import static com.google.udmi.util.MetadataMapKeys.UDMI_GENERATION;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_GENERATION;
import static com.google.udmi.util.MetadataMapKeys.UDMI_UPDATED;
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
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple agent to process discovery events and provisionally provisions the iot provider.
 */
@ComponentName("provision")
public class ProvisioningEngine extends ProcessorBase {

  private static final String EXPECTED_DEVICE_FORMAT = "%s-%s";

  private final Map<String, CloudModel> scanAgent = new ConcurrentHashMap<>();

  public ProvisioningEngine(EndpointConfiguration config) {
    super(config);
  }

  private void bindDeviceToGateway(String registryId, String proxyId, String gatewayId) {
    CloudModel model = new CloudModel();
    model.operation = BIND;
    model.device_ids = new HashMap<>();
    model.device_ids.put(proxyId, new CloudModel());
    iotAccess.modelDevice(registryId, gatewayId, model);
  }

  private void createDeviceEntry(String registryId, String expectedId, String gatewayId,
      Envelope envelope, DiscoveryEvents discoveryEvent) {
    CloudModel deviceModel = new CloudModel();
    deviceModel.operation = Operation.CREATE;
    deviceModel.blocked = true;
    ifNullThen(deviceModel.metadata, () -> deviceModel.metadata = new HashMap<>());
    deviceModel.metadata.put(UDMI_DISCOVERED_FROM, stringifyTerse(envelope));
    deviceModel.metadata.put(UDMI_DISCOVERED_WITH, stringifyTerse(discoveryEvent));
    deviceModel.metadata.put(UDMI_UPDATED, isoConvert());
    deviceModel.metadata.put(UDMI_GENERATION, isoConvert(discoveryEvent.generation));
    catchToElse(ignoreValue(iotAccess.modelDevice(registryId, expectedId, deviceModel)),
        e -> error("Error creating device (exists but not bound?): " + friendlyStackTrace(e)));
    bindDeviceToGateway(registryId, expectedId, gatewayId);
  }

  private CloudModel getCloudModel(String deviceRegistryId, String gatewayId) {
    String gatewayKey = format(EXPECTED_DEVICE_FORMAT, deviceRegistryId, gatewayId);
    return scanAgent.computeIfAbsent(gatewayKey, key -> new CloudModel());
  }

  private synchronized Map<String, CloudModel> refreshModelDevices(String deviceRegistryId,
      String gatewayId, Date generation) {
    CloudModel cloudModel = getCloudModel(deviceRegistryId, gatewayId);
    if (!generation.equals(cloudModel.timestamp)) {
      cloudModel.timestamp = generation;
      cloudModel.device_ids = null;
      CloudModel fetchedModel =
          catchToNull(() -> iotAccess.fetchDevice(deviceRegistryId, gatewayId));
      if (fetchedModel == null) {
        warn("Scan device %s/%s not found, ignoring results", deviceRegistryId, gatewayId);
        return null;
      }
      if (fetchedModel.resource_type != Resource_type.GATEWAY) {
        warn("Scan device %s/%s is not a gateway, ignoring results", deviceRegistryId, gatewayId);
        return null;
      }
      cloudModel.metadata = fetchedModel.metadata;
      cloudModel.device_ids = fetchedModel.device_ids;
      info("Scan device %s/%s generation %s, provisioning %s", deviceRegistryId, gatewayId,
          isoConvert(generation), shouldProvision(generation, cloudModel));
    }
    return ifTrueGet(shouldProvision(generation, cloudModel), cloudModel.device_ids);
  }

  private boolean shouldProvision(Date generation, CloudModel cloudModel) {
    Date provisioningGeneration = getDate(
        ifNotNullGet(cloudModel.metadata, m -> m.get(UDMI_PROVISION_GENERATION)));
    return generation.equals(provisioningGeneration);
  }

  /**
   * Process any discovery events floating around.
   */
  @MessageHandler
  public void discoveryEvent(DiscoveryEvents discoveryEvent) {
    try {
      Envelope envelope = getContinuation(discoveryEvent).getEnvelope();
      String registryId = envelope.deviceRegistryId;
      String gatewayId = envelope.deviceId;
      if (registryId == null || gatewayId == null) {
        info("Skipping incomplete discovery event for %s/%s", registryId, gatewayId);
        return;
      }
      Date generation = requireNonNull(discoveryEvent.generation, "missing scan generation");
      Map<String, CloudModel> deviceIds = refreshModelDevices(registryId, gatewayId, generation);
      if (deviceIds == null) {
        info("Scan device %s/%s provisioning disabled", registryId, gatewayId);
        return;
      }
      ProtocolFamily family = requireNonNull(discoveryEvent.scan_family, "missing scan_family");
      String addr = requireNonNull(discoveryEvent.scan_addr, "missing scan_addr");
      String expectedId = format(EXPECTED_DEVICE_FORMAT, family, addr);
      if (deviceIds.containsKey(expectedId)) {
        debug("Scan device %s/%s target %s already registered", registryId, gatewayId, expectedId);
      } else {
        notice("Scan device %s/%s target %s missing, creating", registryId, gatewayId, expectedId);
        createDeviceEntry(registryId, expectedId, gatewayId, envelope, discoveryEvent);
        deviceIds.put(expectedId, new CloudModel());
      }
    } catch (Exception e) {
      error("Error during discovery event processing: " + friendlyStackTrace(e));
    }
  }
}