package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class DataRef {


  private final String key;
  private final IotDataProvider provider;
  private String registryId;
  private String deviceId;

  public DataRef(IotDataProvider dataProvider, String key) {
    this.provider = dataProvider;
    this.key = sanitize(key);
  }

  private static String sanitize(String key) {
    if (key == null || key.contains("/") || key.contains(":")) {
      throw new IllegalStateException("Invalid entry key " + key);
    }
    return key;
  }

  public DataRef forRegistry(String newRegistryId) {
    checkState(registryId == null, "registryId already defined");
    registryId = sanitize(newRegistryId);
    return this;
  }

  public DataRef forDevice(String newDeviceId) {
    checkState(deviceId == null, "deviceId already defined");
    deviceId = sanitize(newDeviceId);
    return this;
  }

  private String getKey() {
    if (registryId == null) {
      checkState(deviceId == null, "deviceId supplied without registry");
      return format("/s:%s", key);
    } else if (deviceId == null) {
      return format("/r/%s:%s", registryId, key);
    } else {
      return format("/r/%s/d/%s:%s", registryId, deviceId, key);
    }
  }

  public String get() {
    return provider.getEntry(getKey());
  }
}
