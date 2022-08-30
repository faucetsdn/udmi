package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import udmi.schema.BuildingTranslation;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;
import udmi.schema.MappingConfig;
import udmi.schema.MappingEvent;

/**
 * Engine for mapping discovery results to point names.
 */
public class MappingEngine extends MappingBase {

  private final List<HandlerSpecification> handlers = ImmutableList.of(
      MessageHandler.handlerSpecification(DiscoveryEvent.class, this::discoveryEventHandler),
      MessageHandler.handlerSpecification(MappingConfig.class, this::mappingConfigHandler)
  );

  /**
   * Main entry point for the mapping agent.
   *
   * @param args Standard command line arguments
   */
  public static void main(String[] args) {
    new MappingEngine().activate(args);
  }

  void activate() {
    activate();
  }

  void activate(String[] args) {
    initialize("engine", args, handlers);
    messageLoop();
  }

  private void mappingConfigHandler(Envelope envelope, MappingConfig mappingConfig) {
    System.err.printf("Processing mapping config%n");
  }

  private void discoveryEventHandler(Envelope envelope, DiscoveryEvent message) {
    System.err.printf("Processing device %s generation %s%n", message.scan_id, message.generation);
    MappingEvent result = new MappingEvent();
    result.guid = String.format("%08x", Math.abs(Objects.hashCode(message.scan_id)));
    result.translation = new HashMap<>();
    result.translation.computeIfAbsent("hello", this::getTranslation);
    publishMessage(message.scan_id, result);
  }

  private BuildingTranslation getTranslation(String key) {
    BuildingTranslation buildingTranslation = new BuildingTranslation();
    buildingTranslation.present_value = key;
    return buildingTranslation;
  }

}
