package com.google.daq.mqtt.mapping;

import static com.google.daq.mqtt.util.JsonUtil.loadFile;

import com.google.daq.mqtt.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.HashMap;
import java.util.Optional;
import udmi.schema.DeviceMappingConfig;
import udmi.schema.DeviceMappingState;
import udmi.schema.Envelope;
import udmi.schema.MappingCommand;
import udmi.schema.MappingConfig;
import udmi.schema.MappingState;

public class MappingSink {

  private static final String MAPPING_CONFIG_FILE = "out/mapping_config.json";
  private static final String MAPPING_COMMAND_FILE = "out/mapping_translation.json";
  private static final String MAPPING_STATE_FILE = "out/mapping_state.json";

  private final SiteModel siteModel;
  private MappingState mappingState;
  private MappingConfig mappingConfig;

  public MappingSink(SiteModel siteModel) {
    this.siteModel = siteModel;
  }

  public MappingState getMappingState() {
    return mappingState;
  }

  public MappingConfig getMappingConfig() {
    return mappingConfig;
  }

  void initialize() {
    mappingConfig = new MappingConfig();
    mappingConfig.devices = new HashMap<>();
    siteModel.allDeviceIds().forEach(this::loadMappingConfig);

    mappingState = new MappingState();
    mappingState.devices = new HashMap<>();
    siteModel.allDeviceIds().forEach(this::loadMappingState);
  }

  private void loadMappingConfig(String deviceId) {
    DeviceMappingConfig value = loadFile(DeviceMappingConfig.class, mappingConfigFile(deviceId));
    mappingConfig.devices.put(deviceId, ensureDeviceConfig(value));
  }

  private void loadMappingState(String deviceId) {
    DeviceMappingState value = loadFile(DeviceMappingState.class, mappingStateFile(deviceId));
    mappingState.devices.put(deviceId, ensureDeviceState(value));
  }

  private File mappingConfigFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_CONFIG_FILE);
  }

  private File mappingCommandFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_COMMAND_FILE);
  }

  private File mappingStateFile(String deviceId) {
    return new File(siteModel.getDevice(deviceId).getFile(), MAPPING_STATE_FILE);
  }

  void updateState(Envelope envelope, DeviceMappingState mappingState) {
    JsonUtil.writeFile(mappingState, mappingStateFile(envelope.deviceId));
  }

  void updateConfig(Envelope envelope, DeviceMappingConfig mappingConfig) {
    JsonUtil.writeFile(mappingConfig, mappingConfigFile(envelope.deviceId));
  }

  void updateCommand(Envelope envelope, MappingCommand mappingCommand) {
    JsonUtil.writeFile(mappingCommand, mappingCommandFile(envelope.deviceId));
  }

  DeviceMappingState ensureDeviceState(String deviceId) {
    return mappingState.devices.computeIfAbsent(deviceId, key -> new DeviceMappingState());
  }

  DeviceMappingState ensureDeviceState(DeviceMappingState previous) {
    return Optional.ofNullable(previous).orElseGet(DeviceMappingState::new);
  }

  DeviceMappingConfig ensureDeviceConfig(String deviceId) {
    return mappingConfig.devices.computeIfAbsent(deviceId, key -> new DeviceMappingConfig());
  }

  DeviceMappingConfig ensureDeviceConfig(DeviceMappingConfig previous) {
    return Optional.ofNullable(previous).orElseGet(DeviceMappingConfig::new);
  }
}
