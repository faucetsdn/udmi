package com.google.daq.mqtt.util;

import java.util.List;
import udmi.schema.Credential;

/**
 * Bucket of settings to use for a cloud device entry.
 */
public class CloudDeviceSettings {

  public List<Credential> credentials;
  public String metadata;
  public String deviceNumId;
  public List<String> proxyDevices;
  public String config;
  public String keyAlgorithm;
  public byte[] keyBytes;
  public String updated;
  public String generation;
  public boolean blocked;
}
