package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.JsonUtil;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import com.google.udmi.util.SiteModel.Device;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import udmi.schema.DeviceMappingConfig;
import udmi.schema.DeviceMappingState;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryState;
import udmi.schema.Envelope;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.MappingCommand;
import udmi.schema.MappingConfig;
import udmi.schema.MappingEvent;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent extends MappingBase {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final String DISCOVERY_FAMILY = "virtual";
  private static final String MAPPING_CONFIG_FILE = "out/mapping_config.json";
  private static final String MAPPING_TRANSLATION_FILE = "out/mapping_translation.json";
  private static final String MAPPING_STATE_FILE = "out/mapping_state.json";
  private final Map<String, FamilyDiscoveryState> familyStates = new HashMap<>();
  private final List<HandlerSpecification> handlers = ImmutableList.of(
      MessageHandler.handlerSpecification(DiscoveryState.class, this::discoveryStateHandler),
      MessageHandler.handlerSpecification(MappingEvent.class, this::mappingEventHandler)
  );

  /**
   * Main entry point for the mapping agent.
   *
   * @param args Standard command line arguments
   */
  public static void main(String[] args) {
    new MappingAgent().activate(args);
  }

  private void activate(String[] args) {
    initialize("agent", args, handlers);
    startDiscovery();
    loadTranslations();
    messageLoop();
  }

  private void loadTranslations() {
    siteModel.forEachDevice(device -> {
      MappingConfig mappingConfig = JsonUtil.loadFile(MappingConfig.class,
          getMappingConfigFile(device));
    });
  }

  private File getMappingConfigFile(Device device) {
    return new File(device.getFile(), MAPPING_CONFIG_FILE);
  }

  private File getMappingConfigFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_CONFIG_FILE);
  }

  private File getMappingStateFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_STATE_FILE);
  }

  private File getMappingTranslationFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_TRANSLATION_FILE);
  }

  private void startDiscovery() {
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    Date generation = new Date();
    discoveryConfig.families = new HashMap<>();
    FamilyDiscoveryConfig familyConfig = discoveryConfig.families.computeIfAbsent(DISCOVERY_FAMILY,
        key -> new FamilyDiscoveryConfig());
    familyConfig.generation = generation;
    familyConfig.scan_interval_sec = SCAN_INTERVAL_SEC;
    discoveryPublish(discoveryConfig);
    System.err.println("Started discovery generation " + generation);
  }

  private void discoveryStateHandler(DiscoveryState message, Envelope attributes) {
    message.families.forEach(this::processFamilyState);
  }

  private void processFamilyState(String family, FamilyDiscoveryState state) {
    FamilyDiscoveryState previous = familyStates.put(family, state);
    if (previous == null || !Objects.equals(previous.generation, state.generation)) {
      System.err.println("Received new family " + family + " generation " + state.generation);
    }
  }
  private void mappingEventHandler(MappingEvent mappingEvent, Envelope envelope) {
    System.err.println("Processing mapping event for " + envelope.deviceId);

    DeviceMappingState mappingState = new DeviceMappingState();
    mappingState.guid = mappingEvent.guid;
    mappingState.exported = mappingEvent.timestamp;
    JsonUtil.writeFile(mappingState, getMappingStateFile(envelope.deviceId));

    MappingConfig mappingConfig = new MappingConfig();
    mappingConfig.timestamp = mappingEvent.timestamp;
    enginePublish(mappingConfig);
    JsonUtil.writeFile(mappingConfig, getMappingConfigFile(envelope.deviceId));

    MappingCommand mappingCommand = new MappingCommand();
    mappingCommand.guid = mappingEvent.guid;
    mappingCommand.timestamp = mappingEvent.timestamp;
    mappingCommand.translation = mappingEvent.translation;
    JsonUtil.writeFile(mappingCommand, getMappingTranslationFile(envelope.deviceId));
  }
}
