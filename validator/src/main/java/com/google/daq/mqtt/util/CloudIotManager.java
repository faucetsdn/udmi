package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.BindDeviceToGatewayRequest;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.GatewayConfig;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Encapsulation of all Cloud IoT interaction functions.
 */
public class CloudIotManager {

  public static final String UDMI_METADATA = "udmi_metadata";
  private static final String DEVICE_UPDATE_MASK = "blocked,credentials,metadata";
  private static final String UDMI_CONFIG = "udmi_config";
  private static final String UDMI_GENERATION = "udmi_generation";
  private static final String UDMI_UPDATED = "udmi_updated";
  private static final String KEY_BYTES_KEY = "key_bytes";
  private static final String KEY_ALGORITHM_KEY = "key_algorithm";
  private static final int LIST_PAGE_SIZE = 1000;

  public final CloudIotConfig cloudIotConfig;

  private final String registryId;
  private final String projectId;
  private final String cloudRegion;
  private final Map<String, Device> deviceMap = new ConcurrentHashMap<>();
  private final String schemaName;
  private CloudIot cloudIotService;
  private String projectPath;
  private CloudIot.Projects.Locations.Registries cloudIotRegistries;

  /**
   * Create a new CloudIoTManager.
   *
   * @param projectId     project id
   * @param iotConfigFile configuration file
   * @param schemaName    schema name to use for this device
   */
  public CloudIotManager(String projectId, File iotConfigFile, String schemaName) {
    this.projectId = projectId;
    this.schemaName = schemaName;
    cloudIotConfig = validate(readCloudIotConfig(iotConfigFile), projectId);
    registryId = cloudIotConfig.registry_id;
    cloudRegion = cloudIotConfig.cloud_region;
    initializeCloudIoT();
  }

  /**
   * Validate the given configuration.
   *
   * @param cloudIotConfig configuration to validate
   * @param projectId      expected project id
   * @return validated config (for chaining)
   */
  public static CloudIotConfig validate(CloudIotConfig cloudIotConfig, String projectId) {
    if (projectId.equals(cloudIotConfig.alt_project)) {
      System.err.printf("Using alt_registry %s for alt_project %s\n", cloudIotConfig.alt_registry,
          cloudIotConfig.alt_project);
      cloudIotConfig.alt_project = null;
      cloudIotConfig.registry_id = cloudIotConfig.alt_registry;
      cloudIotConfig.alt_registry = null;
    }
    Preconditions.checkNotNull(cloudIotConfig.registry_id, "registry_id not defined");
    Preconditions.checkNotNull(cloudIotConfig.cloud_region, "cloud_region not defined");
    Preconditions.checkNotNull(cloudIotConfig.site_name, "site_name not defined");
    return cloudIotConfig;
  }

  /**
   * Make an auth credential.
   *
   * @param keyFormat key format to use
   * @param keyData   key data
   * @return public key device credential
   */
  public static DeviceCredential makeCredentials(String keyFormat, String keyData) {
    PublicKeyCredential publicKeyCredential = new PublicKeyCredential();
    publicKeyCredential.setFormat(keyFormat);
    publicKeyCredential.setKey(keyData);

    DeviceCredential deviceCredential = new DeviceCredential();
    deviceCredential.setPublicKey(publicKeyCredential);
    return deviceCredential;
  }

  public String getRegistryPath() {
    return projectPath + "/registries/" + registryId;
  }

  private String getDevicePath(String deviceId) {
    return getRegistryPath() + "/devices/" + deviceId;
  }

  private void initializeCloudIoT() {
    projectPath = "projects/" + projectId + "/locations/" + cloudRegion;
    try {
      System.err.println("Initializing with default credentials...");
      GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
          .createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      cloudIotService = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(),
          jsonFactory, init).setApplicationName("com.google.iot.bos").build();
      cloudIotRegistries = cloudIotService.projects().locations().registries();
      System.err.println("Created service for project " + projectPath);
    } catch (Exception e) {
      throw new RuntimeException("While initializing Cloud IoT project " + projectPath, e);
    }
  }

  /**
   * Register the given device in a cloud registry.
   *
   * @param deviceId device to register
   * @param settings settings for the device
   * @return true if this is a new device entry
   */
  public boolean registerDevice(String deviceId, CloudDeviceSettings settings) {
    try {
      Preconditions.checkNotNull(cloudIotService, "CloudIoT service not initialized");
      Preconditions.checkNotNull(deviceMap, "deviceMap not initialized");
      Device device = deviceMap.get(deviceId);
      boolean isNewDevice = device == null;
      if (isNewDevice) {
        createDevice(deviceId, settings);
      } else {
        updateDevice(deviceId, settings, device);
      }
      writeDeviceConfig(deviceId, settings.config);
      return isNewDevice;
    } catch (Exception e) {
      throw new RuntimeException("While registering device " + deviceId, e);
    }
  }

  private void writeDeviceConfig(String deviceId, String config) {
    try {
      cloudIotRegistries.devices().modifyCloudToDeviceConfig(getDevicePath(deviceId),
          new ModifyCloudToDeviceConfigRequest().setBinaryData(
              Base64.getEncoder().encodeToString(config.getBytes()))).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  /**
   * Set the blocked state of a given device.
   *
   * @param deviceId target device
   * @param blocked  should this device be blocked?
   */
  public void blockDevice(String deviceId, boolean blocked) {
    try {
      Device device = new Device();
      device.setBlocked(blocked);
      String path = getDevicePath(deviceId);
      cloudIotRegistries.devices().patch(path, device).setUpdateMask("blocked").execute();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("While (un)blocking device %s/%s=%s", registryId, deviceId, blocked), e);
    }
  }

  private Device makeDevice(String deviceId, CloudDeviceSettings settings, Device oldDevice) {
    Map<String, String> metadataMap = oldDevice == null ? null : oldDevice.getMetadata();
    if (metadataMap == null) {
      metadataMap = new HashMap<>();
    }
    metadataMap.put(UDMI_METADATA, settings.metadata);
    metadataMap.put(UDMI_UPDATED, settings.updated);
    metadataMap.put(UDMI_GENERATION, settings.generation);
    metadataMap.put(UDMI_CONFIG, settings.config);
    if (settings.keyBytes == null) {
      metadataMap.remove(KEY_BYTES_KEY);
      metadataMap.remove(KEY_ALGORITHM_KEY);
    } else {
      String keyBase64 = Base64.getEncoder().encodeToString(settings.keyBytes);
      metadataMap.put(KEY_BYTES_KEY, keyBase64);
      metadataMap.put(KEY_ALGORITHM_KEY, settings.keyAlgorithm);
    }
    return new Device().setId(deviceId).setGatewayConfig(getGatewayConfig(settings))
        .setCredentials(getCredentials(settings)).setMetadata(metadataMap);
  }

  private List<DeviceCredential> getCredentials(CloudDeviceSettings settings) {
    return settings.credentials == null ? ImmutableList.of() : settings.credentials;
  }

  private GatewayConfig getGatewayConfig(CloudDeviceSettings settings) {
    boolean isGateway = settings.proxyDevices != null;
    GatewayConfig gwConfig = new GatewayConfig();
    gwConfig.setGatewayType(isGateway ? "GATEWAY" : "NON_GATEWAY");
    gwConfig.setGatewayAuthMethod("ASSOCIATION_ONLY");
    return gwConfig;
  }

  private void createDevice(String deviceId, CloudDeviceSettings settings) throws IOException {
    try {
      cloudIotRegistries.devices().create(getRegistryPath(), makeDevice(deviceId, settings, null))
          .execute();
    } catch (GoogleJsonResponseException e) {
      throw new RuntimeException("Remote error creating device " + deviceId, e);
    }
  }

  private void updateDevice(String deviceId, CloudDeviceSettings settings, Device oldDevice) {
    try {
      Device device = makeDevice(deviceId, settings, oldDevice).setId(null).setNumId(null);
      cloudIotRegistries.devices().patch(getDevicePath(deviceId), device)
          .setUpdateMask(DEVICE_UPDATE_MASK).execute();
    } catch (Exception e) {
      throw new RuntimeException("Remote error patching device " + deviceId, e);
    }
  }

  /**
   * Fetch the list of registered devices.
   *
   * @return registered device list
   */
  public Set<String> fetchDeviceList() {
    Preconditions.checkNotNull(cloudIotService, "CloudIoT service not initialized");
    Set<Device> allDevices = new HashSet<>();
    String nextPageToken = null;
    try {
      do {
        ListDevicesResponse response = cloudIotRegistries.devices().list(getRegistryPath())
            .setPageToken(nextPageToken).setPageSize(LIST_PAGE_SIZE).execute();
        List<Device> devices = response.getDevices();
        allDevices.addAll(devices == null ? ImmutableList.of() : devices);
        System.err.printf("Retrieved %d devices from registry...%n", allDevices.size());
        nextPageToken = response.getNextPageToken();
      } while (nextPageToken != null);
      return allDevices.stream().map(Device::getId).collect(Collectors.toSet());
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for registry " + registryId, e);
    }
  }

  public Device fetchDevice(String deviceId) {
    return deviceMap.computeIfAbsent(deviceId, this::fetchDeviceRaw);
  }

  private Device fetchDeviceRaw(String deviceId) {
    try {
      return cloudIotRegistries.devices().get(getDevicePath(deviceId)).execute();
    } catch (Exception e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getDetails().getCode() == 404) {
        return null;
      }
      throw new RuntimeException("While fetching " + deviceId, e);
    }
  }

  /**
   * Get target registry.
   *
   * @return Target registry
   */
  public String getRegistryId() {
    return registryId;
  }

  /**
   * Get target project.
   *
   * @return Target GCP project
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Get target site.
   *
   * @return Name for the site (building name)
   */
  public String getSiteName() {
    return cloudIotConfig.site_name;
  }

  /**
   * Get update topic.
   *
   * @return Topic name to use for sending update messages
   */
  public String getUpdateTopic() {
    return cloudIotConfig.update_topic;
  }

  /**
   * Get cloud region.
   *
   * @return Cloud region (for the registry)
   */
  public Object getCloudRegion() {
    return cloudRegion;
  }

  public void bindDevice(String proxyDeviceId, String gatewayDeviceId) throws IOException {
    cloudIotRegistries.bindDeviceToGateway(getRegistryPath(),
        getBindRequest(proxyDeviceId, gatewayDeviceId)).execute();
  }

  private BindDeviceToGatewayRequest getBindRequest(String deviceId, String gatewayId) {
    return new BindDeviceToGatewayRequest().setDeviceId(deviceId).setGatewayId(gatewayId);
  }

  /**
   * Get the configuration for a device.
   *
   * @param deviceId target device
   * @return device configuration
   */
  public String getDeviceConfig(String deviceId) {
    try {
      List<DeviceConfig> deviceConfigs = cloudIotRegistries.devices().configVersions()
          .list(getDevicePath(deviceId)).execute().getDeviceConfigs();
      if (deviceConfigs.size() > 0) {
        return new String(Base64.getDecoder().decode(deviceConfigs.get(0).getBinaryData()));
      }
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId);
    }
  }

  /**
   * Set the device configuration.
   *
   * @param deviceId target device
   * @param data     configuration to set
   */
  public void setDeviceConfig(String deviceId, String data) {
    try {
      ModifyCloudToDeviceConfigRequest req = new ModifyCloudToDeviceConfigRequest();

      String encPayload = Base64.getEncoder()
          .encodeToString(data.getBytes(StandardCharsets.UTF_8.name()));
      req.setBinaryData(encPayload);

      cloudIotRegistries.devices().modifyCloudToDeviceConfig(getDevicePath(deviceId), req)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException("While setting device config for " + deviceId);
    }
  }
}
