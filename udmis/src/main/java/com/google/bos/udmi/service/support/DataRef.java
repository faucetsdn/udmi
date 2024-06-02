package com.google.bos.udmi.service.support;

import static java.lang.String.format;

import java.util.Map;

/**
 * Container reference class for a database entry.
 */
public abstract class DataRef {

  protected String registryId;
  protected String deviceId;
  protected String collection;

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

  public abstract AutoCloseable lock();

  public abstract void put(String key, String value);

  /**
   * Add a registry specification.
   */
  public DataRef registry(String registryId) {
    this.registryId = sanitize(registryId);
    return this;
  }

  public String toString() {
    return format("r/%s/d/%s/c/%s", registryId, deviceId, collection);
  }
}
