package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CONFIG_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CREATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.DELETE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.UPDATE_DEVICE_ACTION;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;

import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.SetupUdmiConfig;

/**
 * Mocked IoT provider for unit testing.
 */
public class IotMockProvider implements IotProvider {

  public static final String MOCK_DEVICE_ID = "MOCK-1";
  public static final String PROXY_DEVICE_ID = "AHU-22";
  private final SiteModel siteModel;
  private final String client;
  private final Map<String, CloudModel> cloudDevices = new HashMap<>();
  private List<MockAction> mockActions = new ArrayList<>();

  IotMockProvider(String projectId, String registryId, String cloudRegion) {
    siteModel = new SiteModel("../sites/udmi_site_model");
    siteModel.initialize();
    siteModel.forEachDeviceId(this::populateCloudModel);
    client = mockClientString(projectId, registryId, cloudRegion);
  }

  public static String mockClientString(String projectId, String registryId, String cloudRegion) {
    return String.format("projects/%s/region/%s/registry/%s", projectId, registryId,
        cloudRegion);
  }

  private void mockAction(ActionType action, String deviceId, Object data) {
    MockAction mockAction = new MockAction();
    mockAction.client = client;
    mockAction.action = action;
    mockAction.deviceId = deviceId;
    mockAction.data = data;
    mockActions.add(mockAction);
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device");
    mockAction(CONFIG_DEVICE_ACTION, deviceId, config);
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    mockAction(BLOCK_DEVICE_ACTION, deviceId, blocked);
  }

  @Override
  public void updateDevice(String deviceId, CloudModel device) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device");
    device.num_id = populateCloudModel(deviceId).num_id;
    mockAction(UPDATE_DEVICE_ACTION, deviceId, device);
  }

  @Override
  public void createDevice(String deviceId, CloudModel device) {
    checkArgument(!cloudDevices.containsKey(deviceId), "device already exists");
    CloudModel cloudModel = populateCloudModel(deviceId);
    device.num_id = cloudModel.num_id;
    cloudModel.resource_type = device.resource_type;
    mockAction(CREATE_DEVICE_ACTION, deviceId, device);
  }

  @Override
  public void deleteDevice(String deviceId) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device");
    cloudDevices.remove(deviceId);
    mockAction(DELETE_DEVICE_ACTION, deviceId, null);
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    return cloudDevices.get(deviceId);
  }

  private CloudModel populateCloudModel(String deviceId) {
    return cloudDevices.computeIfAbsent(deviceId, id -> {
      // By design all devices are initially populated as non-gateway devices.
      CloudModel device = new CloudModel();
      device.num_id = "" + Objects.hash(deviceId);
      return device;
    });
  }

  @Override
  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    checkArgument(cloudDevices.containsKey(proxyDeviceId), "missing proxy device");
    checkArgument(cloudDevices.containsKey(gatewayDeviceId), "missing gateway device");
    checkArgument(populateCloudModel(gatewayDeviceId).resource_type == GATEWAY, "not a gateway");
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

  @Override
  public SetupUdmiConfig getVersionInformation() {
    return new SetupUdmiConfig();
  }

  /**
   * The various actions that are mocked out.
   */
  public enum ActionType {
    BLOCK_DEVICE_ACTION,
    UPDATE_DEVICE_ACTION,
    DELETE_DEVICE_ACTION,
    BIND_DEVICE_ACTION,
    CONFIG_DEVICE_ACTION,
    CREATE_DEVICE_ACTION
  }

  /**
   * Holder class for mocked actions.
   */
  public static class MockAction {

    public String client;
    public ActionType action;
    public String deviceId;
    public Object data;
  }
}
