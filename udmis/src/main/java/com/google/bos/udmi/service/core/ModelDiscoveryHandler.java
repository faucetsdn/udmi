package com.google.bos.udmi.service.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.MapMaker;
import com.google.udmi.util.MetadataMapKeys;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import udmi.lib.ProtocolFamily;
import udmi.schema.Category;
import udmi.schema.CloudModel;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyDiscoveryState.Phase;
import udmi.schema.GatewayModel;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.SystemModel;

/**
 * Handler for executing full-site Model Discovery exports for IoT registries.
 */
public class ModelDiscoveryHandler {

  public static final String MODEL_SUBSET_KEY = "model";
  private static final ConcurrentMap<String, Object> REGISTRY_LOCKS =
      new MapMaker().weakValues().makeMap();

  private final ControlProcessor controller;
  private final IotAccessBase iotAccess;
  private final Envelope envelope;
  private final DiscoveryConfig config;

  /**
   * Create a handler for a site model discovery config message.
   */
  public ModelDiscoveryHandler(ControlProcessor controlProcessor, Envelope envelope,
      DiscoveryConfig config) {
    this.controller = requireNonNull(controlProcessor, "control processor");
    this.iotAccess = requireNonNull(controlProcessor.iotAccess, "iotAccess");
    this.envelope = requireNonNull(envelope, "envelope");
    this.config = requireNonNull(config, "discovery config");
  }

  /**
   * Determine whether the incoming envelope and discovery config request model discovery.
   */
  public static boolean isModelDiscoveryConfig(Envelope envelope, DiscoveryConfig config) {
    if (envelope == null || config == null || !MODEL_SUBSET_KEY.equals(envelope.subSetKey)
        || isNotEmpty(envelope.deviceId)) {
      return false;
    }
    return config.families != null && config.families.get(ProtocolFamily.IOT) != null;
  }

  /**
   * Process a discovery config if it targets site model discovery.
   */
  public static void processDiscoveryConfig(ControlProcessor controlProcessor,
      DiscoveryConfig config) {
    Envelope envelope = controlProcessor.getContinuation(config).getEnvelope();
    if (!isModelDiscoveryConfig(envelope, config)) {
      controlProcessor.debug("Ignoring non-model discovery config for %s/%s (subSetKey=%s)",
          envelope != null ? envelope.deviceRegistryId : null,
          envelope != null ? envelope.deviceId : null,
          envelope != null ? envelope.subSetKey : null);
      return;
    }
    new ModelDiscoveryHandler(controlProcessor, envelope, config).process();
  }

  /**
   * Execute project-wide fan-out or a registry-scoped full-site model export.
   */
  public void process() {
    if (!isNotEmpty(envelope.deviceRegistryId)) {
      fanOutAllRegistries();
    } else {
      exportRegistry(envelope.deviceRegistryId);
    }
  }

  private void fanOutAllRegistries() {
    Set<String> registries = new TreeSet<>(
        ofNullable(iotAccess.getRegistries()).orElse(Set.of()));
    registries.removeIf(ControlProcessor::isReflectRegistry);
    controller.debug("Fanning out model discovery across %d registries", registries.size());
    for (String registryId : registries) {
      Envelope mutated = deepCopy(envelope);
      mutated.deviceRegistryId = registryId;
      mutated.deviceId = null;
      controller.sideProcess(mutated, config);
    }
  }

  private void exportRegistry(String registryId) {
    Date generation = ofNullable(config.families)
        .map(families -> families.get(ProtocolFamily.IOT))
        .map(iotConfig -> iotConfig.generation)
        .or(() -> ofNullable(config.generation))
        .orElseGet(Date::new);
    String transactionId = isoConvert(generation);

    String providerId = iotAccess.getProviderId(registryId);
    checkState(isNotEmpty(providerId), "Missing providerId for registry " + registryId);

    Object lock = REGISTRY_LOCKS.computeIfAbsent(registryId, k -> new Object());
    synchronized (lock) {
      int emittedCount = 0;
      try {
        publishState(registryId, providerId, generation, transactionId, Phase.ACTIVE, null, null);

        TreeSet<String> activeDevices = listActiveDevices(registryId);
        int totalCount = activeDevices.size();
        controller.info("Starting full-site model discovery for %s (%d active devices, gen=%s)",
            registryId, totalCount, transactionId);

        publishMarkerEvent(registryId, providerId, generation, transactionId, 0);

        int eventNo = 0;
        for (String deviceId : activeDevices) {
          eventNo++;
          publishDeviceEvent(registryId, providerId, generation, transactionId, deviceId, eventNo);
          emittedCount = eventNo;
        }

        publishMarkerEvent(registryId, providerId, generation, transactionId, -(totalCount + 1));
        publishState(registryId, providerId, generation, transactionId, Phase.STOPPED, totalCount,
            null);
      } catch (Exception e) {
        controller.error("Model discovery failed for %s: %s", registryId, friendlyStackTrace(e));
        udmi.schema.Entry status = new udmi.schema.Entry();
        status.category = Category.DISCOVERY_FAMILY_SCAN;
        status.level = Level.ERROR.value();
        status.message = isNotEmpty(e.getMessage())
            ? e.getMessage()
            : e.getClass().getSimpleName();
        status.detail = friendlyStackTrace(e);
        status.timestamp = new Date();
        publishState(registryId, providerId, generation, transactionId, Phase.STOPPED, emittedCount,
            status);
      }
    }
  }

  private TreeSet<String> listActiveDevices(String registryId) {
    CloudModel cloudModel = iotAccess.listDevices(registryId, null);
    TreeSet<String> activeDevices = new TreeSet<>();
    if (cloudModel == null || cloudModel.device_ids == null) {
      return activeDevices;
    }
    for (Entry<String, CloudModel> entry : cloudModel.device_ids.entrySet()) {
      CloudModel deviceEntry = entry.getValue();
      if (deviceEntry == null || !isTrue(deviceEntry.blocked)) {
        activeDevices.add(entry.getKey());
      }
    }
    return activeDevices;
  }

  private void publishState(String registryId, String providerId, Date generation,
      String transactionId, Phase phase, Integer activeCount, udmi.schema.Entry status) {
    FamilyDiscoveryState familyState = new FamilyDiscoveryState();
    familyState.generation = generation;
    familyState.phase = phase;
    familyState.active_count = activeCount;
    familyState.status = status;

    DiscoveryState discoveryState = new DiscoveryState();
    discoveryState.timestamp = new Date();
    discoveryState.version = UdmiServicePod.UDMI_VERSION;
    discoveryState.generation = generation;
    discoveryState.families = new HashMap<>();
    discoveryState.families.put(ProtocolFamily.IOT, familyState);

    controller.publish(createOutputEnvelope(registryId, providerId, SubType.STATE, transactionId),
        discoveryState);
  }

  private void publishMarkerEvent(String registryId, String providerId, Date generation,
      String transactionId, int eventNo) {
    DiscoveryEvents event = createDiscoveryEvent(generation, eventNo);
    controller.publish(createOutputEnvelope(registryId, providerId, SubType.EVENTS, transactionId),
        event);
  }

  private void publishDeviceEvent(String registryId, String providerId, Date generation,
      String transactionId, String deviceId, int eventNo) {
    DiscoveryEvents event = createDiscoveryEvent(generation, eventNo);
    event.addr = deviceId;
    event.model = fetchDeviceMetadata(registryId, deviceId);
    controller.publish(createOutputEnvelope(registryId, providerId, SubType.EVENTS, transactionId),
        event);
  }

  private DiscoveryEvents createDiscoveryEvent(Date generation, int eventNo) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.timestamp = new Date();
    event.version = UdmiServicePod.UDMI_VERSION;
    event.generation = generation;
    event.family = ProtocolFamily.IOT;
    event.event_no = eventNo;
    return event;
  }

  private Envelope createOutputEnvelope(String registryId, String providerId, SubType subType,
      String transactionId) {
    Envelope out = new Envelope();
    out.projectId = envelope.projectId;
    out.deviceRegistryId = registryId;
    out.deviceId = null;
    out.providerId = providerId;
    out.subSetKey = MODEL_SUBSET_KEY;
    out.subType = subType;
    out.subFolder = SubFolder.DISCOVERY;
    out.transactionId = transactionId;
    return out;
  }

  private Metadata fetchDeviceMetadata(String registryId, String deviceId) {
    try {
      CloudModel deviceModel = iotAccess.fetchDevice(registryId, deviceId);
      if (deviceModel == null) {
        return null;
      }
      String metadataStr = deviceModel.metadata != null
          ? deviceModel.metadata.get(MetadataMapKeys.UDMI_METADATA)
          : null;
      Metadata metadata = (metadataStr != null && !metadataStr.isBlank())
          ? fromString(Metadata.class, metadataStr)
          : null;
      if (deviceModel.gateway != null && deviceModel.gateway.proxy_ids != null) {
        TreeSet<String> liveProxies = new TreeSet<>();
        deviceModel.gateway.proxy_ids.stream().filter(Objects::nonNull).forEach(liveProxies::add);
        if (metadata == null && !liveProxies.isEmpty()) {
          metadata = new Metadata();
          metadata.timestamp = new Date();
          metadata.version = UdmiServicePod.UDMI_VERSION;
          metadata.system = new SystemModel();
        }
        if (metadata != null) {
          if (metadata.gateway == null) {
            metadata.gateway = new GatewayModel();
          }
          metadata.gateway.proxy_ids = new ArrayList<>(liveProxies);
        }
      }
      return metadata;
    } catch (Exception e) {
      throw new RuntimeException(
          format("Failed fetching model metadata for %s/%s: %s", registryId, deviceId,
              e.getMessage()), e);
    }
  }
}
