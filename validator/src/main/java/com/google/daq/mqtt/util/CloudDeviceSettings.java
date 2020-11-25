package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.DeviceCredential;

import java.util.Date;
import java.util.List;

public class CloudDeviceSettings {
  public DeviceCredential credential;
  public String metadata;
  public String updated;
  public List<String> proxyDevices;
  public String config;
  public String keyAlgorithm;
  public byte[] keyBytes;
}
