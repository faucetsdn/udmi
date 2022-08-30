package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryState;
import udmi.schema.Envelope;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;

/**
 * Agent that maps discovery results to mapping requests.
 */
public class MappingAgent extends MappingBase {

  private static final int SCAN_INTERVAL_SEC = 60;
  private static final String DISCOVERY_FAMILY = "virtual";
  private final Map<String, FamilyDiscoveryState> familyStates = new HashMap<>();
  private final List<HandlerSpecification> handlers = ImmutableList.of(
      MessageHandler.handlerSpecification(DiscoveryState.class, this::discoveryStateHandler)
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
    messageLoop();
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

}
