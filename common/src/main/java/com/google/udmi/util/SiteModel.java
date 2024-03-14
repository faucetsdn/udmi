package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.JsonUtil.loadFileStrict;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.GatewayModel;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Metadata;

public class SiteModel {

  public static final String DEFAULT_CLEARBLADE_HOSTNAME = "us-central1-mqtt.clearblade.com";
  public static final String DEFAULT_CLEARBLADE_HOSTNAME_FORMAT = "%s-mqtt.clearblade.com";
  public static final String DEFAULT_GBOS_HOSTNAME = "mqtt.bos.goog";
  public static final String MOCK_PROJECT = "mock-project";
  public static final String LOCALHOST_HOSTNAME = "localhost";
  private static final String ID_FORMAT = "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final Pattern ID_PATTERN = Pattern.compile(
      "projects/(.*)/locations/(.*)/registries/(.*)/devices/(.*)");

  final String sitePath;
  private Map<String, Metadata> allMetadata;
  private Map<String, CloudModel> allDevices;
  private ExecutionConfiguration executionConfiguration;

  public SiteModel(String sitePath) {
    this.sitePath = sitePath;
  }

  public static EndpointConfiguration makeEndpointConfig(String iotProject,
      ExecutionConfiguration executionConfig, String deviceId) {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.client_id = getClientId(iotProject,
        executionConfig.cloud_region, getRegistryActual(executionConfig), deviceId);
    endpoint.hostname = getEndpointHostname(executionConfig);
    return endpoint;
  }

  private static String getEndpointHostname(ExecutionConfiguration executionConfig) {
    IotProvider iotProvider = ofNullable(executionConfig.iot_provider).orElse(IotProvider.IMPLICIT);
    return switch (iotProvider) {
      case CLEARBLADE -> ifNotNullGet(executionConfig.cloud_region,
              region -> format(DEFAULT_CLEARBLADE_HOSTNAME_FORMAT, region), DEFAULT_CLEARBLADE_HOSTNAME);
      case GBOS -> DEFAULT_GBOS_HOSTNAME;
      case IMPLICIT -> LOCALHOST_HOSTNAME;
      default -> throw new RuntimeException("Unsupported iot_provider " + iotProvider);
    };
  }

  public static String getClientId(String iotProject, String cloudRegion, String registryId,
      String deviceId) {
    return format(ID_FORMAT,
        requireNonNull(iotProject, "iot project not defined"),
        requireNonNull(cloudRegion, "cloud region not defined"),
        requireNonNull(registryId, "registry id not defined"),
        requireNonNull(deviceId, "device id not defined"));
  }

  private static ExecutionConfiguration makeExecutionConfiguration(Envelope attributes) {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.registry_id = checkNotNull(attributes.deviceRegistryId,
        "deviceRegistryId");
    executionConfiguration.cloud_region = checkNotNull(
        attributes.deviceRegistryLocation,
        "deviceRegistryLocation");
    return executionConfiguration;
  }

  public static EndpointConfiguration makeEndpointConfig(Envelope attributes) {
    ExecutionConfiguration executionConfiguration = makeExecutionConfiguration(attributes);
    return makeEndpointConfig(attributes.projectId, executionConfiguration, attributes.deviceId);
  }

  /**
   * Parse a GCP clientId string into component parts including project, etc...
   *
   * @param clientId client id to parse
   * @return bucket of parameters
   */
  public static ClientInfo parseClientId(String clientId) {
    if (clientId == null) {
      throw new IllegalArgumentException("client_id not specified");
    }
    Matcher matcher = ID_PATTERN.matcher(clientId);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          format("client_id %s does not match pattern %s", clientId, ID_PATTERN.pattern()));
    }
    ClientInfo clientInfo = new ClientInfo();
    clientInfo.iotProject = matcher.group(1);
    clientInfo.cloudRegion = matcher.group(2);
    clientInfo.registryId = matcher.group(3);
    clientInfo.deviceId = matcher.group(4);
    return clientInfo;
  }

  public static List<String> listDevices(File devicesDir) {
    if (!devicesDir.exists()) {
      return ImmutableList.of();
    }
    String[] devices = requireNonNull(devicesDir.list());
    return Arrays.stream(devices).filter(SiteModel::validDeviceDirectory)
        .collect(Collectors.toList());
  }

  private static boolean validDeviceDirectory(String dirName) {
    return !(dirName.startsWith(".") || dirName.endsWith("~"));
  }

  public static Metadata loadDeviceMetadata(String sitePath, String deviceId, Class<?> container) {
    try {
      Preconditions.checkState(sitePath != null, "sitePath not defined");
      File deviceDir = getDeviceDir(sitePath, deviceId);
      File deviceMetadataFile = new File(deviceDir, "metadata.json");
      if (!deviceMetadataFile.exists()) {
        return new MetadataException(deviceMetadataFile, new FileNotFoundException());
      }
      Metadata metadata = requireNonNull(captureLoadErrors(deviceMetadataFile), "bad metadata");

      // Missing arrays are automatically parsed to an empty list, which is not what
      // we want, so hacky go through and convert an empty list to null.
      if (metadata.gateway != null && metadata.gateway.proxy_ids.isEmpty()) {
        metadata.gateway.proxy_ids = null;
      }

      return metadata;
    } catch (Exception e) {
      throw new RuntimeException("While loading device metadata for " + deviceId, e);
    }
  }

  private static Metadata captureLoadErrors(File deviceMetadataFile) {
    try {
      return loadFileStrict(Metadata.class, deviceMetadataFile);
    } catch (Exception e) {
      return new MetadataException(deviceMetadataFile, e);
    }
  }

  private static File getDeviceDir(String sitePath, String deviceId) {
    File devicesFile = new File(new File(sitePath), "devices");
    return new File(devicesFile, deviceId);
  }

  public static String getRegistryActual(ExecutionConfiguration iotConfig) {
    return getRegistryActual(iotConfig.udmi_namespace, iotConfig.registry_id,
        iotConfig.registry_suffix);
  }

  public static String getRegistryActual(String namespace,
      String registry_id, String registry_suffix) {
    if (registry_id == null) {
      return null;
    }
    return getNamespacePrefix(namespace) + registry_id + ofNullable(registry_suffix).orElse("");
  }

  public EndpointConfiguration makeEndpointConfig(String iotProject, String deviceId) {
    return makeEndpointConfig(iotProject, executionConfiguration, deviceId);
  }

  private Set<String> getDeviceIds() {
    Preconditions.checkState(sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(sitePath), "devices");
    File[] files = Objects.requireNonNull(devicesFile.listFiles(), "no files in site devices/");
    return Arrays.stream(files).map(File::getName).filter(SiteModel::validDeviceDirectory)
        .collect(Collectors.toSet());
  }

  private void loadAllDeviceMetadata() {
    Set<String> deviceIds = getDeviceIds();
    allMetadata = deviceIds.stream().collect(toMap(key -> key, this::loadDeviceMetadata));
    allDevices = deviceIds.stream().collect(toMap(key -> key, this::newDevice));
  }

  private CloudModel newDevice(String deviceId) {
    return new CloudModel();
  }

  private Metadata loadDeviceMetadata(String deviceId) {
    return loadDeviceMetadata(sitePath, deviceId, SiteModel.class);
  }

  public File getDeviceDir(String deviceId) {
    return getDeviceDir(sitePath, deviceId);
  }

  public File getDeviceFile(String deviceId, String path) {
    return new File(getDeviceDir(deviceId), path);
  }

  public Metadata getMetadata(String deviceId) {
    return allMetadata.get(deviceId);
  }

  public Collection<CloudModel> allDevices() {
    return allDevices.values();
  }

  public Collection<String> allDeviceIds() {
    return allDevices.keySet();
  }

  public void forEachDeviceId(Consumer<String> consumer) {
    allDevices.keySet().forEach(consumer);
  }

  public void forEachMetadata(BiConsumer<String, Metadata> consumer) {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    allMetadata.forEach(consumer);
  }

  public void forEachMetadata(Consumer<Entry<String, Metadata>> consumer) {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    allMetadata.forEach((id, metadata) -> consumer.accept(Map.entry(id, metadata)));
  }

  public Stream<Entry<String, Metadata>> metadataStream() {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    return allMetadata.entrySet().stream();
  }

  private void loadSiteConfig() {
    Preconditions.checkState(sitePath != null,
        "sitePath not defined in configuration");
    File cloudConfig = new File(new File(sitePath), "cloud_iot_config.json");
    try {
      executionConfiguration = OBJECT_MAPPER.readValue(cloudConfig, ExecutionConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  public void initialize() {
    loadSiteConfig();
    loadAllDeviceMetadata();
  }

  public Auth_type getAuthType(String deviceId) {
    return allMetadata.get(deviceId).cloud.auth_type;
  }

  public String getDeviceKeyFile(String deviceId) {
    String gatewayId = findGateway(deviceId);
    String keyDevice = gatewayId != null ? gatewayId : deviceId;
    return format(KEY_SITE_PATH_FORMAT, sitePath,
        keyDevice, getDeviceKeyPrefix(keyDevice));
  }

  private String findGateway(String deviceId) {
    GatewayModel gateway = getMetadata(deviceId).gateway;
    return gateway != null ? gateway.gateway_id : null;
  }

  private String getDeviceKeyPrefix(String targetId) {
    Auth_type auth_type = getMetadata(targetId).cloud.auth_type;
    return auth_type.value().startsWith("RS") ? "rsa" : "ec";
  }

  /**
   * Get the site registry name.
   *
   * @return site registry
   */
  public String getRegistryId() {
    return getRegistryActual(executionConfiguration);
  }

  /**
   * Get the reflect region name
   *
   * @return reflect region
   */
  public String getReflectRegion() {
    return executionConfiguration.reflect_region;
  }

  /**
   * Get the cloud region for this model.
   *
   * @return cloud region
   */
  public String getCloudRegion() {
    return executionConfiguration.cloud_region;
  }

  /**
   * Get the update topic for the site, if defined.
   *
   * @return update topic
   */
  public String getUpdateTopic() {
    return executionConfiguration.update_topic;
  }

  public CloudModel getDevice(String deviceId) {
    return allDevices.get(deviceId);
  }

  public String getSitePath() {
    return sitePath;
  }

  public String validatorKey() {
    return sitePath + "/reflector/rsa_private.pkcs8";
  }

  public File getDeviceWorkingDir(String deviceId) {
    File file = new File(sitePath + "/out/devices/" + deviceId);
    if (!file.exists()) {
      file.mkdirs();
    }
    if (!file.isDirectory()) {
      throw new RuntimeException(
          "Device working dir is not a valid directory: " + file.getAbsolutePath());
    }
    return file;
  }

  public ExecutionConfiguration getExecutionConfiguration() {
    ifNullThen(executionConfiguration, this::loadSiteConfig);
    return executionConfiguration;
  }

  public File getSubdirectory(String path) {
    return new File(sitePath, path);
  }

  public static class MetadataException extends Metadata {

    public final File file;
    public final Exception exception;

    public MetadataException(File deviceMetadataFile, Exception metadataException) {
      file = deviceMetadataFile;
      exception = metadataException;
    }
  }

  public static class ClientInfo {

    public String iotProject;
    public String cloudRegion;
    public String registryId;
    public String deviceId;
  }
}
