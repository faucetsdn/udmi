package com.google.daq.mqtt.util;

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
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

/**
 * Encapsulation of all Cloud IoT interaction functions.
 */
public class CloudIotManager {

  private static final String DEVICE_UPDATE_MASK = "blocked,credentials,metadata";
  private static final String REGISTERED_KEY = "registered";
  private static final String SCHEMA_KEY = "schema_name";
  private static final int LIST_PAGE_SIZE = 1000;

  private final CloudIotConfig cloudIotConfig;

  private final String registryId;
  private final String projectId;
  private final String cloudRegion;

  private CloudIot cloudIotService;
  private String projectPath;
  private CloudIot.Projects.Locations.Registries cloudIotRegistries;
  private Map<String, Device> deviceMap = new HashMap<>();
  private String schemaName;

  public CloudIotManager(String projectId, File iotConfigFile, String schemaName) {
    this.projectId = projectId;
    this.schemaName = schemaName;
    cloudIotConfig = validate(readCloudIotConfig(iotConfigFile));
    registryId = cloudIotConfig.registry_id;
    cloudRegion = cloudIotConfig.cloud_region;
    initializeCloudIoT();
  }

  private static CloudIotConfig validate(CloudIotConfig cloudIotConfig) {
    Preconditions.checkNotNull(cloudIotConfig.registry_id, "registry_id not defined");
    Preconditions.checkNotNull(cloudIotConfig.cloud_region, "cloud_region not defined");
    Preconditions.checkNotNull(cloudIotConfig.site_name, "site_name not defined");
    return cloudIotConfig;
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
      GoogleCredentials credential =
          GoogleCredentials.getApplicationDefault().createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      cloudIotService =
          new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, init)
              .setApplicationName("com.google.iot.bos")
              .build();
      cloudIotRegistries = cloudIotService.projects().locations().registries();
      System.err.println("Created service for project " + projectPath);
    } catch (Exception e) {
      throw new RuntimeException("While initializing Cloud IoT project " + projectPath, e);
    }
  }

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
              Base64.getEncoder().encodeToString(config.getBytes()))
      ).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  public void blockDevice(String deviceId, boolean blocked) {
    try {
      Device device = new Device();
      device.setBlocked(blocked);
      String path = getDevicePath(deviceId);
      cloudIotRegistries.devices().patch(path, device).setUpdateMask("blocked").execute();
    } catch (Exception e) {
      throw new RuntimeException(String.format("While (un)blocking device %s/%s=%s", registryId, deviceId, blocked), e);
    }
  }

  private Device makeDevice(String deviceId, CloudDeviceSettings settings,
      Device oldDevice) {
    Map<String, String> metadataMap = oldDevice == null ? null : oldDevice.getMetadata();
    if (metadataMap == null) {
      metadataMap = new HashMap<>();
    }
    metadataMap.put(REGISTERED_KEY, settings.metadata);
    metadataMap.put(SCHEMA_KEY, schemaName);
    return new Device()
        .setId(deviceId)
        .setGatewayConfig(getGatewayConfig(settings))
        .setCredentials(getCredentials(settings))
        .setMetadata(metadataMap);
  }

  private ImmutableList<DeviceCredential> getCredentials(CloudDeviceSettings settings) {
    if (settings.credential != null) {
      return ImmutableList.of(settings.credential);
    } else {
      return ImmutableList.of();
    }
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
      cloudIotRegistries.devices().create(getRegistryPath(),
          makeDevice(deviceId, settings, null)).execute();
    } catch (GoogleJsonResponseException e) {
      throw new RuntimeException("Remote error creating device " + deviceId, e);
    }
  }

  private void updateDevice(String deviceId, CloudDeviceSettings settings,
      Device oldDevice) {
    try {
      Device device = makeDevice(deviceId, settings, oldDevice)
          .setId(null)
          .setNumId(null);
      cloudIotRegistries
          .devices()
          .patch(getDevicePath(deviceId), device).setUpdateMask(DEVICE_UPDATE_MASK)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException("Remote error patching device " + deviceId, e);
    }
  }

  public static DeviceCredential makeCredentials(String keyFormat, String keyData) {
    PublicKeyCredential publicKeyCredential = new PublicKeyCredential();
    publicKeyCredential.setFormat(keyFormat);
    publicKeyCredential.setKey(keyData);

    DeviceCredential deviceCredential = new DeviceCredential();
    deviceCredential.setPublicKey(publicKeyCredential);
    return deviceCredential;
  }

  public List<Device> fetchDeviceList() {
    Preconditions.checkNotNull(cloudIotService, "CloudIoT service not initialized");
    try {
      List<Device> devices = cloudIotRegistries
          .devices()
          .list(getRegistryPath())
          .setPageSize(LIST_PAGE_SIZE)
          .execute()
          .getDevices();
      if (devices == null) {
        return new ArrayList<>();
      }
      if (devices.size() == LIST_PAGE_SIZE) {
        throw new RuntimeException("Returned exact page size, likely not fetched all devices");
      }
      return devices;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for registry " + registryId, e);
    }
  }

  public Device fetchDevice(String deviceId) {
    return deviceMap.computeIfAbsent(deviceId, this::fetchDeviceFromCloud);
  }

  private Device fetchDeviceFromCloud(String deviceId) {
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

  public String getRegistryId() {
    return registryId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getSiteName() {
    return cloudIotConfig.site_name;
  }

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

  public void setDeviceConfig(String deviceId, String data) {
    try {
      ModifyCloudToDeviceConfigRequest req = new ModifyCloudToDeviceConfigRequest();

      String encPayload = Base64.getEncoder()
          .encodeToString(data.getBytes(StandardCharsets.UTF_8.name()));
      req.setBinaryData(encPayload);

      cloudIotRegistries.devices()
          .modifyCloudToDeviceConfig(getDevicePath(deviceId), req).execute();
    } catch (Exception e) {
      throw new RuntimeException("While setting device config for " + deviceId);
    }
  }
}
