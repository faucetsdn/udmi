package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.GeneralUtils;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
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

  protected abstract Entry<Long, String> fetchConfig(String registryId, String deviceId);

  public abstract CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  public abstract String fetchState(String deviceRegistryId, String deviceId);

  public abstract CloudModel listDevices(String deviceRegistryId);

  public abstract CloudModel modelDevice(String deviceRegistryId, String deviceId,
      CloudModel cloudModel);

  /**
   * Modify a device configuration. Return the full/complete update that was actually written.
   */
  public String modifyConfig(String registryId, String deviceId, Function<String, String> munger) {
    int retryCount = CONFIG_UPDATE_MAX_RETRIES;
    try {
      while (true) {
        try {
          Entry<Long, String> configPair = fetchConfig(registryId, deviceId);
          String updated = munger.apply(ifNotNullGet(configPair, Entry::getValue));
          Long version = ifNotNullGet(configPair, Entry::getKey);
          return updated == null ? null : updateConfig(registryId, deviceId, updated, version);
        } catch (Exception e) {
          warn(
              format("Error updating config for %s/%s, remaining retries %d...", registryId,
                  deviceId,
                  --retryCount));
          safeSleep(CONFIG_UPDATE_BACKOFF_MS);
          if (retryCount <= 0) {
            throw e;
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
          format("Maximum config retry count exceeded for %s/%s, giving up.", registryId,
              deviceId), e);
    }
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
