package com.google.bos.udmi.service.access;

import java.util.Map.Entry;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that runs locally (through filesystem).
 */
public class LocalIotAccessProvider extends IotAccessBase {

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public LocalIotAccessProvider(IotAccess iotAccess) {
  }

  @Override
  String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Entry<String, String> fetchConfig(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder folder, String contents) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder folder, String message) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }
}
