package com.google.bos.udmi.service.access;

import static java.util.Optional.ofNullable;

import java.util.Map.Entry;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

public class ImplicitIotAccessProvider extends IotAccessBase {

  private final boolean enabled;

  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    enabled = !ofNullable(options.get(ENABLED_KEY)).orElse(TRUE_OPTION).isEmpty();
  }

  @Override
  public void activate() {
    // TODO: Get database connection here
    super.activate();
    // TODO: Activate reflector pipe here
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel modelResource(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long version) {
    throw new RuntimeException("Not yet implemented");
  }
}
