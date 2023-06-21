package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.sortedMapCollector;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static java.lang.String.format;

import com.google.bos.udmi.service.core.UdmisComponent;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * An IoT Access Provider that dynamically switches between other providers.
 */
public class DynamicIotAccessProvider extends UdmisComponent implements IotAccessProvider {

  private final Map<String, String> registryProviders = new HashMap<>();
  private final List<String> providerList;
  private final Map<String, IotAccessProvider> providers = new HashMap<>();
  private final Map<String, String> defaultProvisioned = new HashMap<>();

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public DynamicIotAccessProvider(IotAccess iotAccess) {
    providerList = Arrays.asList(iotAccess.project_id.split(","));
  }

  private String determineProvider(String registryId) {
    TreeMap<String, String> sortedMap = providers.entrySet().stream()
        .collect(sortedMapCollector(entry -> registryPriority(registryId, entry)));
    String providerId = sortedMap.lastEntry().getValue();
    debug("Determined registry mapping for " + registryId + " to be " + providerId);
    return providerId;
  }

  private IotAccessProvider getProviderFor(String registryId) {
    return providers.get(registryProviders.computeIfAbsent(registryId, this::determineProvider));
  }

  private String registryPriority(String registryId, Entry<String, IotAccessProvider> provider) {
    String provisionedAt =
        provider.getValue().fetchRegistryMetadata(registryId, "udmi_provisioned");
    debug(format("Provider %s provisioned %s at %s", provider.getKey(), registryId, provisionedAt));
    return Optional.ofNullable(provisionedAt).orElse(defaultProvisioned.get(provider.getKey()));
  }

  @Override
  public void activate() {
    super.activate();
    providerList.forEach(providerId -> providers.get(providerId).activate());
  }

  @Override
  public Entry<String, String> fetchConfig(String registryId, String deviceId) {
    return getProviderFor(registryId).fetchConfig(registryId, deviceId);
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    return getProviderFor(deviceRegistryId).fetchDevice(deviceRegistryId, deviceId);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return getProviderFor(registryId).fetchRegistryMetadata(registryId, metadataKey);
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    return getProviderFor(deviceRegistryId).listDevices(deviceRegistryId);
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    return getProviderFor(deviceRegistryId).modelDevice(deviceRegistryId, deviceId, cloudModel);
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder folder, String contents) {
    getProviderFor(registryId).modifyConfig(registryId, deviceId, folder, contents);
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder folder, String message) {
    getProviderFor(registryId).sendCommand(registryId, deviceId, folder, message);
  }

  @Override
  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
    if (providerId != null) {
      String previous = registryProviders.put(registryId, providerId);
      if (!providerId.equals(previous)) {
        debug(format("Switching registry affinity for %s from %s -> %s", registryId, previous,
            providerId));
      }
    }
  }

  @Override
  public void setProviders(Map<String, IotAccessProvider> allProviders) {
    for (int i = 0; i < providerList.size(); i++) {
      String providerId = providerList.get(i);
      providers.computeIfAbsent(providerId, allProviders::get);
      long inversePriority = (providerList.size() - i) * 10000L;
      String timestamp = getTimestamp(new Date(inversePriority));
      debug("Defaulting provider " + providerId + " to " + timestamp);
      defaultProvisioned.put(providerId, timestamp);
    }
  }

  @Override
  public void shutdown() {
    providers.values().forEach(IotAccessProvider::shutdown);
    super.shutdown();
  }
}
