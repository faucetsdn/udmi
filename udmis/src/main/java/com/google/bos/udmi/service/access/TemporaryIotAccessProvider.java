package com.google.bos.udmi.service.access;

import com.google.bos.udmi.service.core.ComponentName;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Temporary IoT Access Provider that stores things in memory.
 */
@ComponentName("iot-access")
public class TemporaryIotAccessProvider extends IotAccessBase {

  private final Map<String, Map<String, CloudModel>> store = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Entry<Long, String>>> configStore =
      new ConcurrentHashMap<>();

  public TemporaryIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    return store.getOrDefault(registryId, new ConcurrentHashMap<>()).get(deviceId);
  }

  @Override
  public CloudModel listDevices(String registryId, Consumer<String> progress) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.device_ids = new ConcurrentHashMap<>();
    Map<String, CloudModel> registryStore =
        store.getOrDefault(registryId, new ConcurrentHashMap<>());
    for (String deviceId : registryStore.keySet()) {
      cloudModel.device_ids.put(deviceId, registryStore.get(deviceId));
      if (progress != null) {
        progress.accept(deviceId);
      }
    }
    return cloudModel;
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<String> progress) {
    Map<String, CloudModel> registryStore =
        store.computeIfAbsent(registryId, k -> new ConcurrentHashMap<>());
    if (CloudModel.ModelOperation.DELETE == cloudModel.operation) {
      registryStore.remove(deviceId);
    } else {
      registryStore.put(deviceId, cloudModel);
    }
    if (progress != null) {
      progress.accept(deviceId);
    }
    return cloudModel;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return null;
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    return Set.of();
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    return configStore.getOrDefault(registryId, new ConcurrentHashMap<>())
        .getOrDefault(deviceId, new SimpleEntry<>(0L, "{}"));
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long version) {
    Map<String, Entry<Long, String>> registryConfigs =
        configStore.computeIfAbsent(envelope.deviceRegistryId, k -> new ConcurrentHashMap<>());
    Long nextVersion =
        version != null ? version
            : fetchConfig(envelope.deviceRegistryId, envelope.deviceId).getKey() + 1;
    registryConfigs.put(envelope.deviceId, new SimpleEntry<>(nextVersion, config));
    return config;
  }

  @Override
  public void sendCommandBase(Envelope envelope, SubFolder folder, String message) {
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    return "{}";
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    return cloudModel;
  }

  @Override
  public String modifyConfig(Envelope envelope, Function<Entry<Long, String>, String> munger) {
    Entry<Long, String> currentConfig = fetchConfig(envelope.deviceRegistryId, envelope.deviceId);
    String newConfig = munger.apply(currentConfig);
    return updateConfig(envelope, newConfig, currentConfig.getKey() + 1);
  }
}
