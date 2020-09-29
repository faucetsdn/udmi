package com.google.bos.iot.core.proxy;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations.Registries.Devices;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CloudIotManager {

  private static final Logger LOG = LoggerFactory.getLogger(CloudIotManager.class);
  private static final String APPLICATION_NAME = "iot_core_proxy";
  private final CloudIotConfig iotConfig;
  private final CloudIot cloudIot;
  private final String projectId;

  CloudIotManager(String projectId, CloudIotConfig iotConfig) {
    this.projectId = projectId;
    this.iotConfig = iotConfig;
    try {
      LOG.info("Initializing with default credentials...");
      GoogleCredentials credential =
          GoogleCredentials.getApplicationDefault().createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      cloudIot = new CloudIot.Builder(
          GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, init)
          .setApplicationName(APPLICATION_NAME).build();
    } catch (Exception e) {
      throw new RuntimeException("Could not connect to Cloud IoT", e);
    }
  }

  Set<String> listDevices() {
    final String registryPath = String.format("projects/%s/locations/%s/registries/%s",
        projectId, iotConfig.cloud_region, iotConfig.registry_id);
    try {
      Set<String> deviceSet = new HashSet<>();

      String nextPageToken = null;
      do {
        LOG.info("Listing devices for " + registryPath);
        Devices.List listRequest = cloudIot
            .projects()
            .locations()
            .registries()
            .devices()
            .list(registryPath);

        if (nextPageToken != null) {
          listRequest.setPageToken(nextPageToken);
        }

        ListDevicesResponse listDevicesResponse = listRequest.execute();
        List<Device> devices = listDevicesResponse.getDevices();
        if (devices == null) {
          throw new RuntimeException("Devices list is empty");
        }
        devices.forEach(device -> deviceSet.add(device.getId()));
        nextPageToken = listDevicesResponse.getNextPageToken();
      } while (nextPageToken != null);

      return deviceSet;
    } catch (Exception e) {
      throw new RuntimeException("While fetching devices from " + registryPath, e);
    }
  }

  public void setDeviceConfig(String deviceId, String data) {
    try {
      final String registryPath = String.format("projects/%s/locations/%s/registries/%s",
          projectId, iotConfig.cloud_region, iotConfig.registry_id);
      final String devicePath = registryPath + "/devices/" + deviceId;
      ModifyCloudToDeviceConfigRequest req = new ModifyCloudToDeviceConfigRequest();

      String encPayload = Base64.getEncoder()
          .encodeToString(data.getBytes(StandardCharsets.UTF_8.name()));
      req.setBinaryData(encPayload);

      cloudIot
          .projects()
          .locations()
          .registries()
          .devices()
          .modifyCloudToDeviceConfig(devicePath, req).execute();
    } catch (Exception e) {
      throw new RuntimeException("While setting device config for " + deviceId);
    }
  }
}
