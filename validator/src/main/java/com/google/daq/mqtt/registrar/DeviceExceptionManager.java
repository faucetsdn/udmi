package com.google.daq.mqtt.registrar;

import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ConfigUtil.AllDeviceExceptions;
import com.google.daq.mqtt.util.ConfigUtil.DeviceExceptions;
import java.io.File;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

public class DeviceExceptionManager {

  private final AllDeviceExceptions allDeviceExceptions;

  public DeviceExceptionManager(File siteConfig) {
    allDeviceExceptions = ConfigUtil.loadExceptions(siteConfig);
  }

  public List<Pattern> forDevice(String id) {
    if (allDeviceExceptions == null) {
      return List.of();
    }
    Optional<Entry<String, DeviceExceptions>> first = allDeviceExceptions.entrySet()
        .stream().filter(devices -> id.startsWith(devices.getKey())).findFirst();
    return first.map(entry -> entry.getValue().patterns).orElse(null);
  }
}
