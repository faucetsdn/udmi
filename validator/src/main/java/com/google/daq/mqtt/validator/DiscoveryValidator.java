package com.google.daq.mqtt.validator;

import java.util.Map;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Metadata;
import udmi.schema.State;

/**
 * Class to handle validating all sorts of discovery messages.
 */
public class DiscoveryValidator {

  private final Metadata metadata;
  private final ErrorCollector errorCollector;

  public DiscoveryValidator(ErrorCollector errorCollector, Metadata metadata) {
    this.errorCollector = errorCollector;
    this.metadata = metadata;
  }

  void validateMessage(Object message, Map<String, String> attributes) {
    if (message instanceof State stateMessage) {
      validateMessage(stateMessage);
    } else if (message instanceof DiscoveryState discoveryState) {
      validateMessage(discoveryState);
    } else if (message instanceof DiscoveryEvents discoveryEvents) {
      validateMessage(discoveryEvents);
    } else {
      throw new RuntimeException("Unknown discovery message class " + message.getClass().getName());
    }
  }


  public void validateMessage(State stateMessage) {
  }

  public void validateMessage(DiscoveryEvents discoveryEvents) {
  }

  public void validateMessage(DiscoveryState discoveryState) {
  }

}
