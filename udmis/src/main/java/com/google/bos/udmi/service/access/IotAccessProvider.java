package com.google.bos.udmi.service.access;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Map.Entry;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration.IotProvider;
import udmi.schema.IotAccess;

/**
 * Generic interface for accessing iot device management.
 */
public interface IotAccessProvider {

  Map<IotProvider, Class<? extends IotAccessProvider>> PROVIDERS = ImmutableMap.of(
      IotProvider.CLEARBLADE, ClearBladeIotAccessProvider.class,
      IotProvider.GCP, GcpIotAccessProvider.class
  );

  /**
   * Factory constructor for new instances.
   */
  static IotAccessProvider from(IotAccess iotAccess) {
    try {
      return PROVIDERS.get(iotAccess.provider).getDeclaredConstructor(IotAccess.class)
          .newInstance(iotAccess);
    } catch (Exception e) {
      throw new RuntimeException("While instantiating access provider " + iotAccess.provider, e);
    }
  }

  void activate();

  Entry<String, String> fetchConfig(String registryId, String deviceId);

  void shutdown();

  /**
   * Send a command to a device.
   */
  void sendCommand(String registryId, String deviceId, SubFolder folder, String message);

  void modifyConfig(String registryId, String deviceId, SubFolder folder, String contents);

  void updateConfig(String registryId, String deviceId, String config);

  CloudModel listDevices(String deviceRegistryId);

  CloudModel fetchDevice(String deviceRegistryId, String deviceId);

  CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel);
}
