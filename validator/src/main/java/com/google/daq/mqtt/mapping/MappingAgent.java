package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.registrar.Registrar.METADATA_JSON;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_GENERATION;
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
import udmi.schema.CloudModel;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;
import udmi.schema.SystemModel;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent {

  private final ExecutionConfiguration executionConfiguration;
  private final String deviceId;
  private CloudIotManager cloudIotManager;
  private SiteModel siteModel;

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
        case "discover" -> initiateDiscover();
        case "reconcile" -> reconcileDiscovery();
        default -> throw new RuntimeException("Unknown mapping command " + mappingCommand);
      }
    }
  }

  private void initiateDiscover() {
    String generation = isoConvert(new Date());
    System.err.printf("Initiating discovery on %s/%s at %s%n", siteModel.getRegistryId(), deviceId,
        generation);

    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = ImmutableMap.of(UDMI_PROVISION_GENERATION, generation);
    cloudIotManager.modifyDevice(deviceId, cloudModel);

    FamilyDiscoveryConfig familyDiscoveryConfig = new FamilyDiscoveryConfig();
    familyDiscoveryConfig.generation = JsonUtil.getDate(generation);
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    discoveryConfig.families = new HashMap<>();
    discoveryConfig.families.put(ProtocolFamily.VENDOR, familyDiscoveryConfig);
    cloudIotManager.modifyConfig(deviceId, SubFolder.DISCOVERY, stringify(discoveryConfig));
  }

  private void reconcileDiscovery() {
    File extrasDir = siteModel.getExtrasDir();
    File[] extras = extrasDir.listFiles();
    if (extras == null || extras.length == 0) {
      throw new RuntimeException("No extras found to reconcile");
    }
    List<Entry<String, Metadata>> entries = Arrays.stream(extras).map(this::convertExtra).toList();
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

    System.err.printf("Augmenting gateway %s metadata file with new proxyIds%n", deviceId);
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

  @SuppressWarnings("unchecked")
  private Entry<String, Metadata> convertExtra(File file) {
    DiscoveryEvent discoveryEvent = loadFileStrictRequired(DiscoveryEvent.class,
        new File(file, "cloud_metadata/udmi_discovered_with.json"));
    String deviceName = (String) discoveryEvent.system.ancillary.get("device-name");
    Metadata metadata = new Metadata();
    metadata.version = UDMI_VERSION;
    metadata.timestamp = new Date();
    metadata.system = new SystemModel();
    metadata.gateway = new GatewayModel();
    metadata.gateway.gateway_id = deviceId;
    return Map.entry(deviceName, metadata);
  }

  private void initialize() {
    cloudIotManager = new CloudIotManager(executionConfiguration);
    siteModel = new SiteModel(cloudIotManager.getSiteDir());
    siteModel.initialize();
  }

  private void shutdown() {
    cloudIotManager.shutdown();
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }
}
