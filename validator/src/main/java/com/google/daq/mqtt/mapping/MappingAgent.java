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
import udmi.schema.MappingCommand;
import udmi.schema.MappingEvent;
import udmi.schema.NetworkDiscoveryConfig;
import udmi.schema.NetworkDiscoveryState;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent extends MappingBase {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final String DISCOVERY_NETWORK = "virtual";
  private final Map<String, NetworkDiscoveryState> networkStates = new HashMap<>();
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
    discoveryConfig.networks = new HashMap<>();
    NetworkDiscoveryConfig networkConfig = discoveryConfig.networks.computeIfAbsent(
        DISCOVERY_NETWORK,
        key -> new NetworkDiscoveryConfig());
    networkConfig.generation = generation;
    networkConfig.scan_interval_sec = SCAN_INTERVAL_SEC;
    networkConfig.enumerate = true;
    discoveryPublish(discoveryConfig);
    System.err.println("Started discovery generation " + generation);
  }

  private void processNetworkState(String network, NetworkDiscoveryState state) {
    NetworkDiscoveryState previous = networkStates.put(network, state);
    if (previous == null || !Objects.equals(previous.generation, state.generation)) {
      System.err.printf("Received network %s generation %s active %s%n", network,
          JsonUtil.getTimestamp(state.generation), state.active);
    }
  }

  private void discoveryStateHandler(Envelope attributes, DiscoveryState message) {
    message.networks.forEach(this::processNetworkState);
  }

  private void mappingEventHandler(Envelope envelope, MappingEvent mappingEvent) {
    String deviceId = envelope.deviceId;
    System.err.println("Processing mapping event for " + deviceId);

    mappingEvent.entities.forEach((guid, entity) -> {
      DeviceMappingState state = mappingSink.ensureDeviceState(deviceId);
      state.guid = guid;
      state.exported = mappingEvent.timestamp;
      mappingSink.updateState(envelope, state);

      DeviceMappingConfig config = mappingSink.ensureDeviceConfig(deviceId);
      config.applied = mappingEvent.timestamp;
      config.guid = guid;
      enginePublish(mappingSink.getMappingConfig());
      mappingSink.updateConfig(envelope, config);

      MappingCommand mappingCommand = new MappingCommand();
      mappingCommand.guid = guid;
      mappingCommand.timestamp = mappingEvent.timestamp;
      mappingCommand.translation = entity.translation;
      mappingSink.updateCommand(envelope, mappingCommand);
    });
  }

}
