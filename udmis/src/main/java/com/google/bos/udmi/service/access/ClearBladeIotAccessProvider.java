package com.google.bos.udmi.service.access;

import static com.clearblade.cloud.iot.v1.devicetypes.GatewayType.NON_GATEWAY;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
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
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesRequest;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesResponse;
import com.clearblade.cloud.iot.v1.modifycloudtodeviceconfig.ModifyCloudToDeviceConfigRequest;
import com.clearblade.cloud.iot.v1.registrytypes.DeviceRegistry;
import com.clearblade.cloud.iot.v1.registrytypes.LocationName;
import com.clearblade.cloud.iot.v1.registrytypes.RegistryName;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceRequest;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceResponse;
import com.clearblade.cloud.iot.v1.utils.AuthParams;
import com.clearblade.cloud.iot.v1.utils.ByteString;
import com.clearblade.cloud.iot.v1.utils.ConfigParameters;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
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
 */
public class ClearBladeIotAccessProvider extends UdmisComponent implements IotAccessProvider {

  static final Set<String> CLOUD_REGIONS =
      ImmutableSet.of("us-central1", "europe-west1", "asia-east1");
  private static final String EMPTY_JSON = "{}";
  private static final String PROJECT_PATH_FORMAT = "projects/%s";
  private static final String LOCATIONS_PATH_FORMAT = "%s/locations/%s";
  private static final String REGISTRY_PATH_FORMAT = "%s/registries/%s";
  private static final String DEVICE_PATH_FORMAT = "%s/devices/%s";
  private static final String RSA_KEY_FORMAT = "RSA_PEM";
  private static final String RSA_CERT_FORMAT = "RSA_X509_PEM";
  private static final String ES_KEY_FORMAT = "ES256_PEM";
  private static final String ES_CERT_FILE = "ES256_X509_PEM";
  private static final BiMap<Key_format, String> AUTH_TYPE_MAP =
      ImmutableBiMap.of(
          Key_format.RS_256, RSA_KEY_FORMAT,
          Key_format.RS_256_X_509, RSA_CERT_FORMAT,
          Key_format.ES_256, ES_KEY_FORMAT,
          Key_format.ES_256_X_509, ES_CERT_FILE);
  private static final String EMPTY_RETURN_RECEIPT = "-1";
  private static final GatewayConfig GATEWAY_CONFIG = new GatewayConfig();
  public static final String USER_SYSTEM_KEY_FIELD = "userSystemKey";

  static {
    GATEWAY_CONFIG.setGatewayType(GatewayType.GATEWAY);
    GATEWAY_CONFIG.setGatewayAuthMethod(GatewayAuthMethod.ASSOCIATION_ONLY);
  }

  private final String projectId;
  private final Map<String, String> registryCloudRegions;
  private final CloudIot cloudIotService;
  private Projects.Locations.Registries registries;

  /**
   * Create a new instance for interfacing with GCP IoT Core.
   */
  public ClearBladeIotAccessProvider(IotAccess iotAccess) {
    projectId = requireNonNull(iotAccess.project_id, "gcp project id not specified");
    debug("Initializing ClearBlade access provider for project " + projectId);
    cloudIotService = createCloudIotService();
    registryCloudRegions = fetchRegistryCloudRegions();
  }

  private static CloudModel convert(Device deviceRaw, Operation operation) {
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

  @Nullable
  private static Date getSafeDate(String lastEventTime) {
    return getDate(Strings.isNullOrEmpty(lastEventTime) ? null : lastEventTime);
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

  private static DeviceCredential convertUdmi(Credential credential) {
    // return new DeviceCredential()
    // .setPublicKey(new PublicKeyCredential().setKey(credential.key_data)
    //     .setFormat(AUTH_TYPE_MAP.get(credential.key_format)));
    throw new RuntimeException("Not yet implemented");
  }

  private static String extractNumId(Device device) {
    return format("%d", Math.abs(Objects.hash(device.toBuilder().getName())));
  }

  /**
   * Work around issues with the Java version of the ClearBlade API. Specifically, the "registry"
   * parameter is assumed to be static, either by static configuration or environment variable,
   * so the registry parameter of the API call is ignored. Additionally, the auth result caches
   * the value so it can't be easily changed between calls.
   */
  private static void hackClearBladeRegistryRegion(String location, String registry) {
    ConfigParameters.getInstance().setRegion(location);
    ConfigParameters.getInstance().setRegistry(registry);
    try {
      // Force clear the cached value so it recalculates for each API call.
      Field userSystemKey = AuthParams.class.getDeclaredField(USER_SYSTEM_KEY_FIELD);
      userSystemKey.setAccessible(true);
      userSystemKey.set(null, null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @VisibleForTesting
  protected CloudIot createCloudIotService() {
    try {
      // GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
      //     .createScoped(CloudIotScopes.all());
      // JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      // HttpRequestInitializer init
      // = new HttpCredentialsAdapter(credential);
      // CloudIot cloudIotService
      // = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(),
      //     jsonFactory, init).setApplicationName(APPLICATION_NAME).build();
      // return cloudIotService;
      return new CloudIot();
    } catch (Exception e) {
      throw new RuntimeException("While creating GCP IoT Core service", e);
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
        // BindDeviceToGatewayRequest request =
        //     new BindDeviceToGatewayRequest().setDeviceId(id).setGatewayId(gatewayId);
        // registries.bindDeviceToGateway(getRegistryPath(registryId), request).execute();
        throw new RuntimeException("Not yet implemented");
      } catch (Exception e) {
        throw new RuntimeException(format("While binding %s to gateway %s", id, gatewayId), e);
      }
    });
    return reply;
  }

  private Device convert(CloudModel cloudModel) {
    // return new Device()
    //     .setBlocked(cloudModel.blocked)
    //     .setCredentials(convertUdmi(cloudModel.credentials))
    //     .setGatewayConfig(TRUE.equals(cloudModel.is_gateway) ? GATEWAY_CONFIG : null)
    //     .setMetadata(cloudModel.metadata);
    throw new RuntimeException("Not yet implemented");
  }

  private CloudModel convert(Empty execute, Operation operation) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = operation;
    cloudModel.num_id = EMPTY_RETURN_RECEIPT;
    return cloudModel;
  }

  private List<DeviceCredential> convertUdmi(List<Credential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(ClearBladeIotAccessProvider::convertUdmi)
            .collect(Collectors.toList()));
  }

  private String fetchConfig(String registryId, String deviceId) {
    try {
      // List<DeviceConfig> deviceConfigs = registries.devices().configVersions()
      //     .list(getDevicePath(registryId, deviceId)).execute().getDeviceConfigs();
      // if (deviceConfigs.size() > 0) {
      //   return ifNotNullGet(deviceConfigs.get(0).getBinaryData(),
      //       binaryData -> new String(Base64.getDecoder().decode(binaryData)));
      // }
      throw new RuntimeException("Not yet implemented");
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  private Map<String, String> fetchRegistryCloudRegions() {
    Map<String, String> regionMap = CLOUD_REGIONS.stream().map(this::getRegistriesForRegion)
        .flatMap(map -> map.entrySet().stream())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    debug(format("Fetched %s registry regions", regionMap.size()));
    return regionMap;
  }

  private String getDevicePath(String registryId, String deviceId) {
    return format(DEVICE_PATH_FORMAT, getRegistryPath(registryId), deviceId);
  }

  private String getLocationPath(String cloudRegion) {
    return format(LOCATIONS_PATH_FORMAT, getProjectPath(), cloudRegion);
  }

  private String getProjectPath() {
    return format(PROJECT_PATH_FORMAT, projectId);
  }

  @NotNull
  private Map<String, String> getRegistriesForRegion(String region) {
    try {
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
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

  private String getRegistryPath(String registryId) {
    String region = requireNonNull(registryCloudRegions.get(registryId),
        "unknown region for registry " + registryId);
    return format(REGISTRY_PATH_FORMAT, getLocationPath(region), registryId);
  }

  private CloudModel listRegistryDevices(String deviceRegistryId, String gatewayId) {
    String registryPath = getRegistryPath(deviceRegistryId);
    try {
      String location = registryCloudRegions.get(deviceRegistryId);
      hackClearBladeRegistryRegion(location, deviceRegistryId);
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
      GatewayListOptions gatewayListOptions =
          GatewayListOptions.newBuilder().setGatewayType(NON_GATEWAY).build();
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
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for " + registryPath, e);
    }
  }

  private void unbindDevice(String registryId, String gatewayId, String proxyId) {
    try {
      // registries.unbindDeviceFromGateway(getRegistryPath(registryId),
      //         new UnbindDeviceFromGatewayRequest()
      //             .setDeviceId(proxyId)
      //             .setGatewayId(gatewayId))
      //     .execute();
      throw new RuntimeException("Not yet implemented");
    } catch (Exception e) {
      throw new RuntimeException("While unbinding " + proxyId + " from " + gatewayId, e);
    }
  }

  private void unbindGatewayDevices(String registryId, String deviceId) {
    CloudModel cloudModel = listRegistryDevices(registryId, deviceId);
    ifNotNullThen(cloudModel.device_ids, ids -> ids.keySet()
        .forEach(id -> unbindDevice(registryId, deviceId, id)));
  }

  @Override
  public void activate() {
    try {
      // registries = cloudIotService.projects().locations().registries();
    } catch (Exception e) {
      throw new RuntimeException("While activating", e);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    String devicePath = getDevicePath(deviceRegistryId, deviceId);
    try {
      String location = registryCloudRegions.get(deviceRegistryId);
      hackClearBladeRegistryRegion(location, deviceRegistryId);
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
      DeviceName name = DeviceName.of(projectId, location, deviceRegistryId, deviceId);
      GetDeviceRequest request = GetDeviceRequest.Builder.newBuilder().setName(name)
          .setFieldMask(FieldMask.newBuilder().build()).build();
      Device device = deviceManagerClient.getDevice(request);
      requireNonNull(device, "GetDeviceRequest failed");
      // cloudModel.device_ids =
      //      response.getDeviceRegistriesList().stream().map(ClearBladeIotAccessProvider::convertToEntry)
      //          .collect(Collectors.toMap(Entry::getKey, Entry::getValue, GeneralUtils::mapReplace,
      //              HashMap::new));
      return convert(device, Operation.FETCH);
    } catch (Exception e) {
      throw new RuntimeException("While fetching device " + devicePath, e);
    }

  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    return listRegistryDevices(deviceRegistryId, null);
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    String devicePath = getDevicePath(registryId, deviceId);
    Operation operation = cloudModel.operation;
    try {
      // Device device = convert(cloudModel);
      // Devices registryDevices = registries.devices();
      // switch (operation) {
      //   case CREATE:
      //     Device createDevice = device.setId(deviceId);
      //     return convert(
      //         registryDevices.create(getRegistryPath(registryId), createDevice).execute(),
      //         operation);
      //   case UPDATE:
      //     return convert(
      //         registryDevices.patch(devicePath, device.setNumId(null))
      //             .setUpdateMask(UPDATE_FIELD_MASK).execute(), operation);
      //   case DELETE:
      //     unbindGatewayDevices(registryId, deviceId);
      //     return convert(registryDevices.delete(devicePath).execute(), operation);
      //   case BIND:
      //     return bindDeviceToGateway(registryId, deviceId, cloudModel);
      //   default:
      //     throw new RuntimeException("Unknown operation " + operation);
      // }
      throw new RuntimeException("Not yet implemented");
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing " + devicePath, e);
    }
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder subFolder,
      String contents) {
    // TODO: Need to implement checking-and-retry of config version for concurrent operations.
    if (subFolder == SubFolder.UPDATE) {
      updateConfig(registryId, deviceId, contents);
    } else {
      String configString = ofNullable(fetchConfig(registryId, deviceId)).orElse(EMPTY_JSON);
      Map<String, Object> configMap = toMap(configString);
      configMap.put(subFolder.toString(), contents);
      updateConfig(registryId, deviceId, stringify(configMap));
    }
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder subFolder, String message) {
    try {
      String location = registryCloudRegions.get(registryId);
      ByteString binaryData = new ByteString(encodeBase64(message));
      hackClearBladeRegistryRegion(location, registryId);
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
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
      throw new RuntimeException(
          format("While sending %s command to %s/%s", subFolder, registryId, deviceId), e);
    }
  }

  @Override
  public void shutdown() {
    registries = null;
  }

  @Override
  public void updateConfig(String registryId, String deviceId, String config) {
    try {
      DeviceManagerClient deviceManagerClient = new DeviceManagerClient();
      String location = registryCloudRegions.get(registryId);
      ByteString binaryData = new ByteString(encodeBase64(config));
      String updateVersion = null;
      ModifyCloudToDeviceConfigRequest request =
          ModifyCloudToDeviceConfigRequest.Builder.newBuilder()
              .setName(DeviceName.of(projectId, location, registryId, deviceId).toString())
              .setBinaryData(binaryData).setVersionToUpdate(updateVersion).build();
      hackClearBladeRegistryRegion(location, registryId);
      DeviceConfig response = deviceManagerClient.modifyCloudToDeviceConfig(request);
      System.err.println("Config modified version " + response.getVersion());
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  class Projects {

    class Locations {

      class Registries {

      }
    }
  }

  class Empty {
    // Temp hacky class
  }

  class CloudIot {
    // Hacky class for transitions
  }
}

