package com.google.udmi.util;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import daq.pubber.MqttPublisher;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import udmi.schema.CloudIotConfig;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;

public class SiteModel {

  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String DEFAULT_ENDPOINT_HOSTNAME = "mqtt.googleapis.com";

  final String sitePath;
  private Map<String, Metadata> allMetadata;
  private EndpointConfiguration endpointConfig;

  public SiteModel(String sitePath) {
    this.sitePath = sitePath;
  }

  public static EndpointConfiguration extractEndpointConfig(String projectId,
      CloudIotConfig cloudIotConfig, String deviceId) {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.client_id = MqttPublisher.getClientId(projectId,
        cloudIotConfig.cloud_region, cloudIotConfig.registry_id, deviceId);
    endpoint.hostname = DEFAULT_ENDPOINT_HOSTNAME;
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
    return extractEndpointConfig(attributes.projectId, cloudIotConfig, attributes.deviceId);
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

  private void loadEndpointConfig(String projectId, String deviceId) {
    Preconditions.checkState(sitePath != null,
        "sitePath not defined in configuration");
    File cloudConfig = new File(new File(sitePath), "cloud_iot_config.json");
    try {
      endpointConfig = extractEndpointConfig(projectId,
          OBJECT_MAPPER.readValue(cloudConfig, CloudIotConfig.class), deviceId);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  public EndpointConfiguration getEndpointConfig() {
    return endpointConfig;
  }

  public void initialize(String projectId, String deviceId) {
    loadEndpointConfig(projectId, deviceId);
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
