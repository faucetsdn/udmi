package com.google.daq.mqtt.mapping;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.JsonUtil;
import com.google.daq.mqtt.util.MessageHandler;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import udmi.schema.BuildingTranslation;
import udmi.schema.DeviceMappingState;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;
import udmi.schema.MappingConfig;
import udmi.schema.MappingEvent;
import udmi.schema.MappingState;
import udmi.schema.PointEnumerationEvent;

/**
 * Engine for mapping discovery results to point names.
 */
public class MappingEngine extends MappingBase {

  private final MappingState mappingState = new MappingState();
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
    mappingState.devices = new HashMap<>();
    messageLoop();
  }

  private void mappingConfigHandler(Envelope envelope, MappingConfig mappingConfig) {
    System.err.printf("Processing mapping config%n");
  }

  private void discoveryEventHandler(Envelope envelope, DiscoveryEvent message) {
    String deviceId = message.scan_id;
    System.err.printf("Processing device %s generation %s%n", deviceId,
        JsonUtil.getTimestamp(message.generation));

    getDeviceState(deviceId).discovered = message.timestamp;
    updateTranslation(deviceId, message.uniqs);
    publishEngineState();
  }

  private DeviceMappingState getDeviceState(String deviceId) {
    DeviceMappingState state = mappingState.devices.computeIfAbsent(deviceId,
        key -> new DeviceMappingState());
    state.guid = deviceGuid(deviceId);
    return state;
  }

  private void updateTranslation(String deviceId, Map<String, PointEnumerationEvent> uniqs) {
    MappingEvent result = new MappingEvent();
    result.guid = deviceGuid(deviceId);
    result.translation = uniqs.entrySet()
        .stream().map(this::makeTranslation).collect(Collectors.toMap(SimpleEntry::getKey,
            SimpleEntry::getValue, (existing, replacement) -> replacement, HashMap::new));
    result.timestamp = new Date();
    publishMessage(deviceId, result);
    getDeviceState(deviceId).exported = new Date();
  }

  private SimpleEntry<String, BuildingTranslation> makeTranslation(
      Entry<String, PointEnumerationEvent> entry) {
    BuildingTranslation buildingTranslation = new BuildingTranslation();
    PointEnumerationEvent value = entry.getValue();
    buildingTranslation.present_value = value.name;
    buildingTranslation.units = value.units;
    return new SimpleEntry<>(entry.getKey(), buildingTranslation);
  }

  private String deviceGuid(String deviceId) {
    return String.format("%08x", Math.abs(Objects.hashCode(deviceId)));
  }

  private void publishEngineState() {
    mappingState.timestamp = new Date();
    publishMessage(mappingState);
    System.err.println(JsonUtil.stringify(mappingState));
  }

  private BuildingTranslation getTranslation(String key) {
    BuildingTranslation buildingTranslation = new BuildingTranslation();
    buildingTranslation.present_value = key;
    return buildingTranslation;
  }

}
