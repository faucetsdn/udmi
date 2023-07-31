package com.google.bos.udmi.service.core;

import udmi.schema.Envelope.SubFolder;

public interface Distributor {

  void activate();

  void registryBackoffClear(String deviceRegistryId, String deviceId);

  void sendCommand(String reflectRegistry, String deviceRegistry, SubFolder udmi, String stringify);

  void setProviderAffinity(String deviceRegistryId, String deviceId, String source);

  void shutdown();
}
