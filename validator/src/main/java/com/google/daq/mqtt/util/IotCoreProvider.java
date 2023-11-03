package com.google.daq.mqtt.util;

import static com.google.udmi.util.JsonUtil.getTimestamp;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.Resource_type.DEVICE;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;

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
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.GatewayConfig;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.api.services.cloudiot.v1.model.UnbindDeviceFromGatewayRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.math.BigInteger;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.SetupUdmiConfig;

class IotCoreProvider implements IotProvider {

  private static final String DEVICE_UPDATE_MASK = "blocked,credentials,metadata";
  private static final int LIST_PAGE_SIZE = 1000;
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
  public static final String UDMI_REF = "GCP IoT Core Direct";
  public static final String GATEWAY_TYPE = "GATEWAY";
  private final CloudIot.Projects.Locations.Registries registries;
  private final String projectId;
  private final String cloudRegion;
  private final String registryId;

  IotCoreProvider(String projectId, String registryId, String cloudRegion) {
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

  @Override
  public void shutdown() {
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    try {
      String useConfig = Optional.ofNullable(config).orElse("");
      registries.devices().modifyCloudToDeviceConfig(
          getDevicePath(deviceId),
          new ModifyCloudToDeviceConfigRequest().setBinaryData(
              Base64.getEncoder().encodeToString(useConfig.getBytes()))).execute();
    } catch (Exception e) {
      throw new RuntimeException("While modifying device config", e);
    }
  }

  @Override
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

  @Override
  public void updateDevice(String deviceId, CloudModel device) {
    try {
      device.num_id = null;
      registries.devices().patch(getDevicePath(deviceId), convert(device))
          .setUpdateMask(DEVICE_UPDATE_MASK)
          .execute();
    } catch (Exception e) {
      throw new RuntimeException("Error patching device " + deviceId, e);
    }
  }

  @Override
  public void createResource(String deviceId, CloudModel iotDevice) {
    try {
      Device execute = registries.devices()
          .create(getRegistryPath(), convert(iotDevice).setId(deviceId)).execute();
      iotDevice.num_id = execute.getNumId().toString();
    } catch (Exception e) {
      throw new RuntimeException("Error creating device " + deviceId, e);
    }
  }

  @Override
  public void deleteDevice(String deviceId) {
    try {
      Set<String> associatedDevices = fetchDeviceIds(deviceId);
      associatedDevices.forEach(proxyId -> safeUnbind(deviceId, proxyId));
      registries.devices().delete(getDevicePath(deviceId)).execute();
    } catch (Exception e) {
      throw new RuntimeException("Error deleting device " + deviceId, e);
    }
  }

  private void safeUnbind(String deviceId, String proxyId) {
    try {
      registries.unbindDeviceFromGateway(getRegistryPath(),
              new UnbindDeviceFromGatewayRequest()
                  .setDeviceId(proxyId)
                  .setGatewayId(deviceId))
          .execute();
    } catch (Exception e) {
      throw new RuntimeException("While unbinding " + proxyId + " from " + deviceId, e);
    }
  }

  private static GatewayConfig makeGatewayConfig(CloudModel device) {
    if (!TRUE.equals(device.resource_type == GATEWAY)) {
      return null;
    }
    GatewayConfig gatewayConfig = new GatewayConfig();
    gatewayConfig.setGatewayType(GATEWAY_TYPE);
    gatewayConfig.setGatewayAuthMethod("ASSOCIATION_ONLY");
    return gatewayConfig;
  }

  static Device convert(CloudModel device) {
    List<DeviceCredential> newCredentials = ofNullable(device.credentials)
        .map(credentials -> credentials.stream()
            .map(IotCoreProvider::convert).collect(Collectors.toList())).orElse(null);
    BigInteger numId = device.num_id == null ? null : new BigInteger(device.num_id);
    String timestamp = device.last_event_time == null ? null : getTimestamp(device.last_event_time);
    return new Device()
        .setNumId(numId)
        .setBlocked(device.blocked)
        .setCredentials(newCredentials)
        .setGatewayConfig(makeGatewayConfig(device))
        .setLastEventTime(timestamp)
        .setMetadata(device.metadata);
  }

  static CloudModel convert(Device device) {
    List<Credential> newCredentials = ofNullable(device.getCredentials())
        .map(credentials -> credentials.stream()
            .map(IotCoreProvider::convert).collect(Collectors.toList())).orElse(null);
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId().toString();
    cloudModel.blocked = device.getBlocked();
    cloudModel.credentials = newCredentials;
    cloudModel.resource_type = convert(device.getGatewayConfig());
    cloudModel.metadata = ofNullable(device.getMetadata()).map(HashMap::new).orElse(null);
    cloudModel.last_event_time = JsonUtil.getDate(device.getLastEventTime());
    return cloudModel;
  }

  private static DeviceCredential convert(Credential iot) {
    PublicKeyCredential publicKey = new PublicKeyCredential().setKey(iot.key_data)
        .setFormat(AUTH_TYPE_MAP.get(iot.key_format));
    return new DeviceCredential().setPublicKey(publicKey);
  }

  private static Credential convert(DeviceCredential device) {
    PublicKeyCredential publicKey = device.getPublicKey();
    Credential credential = new Credential();
    credential.key_data = publicKey.getKey();
    credential.key_format = AUTH_TYPE_MAP.inverse().get(publicKey.getFormat());
    return credential;
  }

  private static Resource_type convert(GatewayConfig gatewayConfig) {
    if (gatewayConfig == null) {
      return DEVICE;
    }
    return GATEWAY_TYPE.equals(gatewayConfig.getGatewayType()) ? GATEWAY : DEVICE;
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    try {
      return convert(registries.devices().get(getDevicePath(deviceId)).execute());
    } catch (Exception e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getDetails().getCode() == 404) {
        return null;
      }
      throw new RuntimeException("While fetching " + deviceId, e);
    }
  }

  @Override
  public void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId) {
    try {
      registries.bindDeviceToGateway(getRegistryPath(),
              new BindDeviceToGatewayRequest()
                  .setDeviceId(proxyDeviceId)
                  .setGatewayId(gatewayDeviceId))
          .execute();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("While binding device %s to %s", proxyDeviceId, gatewayDeviceId), e);
    }
  }

  @Override
  public Set<String> fetchDeviceIds(String forGatewayId) {
    Set<Device> allDevices = new HashSet<>();
    String nextPageToken = null;
    try {
      do {
        ListDevicesResponse response = registries.devices().list(getRegistryPath())
            .setPageToken(nextPageToken)
            .setPageSize(LIST_PAGE_SIZE)
            .setGatewayListOptionsAssociationsGatewayId(forGatewayId)
            .execute();
        java.util.List<Device> devices = response.getDevices();
        allDevices.addAll(devices == null ? ImmutableList.of() : devices);
        nextPageToken = response.getNextPageToken();
      } while (nextPageToken != null);
      return allDevices.stream().map(Device::getId).collect(Collectors.toSet());
    } catch (Exception e) {
      throw new RuntimeException("While listing devices for registry " + registryId, e);
    }
  }

  @Override
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

  @Override
  public SetupUdmiConfig getVersionInformation() {
    SetupUdmiConfig setupUdmiConfig = new SetupUdmiConfig();
    setupUdmiConfig.udmi_version = Common.getUdmiVersion();
    setupUdmiConfig.udmi_ref = UDMI_REF;
    return setupUdmiConfig;
  }

  @Override
  public List<Object> getMockActions() {
    throw new RuntimeException("This is not a mock provider!");
  }
}
