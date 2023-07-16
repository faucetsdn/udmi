package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.using;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that runs locally (through filesystem).
 */
public class LocalIotAccessProvider extends IotAccessBase {

  BlockingQueue<String> sentCommands = new LinkedBlockingQueue<>();

  /**
   * Create a new instance for interfacing with multiple providers.
   */
  public LocalIotAccessProvider(IotAccess iotAccess) {
  }

  @Override
  public void activate() {
    debug("activate");
  }

  public List<String> getCommands() {
    return using(new ArrayList<>(), sentCommands::drainTo);
  }

  @Override
  public void shutdown() {
    debug("shutdown");
  }

  @Override
  String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
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
  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    sentCommands.add(format("%s/%s/%s:%s", registryId, deviceId, "config", "update"));
    return config;
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder folder, String message) {
    sentCommands.add(format("%s/%s/%s:%s", registryId, deviceId, folder, message));
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
