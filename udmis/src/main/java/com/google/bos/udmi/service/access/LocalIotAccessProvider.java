package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.using;
import static java.lang.String.format;

import com.google.common.collect.ImmutableSet;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that runs locally (through filesystem).
 */
public class LocalIotAccessProvider extends IotAccessBase {

  private static final Map<String, Entry<Long, String>> DEVICE_CONFIGS = new HashMap<>();
  BlockingQueue<String> sentCommands = new LinkedBlockingQueue<>();

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public LocalIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
  }

  @Override
  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    Entry<Long, String> entry = DEVICE_CONFIGS.get(deviceId);
    if (version != null && !entry.getKey().equals(version)) {
      throw new IllegalStateException("Config version mismatch");
    }
    Long previous = Optional.ofNullable(entry).orElse(new SimpleEntry<>(0L, "")).getKey();
    DEVICE_CONFIGS.put(deviceId, new SimpleEntry<>(previous + 1, config));
    return config;
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected Set<String> getRegistriesForRegion(String region) {
    return ImmutableSet.of();
  }

  @Override
  public void activate() {
    debug("activate");
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    return DEVICE_CONFIGS.get(deviceId);
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  public List<String> getCommands() {
    return using(new ArrayList<>(), sentCommands::drainTo);
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    sentCommands.add(format("%s/%s/%s:%s", registryId, deviceId, folder, message));
  }

  @Override
  public void shutdown() {
    debug("shutdown");
  }

  @Override
  String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }
}
