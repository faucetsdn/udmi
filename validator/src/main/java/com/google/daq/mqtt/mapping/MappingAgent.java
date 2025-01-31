package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
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
import com.google.udmi.util.JsonUtil;
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
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;
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
  }

  void process(List<String> argsList) {
    checkState(!argsList.isEmpty(), "no arguments found, no commands given!");
    while (!argsList.isEmpty()) {
      String mappingCommand = removeNextArg(argsList, "mapping command");
      switch (mappingCommand) {
        case "provision" -> setupProvision();
        case "discover" -> initiateDiscover();
        case "reconcile" -> reconcileDiscovery();
        default -> throw new RuntimeException("Unknown mapping command " + mappingCommand);
      }
      System.err.printf("Completed mapper %s command%n", mappingCommand);
    }
  }

  private void setupProvision() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = ImmutableMap.of(UDMI_PROVISION_ENABLE, "true");
    cloudIotManager.updateDevice(deviceId, cloudModel, Operation.MODIFY);
  }

  private void initiateDiscover() {
    Set<String> families = catchToNull(
        () -> siteModel.getMetadata(deviceId).discovery.families.keySet());
    checkNotNull(families, "No discovery families defined");

    generationDate = new Date();
    String generation = isoConvert(generationDate);
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = ImmutableMap.of(UDMI_PROVISION_GENERATION, generation);
    cloudIotManager.updateDevice(deviceId, cloudModel, Operation.MODIFY);

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

  private void reconcileDiscovery() {
    File extrasDir = siteModel.getExtrasDir();
    File[] extras = extrasDir.listFiles();
    if (extras == null || extras.length == 0) {
      throw new RuntimeException("No extras found to reconcile");
    }
    List<Entry<String, Metadata>> entries = Arrays.stream(extras).map(this::convertExtra)
        .filter(entry -> !entry.getKey().equals(NO_DISCOVERY)).toList();
    entries.forEach(entry -> {
      File metadataFile = siteModel.getDeviceFile(entry.getKey(), METADATA_JSON);
      if (metadataFile.exists()) {
        System.err.println("Skipping existing device file " + metadataFile);
      } else {
        System.err.println("Writing device metadata file " + metadataFile);
        metadataFile.getParentFile().mkdirs();
        JsonUtil.writeFile(entry.getValue(), metadataFile);
      }
    });

    List<String> proxyIds = entries.stream().map(Entry::getKey).toList();
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

  private Entry<String, Metadata> convertExtra(File file) {
    DiscoveryEvents discoveryEvents = loadFileStrict(DiscoveryEvents.class,
        new File(file, "cloud_metadata/udmi_discovered_with.json"));
    if (discoveryEvents == null) {
      return Map.entry(NO_DISCOVERY, new Metadata());
    }
    Metadata metadata = new Metadata();
    metadata.version = UDMI_VERSION;
    metadata.timestamp = new Date();
    metadata.system = new SystemModel();
    metadata.gateway = new GatewayModel();
    metadata.gateway.gateway_id = deviceId;
    String deviceName = (String) discoveryEvents.system.ancillary.get("device-name");
    return Map.entry(deviceName, metadata);
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
