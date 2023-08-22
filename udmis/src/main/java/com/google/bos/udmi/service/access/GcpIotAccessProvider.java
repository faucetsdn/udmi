package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getDate;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations.Registries.Devices;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.BindDeviceToGatewayRequest;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.DeviceState;
import com.google.api.services.cloudiot.v1.model.Empty;
import com.google.api.services.cloudiot.v1.model.GatewayConfig;
import com.google.api.services.cloudiot.v1.model.ListDeviceRegistriesResponse;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.api.services.cloudiot.v1.model.SendCommandToDeviceRequest;
import com.google.api.services.cloudiot.v1.model.UnbindDeviceFromGatewayRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * IoT access provider for (deprecated) GCP IoT Core.
 */
public class GcpIotAccessProvider extends IotAccessBase {

  static final Set<String> CLOUD_REGIONS =
      ImmutableSet.of("us-central1", "europe-west1", "asia-east1");
  private static final String GATEWAY_TYPE = "GATEWAY";
  private static final String PROJECT_PATH_FORMAT = "projects/%s";
  private static final String LOCATIONS_PATH_FORMAT = "%s/locations/%s";
  private static final String REGISTRY_PATH_FORMAT = "%s/registries/%s";
  private static final String DEVICE_PATH_FORMAT = "%s/devices/%s";
  private static final String APPLICATION_NAME = "com.google.iot.bos";
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
  private static final String UPDATE_FIELD_MASK = "blocked,credentials,metadata";
  private static final String EMPTY_RETURN_RECEIPT = "-1";
  private static final String ASSOCIATION_ONLY = "ASSOCIATION_ONLY";
  private static final GatewayConfig GATEWAY_CONFIG =
      new GatewayConfig().setGatewayType(GATEWAY_TYPE).setGatewayAuthMethod(ASSOCIATION_ONLY);
  private static final String ENABLED_OPTION = "enabled";
  private final String projectId;
  private final CloudIot cloudIotService;
  private final CloudIot.Projects.Locations.Registries registries;

  /**
   * Create a new instance for interfacing with GCP IoT Core.
   * TODO: Need to implement page tokens for all requisite API calls.
   */
  public GcpIotAccessProvider(IotAccess iotAccess) {
    String options = variableSubstitution(iotAccess.options, null);
    if (!ENABLED_OPTION.equals(options)) {
      warn("access provider disabled, missing option '%s'", ENABLED_OPTION);
      projectId = null;
      cloudIotService = null;
      registries = null;
      return;
    }

    projectId = variableSubstitution(iotAccess.project_id, "gcp project id not specified");
    cloudIotService = createCloudIotService();
    registries = cloudIotService.projects().locations().registries();
    ifTrueThen(isEnabled(), this::fetchRegistryRegions);
  }

  @NotNull
  @VisibleForTesting
  protected CloudIot createCloudIotService() {
    try {
      GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
          .createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      CloudIot cloudIotService = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(),
          jsonFactory, init).setApplicationName(APPLICATION_NAME).build();
      return cloudIotService;
    } catch (Exception e) {
      throw new RuntimeException("While creating GCP IoT Core service", e);
    }
  }

  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    try {
      String useConfig = ofNullable(config).orElse("");
      registries.devices().modifyCloudToDeviceConfig(
          getDevicePath(registryId, deviceId),
          new ModifyCloudToDeviceConfigRequest()
              .setVersionToUpdate(version)
              .setBinaryData(Base64.getEncoder().encodeToString(useConfig.getBytes()))
      ).execute();
      return useConfig;
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
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
        BindDeviceToGatewayRequest request =
            new BindDeviceToGatewayRequest().setDeviceId(id).setGatewayId(gatewayId);
        registries.bindDeviceToGateway(getRegistryPath(registryId), request).execute();
      } catch (Exception e) {
        throw new RuntimeException(format("While binding %s to gateway %s", id, gatewayId), e);
      }
    });
    return reply;
  }

  private CloudModel convert(Device device, Operation operation) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId().toString();
    cloudModel.blocked = device.getBlocked();
    cloudModel.metadata = device.getMetadata();
    cloudModel.last_event_time = getDate(device.getLastEventTime());
    cloudModel.is_gateway = ifNotNullGet(device.getGatewayConfig(),
        config -> GATEWAY_TYPE.equals(config.getGatewayType()));
    cloudModel.credentials = convertIot(device.getCredentials());
    cloudModel.operation = operation;
    return cloudModel;
  }

  private Device convert(CloudModel cloudModel) {
    return new Device()
        .setBlocked(cloudModel.blocked)
        .setCredentials(convertUdmi(cloudModel.credentials))
        .setGatewayConfig(isTrue(cloudModel.is_gateway) ? GATEWAY_CONFIG : null)
        .setMetadata(cloudModel.metadata);
  }

  private CloudModel convert(Empty execute, Operation operation) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = operation;
    cloudModel.num_id = EMPTY_RETURN_RECEIPT;
    return cloudModel;
  }

  private List<Credential> convertIot(List<DeviceCredential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(this::convertIot).collect(Collectors.toList()));
  }

  private Credential convertIot(DeviceCredential device) {
    Credential credential = new Credential();
    credential.key_data = device.getPublicKey().getKey();
    credential.key_format = AUTH_TYPE_MAP.inverse().get(device.getPublicKey().getFormat());
    return credential;
  }

  private Entry<String, CloudModel> convertToEntry(Device device) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId().toString();
    return new SimpleEntry<>(device.getId(), cloudModel);
  }

  private DeviceCredential convertUdmi(Credential credential) {
    return new DeviceCredential().setPublicKey(new PublicKeyCredential().setKey(credential.key_data)
        .setFormat(AUTH_TYPE_MAP.get(credential.key_format)));
  }

  private List<DeviceCredential> convertUdmi(List<Credential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(this::convertUdmi).collect(Collectors.toList()));
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
  protected Set<String> getRegistriesForRegion(String region) {
    String locationPath = getLocationPath(region);
    try {
      debug("Fetching registries for " + locationPath);
      ListDeviceRegistriesResponse response = cloudIotService
          .projects()
          .locations()
          .registries()
          .list(locationPath)
          .execute();
      List<DeviceRegistry> deviceRegistries = response.getDeviceRegistries();
      return ofNullable(deviceRegistries).orElseGet(ImmutableList::of).stream()
          .map(DeviceRegistry::getId)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      throw new RuntimeException("While fetching registries for " + locationPath, e);
    }
  }

  private String getRegistryPath(String registryId) {
    try {
      String region = getRegistryRegion(registryId);
      return format(REGISTRY_PATH_FORMAT, getLocationPath(region), registryId);
    } catch (Exception e) {
      throw new RuntimeException("While getting registry path for " + registryId, e);
    }
  }

  private CloudModel listRegistryDevices(String deviceRegistryId, String gatewayId) {
    String registryPath = getRegistryPath(deviceRegistryId);
    try {
      CloudModel cloudModel = new CloudModel();
      cloudModel.device_ids = fetchDevices(gatewayId, registryPath);
      ifNotNullThen(gatewayId, options -> debug(format("Bound devices for %s: %s",
          gatewayId, CSV_JOINER.join(cloudModel.device_ids.keySet()))));
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for " + registryPath, e);
    }
  }

  @NotNull
  private HashMap<String, CloudModel> fetchDevices(String gatewayId,
      String registryPath) throws IOException {
    HashMap<String, CloudModel> collect = new HashMap<>();
    String pageToken = null;
    do {
      Devices.List request = registries.devices().list(registryPath);
      ifNotNullThen(gatewayId, request::setGatewayListOptionsAssociationsGatewayId);
      request.setPageToken(pageToken);
      ListDevicesResponse response = request.execute();
      List<Device> devices =
          ofNullable(response.getDevices()).orElseGet(ImmutableList::of);
      Map<String, CloudModel> deviceMap = devices.stream().map(this::convertToEntry)
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
      collect.putAll(deviceMap);
      pageToken = response.getNextPageToken();
    } while (pageToken != null);
    return collect;
  }

  private void unbindDevice(String registryId, String gatewayId, String proxyId) {
    try {
      registries.unbindDeviceFromGateway(getRegistryPath(registryId),
              new UnbindDeviceFromGatewayRequest()
                  .setDeviceId(proxyId)
                  .setGatewayId(gatewayId))
          .execute();
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
  protected boolean isEnabled() {
    return projectId != null;
  }

  @Override
  public void activate() {
    super.activate();
    if (!isEnabled()) {
      warn("GCP access provider disabled b/c empty project id");
      return;
    }
    debug("Initializing GCP access provider for project " + projectId);
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    try {
      List<DeviceConfig> deviceConfigs = registries.devices().configVersions()
          .list(getDevicePath(registryId, deviceId)).execute().getDeviceConfigs();
      if (deviceConfigs.isEmpty()) {
        return new SimpleEntry<>(null, EMPTY_JSON);
      }
      DeviceConfig deviceConfig = deviceConfigs.get(0);
      String config = ifNotNullGet(deviceConfig.getBinaryData(),
          binaryData -> new String(Base64.getDecoder().decode(binaryData)));
      return new SimpleEntry<>(deviceConfig.getVersion(), config);
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    String devicePath = getDevicePath(deviceRegistryId, deviceId);
    try {
      CloudModel convert = convert(registries.devices().get(devicePath).execute(), Operation.FETCH);
      convert.device_ids = listRegistryDevices(deviceRegistryId, deviceId).device_ids;
      return convert;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device " + devicePath, e);
    }

  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    try {
      CloudModel cloudModel = fetchDevice(UDMI_REGISTRY, registryId);
      return cloudModel.metadata.get(metadataKey);
    } catch (Exception e) {
      debug(format("No device entry for %s/%s", UDMI_REGISTRY, registryId));
      return null;
    }
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    String devicePath = getDevicePath(deviceRegistryId, deviceId);
    try {
      List<DeviceState> deviceStates =
          registries.devices().states().list(devicePath).execute().getDeviceStates();
      if (deviceStates == null || deviceStates.isEmpty()) {
        return null;
      }
      return decodeBase64(deviceStates.get(0).getBinaryData());
    } catch (Exception e) {
      throw new RuntimeException("While fetching state " + devicePath, e);
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
      Device device = convert(cloudModel);
      Devices registryDevices = registries.devices();
      switch (operation) {
        case CREATE:
          Device createDevice = device.setId(deviceId);
          return convert(
              registryDevices.create(getRegistryPath(registryId), createDevice).execute(),
              operation);
        case UPDATE:
          return convert(
              registryDevices.patch(devicePath, device.setNumId(null))
                  .setUpdateMask(UPDATE_FIELD_MASK).execute(), operation);
        case DELETE:
          unbindGatewayDevices(registryId, deviceId);
          return convert(registryDevices.delete(devicePath).execute(), operation);
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
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    try {
      String subFolder = ifNotNullGet(folder, SubFolder::value);
      SendCommandToDeviceRequest request = new SendCommandToDeviceRequest()
          .setBinaryData(encodeBase64(message))
          .setSubfolder(subFolder);
      registries.devices().sendCommandToDevice(getDevicePath(registryId, deviceId), request)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException(format("While sending command to GCP %s/%s/%s",
          registryId, deviceId, folder), e);
    }
  }

  @Override
  public void shutdown() {
    super.shutdown();
  }
}

