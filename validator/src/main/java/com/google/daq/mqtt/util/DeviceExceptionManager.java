package com.google.daq.mqtt.util;

import com.google.daq.mqtt.util.ConfigUtil.AllDeviceExceptions;
import com.google.daq.mqtt.util.ConfigUtil.DeviceExceptions;
import java.io.File;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Manage validation exceptions for a device. Specifically, handle pattered exclusions.
 */
public class DeviceExceptionManager {

  private final AllDeviceExceptions allDeviceExceptions;

  /**
   * Create a manager for the given site.
   *
   * @param siteConfig site to use for index of allowed exceptions
   */
  public DeviceExceptionManager(File siteConfig) {
    allDeviceExceptions = ConfigUtil.loadExceptions(siteConfig);
  }

  /**
   * Get a list of allowed exception patterns for a given device.
   *
   * @param id device id
   * @return list of exception patterns
   */
  public List<Pattern> forDevice(String id) {
    if (allDeviceExceptions == null) {
      return List.of();
    }
    Optional<Entry<String, DeviceExceptions>> first = allDeviceExceptions.entrySet()
        .stream().filter(devices -> id.startsWith(devices.getKey())).findFirst();
    return first.map(entry -> entry.getValue().patterns).orElse(null);
  }
}
