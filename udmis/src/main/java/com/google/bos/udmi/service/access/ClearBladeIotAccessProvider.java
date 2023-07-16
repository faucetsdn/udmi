package com.google.bos.udmi.service.access;

import static com.clearblade.cloud.iot.v1.devicetypes.GatewayType.NON_GATEWAY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
import com.clearblade.cloud.iot.v1.binddevicetogateway.BindDeviceToGatewayRequest;
import com.clearblade.cloud.iot.v1.createdevice.CreateDeviceRequest;
import com.clearblade.cloud.iot.v1.deletedevice.DeleteDeviceRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListResponse;
import com.clearblade.cloud.iot.v1.devicetypes.Device;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceConfig;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceCredential;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceName;
import com.clearblade.cloud.iot.v1.devicetypes.FieldMask;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayAuthMethod;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayConfig;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayListOptions;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayType;
import com.clearblade.cloud.iot.v1.getdevice.GetDeviceRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsResponse;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesRequest;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesResponse;
import com.clearblade.cloud.iot.v1.modifycloudtodeviceconfig.ModifyCloudToDeviceConfigRequest;
import com.clearblade.cloud.iot.v1.registrytypes.DeviceRegistry;
import com.clearblade.cloud.iot.v1.registrytypes.LocationName;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyCredential;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyFormat;
import com.clearblade.cloud.iot.v1.registrytypes.RegistryName;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceRequest;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceResponse;
import com.clearblade.cloud.iot.v1.unbinddevicefromgateway.UnbindDeviceFromGatewayRequest;
import com.clearblade.cloud.iot.v1.updatedevice.UpdateDeviceRequest;
import com.clearblade.cloud.iot.v1.utils.ByteString;
import com.clearblade.cloud.iot.v1.utils.LogLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import java.util.AbstractMap.SimpleEntry;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * IoT access provider for (deprecated) GCP IoT Core.
 * TODO: Need to implement page tokens for all requisite API calls.
 */
public class ClearBladeIotAccessProvider extends IotAccessBase {

  static final Set<String> CLOUD_REGIONS =
      ImmutableSet.of("us-central1", "europe-west1", "asia-east1");
  private static final String UDMIS_REGISTRY = "UDMS-REFLECT";
  private static final String EMPTY_VERSION = "0";
  private static final String EMPTY_JSON = "{}";
  private static final BiMap<Key_format, PublicKeyFormat> AUTH_TYPE_MAP = ImmutableBiMap.of(
      Key_format.RS_256, PublicKeyFormat.RSA_PEM,
      Key_format.RS_256_X_509, PublicKeyFormat.RSA_X509_PEM,
      Key_format.ES_256, PublicKeyFormat.ES256_PEM,
      Key_format.ES_256_X_509, PublicKeyFormat.ES256_X509_PEM
  );
  private static final String EMPTY_RETURN_RECEIPT = "-1";
  private static final String UPDATE_FIELD_MASK = "blocked,credentials,metadata";
  private static final GatewayConfig NON_GATEWAY_CONFIG = new GatewayConfig();
  private static final GatewayConfig GATEWAY_CONFIG = GatewayConfig.newBuilder()
      .setGatewayType(GatewayType.GATEWAY)
      .setGatewayAuthMethod(GatewayAuthMethod.ASSOCIATION_ONLY)
      .build();

  private final String projectId;
  private Map<String, String> registryCloudRegions;

  /**
   * Create a new instance for interfacing with GCP IoT Core.
   */
  public ClearBladeIotAccessProvider(IotAccess iotAccess) {
    projectId = requireNonNull(iotAccess.project_id, "gcp project id not specified");
  }

  private static CloudModel convertDevice(Device device, Operation operation) {
    // CloudModel cloudModel = new CloudModel();
    // cloudModel.num_id = device.getNumId().toString();
    // cloudModel.blocked = device.getBlocked();
    // cloudModel.metadata = device.getMetadata();
    // cloudModel.last_event_time = getDate(device.getLastEventTime());
    // cloudModel.is_gateway = ifNotNullGet(device.getGatewayConfig(),
    //     config -> GATEWAY_TYPE.equals(config.getGatewayType()));
    // cloudModel.credentials = convertIot(device.getCredentials());
    // cloudModel.operation = operation;
    // return cloudModel;
    throw new RuntimeException("Not yet implemented");
  }

  private static Credential convertIot(DeviceCredential device) {
    // Credential credential = new Credential();
    // credential.key_data = device.getPublicKey().getKey();
    // credential.key_format = AUTH_TYPE_MAP.inverse().get(device.getPublicKey().getFormat());
    // return credential;
    throw new RuntimeException("Not yet implemented");
  }

  private static List<Credential> convertIot(List<DeviceCredential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(ClearBladeIotAccessProvider::convertIot)
            .collect(Collectors.toList()));
  }

  private static Entry<String, CloudModel> convertToEntry(Device device) {
    CloudModel cloudModel = new CloudModel();
    Device.Builder deviceBuilder = device.toBuilder();
    cloudModel.num_id = extractNumId(device);
    return new SimpleEntry<>(deviceBuilder.getId(), cloudModel);
  }

  /**
   * Get a numerical ID for ths device. This field is a legacy from the deprecated GCP IoT Core
   * system, and so this provides a stable replacement value based off a hash of the device name
   * (including project and registry) for systems that require it to exist.
   */
  private static String extractNumId(Device device) {
    return format("%d", Math.abs(Objects.hash(device.toBuilder().getName())));
  }

  @Nullable
  private static Date getSafeDate(String lastEventTime) {
    return getDate(Strings.isNullOrEmpty(lastEventTime) ? null : lastEventTime);
  }

  @VisibleForTesting
  protected Map<String, String> fetchRegistryCloudRegions() {
    Map<String, String> regionMap = CLOUD_REGIONS.stream().map(this::getRegistriesForRegion)
        .flatMap(map -> map.entrySet().stream())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    debug(format("Fetched %s registry regions", regionMap.size()));
    return regionMap;
  }

  @VisibleForTesting
  protected DeviceManagerClient getDeviceManagerClient() {
    return new DeviceManagerClient();
  }

  private CloudModel bindDeviceToGateway(String registryId, String gatewayId,
      CloudModel cloudModel) {
    CloudModel reply = new CloudModel();
    reply.device_ids = new HashMap<>();
    Set<String> deviceIds = cloudModel.device_ids.keySet();
    reply.num_id = deviceIds.size() > 0 ? EMPTY_RETURN_RECEIPT : null;
    reply.operation = cloudModel.operation;
    deviceIds.forEach(id -> {
      try {
        String location = getRegistryLocation(registryId);
        RegistryName parent = RegistryName.of(projectId, location, registryId);
        BindDeviceToGatewayRequest request =
            BindDeviceToGatewayRequest.Builder.newBuilder()
                .setParent(parent.getRegistryFullName())
                .setDevice(id)
                .setGateway(gatewayId)
                .build();
        DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
        requireNonNull(deviceManagerClient.bindDeviceToGateway(request),
            "binding device to gateway");
      } catch (Exception e) {
        throw new RuntimeException(format("While binding %s to gateway %s", id, gatewayId), e);
      }
    });
    return reply;
  }

  private CloudModel convert(Device deviceRaw, Operation operation) {
    Device.Builder device = deviceRaw.toBuilder();
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = extractNumId(deviceRaw);
    cloudModel.blocked = device.isBlocked();
    cloudModel.metadata = device.getMetadata();
    cloudModel.last_event_time = getSafeDate(device.getLastEventTime());
    cloudModel.is_gateway = ifNotNullGet(device.getGatewayConfig(),
        config -> GatewayType.GATEWAY == config.getGatewayType());
    cloudModel.credentials = convertIot(device.getCredentials());
    cloudModel.operation = operation;
    return cloudModel;
  }

  private Device convert(CloudModel cloudModel, String deviceId) {
    return Device.newBuilder()
        .setBlocked(isTrue(cloudModel.blocked))
        .setCredentials(convertUdmi(cloudModel.credentials))
        .setGatewayConfig(isTrue(cloudModel.is_gateway) ? GATEWAY_CONFIG : NON_GATEWAY_CONFIG)
        .setLogLevel(LogLevel.INFO)
        .setMetadata(cloudModel.metadata)
        .setId(deviceId)
        .build();
  }

  private CloudModel convert(Empty execute, Operation operation) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = operation;
    cloudModel.num_id = EMPTY_RETURN_RECEIPT;
    return cloudModel;
  }

  private DeviceCredential convertUdmi(Credential credential) {
    return DeviceCredential.newBuilder()
        .setPublicKey(PublicKeyCredential.newBuilder()
            .setKey(credential.key_data)
            .setFormat(AUTH_TYPE_MAP.get(credential.key_format))
            .build())
        .build();
  }

  private List<DeviceCredential> convertUdmi(List<Credential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(this::convertUdmi).collect(Collectors.toList()));
  }

  private CloudModel createDevice(String registryId, Device device) {
    DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
    String location = getRegistryLocation(registryId);
    String parent = RegistryName.of(projectId, location, registryId).toString();
    CreateDeviceRequest request =
        CreateDeviceRequest.Builder.newBuilder().setParent(parent).setDevice(device)
            .build();
    requireNonNull(deviceManagerClient.createDevice(request),
        "create device failed for " + parent);
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = Operation.CREATE;
    cloudModel.num_id = extractNumId(device);
    return cloudModel;
  }

  private CloudModel deleteDevice(String registryId, Device device) {
    String deviceId = requireNonNull(device.toBuilder().getId(), "unspecified device id");
    try {
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      String location = getRegistryLocation(registryId);
      DeviceName deviceName = DeviceName.of(projectId, location, registryId, deviceId);
      DeleteDeviceRequest request =
          DeleteDeviceRequest.Builder.newBuilder().setName(deviceName).build();
      deviceManagerClient.deleteDevice(request);
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.DELETE;
      cloudModel.num_id = extractNumId(device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException(format("While deleting %s/%s", registryId, deviceId), e);
    }
  }

  private String getDeviceName(String registryId, String deviceId) {
    return DeviceName.of(projectId, getRegistryLocation(registryId), registryId, deviceId)
        .toString();
  }

  private GatewayListOptions getGatewayListOptions(String gatewayId) {
    return GatewayListOptions.newBuilder()
        .setGatewayType(NON_GATEWAY)
        .setAssociationsGatewayId(requireNonNull(gatewayId, "gateway undefined"))
        .build();
  }

  @NotNull
  private Map<String, String> getRegistriesForRegion(String region) {
    try {
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      ListDeviceRegistriesRequest request = ListDeviceRegistriesRequest.Builder.newBuilder()
          .setParent(LocationName.of(projectId, region).getLocationFullName())
          .build();
      ListDeviceRegistriesResponse response = deviceManagerClient.listDeviceRegistries(request);
      requireNonNull(response, "get registries response is null");
      List<DeviceRegistry> deviceRegistries = response.getDeviceRegistriesList();
      Map<String, String> registries =
          ofNullable(deviceRegistries).orElseGet(ImmutableList::of).stream()
              .map(registry -> registry.toBuilder().getId())
              .collect(Collectors.toMap(item -> item, item -> region));
      debug("Fetched " + registries.size() + " registries for region " + region);
      return registries;
    } catch (Exception e) {
      throw new RuntimeException("While fetching registries for region " + region, e);
    }
  }

  private String getRegistryLocation(String registry) {
    return requireNonNull(registryCloudRegions.get(registry), "region for registry " + registry);
  }

  private String getRegistryName(String registryId) {
    return RegistryName.of(projectId, getRegistryLocation(registryId), registryId).toString();
  }

  private CloudModel listRegistryDevices(String deviceRegistryId, String gatewayId) {
    try {
      String location = getRegistryLocation(deviceRegistryId);
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      GatewayListOptions gatewayListOptions = ifNotNullGet(gatewayId, this::getGatewayListOptions);
      String registryFullName =
          RegistryName.of(projectId, location, deviceRegistryId).getRegistryFullName();
      DevicesListRequest request = DevicesListRequest.Builder.newBuilder().setParent(
              registryFullName)
          .setGatewayListOptions(gatewayListOptions)
          .build();
      DevicesListResponse response = deviceManagerClient.listDevices(request);
      requireNonNull(response, "DeviceRegistriesList fetch failed");
      CloudModel cloudModel = new CloudModel();
      cloudModel.device_ids =
          response.getDevicesList().stream().map(ClearBladeIotAccessProvider::convertToEntry)
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue, GeneralUtils::mapReplace,
                  HashMap::new));
      ifNotNullThen(gatewayListOptions, options -> debug(format("Bound devices for %s: %s",
          gatewayId, CSV_JOINER.join(cloudModel.device_ids.keySet()))));
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices " + getRegistryName(deviceRegistryId), e);
    }
  }

  private void unbindDevice(String registryId, String gatewayId, String proxyId) {
    try {
      debug(format("Unbind %s: %s from %s", registryId, proxyId, gatewayId));
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      String location = getRegistryLocation(registryId);
      UnbindDeviceFromGatewayRequest request = UnbindDeviceFromGatewayRequest.Builder.newBuilder()
          .setParent(RegistryName.of(projectId, location, registryId).getRegistryFullName())
          .setGateway(gatewayId).setDevice(proxyId).build();
      requireNonNull(deviceManagerClient.unbindDeviceFromGateway(request), "invalid response");
    } catch (Exception e) {
      throw new RuntimeException("While unbinding " + proxyId + " from " + gatewayId, e);
    }
  }

  private void unbindGatewayDevices(String registryId, Device device) {
    String gatewayId = device.toBuilder().getId();
    CloudModel cloudModel = listRegistryDevices(registryId, gatewayId);
    debug(format("Unbinding from %s/%s: %s", registryId, gatewayId,
        CSV_JOINER.join(cloudModel.device_ids.keySet())));
    ifNotNullThen(cloudModel.device_ids, ids -> ids.keySet()
        .forEach(id -> unbindDevice(registryId, gatewayId, id)));
  }

  private String updateConfig(String registryId, String deviceId, String config) {
    try {
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      ByteString binaryData = new ByteString(encodeBase64(config));
      String updateVersion = null;
      String location = getRegistryLocation(registryId);
      ModifyCloudToDeviceConfigRequest request =
          ModifyCloudToDeviceConfigRequest.Builder.newBuilder()
              .setName(DeviceName.of(projectId, location, registryId, deviceId).toString())
              .setBinaryData(binaryData).setVersionToUpdate(updateVersion).build();
      DeviceConfig response = deviceManagerClient.modifyCloudToDeviceConfig(request);
      System.err.println("Config modified version " + response.getVersion());
      return config;
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  private CloudModel updateDevice(String registryId, Device device) {
    DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
    String deviceId = device.toBuilder().getId();
    String name = getDeviceName(registryId, deviceId);
    Device fullDevice = device.toBuilder().setName(name).build();
    try {
      UpdateDeviceRequest request =
          UpdateDeviceRequest.Builder.newBuilder().setDevice(fullDevice).setName(name)
              .setUpdateMask(UPDATE_FIELD_MASK).build();
      requireNonNull(deviceManagerClient.updateDevice(request), "Invalid RPC response");
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.UPDATE;
      cloudModel.num_id = extractNumId(device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While updating " + deviceId, e);
    }
  }

  @Override
  public void activate() {
    super.activate();
    debug("Initializing ClearBlade access provider for project " + projectId);
    registryCloudRegions = fetchRegistryCloudRegions();
  }

  @Override
  public Entry<String, String> fetchConfig(String registryId, String deviceId) {
    try {
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
      String location = getRegistryLocation(registryId);
      ListDeviceConfigVersionsRequest request = ListDeviceConfigVersionsRequest.Builder.newBuilder()
          .setName(DeviceName.of(projectId, location, registryId, deviceId)
              .toString()).setNumVersions(1).build();
      ListDeviceConfigVersionsResponse listDeviceConfigVersionsResponse =
          deviceManagerClient.listDeviceConfigVersions(request);
      List<DeviceConfig> deviceConfigs = listDeviceConfigVersionsResponse.getDeviceConfigList();
      if (deviceConfigs.isEmpty()) {
        return new SimpleEntry<>(EMPTY_VERSION, EMPTY_JSON);
      }
      DeviceConfig deviceConfig = deviceConfigs.get(0);
      String config = ifNotNullGet((String) deviceConfig.getBinaryData(),
          binaryData -> new String(Base64.getDecoder().decode(binaryData)));
      return new SimpleEntry<>(deviceConfig.getVersion(), config);
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    String devicePath = getDeviceName(deviceRegistryId, deviceId);
    try {
      String location = getRegistryLocation(deviceRegistryId);
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      DeviceName name = DeviceName.of(projectId, location, deviceRegistryId, deviceId);
      GetDeviceRequest request = GetDeviceRequest.Builder.newBuilder().setName(name)
          .setFieldMask(FieldMask.newBuilder().build()).build();
      Device device = deviceManagerClient.getDevice(request);
      requireNonNull(device, "GetDeviceRequest failed");
      CloudModel cloudModel = convert(device, Operation.FETCH);
      cloudModel.device_ids = listRegistryDevices(deviceRegistryId, deviceId).device_ids;
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device " + devicePath, e);
    }

  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    try {
      CloudModel cloudModel = fetchDevice(UDMIS_REGISTRY, registryId);
      return cloudModel.metadata.get(metadataKey);
    } catch (Exception e) {
      debug(format("No device entry for %s/%s", UDMIS_REGISTRY, registryId));
      return null;
    }
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("Not yet implemented " + this.getClass());
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    return listRegistryDevices(deviceRegistryId, null);
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    String devicePath = getDeviceName(registryId, deviceId);
    Operation operation = cloudModel.operation;
    try {
      Device device = convert(cloudModel, deviceId);
      switch (operation) {
        case CREATE:
          return createDevice(registryId, device);
        case UPDATE:
          return updateDevice(registryId, device);
        case DELETE:
          debug("Processing DELETE for " + deviceId);
          unbindGatewayDevices(registryId, device);
          return deleteDevice(registryId, device);
        case BIND:
          return bindDeviceToGateway(registryId, deviceId, cloudModel);
        default:
          throw new RuntimeException("Unknown operation " + operation);
      }
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing " + devicePath, e);
    }
  }

  @Override
  public String modifyConfig(String registryId, String deviceId, SubFolder subFolder,
      String contents) {
    if (subFolder == SubFolder.UPDATE) {
      return updateConfig(registryId, deviceId, contents);
    } else {
      // TODO: Need to implement checking-and-retry of config version for concurrent operations.
      String configString = fetchConfig(registryId, deviceId).getValue();
      Map<String, Object> configMap = toMap(configString);
      configMap.put(subFolder.toString(), contents);
      return updateConfig(registryId, deviceId, stringify(configMap));
    }
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder subFolder, String message) {
    try {
      ByteString binaryData = new ByteString(encodeBase64(message));
      String location = getRegistryLocation(registryId);
      DeviceManagerClient deviceManagerClient = getDeviceManagerClient();
      String deviceName = DeviceName.of(projectId, location, registryId, deviceId).toString();
      SendCommandToDeviceRequest request = SendCommandToDeviceRequest.Builder.newBuilder()
          .setName(deviceName)
          .setBinaryData(binaryData).setSubfolder(subFolder.value()).build();
      SendCommandToDeviceResponse response = deviceManagerClient.sendCommandToDevice(request);
      if (response == null) {
        throw new RuntimeException("SendCommandToDevice execution failed for " + deviceName);
      }
      debug("Sent command to " + deviceName);
    } catch (Exception e) {
      throw new RuntimeException(format("While sending command to ClearBlade %s/%s/%s",
          registryId, deviceId, subFolder), e);
    }
  }

  class Empty {
    // Temp hacky class
  }
}

