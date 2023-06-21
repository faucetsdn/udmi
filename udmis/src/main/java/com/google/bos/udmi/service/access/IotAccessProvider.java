package com.google.bos.udmi.service.access;

import static java.lang.String.format;

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
public interface IotAccessProvider {

  Map<IotProvider, Class<? extends IotAccessProvider>> PROVIDERS = ImmutableMap.of(
      IotProvider.DYNAMIC, DynamicIotAccessProvider.class,
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.GCP, GcpIotAccessProvider.class
  );

  /**
   * Factory constructor for new instances.
   */
  static IotAccessProvider from(Entry<String, IotAccess> iotAccessEntry) {
    String entryName = iotAccessEntry.getKey();
    IotAccess iotAccess = iotAccessEntry.getValue();
    try {
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating access provider %s as %s", entryName, iotAccess.provider), e);
    }
  }

  void activate();


  default void setProviders(Map<String, IotAccessProvider> allProviders) {
  }

  String fetchRegistryMetadata(String registryId, String metadataKey);

  Entry<String, String> fetchConfig(String registryId, String deviceId);

  CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  CloudModel listDevices(String deviceRegistryId);

  CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel);

  void modifyConfig(String registryId, String deviceId, SubFolder folder, String contents);

  /**
   * Send a command to a device.
   */
  void sendCommand(String registryId, String deviceId, SubFolder folder, String message);

  void shutdown();
}
