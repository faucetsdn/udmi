package com.google.daq.mqtt.mapping;

import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryConfig;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final ProtocolFamily DISCOVERY_FAMILY = ProtocolFamily.VENDOR;
  private final ExecutionConfiguration executionConfiguration;
  private final String exePath;
  private IotReflectorClient client;
  private SiteModel siteModel;
  private File configFile;
  private CloudIotManager cloudIotManager;

  public MappingAgent(ExecutionConfiguration exeConfig, String exePath) {
    executionConfiguration = exeConfig;
    this.exePath = exePath;
    initialize();
  }

  public MappingAgent(String profilePath) {
    this(ConfigUtil.readExeConfig(new File(profilePath)), profilePath);
  }

  /**
   * Let's go.
   */
  public static void main(String[] args) {
    List<String> argsList = new ArrayList<>(Arrays.asList(args));
    MappingAgent agent = new MappingAgent(removeNextArg(argsList, "execution profile"));
    try {
      agent.process();
    } finally {
      agent.shutdown();
    }
  }

  private void process() {

  }

  private void initialize() {
    cloudIotManager = new CloudIotManager(executionConfiguration, exePath);
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
}
