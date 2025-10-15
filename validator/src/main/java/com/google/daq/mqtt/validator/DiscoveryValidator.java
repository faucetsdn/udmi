package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.util.providers.FamilyProvider.constructUrl;
import static java.util.Objects.requireNonNull;

import com.google.daq.mqtt.util.providers.FamilyProvider;
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
    }
  }

  public void validateMessage(State stateMessage) {
  }

  /**
   * Validate a discovery event.
   */
  public void validateMessage(DiscoveryEvents discoveryEvents) {
    String scanFamily = requireNonNull(discoveryEvents.family,
        "discovery family not defined");
    FamilyProvider familyProvider = FamilyProvider.NAMED_FAMILIES.get(scanFamily);
    checkNotNull(familyProvider, "Unknown provider for discovery family " + scanFamily);
    if (discoveryEvents.refs == null) {
      familyProvider.validateUrl(constructUrl(scanFamily, discoveryEvents.addr, null));
    } else {
      discoveryEvents.refs.forEach((key, value) ->
          familyProvider.validateUrl(constructUrl(scanFamily, discoveryEvents.addr, key)));
    }
  }

  public void validateMessage(DiscoveryState discoveryState) {
  }
}