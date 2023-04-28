package com.google.bos.udmi.service.access;

import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;

public class GcpIotAccessProvider implements IotAccessProvider {

  private static final String PROJECT_PATH_FORMAT = "projects/%s";
  private static final String REGISTRY_PATH_FORMAT = "%s/locations/%s/registries/%s";
  private static final String DEVICE_PATH_FORMAT = "%s/devices/%s";
  private static final String DEFAULT_CLOUD_REGION = "us-central1";
  private static final String APPLICATION_NAME = "com.google.iot.bos";
  private static final String RSA_KEY_FORMAT = "RSA_PEM";
  private static final String RSA_CERT_FORMAT = "RSA_X509_PEM";
  private static final String ES_KEY_FORMAT = "ES256_PEM";
  private static final String ES_CERT_FILE = "ES256_X509_PEM";
  private static final BiMap<Key_format, String> AUTH_TYPE_MAP =
      ImmutableBiMap.of(
          Key_format.RS_256, RSA_KEY_FORMAT,
          Key_format.RS_256_X_509, RSA_CERT_FORMAT,
          Key_format.ES_256, ES_KEY_FORMAT,
          Key_format.ES_256_X_509, ES_CERT_FILE);
  public static final String EMPTY_JSON = "{}";

  private final String projectId;
  private final Map<String, String> registryCloudRegions = new ConcurrentHashMap<>();
  private CloudIot.Projects.Locations.Registries registries;

  public GcpIotAccessProvider(IotAccess iotAccess) {
    projectId = requireNonNull(iotAccess.project_id, "gcp project id not specified");
  }

  @Override
  public void activate() {
    try {
      GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
          .createScoped(CloudIotScopes.all());
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
      CloudIot cloudIotService = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(),
          jsonFactory, init).setApplicationName(APPLICATION_NAME).build();
      registries = cloudIotService.projects().locations().registries();
    } catch (Exception e) {
      throw new RuntimeException("While activating", e);
    }
  }

  @Override
  public void shutdown() {
    registries = null;
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder subFolder,
      String contents) {
    // TODO: Need to implement checking of config version for concurrent operations.
    String configString = ofNullable(fetchConfig(registryId, deviceId)).orElse(EMPTY_JSON);
    Map<String, Object> configMap = toMap(configString);
    configMap.put(subFolder.toString(), contents);
    updateConfig(registryId, deviceId, stringify(configMap));
  }

  private String fetchConfig(String registryId, String deviceId) {
    try {
      List<DeviceConfig> deviceConfigs = registries.devices().configVersions()
          .list(getDevicePath(registryId, deviceId)).execute().getDeviceConfigs();
      if (deviceConfigs.size() > 0) {
        return new String(Base64.getDecoder().decode(deviceConfigs.get(0).getBinaryData()));
      }
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  public void updateConfig(String registryId, String deviceId, String config) {
    try {
      String useConfig = ofNullable(config).orElse("");
      registries.devices().modifyCloudToDeviceConfig(
          getDevicePath(registryId, deviceId),
          new ModifyCloudToDeviceConfigRequest().setBinaryData(
              Base64.getEncoder().encodeToString(useConfig.getBytes()))).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  private String getProjectPath() {
    return format(PROJECT_PATH_FORMAT, projectId);
  }

  private String getRegistryPath(String registryId) {
    return format(REGISTRY_PATH_FORMAT, getProjectPath(), getCloudRegion(registryId), registryId);
  }

  private String getCloudRegion(String registryId) {
    return registryCloudRegions.computeIfAbsent(registryId, key -> DEFAULT_CLOUD_REGION);
  }

  private String getDevicePath(String registryId, String deviceId) {
    return format(DEVICE_PATH_FORMAT, getRegistryPath(registryId), deviceId);
  }
}

