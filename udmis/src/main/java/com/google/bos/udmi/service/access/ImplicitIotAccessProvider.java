package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.MessageDispatcher.rawString;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.GeneralUtils.booleanString;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.CloudModel.Operation.DELETE;
import static udmi.schema.CloudModel.Resource_type.DEVICE;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.ConnectionBroker;
import com.google.bos.udmi.service.support.ConnectionBroker.BrokerEvent;
import com.google.bos.udmi.service.support.ConnectionBroker.Direction;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.bos.udmi.service.support.MosquittoBroker;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Iot Access Provider that uses internal components.
 */
public class ImplicitIotAccessProvider extends IotAccessBase {

  private static final String CONFIG_VER_KEY = "config_ver";
  private static final String LAST_CONFIG_KEY = "last_config";
  private static final String LAST_STATE_KEY = "last_state";
  private static final String DEVICES_COLLECTION = "devices";
  private static final String BLOCKED_PROPERTY = "blocked";
  private static final String CREATED_AT_PROPERTY = "created_at";
  private static final String REGISTRIES_KEY = "registries";
  private static final String NUM_ID_PROPERTY = "num_id";
  private static final String IMPLICIT_DATABASE_COMPONENT = "database";
  private static final String CLIENT_ID_FORMAT = "/r/%s/d/%s";
  private static final String CLIENT_PREFIX = "/r";
  private static final String AUTH_PASSWORD_PROPERTY = "auth_pass";
  private static final String LAST_CONFIG_ACKED = "last_config_ack";
  private static final String CONFIG_SUFFIX = "/config";
  private final boolean enabled;
  private final ConnectionBroker broker = new MosquittoBroker(this);
  private final Future<Void> connLogger;
  private IotDataProvider database;
  private ReflectProcessor reflect;
  private Map<String, Integer> configPublished = new ConcurrentHashMap<>();

  /**
   * Create an access provider with implicit internal resources.
   */
  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
    connLogger = broker.addEventListener(CLIENT_PREFIX, this::brokerHandler);
  }

  /**
   * Create pseudo device numerical id that can be used for operation verification.
   */
  private static String hashedDeviceId(String registryId, String deviceId) {
    return String.valueOf(Math.abs(Objects.hash(registryId, deviceId)));
  }

  private void bindDevice(String registryId, String deviceId, CloudModel model) {
    // Binding is a nop, so silently do nothing!
  }

  private void blockDevice(String registryId, String deviceId, CloudModel cloudModel) {
    broker.authorize(clientId(registryId, deviceId), null);
    registryDeviceRef(registryId, deviceId).put(BLOCKED_PROPERTY, booleanString(true));
  }

  private void brokerHandler(BrokerEvent event) {
    try {
      if (event.direction == Direction.Sending
          && event.operation == ConnectionBroker.Operation.PUBLISH
          && event.detail.endsWith(CONFIG_SUFFIX)) {
        configPublished.put(event.clientId, event.mesageId);
      } else if (event.direction == Direction.Received
          && event.operation == ConnectionBroker.Operation.PUBACK) {
        Integer integer = configPublished.remove(event.clientId);
        if (integer != null && integer.equals(event.mesageId)) {
          String[] parts = event.clientId.split("/");
          updateLastConfigAcked(parts[2], parts[4], isoConvert(event.timestamp));
        }
      }
    } catch (Exception e) {
      error("Exception processing broker event: " + friendlyStackTrace(e));
    }
  }

  private void updateLastConfigAcked(String registryId, String deviceId, String timestamp) {
    debug("Updating last_config_acked of %s/%s to %s", registryId, deviceId, timestamp);
    registryDeviceRef(registryId, deviceId).put(LAST_CONFIG_ACKED, timestamp);
  }

  private String clientId(String registryId, String deviceId) {
    return format(CLIENT_ID_FORMAT, registryId, deviceId);
  }

  private void createDevice(String registryId, String deviceId, CloudModel cloudModel) {
    String timestamp = touchDeviceEntry(registryId, deviceId);

    ifNullThen(cloudModel.num_id, () -> cloudModel.num_id = hashedDeviceId(registryId, deviceId));

    Map<String, String> map = toDeviceMap(cloudModel, timestamp);
    DataRef props = mungeDevice(registryId, deviceId, map);
    props.entries().keySet().stream().filter(not(map::containsKey)).forEach(props::delete);
  }

  private void deleteDevice(String registryId, String deviceId, CloudModel cloudModel) {
    DataRef properties = registryDeviceRef(registryId, deviceId);
    properties.entries().keySet().forEach(properties::delete);
    registryDevicesCollection(registryId).delete(deviceId);
    broker.authorize(clientId(registryId, deviceId), null);
  }

  private CloudModel getReply(String registryId, String deviceId, CloudModel request,
      String deleteId) {
    String numId =
        deleteId != null ? deleteId : registryDeviceRef(registryId, deviceId).get(NUM_ID_PROPERTY);
    CloudModel reply = new CloudModel();
    reply.operation = requireNonNull(request.operation, "missing operation");
    reply.num_id = requireNonNull(numId, "missing num_id");
    return reply;
  }

  private DataRef mungeDevice(String registryId, String deviceId, Map<String, String> map) {
    DataRef properties = registryDeviceRef(registryId, deviceId);
    map.forEach((key, value) ->
        ifNotNullThen(value, v -> properties.put(key, value), () -> properties.delete(key)));

    if (map.containsKey(AUTH_PASSWORD_PROPERTY)) {
      broker.authorize(clientId(registryId, deviceId), map.get(AUTH_PASSWORD_PROPERTY));
    }
    return properties;
  }

  private DataRef registryDeviceRef(String registryId, String deviceId) {
    return database.ref().registry(registryId).device(deviceId);
  }

  private DataRef registryDevicesCollection(String registryId) {
    return database.ref().registry(registryId).collection(
        DEVICES_COLLECTION);
  }

  private void sendConfigUpdate(String registryId, String deviceId, String config) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subType = SubType.CONFIG;
    envelope.source = IotProvider.IMPLICIT.value();
    reflect.getDispatcher().withEnvelope(envelope).publish(rawString(config));
  }

  private Map<String, String> toDeviceMap(CloudModel cloudModel, String createdAt) {
    Map<String, String> properties = new HashMap<>();
    ifNotNullThen(createdAt, x -> properties.put(CREATED_AT_PROPERTY, createdAt));
    properties.put(BLOCKED_PROPERTY, booleanString(cloudModel.blocked));
    ifNotNullThen(cloudModel.num_id, id -> properties.put(NUM_ID_PROPERTY, id));
    ifTrueThen(!cloudModel.credentials.isEmpty(), () -> {
      checkState(cloudModel.credentials.size() == 1, "only one credential supported");
      Credential cred = cloudModel.credentials.get(0);
      checkState(cred.key_format == Key_format.PASSWORD,
          "key type not supported: " + cred.key_format);
      properties.put(AUTH_PASSWORD_PROPERTY, cred.key_data);
    });
    return properties;
  }

  private String touchDeviceEntry(String registryId, String deviceId) {
    String timestamp = isoConvert();
    registryDevicesCollection(registryId).put(deviceId, timestamp);
    return timestamp;
  }

  private void updateDevice(String registryId, String deviceId, CloudModel cloudModel) {
    touchDeviceEntry(registryId, deviceId);
    Map<String, String> map = toDeviceMap(cloudModel, null);
    mungeDevice(registryId, deviceId, map);
  }

  @Override
  public void activate() {
    database = UdmiServicePod.getComponent(IMPLICIT_DATABASE_COMPONENT);
    reflect = UdmiServicePod.getComponent(ReflectProcessor.class);
    super.activate();
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    DataRef dataRef = registryDeviceRef(registryId, deviceId);
    try (AutoCloseable locked = dataRef.lock()) {
      String config = dataRef.get(LAST_CONFIG_KEY);
      String version = dataRef.get(CONFIG_VER_KEY);
      info("Fetched config %s #%s", dataRef, version);
      Long versionLong = ofNullable(version).map(Long::parseLong).orElse(null);
      return new SimpleEntry<>(versionLong, ofNullable(config).orElse(EMPTY_JSON));
    } catch (Exception e) {
      throw new RuntimeException(
          format("While handling fetchConfig %s/%s", registryId, deviceId), e);
    }
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    touchDeviceEntry(registryId, deviceId);
    Map<String, String> properties = registryDeviceRef(registryId, deviceId).entries();
    return JsonUtil.convertTo(CloudModel.class, properties);
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    return database.ref().registry(registryId).get(metadataKey);
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    return registryDeviceRef(registryId, deviceId).get(LAST_STATE_KEY);
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
  public CloudModel listDevices(String registryId, Consumer<Integer> progress) {
    Map<String, String> entries = registryDevicesCollection(registryId).entries();
    ifNotNullThen(progress, p -> p.accept(entries.size()));
    CloudModel cloudModel = new CloudModel();
    cloudModel.device_ids = entries.keySet().stream().collect(
        Collectors.toMap(id -> id, id -> fetchDevice(registryId, id)));
    return cloudModel;
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    Operation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DEVICE);
    checkState(type == DEVICE || type == GATEWAY, "unexpected resource type " + type);
    try {
      String deleteNumId =
          operation != DELETE ? null : registryDeviceRef(registryId, deviceId).get(NUM_ID_PROPERTY);
      switch (operation) {
        case CREATE -> createDevice(registryId, deviceId, cloudModel);
        case UPDATE -> updateDevice(registryId, deviceId, cloudModel);
        case MODIFY -> modifyDevice(registryId, deviceId, cloudModel);
        case DELETE -> deleteDevice(registryId, deviceId, cloudModel);
        case BIND -> bindDevice(registryId, deviceId, cloudModel);
        case BLOCK -> blockDevice(registryId, deviceId, cloudModel);
        default -> throw new RuntimeException("Unknown device operation " + operation);
      }
      return getReply(registryId, deviceId, cloudModel, deleteNumId);
    } catch (Exception e) {
      throw new RuntimeException(format("While %sing %s/%s", operation, registryId, deviceId), e);
    }
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    Operation operation = cloudModel.operation;
    try {
      // TODO: Make this update the saved metadata for the registry.
      return getReply(registryId, deviceId, cloudModel, "registry");
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing registry " + registryId, e);
    }
  }

  public CloudModel modifyDevice(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modifyDevice not yet implemented");
  }

  @Override
  public void saveState(String registryId, String deviceId, String stateBlob) {
    registryDeviceRef(registryId, deviceId).put(LAST_STATE_KEY, stateBlob);
  }

  @Override
  public void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    envelope.subFolder = folder;
    envelope.subType = SubType.COMMANDS;
    envelope.source = IotProvider.IMPLICIT.value();
    reflect.getDispatcher().withEnvelope(envelope).publish(asMap(message));
  }

  @Override
  public void shutdown() {
    connLogger.cancel(true);
    super.shutdown();
  }

  @Override
  public String updateConfig(String registryId, String deviceId, String config, Long prevVersion) {
    DataRef dataRef = registryDeviceRef(registryId, deviceId);
    try (AutoCloseable dataLock = dataRef.lock()) {
      String prev = dataRef.get(CONFIG_VER_KEY);
      if (prevVersion != null && !prevVersion.toString().equals(prev)) {
        throw new RuntimeException("Config version update mismatch");
      }

      dataRef.put(LAST_CONFIG_KEY, config);
      String update = ofNullable(prevVersion).map(v -> v + 1)
          .orElseGet(() -> ofNullable(prev).map(Long::parseLong).orElse(1L)).toString();
      dataRef.put(CONFIG_VER_KEY, update);
      info("Updated config %s #%s to #%s", dataRef, prev, update);

      sendConfigUpdate(registryId, deviceId, config);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While updating config for %s/%s", registryId, deviceId), e);
    }

    return config;
  }

}
