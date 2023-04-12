package com.google.daq.mqtt.util;

import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;

/**
 * Mocked IoT provider for unit testing.
 */
public class IotMockProvider implements IotProvider {

  public static final String MOCK_DEVICE_ID = "MOCK-1";
  public static final String PROXY_DEVICE_ID = "AHU-22";
  private final SiteModel siteModel;
  private final String client;
  private List<MockAction> mockActions = new ArrayList<>();
  public static final String BLOCK_DEVICE_ACTION = "block";
  public static final String UPDATE_DEVICE_ACTION = "update";
  public static final String BIND_DEVICE_ACTION = "bind";
  public static final String CONFIG_DEVICE_ACTION = "config";

  IotMockProvider(String projectId, String registryId, String cloudRegion) {
    siteModel = new SiteModel("../sites/udmi_site_model");
    siteModel.initialize();
    client = mockClientString(projectId, registryId, cloudRegion);
  }

  public static String mockClientString(String projectId, String registryId, String cloudRegion) {
    return String.format("projects/%s/region/%s/registry/%s", projectId, registryId,
        cloudRegion);
  }

  private void mockAction(String action, String deviceId, Object data) {
    MockAction mockAction = new MockAction();
    mockAction.client = client;
    mockAction.action = action;
    mockAction.deviceId = deviceId;
    mockAction.data = data;
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
  public void updateDevice(String deviceId, CloudModel device) {
    mockAction(UPDATE_DEVICE_ACTION, deviceId, device);
  }

  @Override
  public void createDevice(String deviceId, CloudModel makeDevice) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void deleteDevice(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    CloudModel device = new CloudModel();
    device.num_id = "" + Objects.hash(deviceId);
    return device;
  }

  @Override
  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    mockAction(BIND_DEVICE_ACTION, proxyDeviceId, gatewayDeviceId);
  }

  @Override
  public Set<String> fetchDeviceIds(String forGatewayId) {
    HashSet<String> deviceIds = new HashSet<>(siteModel.allDeviceIds());
    deviceIds.add(MOCK_DEVICE_ID);
    if (forGatewayId != null) {
      deviceIds.remove(forGatewayId);
      deviceIds.remove(PROXY_DEVICE_ID);
    }
    return deviceIds;
  }

  @Override
  public String getDeviceConfig(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void shutdown() {
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
    public String client;
    public String action;
    public String deviceId;
    public Object data;
  }
}
