package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_UNTIL;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import udmi.schema.CloudModel;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryConfig;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final ProtocolFamily DISCOVERY_FAMILY = ProtocolFamily.VENDOR;
  private static final Duration PROVISIONING_WINDOW = Duration.ofMinutes(10);
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
    Date generation = new Date();
    System.err.printf("Initiating discovery on %s/%s at %s%n", siteModel.getRegistryId(), deviceId,
        isoConvert(generation));
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    discoveryConfig.generation = generation;
    cloudIotManager.modifyConfig(deviceId, SubFolder.DISCOVERY, stringify(discoveryConfig));
    Instant theFuture = discoveryConfig.generation.toInstant().plus(PROVISIONING_WINDOW);
    ImmutableMap<String, String> update = ImmutableMap.of(UDMI_PROVISION_UNTIL,
        isoConvert(theFuture));
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
    siteModel.initialize();
  }

  private void shutdown() {
    cloudIotManager.shutdown();
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }
}
