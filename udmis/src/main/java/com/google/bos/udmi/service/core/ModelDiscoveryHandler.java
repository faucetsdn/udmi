package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.fromString;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.MetadataMapKeys;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyDiscoveryState.Phase;
import udmi.schema.Metadata;

/**
 * Handler for executing Site Model Discovery scans for a device registry.
 */
public class ModelDiscoveryHandler {

  public static final String MODEL_SOURCE = "model";

  private final ProcessorBase processor;
  private final IotAccessBase iotAccess;
  private final String registryId;
  private final FamilyDiscoveryConfig familyConfig;
  private final Date generation;

  /**
   * Create a new ModelDiscoveryHandler instance.
   */
  public ModelDiscoveryHandler(ProcessorBase processor, String registryId,
      FamilyDiscoveryConfig familyConfig, Date generation) {
    this.processor = processor;
    this.iotAccess = processor.iotAccess;
    this.registryId = registryId;
    this.familyConfig = familyConfig;
    this.generation = generation;
  }

  /**
   * Determine whether the given envelope and discovery config target Site Model Discovery.
   */
  public static boolean isModelDiscoveryConfig(Envelope envelope, DiscoveryConfig config) {
    if (envelope == null || config == null
        || envelope.subType != SubType.CONFIG || envelope.subFolder != SubFolder.DISCOVERY) {
      return false;
    }
    FamilyDiscoveryConfig iotConfig = config.families != null
        ? config.families.get(ProtocolFamily.IOT)
        : null;
    return iotConfig != null && MODEL_SOURCE.equals(iotConfig.source);
  }

  /**
   * Process a DiscoveryConfig targeting model discovery.
   */
  public static void processDiscoveryConfig(ProcessorBase processor, Envelope envelope,
      DiscoveryConfig config) {
    if (!isModelDiscoveryConfig(envelope, config) || envelope.deviceRegistryId == null) {
      return;
    }
    FamilyDiscoveryConfig iotConfig = config.families.get(ProtocolFamily.IOT);
    processDiscovery(processor, envelope.deviceRegistryId, iotConfig, config.generation);
  }

  /**
   * Execute a model discovery scan for the given registry and family configuration.
   */
  public static void processDiscovery(ProcessorBase processor, String registryId,
      FamilyDiscoveryConfig familyConfig, Date fallbackGeneration) {
    if (registryId == null) {
      return;
    }
    Date generation = ofNullable(familyConfig).map(c -> c.generation).orElse(fallbackGeneration);
    if (generation == null) {
      return;
    }
    new ModelDiscoveryHandler(processor, registryId, familyConfig, generation).process();
  }

  /**
   * Run the complete model discovery lifecycle: start state bookend, start/count event
   * (event_no = 0), individual device model events (event_no = 1..N), and stop state bookend.
   */
  public synchronized void process() {
    processor.info("Starting model discovery for registry %s (generation %s)",
        registryId, generation);

    publishState(Phase.ACTIVE, 0);

    CloudModel registryListing = iotAccess != null ? iotAccess.listDevices(registryId, null) : null;
    List<String> activeDevices = (registryListing == null || registryListing.device_ids == null)
        ? List.of()
        : registryListing.device_ids.entrySet().stream()
            .filter(entry -> !isTrue(entry.getValue().blocked))
            .map(Entry::getKey)
            .filter(this::matchesTarget)
            .sorted()
            .toList();

    int activeCount = activeDevices.size();
    publishStartEvent(activeCount);

    if (shouldDetailEntries()) {
      int eventNo = 0;
      for (String deviceId : activeDevices) {
        publishDeviceEvent(deviceId, ++eventNo);
      }
    }

    publishState(Phase.STOPPED, activeCount);

    processor.info("Completed model discovery for registry %s (%d active devices)",
        registryId, activeCount);
  }

  private boolean shouldDetailEntries() {
    Depth depth = familyConfig != null ? familyConfig.depth : null;
    return depth == null || depth == Depth.DETAILS || depth == Depth.PARTS;
  }

  private boolean matchesTarget(String addr) {
    List<String> targets = familyConfig != null ? familyConfig.addrs : null;
    return targets == null || targets.isEmpty() || (addr != null && targets.contains(addr));
  }

  private Envelope createDiscoveryEnvelope(SubType subType, String deviceId) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subType = subType;
    envelope.subFolder = SubFolder.DISCOVERY;
    envelope.source = MODEL_SOURCE;
    return envelope;
  }

  private DiscoveryEvents createDiscoveryEvent(int eventNo) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.version = UdmiServicePod.UDMI_VERSION;
    event.timestamp = new Date();
    event.generation = generation;
    event.event_no = eventNo;
    event.family = ProtocolFamily.IOT;
    return event;
  }

  private void publishState(Phase phase, int activeCount) {
    FamilyDiscoveryState familyState = new FamilyDiscoveryState();
    familyState.generation = generation;
    familyState.phase = phase;
    familyState.active_count = activeCount;

    DiscoveryState discoveryState = new DiscoveryState();
    discoveryState.version = UdmiServicePod.UDMI_VERSION;
    discoveryState.timestamp = new Date();
    discoveryState.generation = generation;
    discoveryState.families = new HashMap<>();
    discoveryState.families.put(ProtocolFamily.IOT, familyState);

    emitMessage(createDiscoveryEnvelope(SubType.STATE, null), discoveryState);
  }

  private void publishStartEvent(int activeCount) {
    DiscoveryEvents countEvent = createDiscoveryEvent(0);
    countEvent.active_count = activeCount;
    emitMessage(createDiscoveryEnvelope(SubType.EVENTS, null), countEvent);
  }

  private void publishDeviceEvent(String deviceId, int eventNo) {
    DiscoveryEvents deviceEvent = createDiscoveryEvent(eventNo);
    deviceEvent.addr = deviceId;

    CloudModel deviceModel = iotAccess != null ? iotAccess.fetchDevice(registryId, deviceId) : null;
    String metadataStr = (deviceModel != null && deviceModel.metadata != null)
        ? deviceModel.metadata.get(MetadataMapKeys.UDMI_METADATA)
        : null;
    deviceEvent.model = (metadataStr != null && metadataStr.startsWith("{"))
        ? fromString(Metadata.class, metadataStr)
        : null;

    emitMessage(createDiscoveryEnvelope(SubType.EVENTS, deviceId), deviceEvent);
  }

  private void emitMessage(Envelope envelope, Object payload) {
    if (processor instanceof UufiProcessor uufiProcessor) {
      uufiProcessor.publishModelDiscovery(envelope, payload);
      return;
    }
    ifNotNullThen(UdmiServicePod.maybeGetComponent(UufiProcessor.class),
        uufi -> uufi.publishModelDiscovery(envelope, payload));
    processor.publish(envelope, payload);
  }
}
