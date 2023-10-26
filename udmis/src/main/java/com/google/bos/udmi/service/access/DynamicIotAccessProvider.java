package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.pod.UdmiServicePod.getComponent;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.sortedMapCollector;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * An IoT Access Provider that dynamically switches between other providers.
 */
public class DynamicIotAccessProvider extends IotAccessBase {

  private static final long INDEX_ORDERING_MULTIPLIER_MS = 10000L;
  private final Map<String, String> registryProviders = new ConcurrentHashMap<>();
  private final List<String> providerList;
  private final Map<String, IotAccessBase> providers = new HashMap<>();

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public DynamicIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    providerList = Arrays.stream(iotAccess.project_id.split(",")).map(String::trim)
        .collect(Collectors.toList());
  }

  @Override
  protected Map<String, String> fetchRegistryRegions() {
    return ImmutableMap.of();
  }

  @Override
  protected Set<String> getRegistriesForRegion(String region) {
    throw new RuntimeException("Should not be called!");
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    throw new RuntimeException("Shouldn't be called for dynamic provider");
  }

  private String determineProvider(String registryId) {
    TreeMap<String, String> sortedMap = providers.entrySet().stream()
        .collect(sortedMapCollector(entry -> registryPriority(registryId, entry)));
    String providerId = sortedMap.lastEntry().getValue();
    debug("Registry affinity mapping for " + registryId + " is " + providerId);
    return providerId;
  }

  private IotAccessBase getProviderFor(String registryId) {
    IotAccessBase provider =
        providers.get(registryProviders.computeIfAbsent(registryId, this::determineProvider));
    return requireNonNull(
        provider,
        "could not determine provider for " + registryId);
  }

  private String registryPriority(String registryId, Entry<String, IotAccessBase> provider) {
    int providerIndex = providerList.indexOf(provider.getKey());
    String provisionedAt = ofNullable(
        provider.getValue().fetchRegistryMetadata(registryId, "udmi_provisioned")).orElse(
        getTimestamp(new Date(providerIndex * INDEX_ORDERING_MULTIPLIER_MS)));
    debug(format("Registry %s provider %s provisioned %s", registryId, provider.getKey(),
        provisionedAt));
    return provisionedAt;
  }

  @Override
  public void activate() {
    super.activate();
    providerList.forEach(
        providerId -> {
          IotAccessBase component = getComponent(providerId);
          ifTrueThen(component.isEnabled(), () -> providers.put(providerId, component));
        });
    if (providerList.isEmpty()) {
      throw new RuntimeException("No providers enabled");
    }
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
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
  public String fetchState(String deviceRegistryId, String deviceId) {
    return getProviderFor(deviceRegistryId).fetchState(deviceRegistryId, deviceId);
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
  public String modifyConfig(String registryId, String deviceId, Function<String, String> munger) {
    return getProviderFor(registryId).modifyConfig(registryId, deviceId, munger);
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    getProviderFor(registryId).sendCommandBase(registryId, deviceId, folder, message);
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
    super.setProviderAffinity(registryId, deviceId, providerId);
  }

  @Override
  public void updateRegistryRegions(Map<String, String> regions) {
    providers.values().forEach(provider -> provider.updateRegistryRegions(regions));
  }
}
