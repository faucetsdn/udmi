package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import java.util.List;

/**
 * Bucket of settings to use for a cloud device entry.
 */
public class CloudDeviceSettings {

  public List<DeviceCredential> credentials;
  public String metadata;
  public List<String> proxyDevices;
  public String config;
  public String keyAlgorithm;
  public byte[] keyBytes;
  public String updated;
  public String generation;
}
