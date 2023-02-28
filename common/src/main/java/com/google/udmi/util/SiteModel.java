package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.client.json.Json;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;

public class SiteModel {

  public static final String MOCK_PROJECT = "mock-project";
  private static final String ID_FORMAT = "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String DEFAULT_ENDPOINT_HOSTNAME = "mqtt.googleapis.com";
  private static final Pattern ID_PATTERN = Pattern.compile(
      "projects/(.*)/locations/(.*)/registries/(.*)/devices/(.*)");

  final String sitePath;
  private Map<String, Metadata> allMetadata;
  private Map<String, Device> allDevices;
  private ExecutionConfiguration executionConfiguration;

  public SiteModel(String sitePath) {
    this.sitePath = sitePath;
  }

  public static EndpointConfiguration makeEndpointConfig(String projectId,
      ExecutionConfiguration executionConfig, String deviceId) {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.client_id = getClientId(projectId,
        executionConfig.cloud_region, executionConfig.registry_id, deviceId);
    endpoint.hostname = DEFAULT_ENDPOINT_HOSTNAME;
    return endpoint;
  }

  public static String getClientId(String projectId, String cloudRegion, String registryId,
      String deviceId) {
    return String.format(ID_FORMAT, projectId, cloudRegion, registryId, deviceId);
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
          String.format("client_id %s does not match pattern %s", clientId, ID_PATTERN.pattern()));
    }
    ClientInfo clientInfo = new ClientInfo();
    clientInfo.projectId = matcher.group(1);
    clientInfo.cloudRegion = matcher.group(2);
    clientInfo.registryId = matcher.group(3);
    clientInfo.deviceId = matcher.group(4);
    return clientInfo;
  }

  public static List<String> listDevices(File devicesDir) {
    if (!devicesDir.exists()) {
      System.err.println(
          "Directory not found, assuming no devices: " + devicesDir.getAbsolutePath());
      return ImmutableList.of();
    }
    String[] devices = requireNonNull(devicesDir.list());
    return Arrays.stream(devices).filter(SiteModel::validDeviceDirectory)
        .collect(Collectors.toList());
  }

  public EndpointConfiguration makeEndpointConfig(String projectId, String deviceId) {
    return makeEndpointConfig(projectId, executionConfiguration, deviceId);
  }

  private Set<String> getDeviceIds() {
    Preconditions.checkState(sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(sitePath), "devices");
    File[] files = Objects.requireNonNull(devicesFile.listFiles(), "no files in site devices/");
    return Arrays.stream(files).map(File::getName).filter(SiteModel::validDeviceDirectory).collect(Collectors.toSet());
  }

  private static boolean validDeviceDirectory(String dirName) {
    return !(dirName.startsWith(".") || dirName.endsWith("~"));
  }

  private void loadAllDeviceMetadata() {
    Set<String> deviceIds = getDeviceIds();
    allMetadata = deviceIds.stream().collect(toMap(key -> key, this::loadDeviceMetadata));
    allDevices = deviceIds.stream().collect(toMap(key -> key, this::newDevice));
  }

  private Device newDevice(String deviceId) {
    return new SiteModel.Device(deviceId);
  }

  private Metadata loadDeviceMetadata(String deviceId) {
    return loadDeviceMetadata(sitePath, deviceId, SiteModel.class);
  }

  public static Metadata loadDeviceMetadata(String sitePath, String deviceId, Class<?> container) {
    Preconditions.checkState(sitePath != null, "sitePath not defined");
    File deviceDir = getDeviceDir(sitePath, deviceId);
    File deviceMetadataFile = new File(deviceDir, "metadata.json");
    try {
      Metadata metadata = JsonUtil.loadFile(Metadata.class, deviceMetadataFile);
      if (metadata != null) {
        metadata.exception = captureLoadErrors(deviceMetadataFile, container);
        // Missing arrays are automatically parsed to an empty list, which is not what
        // we want, so hacky go through and convert an empty list to null.
        if (metadata.gateway != null && metadata.gateway.proxy_ids.isEmpty()) {
          metadata.gateway.proxy_ids = null;
        }
      }
      return metadata;
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading metadata file " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  private static Exception captureLoadErrors(File deviceMetadataFile, Class<?> container) {
    try {
      JsonUtil.loadStrict(Metadata.class, deviceMetadataFile);
      return null;
    } catch (Exception e) {
      return e;
    }
  }

  private File getDeviceDir(String deviceId) {
    return getDeviceDir(sitePath, deviceId);
  }

  private static File getDeviceDir(String sitePath, String deviceId) {
    File devicesFile = new File(new File(sitePath), "devices");
    return new File(devicesFile, deviceId);
  }

  public Metadata getMetadata(String deviceId) {
    return allMetadata.get(deviceId);
  }

  public Collection<Device> allDevices() {
    return allDevices.values();
  }

  public Collection<String> allDeviceIds() {
    return allDevices.keySet();
  }

  public void forEachDevice(Consumer<Device> consumer) {
    allDevices.values().forEach(consumer);
  }

  public void forEachMetadata(BiConsumer<String, Metadata> consumer) {
    allMetadata.forEach(consumer);
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
    return String.format(KEY_SITE_PATH_FORMAT, sitePath,
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
    return executionConfiguration.registry_id;
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

  public Device getDevice(String deviceId) {
    return allDevices.get(deviceId);
  }

  public String getSitePath() {
    return sitePath;
  }

  public String validatorKey() {
    return sitePath + "/validator/rsa_private.pkcs8";
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

  public static class ClientInfo {

    public String cloudRegion;
    public String projectId;
    public String registryId;
    public String deviceId;
  }

  public class Device {

    public final String deviceId;

    public Device(String deviceId) {
      this.deviceId = deviceId;
    }

    public File getFile() {
      return getDeviceDir(deviceId);
    }
  }
}
