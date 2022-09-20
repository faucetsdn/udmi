package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.GatewayConfig;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulation of all Cloud IoT interaction functions.
 */
public class CloudIotManager {

  public static final String UDMI_METADATA = "udmi_metadata";
  public static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";
  private static final String UDMI_CONFIG = "udmi_config";
  private static final String UDMI_GENERATION = "udmi_generation";
  private static final String UDMI_UPDATED = "udmi_updated";
  private static final String KEY_BYTES_KEY = "key_bytes";
  private static final String KEY_ALGORITHM_KEY = "key_algorithm";
  private static final String MOCK_PROJECT = "unit-testing";

  public final CloudIotConfig cloudIotConfig;

  private final String registryId;
  private final String projectId;
  private final String cloudRegion;
  private final Map<String, Device> deviceMap = new ConcurrentHashMap<>();
  private IotProvider iotProvider;

  /**
   * Create a new CloudIoTManager.
   *
   * @param projectId project id
   * @param siteDir   site model directory
   */
  public CloudIotManager(String projectId, File siteDir) {
    Preconditions.checkNotNull(siteDir, "site directory undefined");
    this.projectId = Preconditions.checkNotNull(projectId, "project id undefined");
    File cloudConfig = new File(siteDir, CLOUD_IOT_CONFIG_JSON);
    try {
      System.err.println("Reading cloud config from " + cloudConfig.getAbsolutePath());
      cloudIotConfig = validate(readCloudIotConfig(cloudConfig), projectId);
      registryId = cloudIotConfig.registry_id;
      cloudRegion = cloudIotConfig.cloud_region;
      initializeIotProvider();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("While initializing project %s from file %s", projectId,
              cloudConfig.getAbsolutePath()), e);
    }
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

  private void initializeIotProvider() {
    try {
      iotProvider = projectId.equals(MOCK_PROJECT)
          ? new IotMockProvider(projectId, registryId, cloudRegion)
          : new IotCoreProvider(projectId, registryId, cloudRegion);
      System.err.println("Created service for project " + projectId);
    } catch (Exception e) {
      throw new RuntimeException("While initializing Cloud IoT project " + projectId, e);
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
    iotProvider.updateConfig(deviceId, config);
  }

  /**
   * Set the blocked state of a given device.
   *
   * @param deviceId target device
   * @param blocked  should this device be blocked?
   */
  public void blockDevice(String deviceId, boolean blocked) {
    iotProvider.setBlocked(deviceId, blocked);
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
    iotProvider.createDevice(makeDevice(deviceId, settings, null));
  }

  private void updateDevice(String deviceId, CloudDeviceSettings settings, Device oldDevice) {
    Device device = makeDevice(deviceId, settings, oldDevice).setId(null).setNumId(null);
    iotProvider.updateDevice(deviceId, device);
  }

  /**
   * Fetch the list of registered devices.
   *
   * @return registered device list
   */
  public Set<String> fetchDeviceIds() {
    return iotProvider.fetchDeviceIds();
  }

  public Device fetchDevice(String deviceId) {
    return deviceMap.computeIfAbsent(deviceId, this::fetchDeviceRaw);
  }

  private Device fetchDeviceRaw(String deviceId) {
    return iotProvider.fetchDevice(deviceId);
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

  public void bindDevice(String proxyDeviceId, String gatewayDeviceId) {
    iotProvider.bindDeviceToGateway(proxyDeviceId, gatewayDeviceId);
  }

  /**
   * Get the configuration for a device.
   *
   * @param deviceId target device
   * @return device configuration
   */
  public String getDeviceConfig(String deviceId) {
    return iotProvider.getDeviceConfig(deviceId);
  }

  public List<Object> getMockActions() {
    return iotProvider.getMockActions();
  }
}
