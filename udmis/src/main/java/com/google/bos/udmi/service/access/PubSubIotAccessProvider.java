package com.google.bos.udmi.service.access;

import java.util.Map.Entry;
import java.util.Set;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

public class PubSubIotAccessProvider extends IotAccessBase {

  PubSubIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
  }

  @Override
  protected Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  protected Set<String> getRegistriesForRegion(String region) {
    return null;
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    System.err.println("Send command!");
  }

  @Override
  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not implemented for PubSub");
  }

  @Override
  String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not implemented for PubSub");
  }
}
