package com.google.bos.udmi.service.support;

import java.util.Map;

/**
 * Container reference class for a database entry.
 */
public abstract class DataRef {

  protected String collection;
  protected String deviceId;
  protected String registryId;

  private static String sanitize(String key) {
    if (key == null || key.contains("/") || key.contains(":")) {
      throw new IllegalStateException("Invalid entry key " + key);
    }
    return key;
  }

  public DataRef collection(String collection) {
    this.collection = sanitize(collection);
    return this;
  }

  public abstract void delete(String key);

  /**
   * Add a device specification.
   */
  public DataRef device(String deviceId) {
    this.deviceId = sanitize(deviceId);
    return this;
  }

  public abstract Map<String, String> entries();

  public abstract String get(String key);

  public abstract void put(String key, String value);

  /**
   * Add a registry specification.
   */
  public DataRef registry(String registryId) {
    this.registryId = sanitize(registryId);
    return this;
  }

}
