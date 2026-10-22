package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.MetadataMapKeys;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import udmi.lib.ProtocolFamily;
import udmi.schema.Category;
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
import udmi.schema.GatewayModel;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;

/**
 * Handler for executing Site Model Discovery scans for a device registry.
 */
public class ModelDiscoveryHandler {

  public static final String MODEL_SOURCE = "model";
  private static final Pattern EXPR_DEVICE_PATTERN =
      Pattern.compile("([-_0-9a-zA-Z$]+):[a-z0-9_]+");
  private static final ConcurrentHashMap<String, Object> REGISTRY_LOCKS =
      new ConcurrentHashMap<>();

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
   * Extract target device IDs referenced in pointset point expressions (excluding $SELF).
   */
  public static Set<String> extractExpressionReferences(Metadata metadata) {
    if (metadata == null || metadata.pointset == null || metadata.pointset.points == null) {
      return Set.of();
    }
    Set<String> refs = new HashSet<>();
    for (PointPointsetModel point : metadata.pointset.points.values()) {
      if (point != null && point.expr != null) {
        Matcher matcher = EXPR_DEVICE_PATTERN.matcher(point.expr);
        while (matcher.find()) {
          String refDevice = matcher.group(1);
          if (!refDevice.startsWith("$")) {
            refs.add(refDevice);
          }
        }
      }
    }
    return refs;
  }

  /**
   * Extract all target device IDs referenced in a device's Metadata model across
   * relationships, pointset point expressions, and gateway bindings.
   */
  public static Set<String> extractReferencedDevices(Metadata metadata) {
    if (metadata == null) {
      return Set.of();
    }
    Set<String> refs = new HashSet<>();
    if (metadata.relationships != null) {
      refs.addAll(metadata.relationships.keySet());
    }
    refs.addAll(extractExpressionReferences(metadata));
    if (metadata.gateway != null) {
      if (metadata.gateway.gateway_id != null && !metadata.gateway.gateway_id.isEmpty()) {
        refs.add(metadata.gateway.gateway_id);
      }
      if (metadata.gateway.proxy_ids != null) {
        metadata.gateway.proxy_ids.stream()
            .filter(Objects::nonNull)
            .forEach(refs::add);
      }
    }
    return refs;
  }

  /**
   * Run the complete model discovery lifecycle: start state bookend, start/count event
   * (event_no = 0), individual device model events (event_no = 1..N), and stop state bookend.
   * Synchronized per registry across all UDMIS processor instances.
   */
  public void process() {
    synchronized (REGISTRY_LOCKS.computeIfAbsent(registryId, k -> new Object())) {
      executeProcess();
    }
  }

  private void executeProcess() {
    processor.info("Starting model discovery for registry %s (generation %s)",
        registryId, generation);

    publishState(Phase.ACTIVE, 0, null);
    int activeCount = 0;

    try {
      CloudModel registryListing = iotAccess != null
          ? iotAccess.listDevices(registryId, null)
          : null;
      Set<String> siteDevices = (registryListing == null || registryListing.device_ids == null)
          ? Set.of()
          : registryListing.device_ids.entrySet().stream()
              .filter(entry -> !isTrue(entry.getValue().blocked))
              .map(Entry::getKey)
              .collect(Collectors.toCollection(TreeSet::new));

      List<String> shardAddrs = getShardAddrs();
      Map<String, Metadata> metadataCache = new HashMap<>();
      List<String> closureDevices = resolveClosure(siteDevices, shardAddrs, metadataCache);

      activeCount = closureDevices.size();
      publishStartEvent(activeCount, shardAddrs);

      if (shouldDetailEntries()) {
        int eventNo = 0;
        for (String deviceId : closureDevices) {
          publishDeviceEvent(deviceId, ++eventNo, metadataCache);
        }
      }

      publishState(Phase.STOPPED, activeCount, null);

      processor.info("Completed model discovery for registry %s (%d active devices)",
          registryId, activeCount);
    } catch (Exception e) {
      udmi.schema.Entry status = new udmi.schema.Entry();
      status.category = Category.DISCOVERY_FAMILY_SCAN;
      status.level = Level.ERROR.value();
      status.message = friendlyStackTrace(e);
      status.timestamp = new Date();
      publishState(Phase.STOPPED, activeCount, status);
      throw new RuntimeException(
          String.format("While executing model discovery for registry %s", registryId), e);
    }
  }

  private boolean shouldDetailEntries() {
    Depth depth = familyConfig != null ? familyConfig.depth : null;
    return depth == null || depth == Depth.DETAILS || depth == Depth.PARTS;
  }

  private List<String> getShardAddrs() {
    if (familyConfig == null || familyConfig.addrs == null || familyConfig.addrs.isEmpty()) {
      return null;
    }
    List<String> addrs = familyConfig.addrs.stream()
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    return addrs.isEmpty() ? null : addrs;
  }

  private List<String> resolveClosure(Set<String> siteDevices, List<String> shardAddrs,
      Map<String, Metadata> metadataCache) {
    if (shardAddrs == null) {
      return new ArrayList<>(siteDevices);
    }
    Set<String> shardSet = new HashSet<>(shardAddrs);
    Map<String, Set<String>> deviceRefs = new HashMap<>();
    Map<String, Set<String>> exprRefs = new HashMap<>();
    for (String deviceId : siteDevices) {
      Metadata metadata = fetchDeviceMetadata(deviceId);
      metadataCache.put(deviceId, metadata);
      deviceRefs.put(deviceId, new HashSet<>(extractReferencedDevices(metadata)));
      exprRefs.put(deviceId, extractExpressionReferences(metadata));
    }

    // Ensure gateway <-> proxy edges are bidirectional even if gateway metadata omitted proxy_ids
    for (String deviceId : siteDevices) {
      Metadata metadata = metadataCache.get(deviceId);
      String gatewayId = (metadata != null && metadata.gateway != null)
          ? metadata.gateway.gateway_id
          : null;
      if (gatewayId != null && !gatewayId.isEmpty() && siteDevices.contains(gatewayId)) {
        deviceRefs.computeIfAbsent(gatewayId, k -> new HashSet<>()).add(deviceId);
        Metadata gwMetadata = metadataCache.get(gatewayId);
        if (gwMetadata != null) {
          if (gwMetadata.gateway == null) {
            gwMetadata.gateway = new GatewayModel();
          }
          Set<String> mergedProxies = new LinkedHashSet<>(
              gwMetadata.gateway.proxy_ids != null ? gwMetadata.gateway.proxy_ids : List.of());
          mergedProxies.add(deviceId);
          gwMetadata.gateway.proxy_ids = new ArrayList<>(mergedProxies);
        }
      }
    }

    Set<String> closure = new TreeSet<>();
    Deque<String> queue = new ArrayDeque<>();

    // 1. Seed with active devices in Shard
    for (String shardId : shardAddrs) {
      if (siteDevices.contains(shardId) && closure.add(shardId)) {
        queue.add(shardId);
      }
    }

    // 2. Seed with 1-hop inbound referrers across all reference types (including to deleted shard
    // devices)
    for (String deviceId : siteDevices) {
      if (!Collections.disjoint(deviceRefs.getOrDefault(deviceId, Set.of()), shardSet)) {
        if (closure.add(deviceId)) {
          queue.add(deviceId);
        }
      }
    }

    // 3. Seed with transitive inbound referrers across pointset expressions (C -> B -> A)
    boolean addedExprInbound = true;
    Set<String> exprTargets = new HashSet<>(shardSet);
    exprTargets.addAll(closure);
    while (addedExprInbound) {
      addedExprInbound = false;
      for (String deviceId : siteDevices) {
        if (!closure.contains(deviceId)
            && !Collections.disjoint(exprRefs.getOrDefault(deviceId, Set.of()), exprTargets)) {
          closure.add(deviceId);
          queue.add(deviceId);
          exprTargets.add(deviceId);
          addedExprInbound = true;
        }
      }
    }

    // 4. Transitive outbound BFS across all reference types
    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (String target : deviceRefs.getOrDefault(current, Set.of())) {
        if (siteDevices.contains(target) && closure.add(target)) {
          queue.add(target);
        }
      }
    }

    return new ArrayList<>(closure);
  }

  private Envelope createDiscoveryEnvelope(SubType subType, String deviceId) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subType = subType;
    envelope.subFolder = SubFolder.DISCOVERY;
    envelope.source = MODEL_SOURCE;
    envelope.transactionId = isoConvert(generation);
    return envelope;
  }

  private DiscoveryEvents createDiscoveryEvent(int eventNo) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.version = UdmiServicePod.UDMI_VERSION;
    event.timestamp = new Date();
    event.generation = generation;
    event.event_no = eventNo;
    event.family = ProtocolFamily.IOT;
    event.source = MODEL_SOURCE;
    return event;
  }

  private void publishState(Phase phase, int activeCount, udmi.schema.Entry status) {
    FamilyDiscoveryState familyState = new FamilyDiscoveryState();
    familyState.generation = generation;
    familyState.phase = phase;
    familyState.active_count = activeCount;
    familyState.source = MODEL_SOURCE;
    familyState.status = status;

    DiscoveryState discoveryState = new DiscoveryState();
    discoveryState.version = UdmiServicePod.UDMI_VERSION;
    discoveryState.timestamp = new Date();
    discoveryState.generation = generation;
    discoveryState.families = new HashMap<>();
    discoveryState.families.put(ProtocolFamily.IOT, familyState);

    processor.publish(createDiscoveryEnvelope(SubType.STATE, null), discoveryState);
  }

  private void publishStartEvent(int activeCount, List<String> shardAddrs) {
    DiscoveryEvents countEvent = createDiscoveryEvent(0);
    countEvent.active_count = activeCount;
    countEvent.addrs = shardAddrs;
    processor.publish(createDiscoveryEnvelope(SubType.EVENTS, null), countEvent);
  }

  private void publishDeviceEvent(String deviceId, int eventNo,
      Map<String, Metadata> metadataCache) {
    DiscoveryEvents deviceEvent = createDiscoveryEvent(eventNo);
    deviceEvent.addr = deviceId;
    deviceEvent.model = metadataCache.containsKey(deviceId)
        ? metadataCache.get(deviceId)
        : fetchDeviceMetadata(deviceId);
    processor.publish(createDiscoveryEnvelope(SubType.EVENTS, deviceId), deviceEvent);
  }

  private Metadata fetchDeviceMetadata(String deviceId) {
    CloudModel deviceModel = iotAccess != null ? iotAccess.fetchDevice(registryId, deviceId) : null;
    if (deviceModel == null) {
      return null;
    }
    String metadataStr = deviceModel.metadata != null
        ? deviceModel.metadata.get(MetadataMapKeys.UDMI_METADATA)
        : null;
    Metadata metadata = (metadataStr != null && !metadataStr.isBlank())
        ? fromString(Metadata.class, metadataStr)
        : null;
    if (deviceModel.gateway != null && deviceModel.gateway.proxy_ids != null
        && !deviceModel.gateway.proxy_ids.isEmpty()) {
      if (metadata == null) {
        metadata = new Metadata();
      }
      if (metadata.gateway == null) {
        metadata.gateway = new GatewayModel();
      }
      Set<String> mergedProxies = new LinkedHashSet<>(
          metadata.gateway.proxy_ids != null ? metadata.gateway.proxy_ids : List.of());
      mergedProxies.addAll(deviceModel.gateway.proxy_ids);
      metadata.gateway.proxy_ids = new ArrayList<>(mergedProxies);
    }
    return metadata;
  }
}
