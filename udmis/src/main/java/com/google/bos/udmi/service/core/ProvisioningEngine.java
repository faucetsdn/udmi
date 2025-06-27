package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_FROM;
import static com.google.udmi.util.MetadataMapKeys.UDMI_DISCOVERED_WITH;
import static com.google.udmi.util.MetadataMapKeys.UDMI_GENERATION;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_ENABLE;
import static com.google.udmi.util.MetadataMapKeys.UDMI_UPDATED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.schema.CloudModel.ModelOperation.BIND;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.JsonUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyDiscoveryState.Phase;

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
    iotAccess.modelDevice(registryId, gatewayId, model, null);
  }

  private void processDeviceEntryChange(String registryId, String expectedId, String deviceId,
      Envelope envelope, DiscoveryEvents discoveryEvent, boolean shouldBindToGateway,
      boolean isUpdate) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = isUpdate ? ModelOperation.UPDATE : ModelOperation.CREATE;
    cloudModel.blocked = true;
    ifNullThen(cloudModel.metadata, () -> cloudModel.metadata = new HashMap<>());
    cloudModel.metadata.put(UDMI_DISCOVERED_FROM, stringifyTerse(envelope));
    cloudModel.metadata.put(UDMI_DISCOVERED_WITH, stringifyTerse(discoveryEvent));
    cloudModel.metadata.put(UDMI_UPDATED, isoConvert());
    cloudModel.metadata.put(UDMI_GENERATION, isoConvert(discoveryEvent.generation));
    catchToElse(
        (Supplier<CloudModel>) () -> iotAccess.modelDevice(registryId, expectedId, cloudModel,
            null),
        (Consumer<Exception>) e -> error(
            format("Error %sing device (exists but not bound?): %s", cloudModel.operation,
                friendlyStackTrace(e))));
    if (shouldBindToGateway && !isUpdate) {
      bindDeviceToGateway(registryId, expectedId, deviceId);
    }
    Envelope modelEnvelope = new Envelope();

    modelEnvelope.deviceRegistryId = registryId;
    modelEnvelope.deviceId = expectedId;
    publish(modelEnvelope, cloudModel);
  }

  private CloudModel getCachedModel(String deviceRegistryId, String gatewayId) {
    String gatewayKey = format(GATEWAY_KEY_FORMAT, deviceRegistryId, gatewayId);
    return scanAgent.computeIfAbsent(gatewayKey, key -> new CloudModel());
  }

  private CloudModel fetchDeviceModel(String deviceRegistryId, String deviceId) {
    return catchToNull(() -> iotAccess.fetchDevice(deviceRegistryId, deviceId));
  }

  private void updateDeviceModel(String deviceRegistryId, String deviceId, CloudModel model) {
    iotAccess.modelDevice(deviceRegistryId, deviceId, model, null);
  }

  private boolean isGateway(CloudModel deviceModel) {
    return deviceModel != null && deviceModel.resource_type == Resource_type.GATEWAY;
  }


  private synchronized Set<String> refreshModelDevices(String deviceRegistryId,
      String gatewayId, Date generation, CloudModel fetchedModel) {
    CloudModel cloudModel = getCachedModel(deviceRegistryId, gatewayId);
    if (!generation.equals(cloudModel.timestamp)) {
      cloudModel.timestamp = generation;
      cloudModel.device_ids = null;
      cloudModel.metadata = fetchedModel.metadata;
      cloudModel.gateway = fetchedModel.gateway;
      info("Scan device %s/%s generation %s, provisioning %s", deviceRegistryId, gatewayId,
          isoConvert(generation), shouldProvision(generation, cloudModel));
    }
    return ifTrueGet(shouldProvision(generation, cloudModel),
        () -> new HashSet<>(cloudModel.gateway.proxy_ids));
  }

  private boolean shouldProvision(Date generation, CloudModel cloudModel) {
    return TRUE_OPTION.equals(ifNotNullGet(cloudModel.metadata, m -> m.get(UDMI_PROVISION_ENABLE)));
  }

  /**
   * Process discovery state events looking for end-of-discovery triggers.
   */
  @MessageHandler
  public void discoveryState(DiscoveryState discoveryState) {
    MessageContinuation continuation = getContinuation(discoveryState);
    Envelope envelope = continuation.getEnvelope();
    AtomicBoolean triggered = new AtomicBoolean();

    CloudModel cloudModel = fetchDeviceModel(envelope.deviceRegistryId, envelope.deviceId);
    Map<String, String> metadata = ifNotNullGet(cloudModel, model -> model.metadata);
    if (metadata == null) {
      error("Missing cloud model for %s/%s", envelope.deviceRegistryId, envelope.deviceId);
      return;
    }

    HashMap<String, FamilyDiscoveryState> families = discoveryState.families;
    if (families == null) {
      return;
    }

    families.forEach((family, familyDiscoveryState) -> {
      if (familyDiscoveryState.phase != Phase.STOPPED) {
        return;
      }
      Date thisGeneration = requireNonNull(familyDiscoveryState.generation, "missing generation");
      String discoveryStateKey = discoveryStateKey(family);
      Date lastGeneration = JsonUtil.getDate(metadata.get(discoveryStateKey));
      if (thisGeneration.equals(lastGeneration)) {
        return;
      }
      familyDiscoveryState.phase = Phase.TRIGGER;
      metadata.put(discoveryStateKey, JsonUtil.isoConvert(thisGeneration));
      triggered.set(true);
    });

    ifTrueThen(triggered.get(), () -> {
      updateDeviceModel(envelope.deviceRegistryId, envelope.deviceId, cloudModel);
      continuation.publish(discoveryState);
    });
  }

  private String discoveryStateKey(String discoveryFamily) {
    return format("udmi_discovery_stopped_%s", discoveryFamily);
  }

  /**
   * Process any discovery events floating around.
   */
  @MessageHandler
  public void discoveryEvent(DiscoveryEvents discoveryEvent) {
    try {
      Envelope envelope = getContinuation(discoveryEvent).getEnvelope();
      String registryId = envelope.deviceRegistryId;
      String deviceId = envelope.deviceId;
      if (registryId == null || deviceId == null) {
        info("Skipping incomplete discovery event for %s/%s", registryId, deviceId);
        return;
      }

      CloudModel deviceModel = fetchDeviceModel(registryId, deviceId);
      Date generation = requireNonNull(discoveryEvent.generation, "missing scan generation");

      if (deviceModel == null) {
        warn("Scan device %s/%s not found, ignoring results", registryId, deviceId);
        return;
      }

      if (!shouldProvision(generation, deviceModel)) {
        info("Scan device %s/%s provisioning disabled", registryId, deviceId);
        return;
      }

      String family = requireNonNull(discoveryEvent.family, "missing family");
      String addr = requireNonNull(discoveryEvent.addr, "missing addr");
      String expectedId = format(DISCOVERED_DEVICE_FORMAT, family, addr);
      boolean isGateway = isGateway(deviceModel);

      Set<String> deviceIds = ifTrueGet(isGateway,
          refreshModelDevices(registryId, deviceId, generation, deviceModel),
          catchToNull(() -> iotAccess.listDevices(registryId, null).device_ids.keySet()));

      boolean isUpdate = deviceIds.contains(expectedId);
      notice("Scan %s device %s/%s target %s", isUpdate ? "update" : "create", registryId, deviceId,
          expectedId);
      processDeviceEntryChange(registryId, expectedId, deviceId, envelope, discoveryEvent,
          isGateway, isUpdate);
    } catch (Exception e) {
      error("Error during discovery event processing: " + friendlyStackTrace(e));
    }
  }
}
