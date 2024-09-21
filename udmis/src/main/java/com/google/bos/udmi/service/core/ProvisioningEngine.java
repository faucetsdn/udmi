package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_FROM;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_WITH;
import static com.google.udmi.util.MetadataMapKeys.UDMI_GENERATION;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_ENABLE;
import static com.google.udmi.util.MetadataMapKeys.UDMI_UPDATED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.schema.CloudModel.Operation.BIND;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple agent to process discovery events and provisionally provisions the iot provider.
 */
@ComponentName("provision")
public class ProvisioningEngine extends ProcessorBase {

  private static final String DISCOVERED_DEVICE_FORMAT = "discovered_%s-%s";
  private static final String GATEWAY_KEY_FORMAT = "%s-%s";

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
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = Operation.CREATE;
    cloudModel.blocked = true;
    ifNullThen(cloudModel.metadata, () -> cloudModel.metadata = new HashMap<>());
    cloudModel.metadata.put(UDMI_DISCOVERED_FROM, stringifyTerse(envelope));
    cloudModel.metadata.put(UDMI_DISCOVERED_WITH, stringifyTerse(discoveryEvent));
    cloudModel.metadata.put(UDMI_UPDATED, isoConvert());
    cloudModel.metadata.put(UDMI_GENERATION, isoConvert(discoveryEvent.generation));
    catchToElse(
        (Supplier<CloudModel>) () -> iotAccess.modelDevice(registryId, expectedId, cloudModel),
        (Consumer<Exception>) e -> error(
            "Error creating device (exists but not bound?): " + friendlyStackTrace(e)));
    bindDeviceToGateway(registryId, expectedId, gatewayId);
    Envelope modelEnvelope = new Envelope();

    modelEnvelope.deviceRegistryId = registryId;
    modelEnvelope.deviceId = expectedId;
    publish(modelEnvelope, cloudModel);
  }

  private CloudModel getCachedModel(String deviceRegistryId, String gatewayId) {
    String gatewayKey = format(GATEWAY_KEY_FORMAT, deviceRegistryId, gatewayId);
    return scanAgent.computeIfAbsent(gatewayKey, key -> new CloudModel());
  }

  private synchronized Map<String, CloudModel> refreshModelDevices(String deviceRegistryId,
      String gatewayId, Date generation) {
    CloudModel cloudModel = getCachedModel(deviceRegistryId, gatewayId);
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
    return TRUE_OPTION.equals(ifNotNullGet(cloudModel.metadata, m -> m.get(UDMI_PROVISION_ENABLE)));
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
      String family = requireNonNull(discoveryEvent.scan_family, "missing scan_family");
      String addr = requireNonNull(discoveryEvent.scan_addr, "missing scan_addr");
      String expectedId = format(DISCOVERED_DEVICE_FORMAT, family, addr);
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
