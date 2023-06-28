package com.google.bos.udmi.service.access;

import static java.lang.String.format;

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

  public void setProviderAffinity(String registryId, String deviceId, String providerId) {
  }

  abstract String fetchRegistryMetadata(String registryId, String metadataKey);

  public abstract Entry<String, String> fetchConfig(String registryId, String deviceId);

  public abstract CloudModel listDevices(String deviceRegistryId);

  public abstract CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  public abstract void modifyConfig(String registryId, String deviceId, SubFolder folder,
      String contents);

  /**
   * Send a command to a device.
   */
  public abstract void sendCommand(String registryId, String deviceId, SubFolder folder,
      String message);

  public abstract String fetchState(String deviceRegistryId, String deviceId);

  public abstract CloudModel modelDevice(String deviceRegistryId, String deviceId,
      CloudModel cloudModel);
}
