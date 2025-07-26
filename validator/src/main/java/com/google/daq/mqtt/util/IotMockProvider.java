package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CONFIG_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CREATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.DELETE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.UPDATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.UPDATE_REGISTRY_ACTION;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;

import com.google.udmi.util.IotProvider;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Metadata;
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
  private List<MockAction> mockActions = Collections.synchronizedList(new ArrayList<>());

  /**
   * Create a mock provider used for unit testing.
   */
  public IotMockProvider(ExecutionConfiguration executionConfiguration) {
    siteModel = new SiteModel(executionConfiguration);
    siteModel.initialize();
    ifNotTrueThen(executionConfiguration.project_id.equals(SiteModel.MOCK_CLEAN),
        () -> siteModel.forEachDeviceId(this::populateCloudModel));
    client = mockClientString(executionConfiguration.project_id, siteModel.getRegistryId(),
        siteModel.getCloudRegion());
  }

  public static String mockClientString(String projectId, String registryId, String cloudRegion) {
    return String.format("projects/%s/region/%s/registry/%s", projectId, registryId,
        cloudRegion);
  }

  private void mockAction(ActionType action, String deviceId, Object data, String modifier) {
    MockAction mockAction = new MockAction();
    mockAction.client = client;
    mockAction.action = action;
    mockAction.deviceId = deviceId;
    mockAction.data = data;
    mockActions.add(mockAction);
  }

  @Override
  public void updateConfig(String deviceId, SubFolder subFolder, String config) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device for config: " + deviceId);
    mockAction(CONFIG_DEVICE_ACTION, deviceId, config, subFolder.value());
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    mockAction(BLOCK_DEVICE_ACTION, deviceId, blocked, null);
  }

  @Override
  public void updateDevice(String deviceId, CloudModel device) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device for update: " + deviceId);
    device.num_id = populateCloudModel(deviceId).num_id;
    mockAction(UPDATE_DEVICE_ACTION, deviceId, device, null);
  }

  @Override
  public void updateRegistry(CloudModel registry) {
    registry.num_id = populateCloudModel("registry_id").num_id;
    mockAction(UPDATE_REGISTRY_ACTION, null, registry, null);
  }

  @Override
  public void createResource(String deviceId, CloudModel device) {
    checkArgument(!cloudDevices.containsKey(deviceId), "device already exists: " + deviceId);
    CloudModel cloudModel = populateCloudModel(deviceId);
    device.num_id = cloudModel.num_id;
    cloudModel.resource_type = device.resource_type;
    mockAction(CREATE_DEVICE_ACTION, deviceId, device, null);
  }

  @Override
  public void deleteDevice(String deviceId, Set<String> unbindIds) {
    checkArgument(cloudDevices.containsKey(deviceId), "missing device for delete: " + deviceId);
    cloudDevices.remove(deviceId);
    mockAction(DELETE_DEVICE_ACTION, deviceId, null, null);
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    return cloudDevices.get(deviceId);
  }

  private synchronized CloudModel populateCloudModel(String deviceId) {
    return cloudDevices.computeIfAbsent(deviceId, id -> {
      // By design all devices are initially populated as non-gateway devices.
      CloudModel device = new CloudModel();
      device.num_id = "" + Objects.hash(deviceId);
      return device;
    });
  }

  @Override
  public void bindGatewayDevices(String gatewayDeviceId, Set<String> proxyDeviceIds,
      boolean toBind) {
    proxyDeviceIds.forEach(proxyDeviceId -> {
      checkArgument(cloudDevices.containsKey(proxyDeviceId),
          "missing proxy device: " + proxyDeviceId);
      checkArgument(cloudDevices.containsKey(gatewayDeviceId),
          "missing gateway device: " + gatewayDeviceId);
      checkArgument(populateCloudModel(gatewayDeviceId).resource_type == GATEWAY,
          "not a gateway: " + gatewayDeviceId);
      mockAction(BIND_DEVICE_ACTION, proxyDeviceId, gatewayDeviceId,
          (toBind ? ModelOperation.BIND : ModelOperation.UNBIND).value());
    });
  }

  @Override
  public Map<String, CloudModel> fetchCloudModels(String forGatewayId) {
    Map<String, CloudModel> siteDeviceModels = siteModel.allMetadata().entrySet().stream().collect(
        Collectors.toMap(Entry::getKey, model -> makeCloudModel(model.getValue())));
    Map<String, CloudModel> deviceModels = new HashMap<>(siteDeviceModels);
    deviceModels.put(MOCK_DEVICE_ID, makeMockModel(MOCK_DEVICE_ID));
    if (forGatewayId != null) {
      deviceModels.remove(forGatewayId);
      deviceModels.remove(PROXY_DEVICE_ID);
    }
    return deviceModels;
  }

  private CloudModel makeCloudModel(Metadata value) {
    return new CloudModel();
  }

  private CloudModel makeMockModel(String mockDeviceId) {
    return new CloudModel();
  }

  @Override
  public String getDeviceConfig(String deviceId) {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public void shutdown() {
  }

  @Override
  public boolean stillActive() {
    return false;
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
    CREATE_DEVICE_ACTION,
    UPDATE_REGISTRY_ACTION
  }

  /**
   * Holder class for mocked actions.
   */
  public static class MockAction {

    public String client;
    public ActionType action;
    public String deviceId;
    public String modifer;
    public Object data;
  }
}
