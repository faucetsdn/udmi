package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
import static com.google.udmi.util.Common.UNKNOWN_DEVICE_ID_PREFIX;
import static com.google.udmi.util.Common.generateColonKey;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.loadFileStrict;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_ENABLE;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_GENERATION;
import static com.google.udmi.util.SiteModel.METADATA_JSON;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.udmi.util.Common;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.GatewayModel;
import udmi.schema.LocalnetModel;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetModel;
import udmi.schema.RefDiscovery;
import udmi.schema.SystemModel;

;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent {

  private static final String NO_DISCOVERY = "not_discovered";
  public static final String MAPPER_TOOL_NAME = "mapper";
  private final ExecutionConfiguration executionConfiguration;
  private final String deviceId;
  private CloudIotManager cloudIotManager;
  private SiteModel siteModel;
  private Date generationDate;

  private AtomicInteger suffixToStart = new AtomicInteger(1);

  /**
   * Create an agent given the configuration.
   */
  public MappingAgent(ExecutionConfiguration exeConfig) {
    executionConfiguration = exeConfig;
    deviceId = requireNonNull(exeConfig.device_id, "device id not specified");
    initialize();
  }

  /**
   * Create a simple agent from the given profile file.
   */
  public MappingAgent(String profilePath) {
    this(ConfigUtil.readExeConfig(new File(profilePath)));
  }

  /**
   * Let's go.
   */
  public static void main(String[] args) {
    List<String> argsList = new ArrayList<>(Arrays.asList(args));
    MappingAgent agent = new MappingAgent(removeNextArg(argsList, "execution profile"));
    try {
      agent.process(argsList);
    } finally {
      agent.shutdown();
    }

    Common.forcedDelayedShutdown();
  }

  void process(List<String> argsList) {
    checkState(!argsList.isEmpty(), "no arguments found, no commands given!");
    while (!argsList.isEmpty()) {
      String mappingCommand = removeNextArg(argsList, "mapping command");
      switch (mappingCommand) {
        case "provision" -> setupProvision();
        case "discover" -> initiateDiscover();
        case "map" -> mapDiscoveredDevices();
        default -> throw new RuntimeException("Unknown mapping command " + mappingCommand);
      }
      System.err.printf("Completed mapper %s command%n", mappingCommand);
    }
  }

  private void setupProvision() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = ImmutableMap.of(UDMI_PROVISION_ENABLE, "true");
    cloudIotManager.updateDevice(deviceId, cloudModel, ModelOperation.MODIFY);
  }

  private void initiateDiscover() {
    Set<String> families = catchToNull(
        () -> siteModel.getMetadata(deviceId).discovery.families.keySet());
    checkNotNull(families, "No discovery families defined");

    generationDate = new Date();
    String generation = isoConvert(generationDate);
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = ImmutableMap.of(UDMI_PROVISION_GENERATION, generation);
    cloudIotManager.updateDevice(deviceId, cloudModel, ModelOperation.MODIFY);

    System.err.printf("Initiating %s discovery on %s/%s at %s%n", families,
        siteModel.getRegistryId(), deviceId, generation);

    DiscoveryConfig discoveryConfig = new DiscoveryConfig();

    HashMap<String, FamilyDiscoveryConfig> familiesMap = new HashMap<>();
    discoveryConfig.families = familiesMap;
    families.forEach(family -> familiesMap.computeIfAbsent(family, this::getFamilyDiscoveryConfig));

    cloudIotManager.modifyConfig(deviceId, SubFolder.DISCOVERY, stringify(discoveryConfig));
  }

  private FamilyDiscoveryConfig getFamilyDiscoveryConfig(String family) {
    FamilyDiscoveryConfig familyDiscoveryConfig = new FamilyDiscoveryConfig();
    familyDiscoveryConfig.generation = generationDate;
    familyDiscoveryConfig.depth = Depth.DETAILS;
    return familyDiscoveryConfig;
  }

  private void mapDiscoveredDevices() {
    List<Entry<String, Metadata>> mappedDiscoveredEntries = getMappedDiscoveredEntries();
    Map<String, Metadata> devicesEntriesMap = getDevicesEntries();
    Map<String, String> devicesFamilyAddressMap = getDeviceFamilyAddressMap();

    Set<String> devicesPresent = new HashSet<>();
    mappedDiscoveredEntries.forEach(entry -> {

      if (devicesEntriesMap.containsKey(entry.getKey())) {
        System.err.println("Skipping existing device file for family::address = " + entry.getKey());
        String deviceId = devicesFamilyAddressMap.get(entry.getKey());
        devicesPresent.add(deviceId);
        siteModel.updateDevice(deviceId, entry.getValue());
      } else {
        String newDeviceId = getNextDeviceId();
        while (devicesPresent.contains(newDeviceId)) {
          newDeviceId = getNextDeviceId();
        }
        devicesPresent.add(newDeviceId);
        siteModel.createNewDevice(newDeviceId, entry.getValue());
      }
    });

    updateProxyIdsForDiscoveryNode(devicesPresent);
  }

  private Map<String, String> getDeviceFamilyAddressMap() {
    Map<String, String> devicesFamilyAddressMap = new HashMap<>();

    for (String device : siteModel.allMetadata().keySet()) {
      Metadata deviceMetadata = siteModel.allMetadata().get(device);
      if (deviceMetadata.localnet == null || deviceMetadata.localnet.families == null) {
        continue;
      }
      Map<String, FamilyLocalnetModel> deviceFamilies = deviceMetadata.localnet.families;
      for (String familyName : deviceFamilies.keySet()) {
        devicesFamilyAddressMap.put(generateColonKey(familyName,
            deviceFamilies.get(familyName).addr), device);
      }
    }

    return devicesFamilyAddressMap;
  }

  private String getNextDeviceId() {
    return UNKNOWN_DEVICE_ID_PREFIX + suffixToStart.getAndIncrement();
  }

  private List<Entry<String, Metadata>> getMappedDiscoveredEntries() {
    File extrasDir = siteModel.getExtrasDir();
    File[] extras = extrasDir.listFiles();
    if (extras == null || extras.length == 0) {
      throw new RuntimeException("No extras found to reconcile");
    }
    List<Entry<String, Metadata>> mappedDiscoveredEntries = Arrays.stream(extras)
        .map(this::convertExtraDiscoveredEvents)
        .filter(entry -> !entry.getKey().equals(NO_DISCOVERY)).toList();
    return mappedDiscoveredEntries;
  }

  private Map<String, Metadata> getDevicesEntries() {
    Map<String, Metadata> devicesEntriesMap = new HashMap<>();

    for (Metadata deviceMetadata : siteModel.allMetadata().values()) {
      if (deviceMetadata.localnet == null || deviceMetadata.localnet.families == null) {
        continue;
      }
      Map<String, FamilyLocalnetModel> deviceFamilies = deviceMetadata.localnet.families;
      for (String familyName : deviceFamilies.keySet()) {
        devicesEntriesMap.put(generateColonKey(familyName,
            deviceFamilies.get(familyName).addr), deviceMetadata);
      }
    }

    return devicesEntriesMap;
  }

  private void updateProxyIdsForDiscoveryNode(Set<String> proxyIds) {
    File gatewayMetadata = siteModel.getDeviceFile(deviceId, METADATA_JSON);
    Metadata metadata = loadFileStrictRequired(Metadata.class, gatewayMetadata);
    List<String> idList = ofNullable(metadata.gateway.proxy_ids).orElse(ImmutableList.of());
    Set<String> idSet = new HashSet<>(idList);
    idSet.addAll(proxyIds);
    idSet.remove(deviceId);
    metadata.gateway.proxy_ids = new ArrayList<>(idSet);
    System.err.printf("Augmenting gateway %s metadata file, %d -> %d%n", deviceId, idList.size(),
        idSet.size());
    writeFile(metadata, gatewayMetadata);
  }

  private Entry<String, Metadata> convertExtraDiscoveredEvents(File file) {
    DiscoveryEvents discoveryEvents = loadFileStrict(DiscoveryEvents.class,
        new File(file, "cloud_metadata/udmi_discovered_with.json"));
    boolean isInvalidDiscoveryEvent = false;
    if (discoveryEvents.family == null || discoveryEvents.addr == null) {
      System.err.println("Invalid discovery event, family or address not present");
      isInvalidDiscoveryEvent = true;
    }
    if (discoveryEvents == null || isInvalidDiscoveryEvent) {
      return Map.entry(NO_DISCOVERY, new Metadata());
    }
    Metadata metadata = new Metadata();
    metadata.version = UDMI_VERSION;
    metadata.timestamp = discoveryEvents.timestamp;
    metadata.system = new SystemModel();
    metadata.gateway = new GatewayModel();
    populateMetadataPoints(discoveryEvents, metadata);
    populateMetadataLocalnet(discoveryEvents, metadata);
    metadata.gateway.gateway_id = deviceId;
    return Map.entry(generateColonKey(discoveryEvents.family,
        discoveryEvents.addr), metadata);
  }

  private void populateMetadataPoints(DiscoveryEvents discoveryEvents, Metadata metadata) {
    HashMap<String, PointPointsetModel> points = new HashMap<>();

    Map<String, RefDiscovery> refDiscoveryMap = discoveryEvents.refs;
    if (refDiscoveryMap == null) {
      System.err.println("No reference discovery present");
      return;
    }

    for (Map.Entry<String, RefDiscovery> entry : refDiscoveryMap.entrySet()) {
      String key = entry.getKey();
      RefDiscovery refDiscovery = entry.getValue();

      // Create a new PointPointsetModel
      PointPointsetModel pointPointsetModel = new PointPointsetModel();
      pointPointsetModel.ref = key;
      pointPointsetModel.units = refDiscovery.units;

      // Add the new PointPointsetModel to the result map
      points.put(refDiscovery.point, pointPointsetModel);
    }

    PointsetModel pointSetModel = new PointsetModel();
    pointSetModel.points = points;
    metadata.pointset = pointSetModel;
  }

  private static void populateMetadataLocalnet(DiscoveryEvents discoveryEvents, Metadata metadata) {
    metadata.localnet = new LocalnetModel();
    metadata.localnet.families = new HashMap<>();
    FamilyLocalnetModel familyLocalnetModel = new FamilyLocalnetModel();
    familyLocalnetModel.addr = discoveryEvents.addr;
    metadata.localnet.families.put(discoveryEvents.family, familyLocalnetModel);
  }

  private void initialize() {
    cloudIotManager = new CloudIotManager(executionConfiguration, MAPPER_TOOL_NAME);
    siteModel = new SiteModel(cloudIotManager.getSiteDir(), executionConfiguration);
    siteModel.initialize();
  }

  private void shutdown() {
    cloudIotManager.shutdown();
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }
}
