package com.google.bos.udmi.service.access;

import static com.clearblade.cloud.iot.v1.devicetypes.GatewayType.NON_GATEWAY;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.udmi.util.Common.EMPTY_RETURN_RECEIPT;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.setOrSize;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MetadataMapKeys.UDMI_UPDATED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.CloudModel.ModelOperation.BIND;
import static udmi.schema.CloudModel.ModelOperation.BOUND;
import static udmi.schema.CloudModel.ModelOperation.CREATE;
import static udmi.schema.CloudModel.ModelOperation.DELETE;
import static udmi.schema.CloudModel.ModelOperation.UPDATE;
import static udmi.schema.CloudModel.Resource_type.DIRECT;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;
import static udmi.schema.CloudModel.Resource_type.REGISTRY;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
import com.clearblade.cloud.iot.v1.DeviceManagerInterface;
import com.clearblade.cloud.iot.v1.binddevicetogateway.BindDeviceToGatewayRequest;
import com.clearblade.cloud.iot.v1.createdevice.CreateDeviceRequest;
import com.clearblade.cloud.iot.v1.createdeviceregistry.CreateDeviceRegistryRequest;
import com.clearblade.cloud.iot.v1.deletedevice.DeleteDeviceRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListResponse;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesRequest;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesResponse;
import com.clearblade.cloud.iot.v1.devicetypes.Device;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceConfig;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceCredential;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceName;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceState;
import com.clearblade.cloud.iot.v1.devicetypes.FieldMask;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayAuthMethod;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayConfig;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayListOptions;
import com.clearblade.cloud.iot.v1.devicetypes.GatewayType;
import com.clearblade.cloud.iot.v1.exception.ApplicationException;
import com.clearblade.cloud.iot.v1.getdevice.GetDeviceRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsResponse;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesRequest;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesResponse;
import com.clearblade.cloud.iot.v1.modifycloudtodeviceconfig.ModifyCloudToDeviceConfigRequest;
import com.clearblade.cloud.iot.v1.registrytypes.DeviceRegistry;
import com.clearblade.cloud.iot.v1.registrytypes.EventNotificationConfig;
import com.clearblade.cloud.iot.v1.registrytypes.LocationName;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyCredential;
import com.clearblade.cloud.iot.v1.registrytypes.PublicKeyFormat;
import com.clearblade.cloud.iot.v1.registrytypes.RegistryName;
import com.clearblade.cloud.iot.v1.registrytypes.StateNotificationConfig;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceRequest;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceResponse;
import com.clearblade.cloud.iot.v1.unbinddevicefromgateway.UnbindDeviceFromGatewayRequest;
import com.clearblade.cloud.iot.v1.updatedevice.UpdateDeviceRequest;
import com.clearblade.cloud.iot.v1.utils.ByteString;
import com.clearblade.cloud.iot.v1.utils.LogLevel;
import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.AbstractMap.SimpleEntry;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.GatewayModel;
import udmi.schema.IotAccess;

/**
 * IoT access provider for (deprecated) GCP IoT Core.
 * TODO: Need to implement page tokens for all requisite API calls.
 */
public class ClearBladeIotAccessProvider extends IotAccessBase {

  public static final String REGISTRIES_FIELD_MASK = "id,name";
  public static final String DEFAULT_REGION = "us-central1";
  public static final String CONFIG_ENV = "CLEARBLADE_CONFIGURATION";
  private static final Set<String> CLOUD_REGIONS = ImmutableSet.of(DEFAULT_REGION);
  private static final String EMPTY_JSON = "{}";
  private static final BiMap<Key_format, PublicKeyFormat> AUTH_TYPE_MAP = ImmutableBiMap.of(
      Key_format.RS_256, PublicKeyFormat.RSA_PEM,
      Key_format.RS_256_X_509, PublicKeyFormat.RSA_X509_PEM,
      Key_format.ES_256, PublicKeyFormat.ES256_PEM,
      Key_format.ES_256_X_509, PublicKeyFormat.ES256_X509_PEM
  );
  private static final String RESOURCE_EXISTS = "-2";
  private static final String BLOCKED_FIELD_MASK = "blocked";
  private static final String UPDATE_FIELD_MASK = "blocked,credentials,metadata";
  private static final String METADATA_FIELD_MASK = "metadata";
  private static final GatewayConfig NON_GATEWAY_CONFIG = new GatewayConfig();
  private static final GatewayConfig GATEWAY_CONFIG = GatewayConfig.newBuilder()
      .setGatewayType(GatewayType.GATEWAY)
      .setGatewayAuthMethod(GatewayAuthMethod.ASSOCIATION_ONLY)
      .build();
  private static final String UDMI_TARGET_TOPIC = "udmi_target"; // TODO: Make this not hardcoded.
  private static final String UDMI_STATE_TOPIC = "udmi_state"; // TODO: Make this not hardcoded.
  private static final String TOPIC_NAME_FORMAT = "projects/%s/topics/%s";
  private static final CharSequence HAD_BOUND_DEVICES_MARKER = " it has associated devices.";
  private static final CharSequence BOUND_TO_GATEWAY_MARKER = " it's associated with ";
  private static final CharSequence DUPLICATES_ERROR_MARKER = " duplicates in bound_devices";
  private final String projectId;
  private final DeviceManagerInterface deviceManager;

  /**
   * Create a new instance for interfacing with GCP IoT Core.
   */
  public ClearBladeIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    deviceManager = getDeviceManager(ofNullable(iotAccess.profile_sec).orElse(0));
    projectId = getProjectId(iotAccess);
    ifNotTrueThen(isEnabled(),
        () -> warn("Clearblade access provided disabled because project id is null or empty"));
  }

  private static Credential convertIot(DeviceCredential device) {
    // Credential credential = new Credential();
    // credential.key_data = device.getPublicKey().getKey();
    // credential.key_format = AUTH_TYPE_MAP.inverse().get(device.getPublicKey().getFormat());
    // return credential;
    throw new RuntimeException("Not yet implemented");
  }

  private static List<Credential> convertIot(List<DeviceCredential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(ClearBladeIotAccessProvider::convertIot)
            .collect(Collectors.toList()));
  }

  private static Entry<String, CloudModel> convertPartial(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId();
    cloudModel.resource_type = resourceType(deviceRaw);
    cloudModel.last_event_time = getSafeDate(device.getLastEventTime());
    cloudModel.blocked = device.isBlocked() ? true : null;
    cloudModel.updated_time = catchToNull(() -> getDate(device.getMetadata().get(UDMI_UPDATED)));
    return new SimpleEntry<>(device.getId(), cloudModel);
  }

  @Nullable
  private static Date getSafeDate(String lastEventTime) {
    return getDate(isNullOrEmpty(lastEventTime) ? null : lastEventTime);
  }

  private boolean isGateway(Entry<String, CloudModel> modelEntry) {
    return modelEntry.getValue().resource_type == GATEWAY;
  }

  /**
   * Core test function for listing the devices in a registry.
   */
  public static void main(String[] args) {
    requireNonNull(System.getenv(CONFIG_ENV), CONFIG_ENV + " not defined");
    IotAccess iotAccess = new IotAccess();
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: registry_id [device_id]");
      return;
    }
    final String registryId = args[0];
    final String deviceId = args.length > 1 ? args[1] : null;

    Map<String, Object> stringObjectMap = JsonUtil.loadMap(System.getenv(CONFIG_ENV));
    iotAccess.project_id = (String) requireNonNull(stringObjectMap.get("project"));
    System.err.println("Extracted project from ClearBlade config file: " + iotAccess.project_id);
    ClearBladeIotAccessProvider clearBladeIotAccessProvider =
        new ClearBladeIotAccessProvider(iotAccess);
    clearBladeIotAccessProvider.populateRegistryRegions();

    if (deviceId == null) {
      CloudModel cloudModel = clearBladeIotAccessProvider.listDevices(registryId, null);
      System.err.printf("Found %d devices in %s%n", cloudModel.device_ids.size(), registryId);
    } else {
      CloudModel cloudModel = clearBladeIotAccessProvider.fetchDevice(registryId, deviceId);
      System.err.printf("Fetched %s/%s num_id %s%n", registryId, deviceId, cloudModel.num_id);

      File deviceFile = new File(format("%s_%s_device.json", registryId, deviceId));
      writeFile(cloudModel, deviceFile);
      System.err.printf("Wrote device info file to %s%n", deviceFile.getAbsoluteFile());

      Entry<Long, String> configInfo =
          clearBladeIotAccessProvider.fetchConfig(registryId, deviceId);
      File configFile = new File(format("%s_%s_config.json", registryId, deviceId));
      writeString(configFile, configInfo.getValue());
      System.err.printf("Wrote device config version %d to %s%n", configInfo.getKey(),
          configFile.getAbsoluteFile());

      String stateInfo = clearBladeIotAccessProvider.fetchState(registryId, deviceId);
      File stateFile = new File(format("%s_%s_state.json", registryId, deviceId));
      writeString(stateFile, stateInfo);
      System.err.printf("Wrote device state to %s%n", stateFile.getAbsoluteFile());

    }
  }

  private static Resource_type resourceType(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    GatewayConfig gatewayConfig = device.getGatewayConfig();
    if (gatewayConfig != null && GatewayType.GATEWAY == gatewayConfig.getGatewayType()) {
      return GATEWAY;
    }
    return Resource_type.DIRECT;
  }

  @VisibleForTesting
  protected DeviceManagerInterface getDeviceManager(int monitorSec) {
    return ProfilingProxy.create(this, new DeviceManagerClient(), monitorSec);
  }

  private CloudModel bindDevicesToGateway(String registryId, String gatewayId,
      CloudModel cloudModel, Consumer<String> progress) {
    Set<String> deviceIds = ReflectProcessor.isLegacyRequest(cloudModel)
        ? cloudModel.device_ids.keySet() : getDeviceIds(cloudModel.gateway);
    boolean toBind = cloudModel.operation == BIND;
    bindDevicesGateways(registryId, ImmutableSet.of(gatewayId), deviceIds, toBind, progress);
    CloudModel reply = new CloudModel();
    reply.num_id = EMPTY_RETURN_RECEIPT;
    reply.operation = cloudModel.operation;
    return reply;
  }

  private static Set<String> getDeviceIds(GatewayModel gateway) {
    return new HashSet<>(gateway.proxy_ids);
  }

  private void bindDevice(String registryId, String gatewayId, String deviceId) {
    try {
      String location = getRegistryLocation(registryId);
      RegistryName parent = RegistryName.of(projectId, location, registryId);
      BindDeviceToGatewayRequest request =
          BindDeviceToGatewayRequest.Builder.newBuilder()
              .setParent(parent.getRegistryFullName())
              .setDevice(deviceId)
              .setGateway(gatewayId)
              .build();
      try {
        requireNonNull(deviceManager.bindDeviceToGateway(request),
            "binding device to gateway");
      } catch (Exception e) {
        if (friendlyStackTrace(e).contains(DUPLICATES_ERROR_MARKER)) {
          warn("Ignoring duplicate bound device error for " + gatewayId);
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While binding %s to gateway %s", deviceId, gatewayId), e);
    }
  }

  private CloudModel blockDevice(String registryId, Device device) {
    String deviceId = device.toBuilder().getId();
    String name = getDeviceName(registryId, deviceId);
    Device fullDevice = device.toBuilder().setName(name).setBlocked(true).build();
    try {
      UpdateDeviceRequest request =
          UpdateDeviceRequest.Builder.newBuilder().setDevice(fullDevice).setName(name)
              .setUpdateMask(BLOCKED_FIELD_MASK).build();
      requireNonNull(deviceManager.updateDevice(request), "Invalid RPC response");
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = ModelOperation.BLOCK;
      cloudModel.num_id = getReturnReceipt(registryId, device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While updating " + deviceId, e);
    }
  }

  private String getReturnReceipt(String registryId, Device device) {
    return Optional.ofNullable(device.toBuilder().getNumId()).orElse(EMPTY_RETURN_RECEIPT);
  }

  private Device convert(CloudModel cloudModel, String deviceId) {
    return Device.newBuilder()
        .setBlocked(isTrue(cloudModel.blocked))
        .setCredentials(convertUdmi(cloudModel.credentials))
        .setGatewayConfig(cloudModel.resource_type == GATEWAY ? GATEWAY_CONFIG : NON_GATEWAY_CONFIG)
        .setLogLevel(LogLevel.LOG_LEVEL_UNSPECIFIED)
        .setMetadata(cloudModel.metadata)
        .setNumId(cloudModel.num_id)
        .setId(deviceId)
        .build();
  }

  private CloudModel convertFull(Device deviceRaw) {
    Device.Builder device = deviceRaw.toBuilder();
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = device.getNumId();
    cloudModel.resource_type = resourceType(deviceRaw);
    cloudModel.blocked = device.isBlocked();
    cloudModel.metadata = device.getMetadata();
    cloudModel.last_event_time = getSafeDate(device.getLastEventTime());
    cloudModel.last_state_time = getSafeDate(device.getLastStateTime());
    cloudModel.last_config_time = getSafeDate(device.getLastConfigSendTime());
    cloudModel.last_config_ack = getSafeDate(device.getLastConfigAckTime());
    cloudModel.last_error_time = getSafeDate(device.getLastErrorTime());
    cloudModel.credentials = convertIot(device.getCredentials());
    cloudModel.updated_time = catchToNull(() -> getDate(device.getMetadata().get(UDMI_UPDATED)));
    return cloudModel;
  }

  private DeviceCredential convertUdmi(Credential credential) {
    return DeviceCredential.newBuilder()
        .setPublicKey(PublicKeyCredential.newBuilder()
            .setKey(credential.key_data)
            .setFormat(AUTH_TYPE_MAP.get(credential.key_format))
            .build())
        .build();
  }

  private List<DeviceCredential> convertUdmi(List<Credential> credentials) {
    return ifNotNullGet(credentials,
        list -> list.stream().map(this::convertUdmi).collect(Collectors.toList()));
  }

  private CloudModel createDevice(String registryId, Device device) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = CREATE;
    try {
      String location = getRegistryLocation(registryId);
      String parent = RegistryName.of(projectId, location, registryId).toString();
      CreateDeviceRequest request =
          CreateDeviceRequest.Builder.newBuilder().setParent(parent).setDevice(device)
              .build();
      requireNonNull(deviceManager.createDevice(request), "create device failed for " + parent);
      CloudModel createdModel = fetchDevice(registryId, device.toBuilder().getId());
      cloudModel.operation = CREATE;
      cloudModel.num_id = createdModel.num_id;
      return cloudModel;
    } catch (ApplicationException applicationException) {
      if (applicationException.getMessage().contains("ALREADY_EXISTS")) {
        cloudModel.num_id = RESOURCE_EXISTS;
        return cloudModel;
      }
      throw applicationException;
    }
  }

  private CloudModel createRegistry(String registryId, Device device) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = CREATE;
    cloudModel.resource_type = REGISTRY;
    cloudModel.num_id = registryId;
    try {
      String location = getRegistryLocation(reflectRegistry);
      DeviceRegistry.Builder registry = DeviceRegistry.newBuilder()
          .setId(registryId)
          .setEventNotificationConfigs(ImmutableList.of(eventNotificationConfig()))
          .setStateNotificationConfig(stateNotificationConfig())
          .setLogLevel(LogLevel.DEBUG);
      CreateDeviceRegistryRequest request = CreateDeviceRegistryRequest.Builder.newBuilder()
          .setParent(LocationName.of(projectId, location).toString())
          .setDeviceRegistry(registry.build()).build();
      deviceManager.createDeviceRegistry(request);
      // TODO: This should also add the metadata for the device.
    } catch (ApplicationException applicationException) {
      if (!applicationException.getMessage().contains("ALREADY_EXISTS")) {
        throw applicationException;
      }
    }
    return cloudModel;
  }

  private EventNotificationConfig eventNotificationConfig() {
    String topicName = getScopedTopic(UDMI_TARGET_TOPIC);
    return EventNotificationConfig.newBuilder().setPubsubTopicName(topicName).build();
  }

  private HashMap<String, CloudModel> fetchDevices(String deviceRegistryId,
      Consumer<String> progress, boolean chattyProgress, GatewayListOptions gatewayListOptions) {
    String location = getRegistryLocation(deviceRegistryId);
    String registryFullName =
        RegistryName.of(projectId, location, deviceRegistryId).getRegistryFullName();
    String pageToken = null;
    HashMap<String, CloudModel> collect = new HashMap<>();
    int queryCount = 0;
    do {
      DevicesListRequest request = DevicesListRequest.Builder.newBuilder().setParent(
              registryFullName)
          .setGatewayListOptions(gatewayListOptions)
          .setPageToken(pageToken)
          .build();
      DevicesListResponse response = deviceManager.listDevices(request);
      requireNonNull(response, "DeviceRegistriesList fetch failed");
      Map<String, CloudModel> responseMap =
          response.getDevicesList().stream().map(ClearBladeIotAccessProvider::convertPartial)
              .collect(toMap(Entry::getKey, Entry::getValue));
      List<String> exists = responseMap.keySet().stream().filter(collect::containsKey).toList();
      ifNotTrueThen(exists.isEmpty(),
          () -> progress.accept("Found duplicate device entries: " + exists));

      collect.putAll(responseMap);
      pageToken = response.getNextPageToken();
      queryCount++;
      ifTrueThen(pageToken != null || chattyProgress,
          () -> progress.accept(getProgressMessage(collect, gatewayListOptions)));
      debug(format("fetchDevices %s #%d found %d total %d more %s", deviceRegistryId,
          queryCount, responseMap.size(), collect.size(), pageToken != null));
    } while (pageToken != null);
    return collect;
  }

  private static String getProgressMessage(HashMap<String, CloudModel> collect,
      GatewayListOptions gatewayListOptions) {
    if (gatewayListOptions != null) {
      String gatewayId = gatewayListOptions.getAssociationsGatewayId();
      if (gatewayId != null) {
        return format("Fetched %d devices bound to gateway %s", collect.size(), gatewayId);
      }
      String deviceId = gatewayListOptions.getAssociationsDeviceId();
      if (deviceId != null) {
        return format("Fetched %s gateways bound to device %s", collect.size(), deviceId);
      }
    }
    return format("Fetched %d devices...", collect.size());
  }

  private CloudModel findDevicesForGateway(String registryId, Device device) {
    CloudModel cloudModel = listRegistryDevices(registryId, device.toBuilder().getId(), null);
    cloudModel.operation = BOUND;
    cloudModel.resource_type = GATEWAY;
    return cloudModel;
  }

  private CloudModel findGatewaysForDevice(String registryId, Device device) {
    String deviceId = requireNonNull(device.toBuilder().getId(), "unspecified device id");
    Set<String> boundGateways = fetchDevices(registryId, this::bitBucket,
        true, getBoundGatewaysOptions(deviceId)).keySet();
    if (boundGateways.isEmpty()) {
      throw new RuntimeException("Was expecting at least one bound gateway!");
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = getReturnReceipt(registryId, device);
    cloudModel.operation = BOUND;
    cloudModel.resource_type = DIRECT;
    cloudModel.gateway = getDeviceGatewayModel(boundGateways);
    return cloudModel;
  }

  private GatewayModel getDeviceGatewayModel(Set<String> boundGateways) {
    GatewayModel gatewayModel = new GatewayModel();
    gatewayModel.gateway_id = GeneralUtils.thereCanBeOnlyOne(boundGateways.stream().toList());
    return gatewayModel;
  }

  private String getDeviceName(String registryId, String deviceId) {
    return DeviceName.of(projectId, getRegistryLocation(registryId), registryId, deviceId)
        .toString();
  }

  private GatewayListOptions getBoundDevicesOptions(String gatewayId) {
    return GatewayListOptions.newBuilder()
        .setGatewayType(NON_GATEWAY)
        .setAssociationsGatewayId(requireNonNull(gatewayId, "gateway undefined"))
        .build();
  }

  private GatewayListOptions getBoundGatewaysOptions(String deviceId) {
    return GatewayListOptions.newBuilder()
        .setGatewayType(GatewayType.GATEWAY)
        .setAssociationsDeviceId(requireNonNull(deviceId, "device undefined"))
        .build();
  }

  private String getRegistryLocation(String registry) {
    return getRegistryRegion(registry);
  }

  private String getRegistryName(String registryId) {
    return RegistryName.of(projectId, getRegistryLocation(registryId), registryId).toString();
  }

  private String getScopedTopic(String udmiTargetTopic) {
    return format(TOPIC_NAME_FORMAT, projectId, getPodNamespacePrefix() + udmiTargetTopic);
  }

  private CloudModel listRegistryDevices(String registryId, String gatewayId,
      Consumer<String> maybeProgress) {
    return listRegistryDevices(registryId, gatewayId, maybeProgress, true);
  }

  private CloudModel listRegistryDevices(String registryId, String gatewayId,
      Consumer<String> maybeProgress, boolean chattyProgress) {
    try {
      CloudModel cloudModel = new CloudModel();
      Consumer<String> progress = ofNullable(maybeProgress).orElse(this::bitBucket);
      GatewayListOptions options = ifNotNullGet(gatewayId, this::getBoundDevicesOptions);
      HashMap<String, CloudModel> boundDevices =
          fetchDevices(registryId, progress, chattyProgress, options);
      debug(format("Fetched %d devices from %s gateway %s", boundDevices.size(), registryId,
          gatewayId));
      if (gatewayId != null) {
        cloudModel.gateway = makeGatewayModel(boundDevices);
      } else {
        cloudModel.device_ids = augmentGatewayModels(registryId, boundDevices, progress);
      }
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While listing devices " + getRegistryName(registryId), e);
    }
  }

  private Map<String, CloudModel> augmentGatewayModels(String registryId,
      HashMap<String, CloudModel> boundDevices, Consumer<String> progress) {
    requireNonNull(progress, "augmentGatewayModels has null progress");
    HashMap<String, String> proxyDeviceGateways = new HashMap<>();
    Set<Entry<String, CloudModel>> gateways =
        boundDevices.entrySet().stream().filter(this::isGateway).collect(Collectors.toSet());
    AtomicInteger count = new AtomicInteger();
    gateways.forEach(entry -> {
      int added = augmentGatewayModel(registryId, entry, proxyDeviceGateways, progress);
      progress.accept(format("Augmented gateway %s (%d/%d) with %d entries",
          entry.getKey(), count.incrementAndGet(), gateways.size(), added));
    });
    progress.accept(format("Bound to gateways: %s", setOrSize(proxyDeviceGateways.keySet())));
    boundDevices.entrySet()
        .forEach(entry -> augmentProxiedModel(entry, proxyDeviceGateways));
    return boundDevices;
  }

  private void augmentProxiedModel(Entry<String, CloudModel> entry,
      HashMap<String, String> proxyDeviceGateways) {
    String gatewayId = proxyDeviceGateways.get(entry.getKey());
    ifNotNullThen(gatewayId, id -> {
      GatewayModel gatewayModel = new GatewayModel();
      gatewayModel.gateway_id = id;
      gatewayModel.proxy_ids = null;
      entry.getValue().gateway = gatewayModel;
    });
  }

  private int augmentGatewayModel(String registryId, Entry<String, CloudModel> model,
      HashMap<String, String> proxyDeviceGateways, Consumer<String> progress) {
    String gatewayId = model.getKey();
    CloudModel gatewayModel = listRegistryDevices(registryId, gatewayId, progress, false);
    model.getValue().gateway = gatewayModel.gateway;
    gatewayModel.gateway.proxy_ids.forEach(proxyId -> proxyDeviceGateways.put(proxyId, gatewayId));
    return gatewayModel.gateway.proxy_ids.size();
  }

  private GatewayModel makeGatewayModel(HashMap<String, CloudModel> boundDevices) {
    GatewayModel gatewayModel = new GatewayModel();
    gatewayModel.proxy_ids = boundDevices.keySet().stream().toList();
    return gatewayModel;
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<String> maybeProgress) {
    String devicePath = getDeviceName(registryId, deviceId);
    ModelOperation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DIRECT);
    checkState(type == DIRECT || type == GATEWAY, "unexpected resource type " + type);
    Consumer<String> progress = ofNullable(maybeProgress).orElse(this::bitBucket);
    try {
      Device device = convert(cloudModel, deviceId);
      return switch (operation) {
        case CREATE -> createDevice(registryId, device);
        case UPDATE -> updateDevice(registryId, device);
        case DELETE -> unbindAndDelete(registryId, device, cloudModel, progress);
        case MODIFY -> modifyDevice(registryId, device);
        case BIND, UNBIND -> bindDevicesToGateway(registryId, deviceId, cloudModel, progress);
        case BLOCK -> blockDevice(registryId, device);
        default -> throw new RuntimeException("Unknown device operation " + operation);
      };
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing " + devicePath, e);
    }
  }

  private void bitBucket(String s) {
    // Do nothing, just drop the messages on the floor.
  }

  private CloudModel getReply(String registryId, String deviceId, CloudModel request,
      String numId) {
    CloudModel reply = new CloudModel();
    reply.operation = requireNonNull(request.operation, "missing operation");
    reply.num_id = requireNonNull(numId, "missing num_id");
    return reply;
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    ModelOperation operation = cloudModel.operation;
    String registryActual = registryId + deviceId;
    try {
      if (operation == UPDATE) {
        // TODO: This should update the metadata of the registry.
        return getReply(registryId, deviceId, cloudModel, "registry");
      } else if (operation == CREATE) {
        if (deviceId != null && !deviceId.isEmpty()) {
          CloudModel deviceModel = deepCopy(cloudModel);
          deviceModel.resource_type = DIRECT;
          modelDevice(reflectRegistry, registryActual, deviceModel, null);
        }
        Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DIRECT);
        checkState(type == REGISTRY, "unexpected resource type " + type);
        Device device = convert(cloudModel, deviceId);
        return createRegistry(registryActual, device);
      } else {
        throw new RuntimeException("Unsupported operation " + operation);
      }
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing registry " + registryActual, e);
    }
  }

  private CloudModel modifyDevice(String registryId, Device device) {
    Device.Builder builder = device.toBuilder();
    String deviceId = builder.getId();
    CloudModel model = fetchDevice(registryId, deviceId);
    model.metadata.putAll(builder.getMetadata());
    builder.setMetadata(model.metadata);
    CloudModel cloudModel = updateDevice(registryId, builder.build(), METADATA_FIELD_MASK);
    cloudModel.operation = ModelOperation.MODIFY;
    cloudModel.gateway = model.gateway;
    cloudModel.device_ids = model.device_ids;
    return cloudModel;
  }

  private StateNotificationConfig stateNotificationConfig() {
    String topicName = getScopedTopic(UDMI_STATE_TOPIC);
    return StateNotificationConfig.newBuilder().setPubsubTopicName(topicName).build();
  }

  private CloudModel unbindAndDelete(String registryId, Device device, CloudModel request,
      Consumer<String> progress) {
    try {
      final Set<String> unbindIds = ReflectProcessor.isLegacyRequest(request)
          ? legacyFindGateways(registryId, device)
          : ifNotNullGet(request.gateway, ClearBladeIotAccessProvider::getDeviceIds);
      return unbindAndDeleteCore(registryId, device, unbindIds, progress);
    } catch (Exception e) {
      String stackTrace = friendlyStackTrace(e);
      if (stackTrace.contains(BOUND_TO_GATEWAY_MARKER)) {
        debug("Device bound to gateway. Finding bindings to unbind...");
        CloudModel gatewaysForDevice = findGatewaysForDevice(registryId, device);
        if (ReflectProcessor.isLegacyRequest(request)) {
          warn("Handling legacy bound delete...");
          return findUnbindAndDelete(registryId, device, gatewaysForDevice, progress);
        }
        return gatewaysForDevice;
      } else if (stackTrace.contains(HAD_BOUND_DEVICES_MARKER)) {
        debug("Gateway has bound devices. Finding bindings to unbind...");
        return findDevicesForGateway(registryId, device);
      } else {
        throw e;
      }
    }
  }

  private CloudModel findUnbindAndDelete(String registryId, Device device, CloudModel request,
      Consumer<String> progress) {
    String gatewayId = request.gateway.gateway_id;
    String deviceId = device.toBuilder().getId();
    unbindDevice(registryId, gatewayId, deviceId);
    unbindAndDeleteCore(registryId, device, null, progress);
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = DELETE;
    return cloudModel;
  }

  private Set<String> legacyFindGateways(String registryId, Device device) {
    try {
      return getDeviceIds(findDevicesForGateway(registryId, device).gateway);
    } catch (RuntimeException e) {
      warn("Ignoring legacy exception (likely not a gateway)");
      return null;
    }
  }

  private CloudModel unbindAndDeleteCore(String registryId, Device device, Set<String> unbindIds,
      Consumer<String> progress) {
    String deviceId = requireNonNull(device.toBuilder().getId(), "unspecified device id");
    try {
      ifNotNullThen(unbindIds, ids -> unbindGatewayDevices(registryId, device, ids, progress));
      String location = getRegistryLocation(registryId);
      DeviceName deviceName = DeviceName.of(projectId, location, registryId, deviceId);
      DeleteDeviceRequest request =
          DeleteDeviceRequest.Builder.newBuilder().setName(deviceName).build();
      deviceManager.deleteDevice(request);
      CloudModel cloudModel = new CloudModel();
      cloudModel.operation = DELETE;
      cloudModel.num_id = getReturnReceipt(registryId, device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException(format("While deleting %s/%s", registryId, deviceId), e);
    }
  }

  private void bindDevicesGateways(String registryId, Set<String> gatewayIds,
      Set<String> deviceIds, boolean toBind, Consumer<String> progress) {

    requireNonNull(progress, "bindDevicesGateways has null progress");
    String opCode = toBind ? "Binding" : "Unbinding";
    info("%s %s: gateways %s devices %s", opCode, registryId, gatewayIds, deviceIds);
    AtomicInteger opCount = new AtomicInteger(0);
    progress.accept(format("%s %d devices on %d gateways", opCode, deviceIds.size(),
        gatewayIds.size()));
    gatewayIds.forEach(gatewayId -> {
      deviceIds.forEach(deviceId -> {
        progress.accept(format("%s %s from/to %s", opCode, deviceId, gatewayId));
        ifTrueThen(toBind,
            () -> bindDevice(registryId, gatewayId, deviceId),
            () -> unbindDevice(registryId, gatewayId, deviceId));
      });
    });
    ifTrueThen(opCount.get() > 0, () -> progress.accept(
        format("Completed binding %d devices.", opCount.get())));
  }

  private void unbindDevice(String registryId, String gatewayId, String proxyId) {
    try {
      debug(format("Unbind %s: %s from %s", registryId, proxyId, gatewayId));
      String location = getRegistryLocation(registryId);
      UnbindDeviceFromGatewayRequest request = UnbindDeviceFromGatewayRequest.Builder.newBuilder()
          .setParent(RegistryName.of(projectId, location, registryId).getRegistryFullName())
          .setGateway(gatewayId).setDevice(proxyId).build();
      requireNonNull(deviceManager.unbindDeviceFromGateway(request), "invalid response");
    } catch (Exception e) {
      throw new RuntimeException("While unbinding " + proxyId + " from " + gatewayId, e);
    }
  }

  private boolean unbindGatewayDevices(String registryId, Device gatewayDevice,
      Set<String> unbindIds, Consumer<String> progress) {
    try {
      ImmutableSet<String> gatewayIds = ImmutableSet.of(gatewayDevice.toBuilder().getId());
      bindDevicesGateways(registryId, gatewayIds, unbindIds, false, progress);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private CloudModel updateDevice(String registryId, Device device) {
    CloudModel cloudModel = updateDevice(registryId, device, UPDATE_FIELD_MASK);
    cloudModel.operation = UPDATE;
    return cloudModel;
  }

  private CloudModel updateDevice(String registryId, Device device, String updateFieldMask) {
    String deviceId = device.toBuilder().getId();
    String name = getDeviceName(registryId, deviceId);
    Device fullDevice = device.toBuilder().setName(name).build();
    try {
      UpdateDeviceRequest request =
          UpdateDeviceRequest.Builder.newBuilder().setDevice(fullDevice).setName(name)
              .setUpdateMask(updateFieldMask).build();
      requireNonNull(deviceManager.updateDevice(request), "Invalid RPC response");
      CloudModel cloudModel = new CloudModel();
      cloudModel.num_id = getReturnReceipt(registryId, device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While updating " + deviceId, e);
    }
  }

  @Override
  public void activate() {
    super.activate();
    if (!isEnabled()) {
      warn("ClearBlade access provider disabled");
      return;
    }
    debug("Initializing ClearBlade access provider for project " + projectId);
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    try {
      String location = getRegistryLocation(registryId);
      ListDeviceConfigVersionsRequest request = ListDeviceConfigVersionsRequest.Builder.newBuilder()
          .setName(DeviceName.of(projectId, location, registryId, deviceId)
              .toString()).setNumVersions(1).build();
      ListDeviceConfigVersionsResponse listDeviceConfigVersionsResponse =
          deviceManager.listDeviceConfigVersions(request);
      List<DeviceConfig> deviceConfigs = listDeviceConfigVersionsResponse.getDeviceConfigList();
      if (deviceConfigs.isEmpty()) {
        return new SimpleEntry<>(null, EMPTY_JSON);
      }
      DeviceConfig deviceConfig = deviceConfigs.get(0);
      String config = ifNotNullGet((String) deviceConfig.getBinaryData(),
          binaryData -> new String(Base64.getDecoder().decode(binaryData)));
      return new SimpleEntry<>(Long.parseLong(deviceConfig.getVersion()), config);
    } catch (Exception e) {
      throw new RuntimeException("While fetching device configurations for " + deviceId, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    String devicePath = getDeviceName(registryId, deviceId);
    try {
      String location = getRegistryLocation(registryId);
      DeviceName name = DeviceName.of(projectId, location, registryId, deviceId);
      GetDeviceRequest request = GetDeviceRequest.Builder.newBuilder().setName(name)
          .setFieldMask(FieldMask.newBuilder().build()).build();
      Device device = deviceManager.getDevice(request);
      requireNonNull(device, "GetDeviceRequest failed");
      CloudModel cloudModel = convertFull(device);
      cloudModel.operation = ModelOperation.READ;
      cloudModel.gateway = fetchDeviceGatewayModel(registryId, deviceId, device);
      return cloudModel;
    } catch (Exception e) {
      throw new RuntimeException("While fetching device " + devicePath, e);
    }

  }

  private GatewayModel fetchDeviceGatewayModel(String registryId, String gatewayId, Device device) {
    requireNonNull(gatewayId, "gateway id not specified");
    return ifTrueGet(device.toBuilder().getGatewayConfig().getGatewayType() == GatewayType.GATEWAY,
        () -> listRegistryDevices(registryId, gatewayId, null).gateway);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    try {
      CloudModel cloudModel = fetchDevice(reflectRegistry, registryId);
      return cloudModel.metadata.get(metadataKey);
    } catch (Exception e) {
      debug(format("No device entry for %s/%s", reflectRegistry, registryId));
      return null;
    }
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    String devicePath = getDeviceName(registryId, deviceId);
    try {
      String location = getRegistryLocation(registryId);
      DeviceName name = DeviceName.of(projectId, location, registryId, deviceId);

      ListDeviceStatesRequest request = ListDeviceStatesRequest.Builder.newBuilder()
          .setName(name.toString())
          .setNumStates(1).build();
      ListDeviceStatesResponse response = requireNonNull(
          deviceManager.listDeviceStates(request), "Null response returned");
      List<DeviceState> deviceStatesList = response.getDeviceStatesList();
      return deviceStatesList.isEmpty() ? null : (String) deviceStatesList.get(0).getBinaryData();
    } catch (Exception e) {
      throw new RuntimeException("While fetching state for device " + devicePath, e);
    }
  }

  @NotNull
  @Override
  public Set<String> getRegistriesForRegion(String region) {
    if (region == null) {
      return CLOUD_REGIONS;
    }

    try {
      String parent = LocationName.of(projectId, region).getLocationFullName();
      ListDeviceRegistriesRequest request = ListDeviceRegistriesRequest.Builder.newBuilder()
          .setParent(parent).setFieldMask(REGISTRIES_FIELD_MASK).build();
      ListDeviceRegistriesResponse response = deviceManager.listDeviceRegistries(request);
      requireNonNull(response, "get registries response is null");
      List<DeviceRegistry> deviceRegistries = response.getDeviceRegistriesList();
      Set<String> registries =
          ofNullable(deviceRegistries).orElseGet(ImmutableList::of).stream()
              .map(registry -> registry.toBuilder().getId())
              .collect(Collectors.toSet());
      debug("Fetched " + registries.size() + " registries for region " + region);
      return registries;
    } catch (Exception e) {
      throw new RuntimeException("While fetching registries for region " + region, e);
    }
  }

  @Override
  public boolean isEnabled() {
    return !isNullOrEmpty(projectId);
  }

  @Override
  public CloudModel listDevices(String registryId, Consumer<String> progress) {
    return listRegistryDevices(registryId, null, progress);
  }

  @Override
  public void sendCommandBase(Envelope envelope, SubFolder folder,
      String message) {
    String subFolder = ifNotNullGet(folder, SubFolder::value);
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
    try {
      ByteString binaryData = new ByteString(encodeBase64(message));
      String location = getRegistryLocation(registryId);
      String deviceName = DeviceName.of(projectId, location, registryId, deviceId).toString();
      SendCommandToDeviceRequest request = SendCommandToDeviceRequest.Builder.newBuilder()
          .setName(deviceName)
          .setBinaryData(binaryData)
          .setSubfolder(subFolder)
          .build();
      SendCommandToDeviceResponse response = deviceManager.sendCommandToDevice(request);
      if (response == null) {
        throw new RuntimeException("SendCommandToDevice execution failed for " + deviceName);
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While sending command to ClearBlade %s/%s/%s",
          registryId, deviceId, subFolder), e);
    }
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long version) {
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
    try {
      String updateVersion = ifNotNullGet(version, v -> Long.toString(version));
      ByteString binaryData = new ByteString(encodeBase64(config));
      String location = getRegistryLocation(registryId);
      ModifyCloudToDeviceConfigRequest request =
          ModifyCloudToDeviceConfigRequest.Builder.newBuilder()
              .setName(DeviceName.of(projectId, location, registryId, deviceId).toString())
              .setBinaryData(binaryData).setVersionToUpdate(updateVersion).build();
      DeviceConfig response = deviceManager.modifyCloudToDeviceConfig(request);
      debug("Modified %s/%s config version %s -> %s", registryId, deviceId, updateVersion,
          response.getVersion());
      return config;
    } catch (Exception e) {
      throw new RuntimeException(
          format("While modifying %s/%s config version %s", registryId, deviceId, version), e);
    }
  }

  class Empty {
    // Temp hacky class
  }
}

