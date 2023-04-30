package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations.Registries.Devices;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.ListDeviceRegistriesResponse;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.SendCommandToDeviceRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.udmi.util.GeneralUtils;
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
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

/**
 * IoT access provider for (deprecated) GCP IoT Core.
 */
public class GcpIotAccessProvider extends UdmisComponent implements IotAccessProvider {

  private static final String EMPTY_JSON = "{}";
  private static final String PROJECT_PATH_FORMAT = "projects/%s";
  private static final String LOCATIONS_PATH_FORMAT = "%s/locations/%s";
  private static final String REGISTRY_PATH_FORMAT = "%s/registries/%s";
  private static final String DEVICE_PATH_FORMAT = "%s/devices/%s";
  private static final Set<String> CLOUD_REGIONS =
      ImmutableSet.of("us-central1", "europe-west1", "asia-east1");
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
  private final String projectId;
  private final Map<String, String> registryCloudRegions;
  private final CloudIot cloudIotService;
  private CloudIot.Projects.Locations.Registries registries;

  public GcpIotAccessProvider(IotAccess iotAccess) {
    projectId = requireNonNull(iotAccess.project_id, "gcp project id not specified");
    cloudIotService = createCloudIotService();
    registryCloudRegions = fetchRegistryCloudRegions();
  }

  private static Entry<String, CloudModel> convert(Device device) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId().toString();
    return new SimpleEntry<>(device.getId(), cloudModel);
  }

  @NotNull
  private CloudIot createCloudIotService() {
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

  private String fetchConfig(String registryId, String deviceId) {
    try {
      List<DeviceConfig> deviceConfigs = registries.devices().configVersions()
          .list(getDevicePath(registryId, deviceId)).execute().getDeviceConfigs();
      if (deviceConfigs.size() > 0) {
        return new String(Base64.getDecoder().decode(deviceConfigs.get(0).getBinaryData()));
      }
      return null;
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
      debug("Fetching registries for " + region);
      ListDeviceRegistriesResponse response = cloudIotService.projects().locations().registries()
          .list(getLocationPath(region)).execute();
      List<DeviceRegistry> deviceRegistries = response.getDeviceRegistries();
      return ofNullable(deviceRegistries).orElseGet(ImmutableList::of).stream()
          .map(DeviceRegistry::getId)
          .collect(Collectors.toMap(item -> item, item -> region));
    } catch (Exception e) {
      throw new RuntimeException("While fetching registry cloud regions", e);
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
      Devices.List request = registries.devices().list(registryPath);
      ifNotNullThen(gatewayId, request::setGatewayListOptionsAssociationsGatewayId);
      ListDevicesResponse response = request.execute();
      CloudModel cloudModel = new CloudModel();
      cloudModel.device_ids = response.getDevices().stream().map(GcpIotAccessProvider::convert)
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue, GeneralUtils::mapReplace,
              HashMap::new));
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for " + registryPath, e);
    }
  }

  @Override
  public void activate() {
    try {
      registries = cloudIotService.projects().locations().registries();
    } catch (Exception e) {
      throw new RuntimeException("While activating", e);
    }
  }

  @Override
  public CloudModel listRegistryDevices(String deviceRegistryId) {
    return listRegistryDevices(deviceRegistryId, null);
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder subFolder,
      String contents) {
    // TODO: Need to implement checking of config version for concurrent operations.
    String configString = ofNullable(fetchConfig(registryId, deviceId)).orElse(EMPTY_JSON);
    Map<String, Object> configMap = toMap(configString);
    configMap.put(subFolder.toString(), contents);
    updateConfig(registryId, deviceId, stringify(configMap));
  }

  @Override
  public void sendCommand(String registryId, String deviceId, SubFolder folder, String message) {
    try {
      requireNonNull(registryId, "registry not defined");
      requireNonNull(deviceId, "device not defined");
      String subFolder = requireNonNull(folder, "subfolder not defined").value();
      SendCommandToDeviceRequest request =
          new SendCommandToDeviceRequest().setBinaryData(encodeBase64(message)).setSubfolder(subFolder);
      registries.devices().sendCommandToDevice(getDevicePath(registryId, deviceId), request)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException(
          format("While sending %s command to %s/%s", folder, registryId, deviceId), e);
    }
  }

  @Override
  public void shutdown() {
    registries = null;
  }

  @Override
  public void updateConfig(String registryId, String deviceId, String config) {
    try {
      String useConfig = ofNullable(config).orElse("");
      registries.devices().modifyCloudToDeviceConfig(
          getDevicePath(registryId, deviceId),
          new ModifyCloudToDeviceConfigRequest().setBinaryData(
              Base64.getEncoder().encodeToString(useConfig.getBytes()))).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }
}

