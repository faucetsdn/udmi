package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.Device;
import java.util.Set;

public class IotMockProvider implements IotProvider {

  public IotMockProvider(String projectId, String registryId, String cloudRegion) {
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void updateDevice(String deviceId, Device device) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void createDevice(Device makeDevice) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Device fetchDevice(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Set<String> fetchDeviceList() {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public String getDeviceConfig(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }
}
