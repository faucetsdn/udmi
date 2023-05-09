package com.google.bos.udmi.service.access;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Generic interface for accessing iot device management.
 */
public interface IotAccessProvider {

  Map<IotAccess.Provider, Class<? extends IotAccessProvider>> PROVIDERS = ImmutableMap.of(
      IotAccess.Provider.CLEARBLADE_IOT_CORE, ClearBladeIotAccessProvider.class,
      IotAccess.Provider.GCP_IOT_CORE, GcpIotAccessProvider.class
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
