package com.google.bos.udmi.service.access;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.bos.udmi.service.pod.UdmiServicePod.getComponent;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.sortedMapCollector;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.ContainerBase;
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
    // TODO: In the future, different registries might be governed by different backends
    // using the same set of providers, so inheriting affinity from the reflector like
    // this might need to be re-evaluated.
    // For now, we assume the only active backend is Mosquitto (ImplicitIotAccessProvider),
    // and inheriting the reflector's affinity is necessary to resolve the cold-start
    // deadlock where the device hasn't sent a message yet but needs its config.
    String reflectorKey = getProviderKey(ContainerBase.REFLECT_BASE, registryId);
    String reflectorAffinity = registryProviders.get(reflectorKey);
    if (reflectorAffinity != null) {
      debug("Registry affinity mapping for %s inherited from reflector %s: %s",
          registryId, reflectorKey, reflectorAffinity);
      return reflectorAffinity;
    }
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
    if (ContainerBase.REFLECT_BASE.equals(envelope.deviceRegistryId)
        && envelope.source != null) {
      int index = envelope.source.indexOf(Common.SOURCE_SEPARATOR);
      String transport = index < 0 ? envelope.source : envelope.source.substring(0, index);
      String source = index < 0 ? null : envelope.source.substring(index + 1);

      String target = transport;
      if (source != null) {
        if ("bridge".equals(source)) {
          target = "implicit";
        } else if (getProviders().containsKey(source)) {
          target = source;
        }
      } else {
        if ("bridge".equals(transport)) {
          target = "implicit";
        }
      }

      if (getProviders().containsKey(target)) {
        return getProviders().get(target);
      }
    }
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

  private IotAccessProvider getRegistryProvider(Envelope envelope) {
    return getRegistryProvider(envelope.deviceRegistryId, envelope.deviceId);
  }

  private IotAccessProvider getRegistryProvider(String registryId, String deviceId) {
    IotAccessProvider provider = getProviderFor(registryId, deviceId);
    if (provider instanceof PubSubIotAccessProvider) {
      IotAccessProvider fallback = getProviders().values().stream()
          .filter(p -> !(p instanceof PubSubIotAccessProvider))
          .findFirst()
          .orElse(null);
      if (fallback != null) {
        debug("PubSub provider does not support registry operations, falling back to %s",
            fallback.getClass().getSimpleName());
        return fallback;
      }
    }
    return provider;
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
    return getRegistryProvider(registryId, deviceId).fetchDevice(registryId, deviceId);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return getRegistryProvider(registryId, null).fetchRegistryMetadata(registryId, metadataKey);
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    return getRegistryProvider(registryId, deviceId).fetchState(registryId, deviceId);
  }

  @Override
  public void saveState(String registryId, String deviceId, String stateBlob) {
    getRegistryProvider(registryId, deviceId).saveState(registryId, deviceId, stateBlob);
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
  public CloudModel listDevices(String registryId, Consumer<String> progress) {
    return getRegistryProvider(registryId, null).listDevices(registryId, progress);
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<String> progress) {
    debug("%s iot device %s/%s, %s %s", cloudModel.operation, registryId, deviceId,
        cloudModel.blocked, cloudModel.num_id);
    return getRegistryProvider(registryId, deviceId).modelDevice(registryId, deviceId, cloudModel,
        progress);
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    return getRegistryProvider(registryId, deviceId)
        .modelRegistry(registryId, deviceId, cloudModel);
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

  /**
   * Sets the provider affinity for a given registry and device.
   *
   * <p>If a client specifies a provider source (e.g., native PubSub source "pubsub/user"),
   * we cache the parsed affinity (e.g., "pubsub") to ensure all subsequent downlink
   * replies/commands are routed back through that same provider.
   *
   * <p>If the client does not specify a source (e.g., a proxied MQTT connection where the
   * proxy does not propagate the source attribute, resulting in a null providerId), we
   * explicitly clear the cached affinity. This prevents UDMIS from getting permanently
   * stuck on a previous client's affinity (like a sticky "pubsub" mapping) and allows it
   * to correctly fall back to the default configured provider for that registry (like
   * "implicit" Mosquitto MQTT).
   */
  @Override
  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
    String providerKey = getProviderKey(registryId, deviceId);
    if (providerId != null) {
      int index = providerId.indexOf(Common.SOURCE_SEPARATOR);
      String transport = providerId.substring(0, index < 0 ? providerId.length() : index);
      String source = index < 0 ? null : providerId.substring(index + 1);

      String affinity = transport;
      if (source != null) {
        if ("bridge".equals(source)) {
          affinity = "implicit";
        } else if (getProviders().containsKey(source)) {
          affinity = source;
        }
      } else {
        if ("bridge".equals(transport)) {
          affinity = "implicit";
        }
      }

      if (!getProviders().containsKey(affinity) && !"implicit".equals(affinity)) {
        warn(format("Requested invalid or inactive provider affinity '%s' for %s", affinity, providerKey));
        throw new IllegalArgumentException(format("Unknown or inactive requested provider '%s'", affinity));
      }

      String previous = registryProviders.put(providerKey, affinity);
      if (!affinity.equals(previous)) {
        debug(format("Switched registry affinity for %s from %s -> %s", providerKey, previous,
            affinity));
      }
    } else {
      String previous = registryProviders.remove(providerKey);
      if (previous != null) {
        debug(format("Cleared registry affinity for %s (was %s)", providerKey, previous));
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
