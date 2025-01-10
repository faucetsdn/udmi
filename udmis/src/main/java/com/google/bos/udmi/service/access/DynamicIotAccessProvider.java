package com.google.bos.udmi.service.access;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.bos.udmi.service.pod.UdmiServicePod.getComponent;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.sortedMapCollector;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.udmi.util.Common;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * An IoT Access Provider that dynamically switches between other providers.
 */
public class DynamicIotAccessProvider extends IotAccessBase {

  private static final long INDEX_ORDERING_MULTIPLIER_MS = 10000L;
  public static final String PROVIDER_KEY_FORMAT = "%s/%s";
  private final Map<String, String> registryProviders = new ConcurrentHashMap<>();
  private final List<String> providerList;
  private final Map<String, IotAccessProvider> providers = new HashMap<>();

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public DynamicIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    providerList = Arrays.stream(iotAccess.project_id.split(",")).map(String::trim)
        .collect(Collectors.toList());
  }

  private String determineProvider(String registryId) {
    getProviders();
    TreeMap<String, String> sortedMap = getProviders().entrySet().stream()
        .filter(access -> access.getValue().isEnabled())
        .collect(sortedMapCollector(entry -> registryPriority(registryId, entry)));
    checkState(!sortedMap.isEmpty(), "no viable iot providers found");
    String providerId = sortedMap.lastEntry().getValue();
    debug("Registry affinity mapping for " + registryId + " is " + providerId);
    return providerId;
  }

  private IotAccessProvider getProviderFor(Envelope envelope) {
    return getProviderFor(envelope.deviceRegistryId, envelope.deviceId);
  }

  private IotAccessProvider getProviderFor(String registryId, String deviceId) {
    String entryKey = getProviderKey(registryId, deviceId);
    String providerKey =
        registryProviders.computeIfAbsent(entryKey, k -> determineProvider(registryId));
    IotAccessProvider provider = getProviders().get(providerKey);
    return requireNonNull(provider,
        format("Could not determine provider for %s from %s", providerKey, entryKey));
  }

  private Map<String, IotAccessProvider> getProviders() {
    if (!providers.isEmpty()) {
      return providers;
    }
    providerList.forEach(
        providerId -> {
          IotAccessProvider component = getComponent(providerId);
          ifTrueThen(component.isEnabled(), () -> providers.put(providerId, component));
        });
    info("Populated providers list with %d out of %d", providers.size(), providerList.size());
    if (providerList.isEmpty()) {
      throw new RuntimeException("No providers enabled");
    }
    return providers;
  }

  private String registryPriority(String registryId, Entry<String, IotAccessProvider> provider) {
    int providerIndex = providerList.size() - providerList.indexOf(provider.getKey());
    String provisionedAt = ofNullable(
        provider.getValue().fetchRegistryMetadata(registryId, "udmi_provisioned")).orElse(
        isoConvert(new Date(providerIndex * INDEX_ORDERING_MULTIPLIER_MS)));
    debug(format("Registry %s provider %s provisioned %s", registryId, provider.getKey(),
        provisionedAt));
    return provisionedAt;
  }

  @Override
  public void activate() {
    super.activate();
    getProviders();
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    return getProviderFor(registryId, deviceId).fetchConfig(registryId, deviceId);
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    return getProviderFor(registryId, deviceId).fetchDevice(registryId, deviceId);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return getProviderFor(registryId, null).fetchRegistryMetadata(registryId, metadataKey);
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    return getProviderFor(registryId, deviceId).fetchState(registryId, deviceId);
  }

  @Override
  public void saveState(String registryId, String deviceId, String stateBlob) {
    getProviderFor(registryId, deviceId).saveState(registryId, deviceId, stateBlob);
  }

  @Override
  public Set<String> getRegistries() {
    return getProviders().values().stream().map(IotAccessProvider::getRegistries)
        .collect(HashSet::new, HashSet::addAll, HashSet::addAll);
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public CloudModel listDevices(String registryId, Consumer<Integer> progress) {
    return getProviderFor(registryId, null).listDevices(registryId, progress);
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<Integer> progress) {
    debug("%s iot device %s/%s, %s %s", cloudModel.operation, registryId, deviceId,
        cloudModel.blocked, cloudModel.num_id);
    return getProviderFor(registryId, deviceId).modelDevice(registryId, deviceId, cloudModel, null);
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    return getProviderFor(registryId, deviceId).modelRegistry(registryId, deviceId, cloudModel);
  }

  @Override
  public String modifyConfig(Envelope envelope, Function<Entry<Long, String>, String> munger) {
    return getProviderFor(envelope).modifyConfig(envelope, munger);
  }

  @Override
  public void sendCommandBase(Envelope envelope, SubFolder folder,
      String message) {
    getProviderFor(envelope).sendCommandBase(envelope, folder, message);
  }

  @Override
  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
    if (providerId != null) {
      int index = providerId.indexOf(Common.SOURCE_SEPARATOR);
      String affinity = providerId.substring(0, index < 0 ? providerId.length() : index);
      String providerKey = getProviderKey(registryId, deviceId);
      String previous = registryProviders.put(providerKey, affinity);
      if (!affinity.equals(previous)) {
        debug(format("Switched registry affinity for %s from %s -> %s", providerKey, previous,
            affinity));
      }
    }
    super.setProviderAffinity(registryId, deviceId, providerId);
  }

  private static String getProviderKey(String registryId, String deviceId) {
    return format(PROVIDER_KEY_FORMAT, registryId, deviceId);
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long version) {
    throw new RuntimeException("Shouldn't be called for dynamic provider");
  }

  @Override
  public void updateRegistryRegions(Map<String, String> regions) {
    getProviders().values().forEach(provider -> provider.updateRegistryRegions(regions));
  }
}
