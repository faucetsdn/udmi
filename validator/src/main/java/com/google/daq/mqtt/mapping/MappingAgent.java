package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import com.google.udmi.util.JsonUtil;
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
import udmi.schema.MappingEvent;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent extends MappingBase {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final String DISCOVERY_FAMILY = "virtual";
  private final Map<String, FamilyDiscoveryState> familyStates = new HashMap<>();
  private MappingSink mappingSink;
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
    mappingEngineId = "_mapping_engine";
    initialize("agent", args, handlers);
    initializeSink();
    startDiscovery();
    messageLoop();
  }

  private void initializeSink() {
    mappingSink = new MappingSink(siteModel);
    mappingSink.initialize();
  }

  private void startDiscovery() {
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    Date generation = new Date();
    discoveryConfig.families = new HashMap<>();
    FamilyDiscoveryConfig familyConfig = discoveryConfig.families.computeIfAbsent(DISCOVERY_FAMILY,
        key -> new FamilyDiscoveryConfig());
    familyConfig.generation = generation;
    familyConfig.scan_interval_sec = SCAN_INTERVAL_SEC;
    familyConfig.enumerate = true;
    discoveryPublish(discoveryConfig);
    System.err.println("Started discovery generation " + generation);
  }

  private void processFamilyState(String family, FamilyDiscoveryState state) {
    FamilyDiscoveryState previous = familyStates.put(family, state);
    if (previous == null || !Objects.equals(previous.generation, state.generation)) {
      System.err.printf("Received family %s generation %s active %s%n", family,
          JsonUtil.getTimestamp(state.generation), state.active);
    }
  }

  private void discoveryStateHandler(Envelope attributes, DiscoveryState message) {
    message.families.forEach(this::processFamilyState);
  }

  private void mappingEventHandler(Envelope envelope, MappingEvent mappingEvent) {
    String deviceId = envelope.deviceId;
    System.err.println("Processing mapping event for " + deviceId);

    DeviceMappingState state = mappingSink.ensureDeviceState(deviceId);
    state.guid = mappingEvent.guid;
    state.exported = mappingEvent.timestamp;
    mappingSink.updateState(envelope, state);

    DeviceMappingConfig config = mappingSink.ensureDeviceConfig(deviceId);
    config.applied = mappingEvent.timestamp;
    config.guid = mappingEvent.guid;
    enginePublish(mappingSink.getMappingConfig());
    mappingSink.updateConfig(envelope, config);

    MappingCommand mappingCommand = new MappingCommand();
    mappingCommand.guid = mappingEvent.guid;
    mappingCommand.timestamp = mappingEvent.timestamp;
    mappingCommand.translation = mappingEvent.translation;
    mappingCommand.device_num_id = Objects.hashCode(deviceId);
    mappingSink.updateCommand(envelope, mappingCommand);
  }

}
