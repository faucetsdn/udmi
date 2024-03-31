package com.google.daq.mqtt.mapping;

import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_UNTIL;
import static java.util.Objects.requireNonNull;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import udmi.schema.CloudModel;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryConfig;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final ProtocolFamily DISCOVERY_FAMILY = ProtocolFamily.VENDOR;
  private static final Duration PROVISIONING_WINDOW = Duration.ofMinutes(10);
  private final ExecutionConfiguration executionConfiguration;
  private CloudIotManager cloudIotManager;
  private String deviceId;
  private SiteModel siteModel;

  public MappingAgent(ExecutionConfiguration exeConfig) {
    executionConfiguration = exeConfig;
    deviceId = requireNonNull(exeConfig.device_id, "device id not specified");
    initialize();
  }

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
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    discoveryConfig.generation = new Date();
    cloudIotManager.modifyConfig(deviceId, SubFolder.DISCOVERY, stringify(discoveryConfig));
    Instant theFuture = discoveryConfig.generation.toInstant().plus(PROVISIONING_WINDOW);
    ImmutableMap<String, String> update = ImmutableMap.of(UDMI_PROVISION_UNTIL, isoConvert(theFuture));
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = update;
    cloudIotManager.modifyDevice(deviceId, cloudModel);
  }

  private void reconcileDiscovery() {
    throw new RuntimeException("Not yet implemented");
  }

  private void initialize() {
    cloudIotManager = new CloudIotManager(executionConfiguration);
    siteModel = new SiteModel(cloudIotManager.getSiteDir());
  }

  private void shutdown() {
    client.close();
  }

  private void startDiscovery() {
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    Date generation = new Date();
    discoveryConfig.families = new HashMap<>();
    FamilyDiscoveryConfig familyConfig = discoveryConfig.families.computeIfAbsent(DISCOVERY_FAMILY,
        key -> new FamilyDiscoveryConfig());
    familyConfig.generation = generation;
    familyConfig.scan_interval_sec = SCAN_INTERVAL_SEC;
    familyConfig.depth = Depth.ENTRIES;
    publish(discoveryConfig);
    System.err.println("Started discovery generation " + generation);
  }

  private void publish(DiscoveryConfig discoveryConfig) {
    String topic = "foo";
    String sendId = client.publish(executionConfiguration.device_id, topic,
        stringify(discoveryConfig));
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }
}
