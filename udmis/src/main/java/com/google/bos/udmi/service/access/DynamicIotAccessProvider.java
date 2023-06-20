package com.google.bos.udmi.service.access;

import java.util.Map.Entry;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;

public class DynamicIotAccessProvider implements IotAccessProvider {

  @Override
  public void activate() {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public Entry<String, String> fetchConfig(String registryId, String deviceId) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public void shutdown() {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder folder, String message) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder folder, String contents) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }
}
