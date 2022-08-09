package com.google.udmi.util;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Envelope;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;

public class SiteModel {

  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  final String sitePath;
  private Map<String, Metadata> allMetadata;
  private EndpointConfiguration endpointConfig;

  public SiteModel(String sitePath) {
    this.sitePath = sitePath;
  }

  public static EndpointConfiguration extractEndpointConfig(CloudIotConfig cloudIotConfig) {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.registryId = cloudIotConfig.registry_id;
    endpoint.cloudRegion = cloudIotConfig.cloud_region;
    return endpoint;
  }

  private static CloudIotConfig makeCloudIotConfig(Envelope attributes) {
    CloudIotConfig cloudIotConfig = new CloudIotConfig();
    cloudIotConfig.registry_id = Preconditions.checkNotNull(attributes.deviceRegistryId,
        "deviceRegistryId");
    cloudIotConfig.cloud_region = Preconditions.checkNotNull(attributes.deviceRegistryLocation,
        "deviceRegistryLocation");
    return cloudIotConfig;
  }

  public static EndpointConfiguration makeEndpointConfig(Envelope attributes) {
    CloudIotConfig cloudIotConfig = makeCloudIotConfig(attributes);
    return extractEndpointConfig(cloudIotConfig);
  }

  private Set<String> getAllDevices() {
    Preconditions.checkState(sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(sitePath), "devices");
    File[] files = Preconditions.checkNotNull(devicesFile.listFiles(), "no files in site devices/");
    return Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
  }

  private void loadAllDeviceMetadata() {
    allMetadata = getAllDevices().stream().collect(toMap(key -> key, this::loadDeviceMetadata));
  }

  private Metadata loadDeviceMetadata(String deviceId) {
    Preconditions.checkState(sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(sitePath), "devices");
    File deviceDir = new File(devicesFile, deviceId);
    File deviceMetadataFile = new File(deviceDir, "metadata.json");
    try {
      return OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading metadata file " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  public Metadata getMetadata(String deviceId) {
    return allMetadata.get(deviceId);
  }

  public void forEachDevice(BiConsumer<String, Metadata> consumer) {
    allMetadata.forEach(consumer);
  }

  private void loadEndpointConfig(String projectId) {
    Preconditions.checkState(sitePath != null,
        "sitePath not defined in configuration");
    File cloudConfig = new File(new File(sitePath), "cloud_iot_config.json");
    try {
      endpointConfig = extractEndpointConfig(
          OBJECT_MAPPER.readValue(cloudConfig, CloudIotConfig.class));
      endpointConfig.projectId = projectId;
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  public EndpointConfiguration getEndpointConfig() {
    return endpointConfig;
  }

  public void initialize(String projectId) {
    loadEndpointConfig(projectId);
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
}
