package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.Device;
import com.google.udmi.util.SiteModel;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mocked IoT provider for unit testing.
 */
public class IotMockProvider implements IotProvider {

  public static final String MOCK_DEVICE_ID = "MOCK-1";
  public static final String MOCK_SITE_MODEL = "src/test/site";
  private final SiteModel siteModel;
  private List<MockAction> mockActions = new ArrayList<>();
  public static final String BLOCK_DEVICE_ACTION = "block";
  public static final String UPDATE_DEVICE_ACTION = "update";
  public static final String BIND_DEVICE_ACTION = "bind";
  public static final String CONFIG_DEVICE_ACTION = "config";

  IotMockProvider(String projectId, String registryId, String cloudRegion) {
    siteModel = new SiteModel(MOCK_SITE_MODEL);
    siteModel.initialize();
  }

  private void mockAction(String action, String deviceId, Object paramater) {
    MockAction mockAction = new MockAction();
    mockAction.action = action;
    mockAction.deviceId = deviceId;
    mockAction.data = paramater;
    mockActions.add(mockAction);
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    mockAction(CONFIG_DEVICE_ACTION, deviceId, config);
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    mockAction(BLOCK_DEVICE_ACTION, deviceId, blocked);
  }

  @Override
  public void updateDevice(String deviceId, Device device) {
    mockAction(UPDATE_DEVICE_ACTION, deviceId, device);
  }

  @Override
  public void createDevice(Device makeDevice) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public Device fetchDevice(String deviceId) {
    Device device = new Device();
    SiteModel.Device modelDevice = siteModel.getDevice(deviceId);
    device.setId(deviceId);
    device.setNumId(new BigInteger("" + Objects.hash(deviceId), 10));
    return device;
  }

  @Override
  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    mockAction(BIND_DEVICE_ACTION, proxyDeviceId, gatewayDeviceId);
  }

  @Override
  public Set<String> fetchDeviceIds() {
    HashSet<String> deviceIds = new HashSet<>(siteModel.allDeviceIds());
    deviceIds.add(MOCK_DEVICE_ID);
    return deviceIds;
  }

  @Override
  public String getDeviceConfig(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public List<Object> getMockActions() {
    List<Object> savedActions = mockActions.stream().map(a -> (Object) a)
        .collect(Collectors.toList());
    mockActions = new ArrayList<>();
    return savedActions;
  }

  /**
   * Holder class for mocked actions.
   */
  public static class MockAction {
    public String action;
    public String deviceId;
    public Object data;
  }
}
