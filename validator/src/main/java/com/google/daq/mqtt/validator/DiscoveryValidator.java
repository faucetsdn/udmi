package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.FamilyProvider.constructRef;
import static udmi.lib.ProtocolFamily.BACNET;

import com.google.daq.mqtt.util.FamilyProvider;
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
      return;
    }
  }

  public void validateMessage(State stateMessage) {
  }

  public void validateMessage(DiscoveryEvents discoveryEvents) {
    String scanFamily = discoveryEvents.scan_family;
    FamilyProvider familyProvider = FamilyProvider.NAMED_FAMILIES.get(scanFamily);
    discoveryEvents.refs.forEach((key, value)->
        familyProvider.refValidator(constructRef(scanFamily, discoveryEvents.scan_addr, key)));
  }

  public void validateMessage(DiscoveryState discoveryState) {
  }
}