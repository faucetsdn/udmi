package com.google.bos.udmi.service.access;

import static java.lang.String.format;

import com.google.bos.udmi.service.pod.ContainerProvider;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

public interface IotAccessProvider extends ContainerProvider {

  Map<IotProvider, Class<? extends IotAccessBase>> PROVIDERS = ImmutableMap.of(
      IotProvider.DYNAMIC, DynamicIotAccessProvider.class,
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.GCP, GcpIotAccessProvider.class,
      IotProvider.PUBSUB, PubSubIotAccessProvider.class,
      IotProvider.LOCAL, LocalIotAccessProvider.class
  );

  /**
   * Factory constructor for new instances.
   */
  static IotAccessProvider from(IotAccess iotAccess) {
    try {
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating access provider type %s", iotAccess.provider), e);
    }
  }

  Entry<Long, String> fetchConfig(String registryId, String deviceId);

  Set<String> getRegistriesForRegion(String region);

  boolean isEnabled();

  void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message);

  String updateConfig(String registryId, String deviceId, String config,
      Long version);

  CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  String fetchState(String deviceRegistryId, String deviceId);

  CloudModel listDevices(String deviceRegistryId);

  CloudModel modelResource(String deviceRegistryId, String deviceId,
      CloudModel cloudModel);

  String fetchRegistryMetadata(String registryId, String metadataKey);
}
