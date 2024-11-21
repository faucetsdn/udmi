package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.ConfigUtil.readExeConfig;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.GeneralUtils.toJsonString;
import static java.lang.String.format;
import static java.nio.file.Files.readAllBytes;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.IotAccess.IotProvider.GBOS;
import static udmi.schema.IotAccess.IotProvider.MQTT;
import static udmi.schema.IotAccess.IotProvider.PUBSUB;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.IotProvider;
import com.google.udmi.util.MetadataMapKeys;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.SiteMetadata;

/**
 * Encapsulation of all Cloud IoT interaction functions.
 */
public class CloudIotManager {

  public static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";
  public static final int METADATA_SIZE_LIMIT = 32767;
  public static final String REDACTED_MESSAGE = "REDACTED DUE TO SIZE LIMIT";
  public static final String EMPTY_CONFIG = "{}";
  private static final String PRIVATE_KEY_BYTES_FMT = "devices/%s/%s_private.pkcs8";
  public final ExecutionConfiguration executionConfiguration;

  private final String registryId;
  private final String projectId;
  private final String cloudRegion;
  private final String toolName;
  private final Map<String, CloudModel> deviceMap = new ConcurrentHashMap<>();
  private final File siteModel;
  private final boolean useReflectClient;
  private IotProvider iotProvider;
  private boolean usePasswords;

  /**
   * Create a new CloudIoTManager.
   */
  public CloudIotManager(String projectId, File siteDir, String altRegistry,
      String registrySuffix, IotAccess.IotProvider iotProvider, String toolName) {
    this.toolName = toolName;
    checkNotNull(projectId, "project id undefined");
    this.siteModel = checkNotNull(siteDir, "site directory undefined");
    checkState(siteDir.isDirectory(), "not a directory " + siteDir.getAbsolutePath());
    this.useReflectClient = iotProvider != null && iotProvider != PUBSUB;
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
   * New instance from a configuration profile.
   */
  public CloudIotManager(ExecutionConfiguration config, String toolName) {
    try {
      this.projectId = requireNonNull(config.project_id, "no project_id defined");
      this.useReflectClient = shouldUseReflectorClient(config);
      this.toolName = toolName;
      File model = new File(config.site_model != null ? config.site_model : ".");
      siteModel = model.isAbsolute() ? model
          : new File(new File(config.src_file).getParentFile(), model.getPath());
      File baseConfig = new File(siteModel, CLOUD_IOT_CONFIG_JSON);
      ExecutionConfiguration newConfig =
          config.src_file == null ? mergeObject(readExeConfig(baseConfig), config) : config;
      executionConfiguration = validate(newConfig, this.projectId);
      executionConfiguration.iot_provider = ofNullable(executionConfiguration.iot_provider).orElse(
          GBOS);
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
        || ofNullable(config.iot_provider).orElse(PUBSUB) != PUBSUB;
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
      System.err.printf(
          "Instantiated iot provider %s as %s%n", executionConfiguration.iot_provider,
          ofNullable(iotProvider).map(p -> p.getClass().getSimpleName()).orElse("undefined"));
    } catch (Exception e) {
      throw new RuntimeException("While initializing Cloud IoT project " + projectId, e);
    }
  }

  private IotProvider makeIotProvider() {
    usePasswords = executionConfiguration.iot_provider == MQTT;

    if (projectId.equals(SiteModel.MOCK_PROJECT)) {
      System.err.println("Using mock iot client for special client " + projectId);
      return new IotMockProvider(executionConfiguration);
    }

    if (useReflectClient) {
      System.err.println("Using reflector iot client");
      return new IotReflectorClient(executionConfiguration, toolName);
    }

    if (executionConfiguration.iot_provider == PUBSUB) {
      return null;
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
    if (usePasswords) {
      coerceCredentialsToPassword(deviceId, settings);
    }
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

  private void coerceCredentialsToPassword(String deviceId, CloudDeviceSettings settings) {
    // TODO: Make this less ugly/hacky. Ick.
    settings.credentials.forEach(credential -> {
      try {
        String prefix = getCredentialPrefix(credential.key_format);
        credential.key_format = Key_format.PASSWORD;
        File privateKey = new File(siteModel, format(PRIVATE_KEY_BYTES_FMT, deviceId, prefix));
        credential.key_data = makePassword(readAllBytes(privateKey.toPath()));
      } catch (Exception e) {
        throw new RuntimeException("While coercing credential for " + deviceId, e);
      }
    });
  }

  private String getCredentialPrefix(Key_format keyFormat) {
    return switch (keyFormat) {
      case ES_256, ES_256_X_509 -> "ec";
      case RS_256, RS_256_X_509 -> "rsa";
      default -> throw new RuntimeException("Unsupported key format conversion " + keyFormat);
    };
  }

  public CloudModel getRegisteredDevice(String deviceId) {
    checkNotNull(deviceMap, "deviceMap not initialized");
    return deviceMap.get(deviceId);
  }

  private void writeDeviceConfig(String deviceId, String config) {
    getIotProvider().updateConfig(deviceId, SubFolder.UPDATE, config);
  }

  public void modifyConfig(String deviceId, SubFolder subFolder, String config) {
    getIotProvider().updateConfig(deviceId, subFolder, config);
  }

  /**
   * Set the blocked state of a given device.
   *
   * @param deviceId target device
   * @param blocked  should this device be blocked?
   */
  public void blockDevice(String deviceId, boolean blocked) {
    getIotProvider().setBlocked(deviceId, blocked);
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
      metadataMap.put(MetadataMapKeys.KEY_BYTES_KEY, encodeBase64(settings.keyBytes));
      metadataMap.put(MetadataMapKeys.KEY_ALGORITHM_KEY, settings.keyAlgorithm);
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.resource_type = gatewayIfTrue(settings.proxyDevices != null);
    cloudModel.credentials = getCredentials(settings);
    cloudModel.metadata = metadataMap;
    cloudModel.num_id = settings.deviceNumId;
    return cloudModel;
  }

  private String makePassword(byte[] keyBytes) {
    return GeneralUtils.sha256(keyBytes).substring(0, 8);
  }

  private List<Credential> getCredentials(CloudDeviceSettings settings) {
    return settings.credentials == null ? ImmutableList.of() : settings.credentials;
  }

  private void createDevice(String deviceId, CloudDeviceSettings settings) {
    CloudModel newDevice = makeDevice(settings, null);
    limitValueSizes(newDevice.metadata);
    getIotProvider().createResource(deviceId, newDevice);
    deviceMap.put(deviceId, newDevice);
  }

  public void updateDevice(String deviceId, CloudDeviceSettings settings, Operation operation) {
    updateDevice(deviceId, makeDevice(settings, null), operation);
  }

  public void updateDevice(String deviceId, CloudDeviceSettings settings, CloudModel oldDevice) {
    updateDevice(deviceId, makeDevice(settings, oldDevice), Operation.UPDATE);
  }

  /**
   * Update a device using the given operation parameter.
   */
  public void updateDevice(String deviceId, CloudModel device, Operation operation) {
    limitValueSizes(device.metadata);
    device.operation = operation;
    getIotProvider().updateDevice(deviceId, device);
  }

  private void limitValueSizes(Map<String, String> metadata) {
    metadata.keySet().forEach(key -> ifNotNullThen(metadata.get(key),
        value -> ifTrueThen(value.length() > METADATA_SIZE_LIMIT,
            () -> metadata.put(key, REDACTED_MESSAGE))));
  }

  public SetupUdmiConfig getVersionInformation() {
    return getIotProvider().getVersionInformation();
  }

  /**
   * Fetch the list of registered devices.
   *
   * @return registered device list
   */
  public Map<String, CloudModel> fetchCloudModels() {
    return getIotProvider().fetchCloudModels(null);
  }

  /**
   * Fetch the list of devices bound to a gateway.
   *
   * @return registered device list
   */
  public Set<String> fetchBoundDevices(String gatewayId) {
    return ifNotNullGet(getIotProvider().fetchCloudModels(gatewayId), Map::keySet);
  }

  public CloudModel fetchDevice(String deviceId) {
    return deviceMap.computeIfAbsent(deviceId, this::fetchDeviceRaw);
  }

  private CloudModel fetchDeviceRaw(String deviceId) {
    return getIotProvider().fetchDevice(deviceId);
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
    getIotProvider().bindDeviceToGateway(proxyDeviceId, gatewayDeviceId);
  }

  public List<Object> getMockActions() {
    return getIotProvider().getMockActions();
  }

  public void shutdown() {
    ifNotNullThen(iotProvider, IotProvider::shutdown);
  }

  public void deleteDevice(String deviceId) {
    getIotProvider().deleteDevice(deviceId);
    deviceMap.remove(deviceId);
  }

  /**
   * Create a registry with the given suffix.
   */
  public String createRegistry(String suffix) {
    CloudModel settings = new CloudModel();
    settings.resource_type = Resource_type.REGISTRY;
    settings.credentials = List.of(getIotProvider().getCredential());
    getIotProvider().createResource(suffix, settings);
    return requireNonNull(settings.num_id, "Missing registry name in reply");
  }

  /**
   * Update the cloud site metadata for the current registry.
   */
  public void updateRegistry(SiteMetadata siteMetadata, Operation operation) {
    CloudModel registryModel = new CloudModel();
    registryModel.resource_type = Resource_type.REGISTRY;
    registryModel.operation = operation;
    registryModel.metadata = new HashMap<>();
    registryModel.metadata.put(
        MetadataMapKeys.UDMI_METADATA, toJsonString(siteMetadata)
    );
    getIotProvider().updateRegistry(registryModel);
  }

  public String getSiteDir() {
    return executionConfiguration.site_model;
  }

  private IotProvider getIotProvider() {
    return checkNotNull(iotProvider, "iot provider not properly initialized");
  }

  public boolean canUpdateCloud() {
    return iotProvider != null;
  }
}
