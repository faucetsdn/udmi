package com.google.bos.udmi.service.access;

import static com.clearblade.cloud.iot.v1.devicetypes.GatewayType.NON_GATEWAY;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getDate;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.Operation.CREATE;
import static udmi.schema.CloudModel.Operation.DELETE;
import static udmi.schema.CloudModel.Resource_type.DEVICE;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;
import static udmi.schema.CloudModel.Resource_type.REGISTRY;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
import com.clearblade.cloud.iot.v1.DeviceManagerInterface;
import com.clearblade.cloud.iot.v1.binddevicetogateway.BindDeviceToGatewayRequest;
import com.clearblade.cloud.iot.v1.createdevice.CreateDeviceRequest;
import com.clearblade.cloud.iot.v1.createdeviceregistry.CreateDeviceRegistryRequest;
import com.clearblade.cloud.iot.v1.deletedevice.DeleteDeviceRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListResponse;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesRequest;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesResponse;
import com.clearblade.cloud.iot.v1.devicetypes.Device;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceConfig;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceCredential;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceName;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceState;
import com.clearblade.cloud.iot.v1.devicetypes.FieldMask;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayAuthMethod;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayConfig;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayListOptions;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayType;
import com.clearblade.cloud.iot.v1.exception.ApplicationException;
import com.clearblade.cloud.iot.v1.getdevice.GetDeviceRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsResponse;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesRequest;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesResponse;
import com.clearblade.cloud.iot.v1.modifycloudtodeviceconfig.ModifyCloudToDeviceConfigRequest;
import com.clearblade.cloud.iot.v1.registrytypes.DeviceRegistry;
import com.clearblade.cloud.iot.v1.registrytypes.EventNotificationConfig;
import com.clearblade.cloud.iot.v1.registrytypes.LocationName;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyCredential;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyFormat;
import com.clearblade.cloud.iot.v1.registrytypes.RegistryName;
import com.clearblade.cloud.iot.v1.registrytypes.StateNotificationConfig;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceRequest;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceResponse;
import com.clearblade.cloud.iot.v1.unbinddevicefromgateway.UnbindDeviceFromGatewayRequest;
import com.clearblade.cloud.iot.v1.updatedevice.UpdateDeviceRequest;
import com.clearblade.cloud.iot.v1.utils.ByteString;
import com.clearblade.cloud.iot.v1.utils.LogLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * IoT access provider for (deprecated) GCP IoT Core.
 * TODO: Need to implement page tokens for all requisite API calls.
 */
public class ClearBladeIotAccessProvider extends IotAccessBase {

  private static final Set<String> CLOUD_REGIONS = ImmutableSet.of("us-central1");
  private static final String EMPTY_JSON = "{}";
  private static final BiMap<Key_format, PublicKeyFormat> AUTH_TYPE_MAP = ImmutableBiMap.of(
      Key_format.RS_256, PublicKeyFormat.RSA_PEM,
      Key_format.RS_256_X_509, PublicKeyFormat.RSA_X509_PEM,
      Key_format.ES_256, PublicKeyFormat.ES256_PEM,
      Key_format.ES_256_X_509, PublicKeyFormat.ES256_X509_PEM
  );
  private static final String EMPTY_RETURN_RECEIPT = "-1";
  private static final String RESOURCE_EXISTS = "-2";
  private static final String BLOCKED_FIELD_MASK = "blocked";
  private static final String UPDATE_FIELD_MASK = "blocked,credentials,metadata";
  private static final GatewayConfig NON_GATEWAY_CONFIG = new GatewayConfig();
  private static final GatewayConfig GATEWAY_CONFIG = GatewayConfig.newBuilder()
      .setGatewayType(GatewayType.GATEWAY)
      .setGatewayAuthMethod(GatewayAuthMethod.ASSOCIATION_ONLY)
      .build();

  private static final String UDMI_TARGET_TOPIC = "udmi_target"; // TODO: Make this not hardcoded.
  private static final String UDMI_STATE_TOPIC = "udmi_state"; // TODO: Make this not hardcoded.
  private static final String TOPIC_NAME_FORMAT = "projects/%s/topics/%s";
  public static final String REGISTRIES_FIELD_MASK = "id,name";

  private final String projectId;
  private final DeviceManagerInterface deviceManager;

  /**
   * Create a new instance for interfacing with GCP IoT Core.
   */
  public ClearBladeIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    deviceManager = getDeviceManager(ofNullable(iotAccess.profile_sec).orElse(0));
    projectId = getProjectId(iotAccess);
    ifNotTrueThen(isEnabled(),
        () -> warn("Clearblade access provided disabled because project id is null or empty"));
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

  @Nullable
  private static Date getSafeDate(String lastEventTime) {
    return getDate(isNullOrEmpty(lastEventTime) ? null : lastEventTime);
  }

  private static boolean isGateway(Device device) {
    return resourceType(device) == GATEWAY;
  }

  private static Resource_type resourceType(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    GatewayConfig gatewayConfig = device.getGatewayConfig();
    if (gatewayConfig != null && GatewayType.GATEWAY == gatewayConfig.getGatewayType()) {
      return GATEWAY;
    }
    return Resource_type.DEVICE;
  }

  @VisibleForTesting
  protected DeviceManagerInterface getDeviceManager(int monitorSec) {
    return ProfilingProxy.create(this, new DeviceManagerClient(), monitorSec);
  }

  @NotNull
  @Override
  public Set<String> getRegistriesForRegion(String region) {
    if (region == null) {
      return CLOUD_REGIONS;
    }

    try {
      String parent = LocationName.of(projectId, region).getLocationFullName();
      ListDeviceRegistriesRequest request = ListDeviceRegistriesRequest.Builder.newBuilder()
          .setParent(parent).setFieldMask(REGISTRIES_FIELD_MASK).build();
      ListDeviceRegistriesResponse response = deviceManager.listDeviceRegistries(request);
      requireNonNull(response, "get registries response is null");
      List<DeviceRegistry> deviceRegistries = response.getDeviceRegistriesList();
      Set<String> registries =
          ofNullable(deviceRegistries).orElseGet(ImmutableList::of).stream()
              .map(registry -> registry.toBuilder().getId())
              .collect(Collectors.toSet());
      debug("Fetched " + registries.size() + " registries for region " + region);
      return registries;
    } catch (Exception e) {
      throw new RuntimeException("While fetching registries for region " + region, e);
    }
  }

  @Override
  public boolean isEnabled() {
    return !isNullOrEmpty(projectId);
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long version) {
    try {
      ByteString binaryData = new ByteString(encodeBase64(config));
      String updateVersion = ifNotNullGet(version, v -> Long.toString(version));
      String location = getRegistryLocation(registryId);
      ModifyCloudToDeviceConfigRequest request =
          ModifyCloudToDeviceConfigRequest.Builder.newBuilder()
              .setName(DeviceName.of(projectId, location, registryId, deviceId).toString())
              .setBinaryData(binaryData).setVersionToUpdate(updateVersion).build();
      DeviceConfig response = deviceManager.modifyCloudToDeviceConfig(request);
      debug("Modified %s/%s config version %s", registryId, deviceId, response.getVersion());
      return config;
    } catch (Exception e) {
      throw new RuntimeException(
          format("While modifying device config %s/%s", registryId, deviceId), e);
    }
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
        requireNonNull(deviceManager.bindDeviceToGateway(request),
            "binding device to gateway");
      } catch (Exception e) {
        throw new RuntimeException(format("While binding %s to gateway %s", id, gatewayId), e);
      }
    });
    return reply;
  }

  private static Entry<String, CloudModel> convertPartial(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId();
    cloudModel.resource_type = resourceType(deviceRaw);
    cloudModel.last_event_time = getSafeDate(device.getLastEventTime());
    cloudModel.blocked = device.isBlocked() ? true : null;
    cloudModel.credentials = null;
    return new SimpleEntry<>(device.getId(), cloudModel);
  }

  private CloudModel convertFull(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId();
    cloudModel.resource_type = resourceType(deviceRaw);
    cloudModel.blocked = device.isBlocked();
    cloudModel.metadata = device.getMetadata();
    cloudModel.last_event_time = getSafeDate(device.getLastEventTime());
    cloudModel.last_state_time = getSafeDate(device.getLastStateTime());
    cloudModel.last_config_time = getSafeDate(device.getLastConfigSendTime());
    cloudModel.last_config_ack = getSafeDate(device.getLastConfigAckTime());
    cloudModel.last_event_time = getSafeDate(device.getLastErrorTime());
    cloudModel.credentials = convertIot(device.getCredentials());
    return cloudModel;
  }

  private Device convert(CloudModel cloudModel, String deviceId) {
    return Device.newBuilder()
        .setBlocked(isTrue(cloudModel.blocked))
        .setCredentials(convertUdmi(cloudModel.credentials))
        .setGatewayConfig(cloudModel.resource_type == GATEWAY ? GATEWAY_CONFIG : NON_GATEWAY_CONFIG)
        .setLogLevel(LogLevel.LOG_LEVEL_UNSPECIFIED)
        .setMetadata(cloudModel.metadata)
        .setNumId(cloudModel.num_id)
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
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = CREATE;
    try {
      String location = getRegistryLocation(registryId);
      String parent = RegistryName.of(projectId, location, registryId).toString();
      CreateDeviceRequest request =
          CreateDeviceRequest.Builder.newBuilder().setParent(parent).setDevice(device)
              .build();
      requireNonNull(deviceManager.createDevice(request),
          "create device failed for " + parent);
      cloudModel.num_id = hashedDeviceId(registryId, device.toBuilder().getId());
      return cloudModel;
    } catch (ApplicationException applicationException) {
      if (applicationException.getMessage().contains("ALREADY_EXISTS")) {
        cloudModel.num_id = RESOURCE_EXISTS;
        return cloudModel;
      }
      throw applicationException;
    }
  }

  @NotNull
  private HashMap<String, CloudModel> fetchDevices(String deviceRegistryId, String gatewayId) {
    String location = getRegistryLocation(deviceRegistryId);
    GatewayListOptions gatewayListOptions = ifNotNullGet(gatewayId, this::getGatewayListOptions);
    String registryFullName =
        RegistryName.of(projectId, location, deviceRegistryId).getRegistryFullName();
    String pageToken = null;
    HashMap<String, CloudModel> collect = new HashMap<>();
    do {
      DevicesListRequest request = DevicesListRequest.Builder.newBuilder().setParent(
              registryFullName)
          .setGatewayListOptions(gatewayListOptions)
          .setPageToken(pageToken)
          .build();
      DevicesListResponse response = deviceManager.listDevices(request);
      requireNonNull(response, "DeviceRegistriesList fetch failed");
      Map<String, CloudModel> responseMap =
          response.getDevicesList().stream().map(ClearBladeIotAccessProvider::convertPartial)
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
      collect.putAll(responseMap);
      pageToken = response.getNextPageToken();
    } while (pageToken != null);
    return collect;
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

  private String getRegistryLocation(String registry) {
    return getRegistryRegion(registry);
  }

  private String getRegistryName(String registryId) {
    return RegistryName.of(projectId, getRegistryLocation(registryId), registryId).toString();
  }

  private CloudModel listRegistryDevices(String deviceRegistryId, String gatewayId) {
    try {
      CloudModel cloudModel = new CloudModel();
      cloudModel.device_ids = fetchDevices(deviceRegistryId, gatewayId);
      ifNotNullThen(gatewayId, options -> debug(format("Bound devices for %s: %s",
          gatewayId, CSV_JOINER.join(cloudModel.device_ids.keySet()))));
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices " + getRegistryName(deviceRegistryId), e);
    }
  }

  private CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    String devicePath = getDeviceName(registryId, deviceId);
    Operation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DEVICE);
    checkState(type == DEVICE || type == GATEWAY, "unexpected resource type " + type);
    try {
      Device device = convert(cloudModel, deviceId);
      return switch (operation) {
        case CREATE -> createDevice(registryId, device);
        case UPDATE -> updateDevice(registryId, device);
        case DELETE -> unbindAndDelete(registryId, device);
        case BIND -> bindDeviceToGateway(registryId, deviceId, cloudModel);
        case BLOCK -> blockDevice(registryId, device);
        default -> throw new RuntimeException("Unknown device operation " + operation);
      };
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing " + devicePath, e);
    }
  }

  private CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    String registryActual = registryId + deviceId;
    if (!deviceId.isEmpty()) {
      CloudModel deviceModel = deepCopy(cloudModel);
      deviceModel.resource_type = DEVICE;
      modelDevice(reflectRegistry, registryActual, deviceModel);
    }
    Operation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DEVICE);
    checkState(type == REGISTRY, "unexpected resource type " + type);
    try {
      Device device = convert(cloudModel, deviceId);
      if (operation == CREATE) {
        return createRegistry(registryActual, device);
      } else {
        throw new RuntimeException("Unsupported operation " + operation);
      }
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing registry " + registryActual, e);
    }
  }

  private CloudModel createRegistry(String registryId, Device device) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = CREATE;
    cloudModel.resource_type = REGISTRY;
    cloudModel.num_id = registryId;
    try {
      String location = getRegistryLocation(reflectRegistry);
      DeviceRegistry.Builder registry = DeviceRegistry.newBuilder()
          .setId(registryId)
          .setEventNotificationConfigs(ImmutableList.of(eventNotificationConfig()))
          .setStateNotificationConfig(stateNotificationConfig())
          .setLogLevel(LogLevel.DEBUG);
      CreateDeviceRegistryRequest request = CreateDeviceRegistryRequest.Builder.newBuilder()
          .setParent(LocationName.of(projectId, location).toString())
          .setDeviceRegistry(registry.build()).build();
      deviceManager.createDeviceRegistry(request);
    } catch (ApplicationException applicationException) {
      if (!applicationException.getMessage().contains("ALREADY_EXISTS")) {
        throw applicationException;
      }
    }
    return cloudModel;
  }

  private String getScopedTopic(String udmiTargetTopic) {
    return format(TOPIC_NAME_FORMAT, projectId, getPodNamespacePrefix() + udmiTargetTopic);
  }

  private EventNotificationConfig eventNotificationConfig() {
    String topicName = getScopedTopic(UDMI_TARGET_TOPIC);
    return EventNotificationConfig.newBuilder().setPubsubTopicName(topicName).build();
  }

  private StateNotificationConfig stateNotificationConfig() {
    String topicName = getScopedTopic(UDMI_STATE_TOPIC);
    return StateNotificationConfig.newBuilder().setPubsubTopicName(topicName).build();
  }

  @NotNull
  private CloudModel unbindAndDelete(String registryId, Device device) {
    String deviceId = requireNonNull(device.toBuilder().getId(), "unspecified device id");
    try {
      unbindGatewayDevices(registryId, device);
      String location = getRegistryLocation(registryId);
      DeviceName deviceName = DeviceName.of(projectId, location, registryId, deviceId);
      DeleteDeviceRequest request =
          DeleteDeviceRequest.Builder.newBuilder().setName(deviceName).build();
      deviceManager.deleteDevice(request);
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = DELETE;
      cloudModel.num_id = hashedDeviceId(registryId, deviceId);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException(format("While deleting %s/%s", registryId, deviceId), e);
    }
  }

  private void unbindDevice(String registryId, String gatewayId, String proxyId) {
    try {
      debug(format("Unbind %s: %s from %s", registryId, proxyId, gatewayId));
      String location = getRegistryLocation(registryId);
      UnbindDeviceFromGatewayRequest request = UnbindDeviceFromGatewayRequest.Builder.newBuilder()
          .setParent(RegistryName.of(projectId, location, registryId).getRegistryFullName())
          .setGateway(gatewayId).setDevice(proxyId).build();
      requireNonNull(deviceManager.unbindDeviceFromGateway(request), "invalid response");
    } catch (Exception e) {
      throw new RuntimeException("While unbinding " + proxyId + " from " + gatewayId, e);
    }
  }

  private void unbindGatewayDevices(String registryId, Device device) {
    String gatewayId = device.toBuilder().getId();
    CloudModel cloudModel = listRegistryDevices(registryId, gatewayId);
    if (!cloudModel.device_ids.isEmpty()) {
      debug(format("Unbinding from %s/%s: %s", registryId, gatewayId,
          CSV_JOINER.join(cloudModel.device_ids.keySet())));
      ifNotNullThen(cloudModel.device_ids, ids -> ids.keySet()
          .forEach(id -> unbindDevice(registryId, gatewayId, id)));
    }
  }

  private CloudModel blockDevice(String registryId, Device device) {
    String deviceId = device.toBuilder().getId();
    String name = getDeviceName(registryId, deviceId);
    Device fullDevice = device.toBuilder().setName(name).setBlocked(true).build();
    try {
      UpdateDeviceRequest request =
          UpdateDeviceRequest.Builder.newBuilder().setDevice(fullDevice).setName(name)
              .setUpdateMask(BLOCKED_FIELD_MASK).build();
      requireNonNull(deviceManager.updateDevice(request), "Invalid RPC response");
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.BLOCK;
      cloudModel.num_id = hashedDeviceId(registryId, deviceId);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While updating " + deviceId, e);
    }
  }

  private CloudModel updateDevice(String registryId, Device device) {
    String deviceId = device.toBuilder().getId();
    String name = getDeviceName(registryId, deviceId);
    Device fullDevice = device.toBuilder().setName(name).build();
    try {
      UpdateDeviceRequest request =
          UpdateDeviceRequest.Builder.newBuilder().setDevice(fullDevice).setName(name)
              .setUpdateMask(UPDATE_FIELD_MASK).build();
      requireNonNull(deviceManager.updateDevice(request), "Invalid RPC response");
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = Operation.UPDATE;
      cloudModel.num_id = hashedDeviceId(registryId, deviceId);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While updating " + deviceId, e);
    }
  }

  /**
   * Create pseudo device numerical id that can be used for operation verification.
   */
  private String hashedDeviceId(String registryId, String deviceId) {
    return String.valueOf(Objects.hash(registryId, deviceId));
  }

  @Override
  public void activate() {
    super.activate();
    if (!isEnabled()) {
      warn("ClearBlade access provider disabled");
      return;
    }
    debug("Initializing ClearBlade access provider for project " + projectId);
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    try {
      String location = getRegistryLocation(registryId);
      ListDeviceConfigVersionsRequest request = ListDeviceConfigVersionsRequest.Builder.newBuilder()
          .setName(DeviceName.of(projectId, location, registryId, deviceId)
              .toString()).setNumVersions(1).build();
      ListDeviceConfigVersionsResponse listDeviceConfigVersionsResponse =
          deviceManager.listDeviceConfigVersions(request);
      List<DeviceConfig> deviceConfigs = listDeviceConfigVersionsResponse.getDeviceConfigList();
      if (deviceConfigs.isEmpty()) {
        return new SimpleEntry<>(null, EMPTY_JSON);
      }
      DeviceConfig deviceConfig = deviceConfigs.get(0);
      String config = ifNotNullGet((String) deviceConfig.getBinaryData(),
          binaryData -> new String(Base64.getDecoder().decode(binaryData)));
      return new SimpleEntry<>(Long.parseLong(deviceConfig.getVersion()), config);
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    String devicePath = getDeviceName(deviceRegistryId, deviceId);
    try {
      String location = getRegistryLocation(deviceRegistryId);
      DeviceName name = DeviceName.of(projectId, location, deviceRegistryId, deviceId);
      GetDeviceRequest request = GetDeviceRequest.Builder.newBuilder().setName(name)
          .setFieldMask(FieldMask.newBuilder().build()).build();
      Device device = deviceManager.getDevice(request);
      requireNonNull(device, "GetDeviceRequest failed");
      CloudModel cloudModel = convertFull(device);
      cloudModel.operation = Operation.FETCH;
      cloudModel.device_ids = listRegistryDevices(deviceRegistryId, deviceId).device_ids;
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device " + devicePath, e);
    }

  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    try {
      CloudModel cloudModel = fetchDevice(reflectRegistry, registryId);
      return cloudModel.metadata.get(metadataKey);
    } catch (Exception e) {
      debug(format("No device entry for %s/%s", reflectRegistry, registryId));
      return null;
    }
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    String devicePath = getDeviceName(deviceRegistryId, deviceId);
    try {
      String location = getRegistryLocation(deviceRegistryId);
      DeviceName name = DeviceName.of(projectId, location, deviceRegistryId, deviceId);

      ListDeviceStatesRequest request = ListDeviceStatesRequest.Builder.newBuilder()
          .setName(name.toString())
          .setNumStates(1).build();
      ListDeviceStatesResponse response = requireNonNull(
          deviceManager.listDeviceStates(request), "Null response returned");
      List<DeviceState> deviceStatesList = response.getDeviceStatesList();
      return deviceStatesList.isEmpty() ? null : (String) deviceStatesList.get(0).getBinaryData();
    } catch (Exception e) {
      throw new RuntimeException("While fetching state for device " + devicePath, e);
    }
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    return listRegistryDevices(deviceRegistryId, null);
  }

  @Override
  public CloudModel modelResource(String registryId, String deviceId, CloudModel cloudModel) {
    if (cloudModel.resource_type == REGISTRY) {
      return modelRegistry(registryId, deviceId, cloudModel);
    }
    return modelDevice(registryId, deviceId, cloudModel);
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    String subFolder = ifNotNullGet(folder, SubFolder::value);
    try {
      ByteString binaryData = new ByteString(encodeBase64(message));
      String location = getRegistryLocation(registryId);
      String deviceName = DeviceName.of(projectId, location, registryId, deviceId).toString();
      SendCommandToDeviceRequest request = SendCommandToDeviceRequest.Builder.newBuilder()
          .setName(deviceName)
          .setBinaryData(binaryData)
          .setSubfolder(subFolder)
          .build();
      SendCommandToDeviceResponse response = deviceManager.sendCommandToDevice(request);
      if (response == null) {
        throw new RuntimeException("SendCommandToDevice execution failed for " + deviceName);
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While sending command to ClearBlade %s/%s/%s",
          registryId, deviceId, subFolder), e);
    }
  }

  class Empty {
    // Temp hacky class
  }
}

