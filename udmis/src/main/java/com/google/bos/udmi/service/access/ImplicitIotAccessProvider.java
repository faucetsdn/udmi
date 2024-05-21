package com.google.bos.udmi.service.access;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.CloudModel.Operation.BIND;
import static udmi.schema.CloudModel.Operation.CREATE;
import static udmi.schema.CloudModel.Resource_type.DEVICE;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;
import static udmi.schema.CloudModel.Resource_type.REGISTRY;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.IotAccess;

/**
 * Iot Access Provider that uses internal components.
 */
public class ImplicitIotAccessProvider extends IotAccessBase {

  public static final String CONFIG_VER_KEY = "config_ver";
  public static final String LAST_CONFIG_KEY = "last_config";
  public static final String DEVICES_COLLECTION = "devices";
  private static final String REGISTRIES_KEY = "registries";
  private static final String IMPLICIT_DATABASE_COMPONENT = "database";
  private final boolean enabled;
  private IotDataProvider database;
  private ReflectProcessor reflect;

  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
  }

  private CloudModel bindDevicesToGateway(String registryId, String deviceId, CloudModel model) {
    CloudModel reply = new CloudModel();
    reply.operation = BIND;
    return reply;
  }

  private CloudModel blockDevice(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("blockDevice not yet implemented");
  }

  private CloudModel createDevice(String registryId, String deviceId, CloudModel cloudModel) {
    Map<String, String> map = toDeviceMap(cloudModel);
    DataRef devices = database.ref().registry(registryId).device(deviceId);
    Set<String> existing = devices.entries().keySet();
    map.forEach(devices::put);
    existing.stream().filter(not(map::containsKey)).forEach(devices::delete);
    CloudModel reply = new CloudModel();
    reply.operation = CREATE;
    return reply;
  }

  private CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    Operation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DEVICE);
    checkState(type == DEVICE || type == GATEWAY, "unexpected resource type " + type);
    try {
      return switch (operation) {
        case CREATE -> createDevice(registryId, deviceId, cloudModel);
        case UPDATE -> updateDevice(registryId, deviceId, cloudModel);
        case MODIFY -> modifyDevice(registryId, deviceId, cloudModel);
        case DELETE -> unbindAndDelete(registryId, deviceId, cloudModel);
        case BIND -> bindDevicesToGateway(registryId, deviceId, cloudModel);
        case BLOCK -> blockDevice(registryId, deviceId, cloudModel);
        default -> throw new RuntimeException("Unknown device operation " + operation);
      };
    } catch (Exception e) {
      throw new RuntimeException(format("While %sing %s/%s", operation, registryId, deviceId), e);
    }
  }

  private CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modelRegistry not yet implemented");
  }

  private CloudModel modifyDevice(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modifyDevice not yet implemented");
  }

  private Map<String, String> toDeviceMap(CloudModel cloudModel) {
    return ImmutableMap.of("created_at", isoConvert());
  }

  private CloudModel unbindAndDelete(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("unbindAndDelete not yet implemented");
  }

  private CloudModel updateDevice(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("updateDevice not yet implemented");
  }

  @Override
  public void activate() {
    database = UdmiServicePod.getComponent(IMPLICIT_DATABASE_COMPONENT);
    reflect = UdmiServicePod.getComponent(ReflectProcessor.class);
    super.activate();
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    // TODO: Implement lease for atomic transaction.
    String config = database.ref().registry(registryId).device(deviceId).get(LAST_CONFIG_KEY);
    String version = database.ref().registry(registryId).device(deviceId).get(CONFIG_VER_KEY);
    Long versionLong = ofNullable(version).map(Long::parseLong).orElse(null);
    return new SimpleEntry<>(versionLong, ofNullable(config).orElse(EMPTY_JSON));
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    Map<String, String> entries =
        database.ref().registry(deviceRegistryId).device(deviceId).entries();
    return JsonUtil.convertTo(CloudModel.class, entries);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return database.ref().registry(registryId).get(metadataKey);
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("fetchState not yet implemented");
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    if (region == null) {
      return ImmutableSet.of(DEFAULT_REGION);
    }
    if (!region.equals(DEFAULT_REGION)) {
      return ImmutableSet.of();
    }
    String regionsString = ofNullable(database.ref().get(REGISTRIES_KEY)).orElse("");
    return Arrays.stream(regionsString.split(",")).map(String::trim)
        .filter(GeneralUtils::isNotEmpty).collect(Collectors.toSet());
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public CloudModel listDevices(String registryId) {
    Map<String, String> entries = database.ref().registry(registryId).collection(
        DEVICES_COLLECTION).entries();
    CloudModel cloudModel = new CloudModel();
    cloudModel.device_ids = entries.keySet().stream().collect(
        Collectors.toMap(id -> id, id -> fetchDevice(registryId, id)));
    return cloudModel;
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    return modelRegistry(registryId, deviceId, cloudModel)
  }

  @Override
  public CloudModel modelRegistry(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    return modelDevice(registryId, deviceId, cloudModel);

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subFolder = folder;
    envelope.subType = SubType.COMMANDS;
    reflect.getDispatcher().withEnvelope(envelope).publish(asMap(message));
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long version) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subType = SubType.CONFIG;
    reflect.getDispatcher().withEnvelope(envelope).publish(asMap(config));

    // TODO: Implement lease for atomic transaction.
    String prev = database.ref().registry(registryId).device(deviceId).get(CONFIG_VER_KEY);
    if (version != null && !version.toString().equals(prev)) {
      throw new RuntimeException("Config version update mismatch");
    }

    database.ref().registry(registryId).device(deviceId).put(LAST_CONFIG_KEY, config);
    String update = ofNullable(version).map(v -> v + 1)
        .orElseGet(() -> ofNullable(prev).map(Long::parseLong).orElse(1L)).toString();
    database.ref().registry(registryId).device(deviceId).put(CONFIG_VER_KEY, update);

    return config;
  }
}
