package com.google.bos.udmi.service.access;

import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Map.Entry;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Generic interface for accessing iot device management.
 */
public abstract class IotAccessBase extends ContainerBase {

  protected static final String EMPTY_JSON = "{}";
  private static final long CONFIG_UPDATE_BACKOFF_MS = 1000;
  private static final int CONFIG_UPDATE_MAX_RETRIES = 10;
  private static final Map<IotProvider, Class<? extends IotAccessBase>> PROVIDERS = ImmutableMap.of(
      IotProvider.DYNAMIC, DynamicIotAccessProvider.class,
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.GCP, GcpIotAccessProvider.class,
      IotProvider.LOCAL, LocalIotAccessProvider.class
  );

  /**
   * Factory constructor for new instances.
   */
  public static IotAccessBase from(IotAccess iotAccess) {
    try {
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating access provider %s", iotAccess.provider), e);
    }
  }

  protected abstract String updateConfig(String registryId, String deviceId, String config,
      Long version);

  public abstract Entry<Long, String> fetchConfig(String registryId, String deviceId);

  public abstract CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  public abstract String fetchState(String deviceRegistryId, String deviceId);

  public abstract CloudModel listDevices(String deviceRegistryId);

  public abstract CloudModel modelDevice(String deviceRegistryId, String deviceId,
      CloudModel cloudModel);

  /**
   * Modify a device configuration. Return the full/complete update that was actually written.
   */
  public String modifyConfig(String registryId, String deviceId, SubFolder subFolder,
      String config) {
    int retryCount = CONFIG_UPDATE_MAX_RETRIES;
    while (retryCount > 0) {
      try {
        if (subFolder == SubFolder.UPDATE) {
          return updateConfig(registryId, deviceId, config, null);
        } else {
          Entry<Long, String> configPair = fetchConfig(registryId, deviceId);
          String configString = ofNullable(configPair.getValue()).orElse(EMPTY_JSON);
          Map<String, Object> configMap = toMap(configString);
          configMap.put(subFolder.toString(), toMap(config));
          return updateConfig(registryId, deviceId, stringify(configMap), configPair.getKey());
        }
      } catch (Exception e) {
        warn(
            format("Error updating config for %s/%s, remaining retries %d...", registryId, deviceId,
                --retryCount));
        safeSleep(CONFIG_UPDATE_BACKOFF_MS);
      }
    }
    throw new RuntimeException(
        format("Maximum config retry count exceeded for %s/%s, giving up.", registryId, deviceId));
  }

  /**
   * Send a command to a device.
   */
  public abstract void sendCommand(String registryId, String deviceId, SubFolder folder,
      String message);

  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
  }

  abstract String fetchRegistryMetadata(String registryId, String metadataKey);


}
