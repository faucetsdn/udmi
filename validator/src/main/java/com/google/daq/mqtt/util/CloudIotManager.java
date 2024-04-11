package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.ConfigUtil.readExeConfig;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.IotAccess.IotProvider.GCP_NATIVE;
import static udmi.schema.IotAccess.IotProvider.IMPLICIT;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.MetadataMapKeys;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess;
import udmi.schema.SetupUdmiConfig;

/**
 * Encapsulation of all Cloud IoT interaction functions.
 */
public class CloudIotManager {

  public static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";
  public static final int METADATA_SIZE_LIMIT = 32767;
  public static final String REDACTED_MESSAGE = "REDACTED DUE TO SIZE LIMIT";
  public static final String EMPTY_CONFIG = "{}";
  public final ExecutionConfiguration executionConfiguration;

  private final String registryId;
  private final String projectId;
  private final String cloudRegion;
  private final Map<String, CloudModel> deviceMap = new ConcurrentHashMap<>();
  private final File siteModel;
  private final boolean useReflectClient;
  private IotProvider iotProvider;

  /**
   * Create a new CloudIoTManager.
   */
  public CloudIotManager(String projectId, File siteDir, String altRegistry,
      String registrySuffix, IotAccess.IotProvider iotProvider) {
    checkNotNull(projectId, "project id undefined");
    this.siteModel = checkNotNull(siteDir, "site directory undefined");
    checkState(siteDir.isDirectory(), "not a directory " + siteDir.getAbsolutePath());
    this.useReflectClient = iotProvider != null;
    this.projectId = projectId;
    File cloudConfig = new File(siteDir, CLOUD_IOT_CONFIG_JSON);
    try {
      System.err.println("Reading cloud config from " + cloudConfig.getAbsolutePath());
      executionConfiguration = validate(readExeConfig(cloudConfig), this.projectId);
      executionConfiguration.iot_provider = iotProvider;
      executionConfiguration.site_model = siteDir.getPath();
      executionConfiguration.registry_suffix = registrySuffix;
      String targetRegistry = ofNullable(altRegistry).orElse(executionConfiguration.registry_id);
      String udmiNamespace = executionConfiguration.udmi_namespace;
      registryId = SiteModel.getRegistryActual(udmiNamespace, targetRegistry, registrySuffix);
      cloudRegion = executionConfiguration.cloud_region;
      initializeIotProvider();
    } catch (Exception e) {
      throw new RuntimeException(format("While initializing project %s from file %s",
          this.projectId, cloudConfig.getAbsolutePath()), e);
    }
  }

  /**
   * Create a new iot manager using a full configuration file.
   */
  public CloudIotManager(File siteConfig) {
    this(readExeConfig(siteConfig));
  }

  /**
   * New instance from a configuration profile.
   */
  public CloudIotManager(ExecutionConfiguration config) {
    try {
      this.projectId = requireNonNull(config.project_id, "no project_id defined");
      this.useReflectClient = shouldUseReflectorClient(config);
      File model = new File(config.site_model != null ? config.site_model : ".");
      siteModel = model.isAbsolute() ? model
          : new File(new File(config.src_file).getParentFile(), model.getPath());
      File baseConfig = new File(siteModel, CLOUD_IOT_CONFIG_JSON);
      ExecutionConfiguration newConfig =
          config.src_file == null ? mergeObject(readExeConfig(baseConfig), config) : config;
      executionConfiguration = validate(newConfig, this.projectId);
      executionConfiguration.iot_provider = ofNullable(executionConfiguration.iot_provider).orElse(
          IMPLICIT);
      executionConfiguration.site_model = siteModel.getAbsolutePath();
      String udmiNamespace = executionConfiguration.udmi_namespace;
      String targetRegistry = ofNullable(newConfig.alt_registry).orElse(newConfig.registry_id);
      String registrySuffix = newConfig.registry_suffix;
      registryId = SiteModel.getRegistryActual(udmiNamespace, targetRegistry, registrySuffix);
      cloudRegion = executionConfiguration.cloud_region;
      initializeIotProvider();
    } catch (Exception e) {
      throw new RuntimeException(format("While initializing from %s", config.src_file), e);
    }
  }

  private static boolean shouldUseReflectorClient(ExecutionConfiguration config) {
    return (config.reflector_endpoint != null)
        || ofNullable(config.iot_provider).orElse(GCP_NATIVE) != GCP_NATIVE;
  }

  /**
   * Validate the given configuration.
   *
   * @param executionConfiguration configuration to validate
   * @param projectId              expected project id
   * @return validated config (for chaining)
   */
  public static ExecutionConfiguration validate(ExecutionConfiguration executionConfiguration,
      String projectId) {
    if (projectId.equals(executionConfiguration.alt_project)) {
      System.err.printf("Using alt_registry %s for alt_project %s\n",
          executionConfiguration.alt_registry,
          executionConfiguration.alt_project);
      executionConfiguration.alt_project = null;
      executionConfiguration.registry_id = executionConfiguration.alt_registry;
      executionConfiguration.alt_registry = null;
    }
    if (executionConfiguration.project_id == null) {
      executionConfiguration.project_id = projectId;
    }
    checkNotNull(executionConfiguration.registry_id, "registry_id not defined");
    checkNotNull(executionConfiguration.site_name, "site_name not defined");
    return executionConfiguration;
  }

  /**
   * Make an auth credential.
   *
   * @param keyFormat key format to use
   * @param keyData   key data
   * @return public key device credential
   */
  public static Credential makeCredential(String keyFormat, String keyData) {
    Credential deviceCredential = new Credential();
    deviceCredential.key_format = Key_format.fromValue(keyFormat);
    deviceCredential.key_data = keyData;
    return deviceCredential;
  }

  private static Resource_type gatewayIfTrue(boolean isGateway) {
    return isGateway ? Resource_type.GATEWAY : Resource_type.DEVICE;
  }

  private void initializeIotProvider() {
    try {
      iotProvider = makeIotProvider();
      System.err.println("Created service for project " + projectId);
    } catch (Exception e) {
      throw new RuntimeException("While initializing Cloud IoT project " + projectId, e);
    }
  }

  @NotNull
  private IotProvider makeIotProvider() {
    if (projectId.equals(SiteModel.MOCK_PROJECT)) {
      System.err.println("Using mock iot client for special client " + projectId);
      return new IotMockProvider(executionConfiguration);
    }
    if (useReflectClient) {
      System.err.println("Using reflector iot client");
      return new IotReflectorClient(executionConfiguration);
    }
    throw new RuntimeException("Unknown IoT Core selection strategy");
  }

  /**
   * Register the given device in a cloud registry.
   *
   * @param deviceId device to register
   * @param settings settings for the device
   * @return true if this is a new device entry
   */
  public boolean registerDevice(String deviceId, CloudDeviceSettings settings) {
    ExceptionMap exceptions = new ExceptionMap("registering");
    CloudModel device = getRegisteredDevice(deviceId);
    if (device == null) {
      exceptions.capture("creating", () -> createDevice(deviceId, settings));
    } else {
      exceptions.capture("updating", () -> updateDevice(deviceId, settings, device));
    }
 
    if (settings.config != null) {
      exceptions.capture("configuring", () -> writeDeviceConfig(deviceId, settings.config));
    }

    exceptions.throwIfNotEmpty();
    return device == null;
  }

  public CloudModel getRegisteredDevice(String deviceId) {
    checkNotNull(deviceMap, "deviceMap not initialized");
    return deviceMap.get(deviceId);
  }

  private void writeDeviceConfig(String deviceId, String config) {
    iotProvider.updateConfig(deviceId, SubFolder.UPDATE, config);
  }

  public void modifyConfig(String deviceId, SubFolder subFolder, String config) {
    iotProvider.updateConfig(deviceId, subFolder, config);
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

  private CloudModel makeDevice(CloudDeviceSettings settings, CloudModel oldDevice) {
    Map<String, String> metadataMap = ofNullable(oldDevice)
        .map(device -> device.metadata).orElse(new HashMap<>());
    metadataMap.put(MetadataMapKeys.UDMI_METADATA, settings.metadata);
    metadataMap.put(MetadataMapKeys.UDMI_UPDATED, settings.updated);
    metadataMap.put(MetadataMapKeys.UDMI_GENERATION, settings.generation);
    metadataMap.put(MetadataMapKeys.UDMI_CONFIG, settings.config);
    if (settings.keyBytes == null) {
      metadataMap.remove(MetadataMapKeys.KEY_BYTES_KEY);
      metadataMap.remove(MetadataMapKeys.KEY_ALGORITHM_KEY);
    } else {
      String keyBase64 = Base64.getEncoder().encodeToString(settings.keyBytes);
      metadataMap.put(MetadataMapKeys.KEY_BYTES_KEY, keyBase64);
      metadataMap.put(MetadataMapKeys.KEY_ALGORITHM_KEY, settings.keyAlgorithm);
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.resource_type = gatewayIfTrue(settings.proxyDevices != null);
    cloudModel.credentials = getCredentials(settings);
    cloudModel.metadata = metadataMap;
    cloudModel.num_id = settings.deviceNumId;
    return cloudModel;
  }

  private List<Credential> getCredentials(CloudDeviceSettings settings) {
    return settings.credentials == null ? ImmutableList.of() : settings.credentials;
  }

  private void createDevice(String deviceId, CloudDeviceSettings settings) {
    CloudModel newDevice = makeDevice(settings, null);
    limitValueSizes(newDevice.metadata);
    iotProvider.createResource(deviceId, newDevice);
    deviceMap.put(deviceId, newDevice);
  }

  private void updateDevice(String deviceId, CloudDeviceSettings settings, CloudModel oldDevice) {
    CloudModel device = makeDevice(settings, oldDevice);
    limitValueSizes(device.metadata);
    iotProvider.updateDevice(deviceId, device);
  }

  /**
   * Modify some metadata of the target device (not a complete update).
   */
  public void modifyDevice(String deviceId, CloudModel update) {
    limitValueSizes(update.metadata);
    update.operation = Operation.MODIFY;
    iotProvider.updateDevice(deviceId, update);
  }

  private void limitValueSizes(Map<String, String> metadata) {
    metadata.keySet().forEach(key -> ifNotNullThen(metadata.get(key),
        value -> ifTrueThen(value.length() > METADATA_SIZE_LIMIT,
            () -> metadata.put(key, REDACTED_MESSAGE))));
  }

  public SetupUdmiConfig getVersionInformation() {
    return iotProvider.getVersionInformation();
  }

  /**
   * Fetch the list of registered devices.
   *
   * @return registered device list
   */
  public Map<String, CloudModel> fetchCloudModels() {
    return iotProvider.fetchCloudModels(null);
  }

  /**
   * Fetch the list of devices bound to a gateway.
   *
   * @return registered device list
   */
  public Set<String> fetchBoundDevices(String gatewayId) {
    return ifNotNullGet(iotProvider.fetchCloudModels(gatewayId), Map::keySet);
  }

  public CloudModel fetchDevice(String deviceId) {
    return deviceMap.computeIfAbsent(deviceId, this::fetchDeviceRaw);
  }

  private CloudModel fetchDeviceRaw(String deviceId) {
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
    return executionConfiguration.site_name;
  }

  /**
   * Get update topic.
   *
   * @return Topic name to use for sending update messages
   */
  public String getUpdateTopic() {
    return executionConfiguration.update_topic;
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

  public List<Object> getMockActions() {
    return iotProvider.getMockActions();
  }

  public void shutdown() {
    iotProvider.shutdown();
  }

  public void deleteDevice(String deviceId) {
    iotProvider.deleteDevice(deviceId);
    deviceMap.remove(deviceId);
  }

  /**
   * Create a registry with the given suffix.
   */
  public String createRegistry(String suffix) {
    CloudModel settings = new CloudModel();
    settings.resource_type = Resource_type.REGISTRY;
    settings.credentials = List.of(iotProvider.getCredential());
    iotProvider.createResource(suffix, settings);
    return requireNonNull(settings.num_id, "Missing registry name in reply");
  }

  public String getSiteDir() {
    return executionConfiguration.site_model;
  }
}
