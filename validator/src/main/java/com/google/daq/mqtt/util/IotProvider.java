package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.Device;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Set;

/**
 * Abstraction for a cloud-based IoT provider.
 */
public interface IotProvider {

  void updateConfig(String deviceId, String config);

  void setBlocked(String deviceId, boolean blocked);

  void updateDevice(String deviceId, Device device);

  void createDevice(Device makeDevice);

  Device fetchDevice(String deviceId);

  void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId);

  Set<String> fetchDeviceIds();

  String getDeviceConfig(String deviceId);

  List<Object> getMockActions();
}
