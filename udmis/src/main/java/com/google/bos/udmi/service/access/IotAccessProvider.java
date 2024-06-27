package com.google.bos.udmi.service.access;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.UdmiComponent;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Interface for things that provide for iot-access controls for connecting to devices.
 */
public interface IotAccessProvider extends UdmiComponent {

  Map<IotProvider, Class<? extends IotAccessBase>> PROVIDERS = ImmutableMap.of(
      IotProvider.DYNAMIC, DynamicIotAccessProvider.class,
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.PUBSUB, PubSubIotAccessProvider.class,
      IotProvider.IMPLICIT, ImplicitIotAccessProvider.class,
      IotProvider.LOCAL, LocalIotAccessProvider.class
  );

  /**
   * Factory constructor for new instances.
   */
  static IotAccessProvider from(IotAccess iotAccess) {
    try {
      checkState(PROVIDERS.containsKey(iotAccess.provider),
          "Unknown access provider " + iotAccess.provider);
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While instantiating access provider type %s", iotAccess.provider), e);
    }
  }

  Entry<Long, String> fetchConfig(String registryId, String deviceId);

  CloudModel fetchDevice(String registryId, String deviceId);

  String fetchRegistryMetadata(String registryId, String metadataKey);

  String fetchState(String registryId, String deviceId);

  /**
   * Get all registries associated with this provider.
   */
  Set<String> getRegistries();

  boolean isEnabled();

  CloudModel listDevices(String registryId);

  CloudModel modelDevice(String registryId, String deviceId,
      CloudModel cloudModel);

  CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel);

  String modifyConfig(String registryId, String deviceId,
      Function<Entry<Long, String>, String> munger);

  void saveState(String registryId, String deviceId, String stateBlob);

  void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message);

  String updateConfig(String registryId, String deviceId, String config,
      Long version);

  void updateRegistryRegions(Map<String, String> regions);
}
