package com.google.bos.udmi.service.access;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.using;
import static java.lang.String.format;

import com.google.bos.udmi.service.core.ComponentName;
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
import java.util.function.Consumer;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that runs locally (through filesystem).
 */
@ComponentName("iot-access")
public class LocalIotAccessProvider extends IotAccessBase {

  private static final Map<String, Entry<Long, String>> DEVICE_CONFIGS = new HashMap<>();
  @VisibleForTesting
  BlockingQueue<String> sentCommands = new LinkedBlockingQueue<>();
  private boolean failActivation;

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public LocalIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
  }

  @Override
  public void activate() {
    super.activate();
    checkState(!failActivation, "failing activation for test");
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    return DEVICE_CONFIGS.getOrDefault(deviceId, new SimpleEntry<>(null, EMPTY_JSON));
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  public List<String> getCommands() {
    return using(new ArrayList<>(), sentCommands::drainTo);
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    return ImmutableSet.of();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public CloudModel listDevices(String registryId, Consumer<String> progress) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<Integer> progress) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void sendCommandBase(Envelope envelope, SubFolder folder, String message) {
    sentCommands.add(
        format("%s/%s/%s:%s", envelope.deviceRegistryId, envelope.deviceId, folder, message));
  }

  @TestOnly
  public void setFailureForTest() {
    failActivation = true;
  }

  @Override
  public void shutdown() {
    debug("shutdown");
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long version) {
    String deviceId = envelope.deviceId;
    Entry<Long, String> entry = DEVICE_CONFIGS.get(deviceId);
    if (version != null && !entry.getKey().equals(version)) {
      throw new IllegalStateException("Config version mismatch");
    }
    Long previous = Optional.ofNullable(entry).orElse(new SimpleEntry<>(0L, "")).getKey();
    DEVICE_CONFIGS.put(deviceId, new SimpleEntry<>(previous + 1, config));
    return config;
  }
}
