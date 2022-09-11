package com.google.daq.mqtt.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIot.Builder;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.BindDeviceToGatewayRequest;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IoTCoreProvider {

  private static final String DEVICE_UPDATE_MASK = "blocked,credentials,metadata";
  private static final int LIST_PAGE_SIZE = 1000;
  private final CloudIot.Projects.Locations.Registries registries;
  private final String projectId;
  private final String cloudRegion;
  private final String registryId;

  public IoTCoreProvider(String projectId, String registryId, String cloudRegion) {
    try {
      this.projectId = projectId;
      this.registryId = registryId;
      this.cloudRegion = cloudRegion;
      System.err.println("Initializing with default credentials...");
      GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
          .createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      CloudIot cloudIotService = new Builder(GoogleNetHttpTransport.newTrustedTransport(),
          jsonFactory, init).setApplicationName("com.google.iot.bos").build();
      registries = cloudIotService.projects().locations().registries();
    } catch (Exception e) {
      throw new RuntimeException("While creating IoTCoreProvider", e);
    }
  }

  public void updateConfig(String deviceId, String config) {
    try {
      registries.devices().modifyCloudToDeviceConfig(
          getDevicePath(deviceId),
          new ModifyCloudToDeviceConfigRequest().setBinaryData(
              Base64.getEncoder().encodeToString(config.getBytes()))).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  public void setBlocked(String deviceId, boolean blocked) {
    try {
      Device device = new Device();
      device.setBlocked(blocked);
      registries.devices().patch(getDevicePath(deviceId), device).setUpdateMask("blocked")
          .execute();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("While (un)blocking device %s/%s=%s", registryId, deviceId, blocked), e);
    }
  }

  public void updateDevice(String deviceId, Device device) {
    try {
      registries.devices().patch(getDevicePath(deviceId), device).setUpdateMask(DEVICE_UPDATE_MASK)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException("Remote error patching device " + deviceId, e);
    }
  }

  public void createDevice(Device makeDevice) {
    String deviceId = makeDevice.getId();
    try {
      registries.devices().create(getRegistryPath(), makeDevice).execute();
    } catch (Exception e) {
      throw new RuntimeException("Remote error creating device " + deviceId, e);
    }
  }

  public Device fetchDevice(String deviceId) {
    try {
      return registries.devices().get(getDevicePath(deviceId)).execute();
    } catch (Exception e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getDetails().getCode() == 404) {
        return null;
      }
      throw new RuntimeException("While fetching " + deviceId, e);
    }
  }

  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    try {
      registries.bindDeviceToGateway(getRegistryPath(),
              new BindDeviceToGatewayRequest().setDeviceId(proxyDeviceId).setGatewayId(gatewayDeviceId))
          .execute();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("While binding device %s to %s", proxyDeviceId, gatewayDeviceId), e);
    }
  }

  public Set<String> fetchDeviceList() {
    Set<Device> allDevices = new HashSet<>();
    String nextPageToken = null;
    try {
      do {
        ListDevicesResponse response = registries.devices().list(getRegistryPath())
            .setPageToken(nextPageToken).setPageSize(LIST_PAGE_SIZE).execute();
        java.util.List<Device> devices = response.getDevices();
        allDevices.addAll(devices == null ? ImmutableList.of() : devices);
        System.err.printf("Retrieved %d devices from registry...%n", allDevices.size());
        nextPageToken = response.getNextPageToken();
      } while (nextPageToken != null);
      return allDevices.stream().map(Device::getId).collect(Collectors.toSet());
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for registry " + registryId, e);
    }
  }

  public String getDeviceConfig(String deviceId) {
    try {
      List<DeviceConfig> deviceConfigs = registries.devices().configVersions()
          .list(getDevicePath(deviceId)).execute().getDeviceConfigs();
      if (deviceConfigs.size() > 0) {
        return new String(Base64.getDecoder().decode(deviceConfigs.get(0).getBinaryData()));
      }
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  private String getProjectPath() {
    return "projects/" + projectId + "/locations/" + cloudRegion;
  }

  private String getRegistryPath() {
    return getProjectPath() + "/registries/" + registryId;
  }

  private String getDevicePath(String deviceId) {
    return getRegistryPath() + "/devices/" + deviceId;
  }

}
