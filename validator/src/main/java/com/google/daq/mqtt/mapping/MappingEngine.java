package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import java.util.List;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.Envelope;

/**
 * Engine for mapping discovery results to point names.
 */
public class MappingEngine extends MappingBase {

  private List<HandlerSpecification> handlers = ImmutableList.of(
      MessageHandler.handlerSpecification(DiscoveryEvent.class, this::discoveryEventHandler)
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

  private void discoveryEventHandler(DiscoveryEvent message, Envelope attributes) {
    System.err.printf("Received discovery event for generation %s%n", message.generation);
  }

}
