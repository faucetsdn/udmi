package com.google.bos.udmi.service.access;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.booleanString;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;
import static com.google.udmi.util.GeneralUtils.requireNull;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.CloudModel.ModelOperation.DELETE;
import static udmi.schema.CloudModel.ModelOperation.READ;
import static udmi.schema.CloudModel.Resource_type.DIRECT;
import static udmi.schema.CloudModel.Resource_type.GATEWAY;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.SimpleMqttPipe;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.ConnectionBroker;
import com.google.bos.udmi.service.support.ConnectionBroker.BrokerEvent;
import com.google.bos.udmi.service.support.ConnectionBroker.Direction;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.bos.udmi.service.support.MosquittoBroker;
import com.google.bos.udmi.service.support.QueueFullException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.GatewayModel;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * Iot Access Provider that uses internal components.
 *
 * <p>Supported options:
 * <ul>
 * <li><code>use_password</code>: Sets the password for all devices to the
 * specified value.
 * This is used when authentication is handled by an external proxy, and
 * Mosquitto
 * still needs to enforce ACLs based on username.</li>
 * <li><code>disable_logging</code>: If set to true, disables tailing the
 * mosquitto log file.</li>
 * </ul>
 */
public class ImplicitIotAccessProvider extends IotAccessBase {

  private static final String CONFIG_VER_KEY = "config_ver";
  private static final String DISABLE_LOGGING_KEY = "disable_logging";
  private static final String USE_PASSWORD_KEY = "use_password";
  private static final String BROKER_USER_KEY = "broker_user";
  private static final String BROKER_PASS_KEY = "broker_pass";
  private static final String BROKER_HOST_KEY = "broker_host";
  private static final String BROKER_PORT_KEY = "broker_port";
  private static final String LAST_CONFIG_KEY = "last_config";
  private static final String LAST_STATE_KEY = "last_state";
  private static final String DEVICES_ACTIVE = "active";
  private static final String BOUND_TO_KEY = "bound_to";
  private static final String BIND_STATUS_KEY = "bind_status";
  private static final String BLOCKED_PROPERTY = "blocked";
  private static final String CREATED_AT_PROPERTY = "created_at";
  private static final String REGISTRIES_KEY = "registries";
  private static final String NUM_ID_PROPERTY = "num_id";
  private static final String IMPLICIT_DATABASE_COMPONENT = "database";
  private static final String CLIENT_ID_FORMAT = "/r/%s/d/%s";
  private static final String CLIENT_PREFIX = "/r";
  private static final String AUTH_PASSWORD_PROPERTY = "auth_pass";
  private static final String AUTH_KEY_PROPERTY = "auth_key";
  private static final String AUTH_TYPE_PROPERTY = "auth_type";
  private static final String LAST_CONFIG_ACKED = "last_config_ack";
  private static final String CONFIG_SUFFIX = "/config";
  private static final String METADATA_STR_KEY = "metadata_str";
  private static final String RESOURCE_TYPE_PROPERTY = "resource_type";
  private static final int DEVICE_FETCH_BATCH_SIZE = 100;
  private static final int MAX_QUEUE_RETRIES = 5;
  private static final long QUEUE_RETRY_DELAY_MS = 1000;
  private final boolean enabled;
  private final String usePassword;
  private final ConnectionBroker broker;
  private final Future<Void> connLogger;
  private IotDataProvider database;

  private final Map<String, Integer> configPublished = new ConcurrentHashMap<>();
  private final String brokerHost;
  private final String brokerPort;
  private final String brokerUser;
  private final String brokerPass;
  private EndpointConfiguration endpointConfig;
  private SimpleMqttPipe mqttPipe;
  private final String clientId =
      format("implicit-access-%08x", (long) (Math.random() * 0x100000000L));

  /**
   * Create an access provider with implicit internal resources.
   */
  public ImplicitIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);

    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
    usePassword = options.get(USE_PASSWORD_KEY);

    if (iotAccess.endpoint != null) {
      endpointConfig = deepCopy(iotAccess.endpoint);
      brokerUser = endpointConfig.auth_provider != null
          && endpointConfig.auth_provider.basic != null
              ? endpointConfig.auth_provider.basic.username
              : null;
      brokerPass = endpointConfig.auth_provider != null
          && endpointConfig.auth_provider.basic != null
              ? endpointConfig.auth_provider.basic.password
              : null;
      brokerHost = ofNullable(endpointConfig.hostname).orElse("localhost");
      brokerPort = ofNullable(endpointConfig.port).map(Object::toString).orElse("8883");
    } else {
      endpointConfig = new EndpointConfiguration();
      brokerUser = options.get(BROKER_USER_KEY);
      brokerPass = options.get(BROKER_PASS_KEY);
      brokerHost = ofNullable(options.get(BROKER_HOST_KEY)).orElse("localhost");
      brokerPort = ofNullable(options.get(BROKER_PORT_KEY)).orElse("8883");

      endpointConfig.hostname = brokerHost;
      endpointConfig.port = Integer.parseInt(brokerPort);
      endpointConfig.transport = "1883".equals(brokerPort) ? Transport.TCP : Transport.SSL;
      endpointConfig.client_id = clientId;

      if (isPublishEnabled()) {
        endpointConfig.auth_provider = new Auth_provider();
        endpointConfig.auth_provider.basic = new Basic();
        endpointConfig.auth_provider.basic.username = brokerUser;
        endpointConfig.auth_provider.basic.password = brokerPass;
      }
    }

    boolean disableLogging = TRUE_OPTION.equals(options.get(DISABLE_LOGGING_KEY));
    broker = new MosquittoBroker(this, endpointConfig, disableLogging);

    connLogger = broker.addEventListener(CLIENT_PREFIX, this::brokerHandler);
  }

  /**
   * Create pseudo device numerical id that can be used for operation
   * verification.
   */
  public static String hashedDeviceId(String registryId, String deviceId) {
    return String.valueOf(Math.abs(Objects.hash(registryId, deviceId)));
  }

  private Set<String> getDeviceIds(CloudModel cloudModel) {
    return cloudModel.gateway != null && cloudModel.gateway.proxy_ids != null
        ? ImmutableSet.copyOf(cloudModel.gateway.proxy_ids)
        : ImmutableSet.of();
  }

  private void bindDevicesToGateway(
      String registryId, String gatewayId, CloudModel cloudModel, Consumer<String> progress) {
    Set<String> deviceIds = getDeviceIds(cloudModel);
    AtomicInteger count = new AtomicInteger();
    int total = deviceIds.size();
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    deviceIds.forEach(deviceId -> {
      int current = count.incrementAndGet();
      if (current % 50 == 0 && progress != null) {
        progress.accept(format("Binding %d/%d devices to %s...", current, total, gatewayId));
      }
      CompletableFuture<Void> bindFuture = withQueueRetry(() -> broker.bindGateway(
          clientId(registryId, gatewayId),
          clientId(registryId, deviceId)));
      CompletableFuture<Void> chainedFuture = bindFuture.whenComplete((result, ex) -> {
        if (ex == null) {
          registryDeviceRef(registryId, deviceId).put(BOUND_TO_KEY, gatewayId);
          registryDeviceRef(registryId, deviceId).put(BIND_STATUS_KEY, "bound");
          gatewayBoundRef(registryId, gatewayId).put(deviceId, "bound");
        } else {
          registryDeviceRef(registryId, deviceId).put(BOUND_TO_KEY, gatewayId);
          registryDeviceRef(registryId, deviceId).put(BIND_STATUS_KEY, "corrupt");
          gatewayBoundRef(registryId, gatewayId).put(deviceId, "corrupt");
        }
      });
      futures.add(chainedFuture);
    });
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void unbindDevicesFromGateway(String registryId, String gatewayId,
      CloudModel cloudModel, Consumer<String> progress) {
    Set<String> deviceIds = getDeviceIds(cloudModel);
    AtomicInteger count = new AtomicInteger();
    int total = deviceIds.size();
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    deviceIds.forEach(deviceId -> {
      int current = count.incrementAndGet();
      if (current % 50 == 0 && progress != null) {
        progress.accept(format("Unbinding %d/%d devices from %s...", current, total, gatewayId));
      }
      registryDeviceRef(registryId, deviceId).delete(BOUND_TO_KEY);
      registryDeviceRef(registryId, deviceId).delete(BIND_STATUS_KEY);
      gatewayBoundRef(registryId, gatewayId).delete(deviceId);
      futures.add(withQueueRetry(() -> broker.unbindGateway(
          clientId(registryId, gatewayId),
          clientId(registryId, deviceId))));
    });
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void blockDevice(String registryId, String deviceId, CloudModel cloudModel) {
    withQueueRetry(() -> broker.authorize(clientId(registryId, deviceId), null)).join();
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
    info("Deleting device %s/%s", registryId, deviceId);
    DataRef properties = registryDeviceRef(registryId, deviceId);

    // If this device is a gateway, unbind all its bound devices first!
    if (GATEWAY.toString().equals(properties.get(RESOURCE_TYPE_PROPERTY))) {
      unbindGatewayDevices(registryId, deviceId);
    }

    String gatewayId = properties.get(BOUND_TO_KEY);
    properties.entries().keySet().forEach(properties::delete);
    registryDevicesRef(registryId).delete(deviceId);
    CompletableFuture<Void> f1 = null;
    if (gatewayId == null) {
      info("Revoking broker credentials/authorization for device %s/%s", registryId, deviceId);
      f1 = withQueueRetry(() -> broker.authorize(clientId(registryId, deviceId), null));
    }

    CompletableFuture<Void> f2 = null;
    if (gatewayId != null) {
      info("Unbinding device %s/%s from gateway %s in database and broker",
          registryId, deviceId, gatewayId);
      gatewayBoundRef(registryId, gatewayId).delete(deviceId);
      f2 = withQueueRetry(() -> broker.unbindGateway(clientId(registryId, gatewayId),
          clientId(registryId, deviceId)));
    }
    if (f1 != null) {
      info("Waiting for broker credential revocation to complete for %s/%s...",
          registryId, deviceId);
      f1.join();
      info("Successfully revoked broker credentials/authorization for device %s/%s",
          registryId, deviceId);
    }
    if (f2 != null) {
      info("Waiting for broker unbind to complete for %s/%s...", registryId, deviceId);
      f2.join();
      info("Successfully unbound device %s/%s from gateway %s in broker",
          registryId, deviceId, gatewayId);
    }
  }

  private void unbindGatewayDevices(String registryId, String gatewayId) {
    try {
      Map<String, CloudModel> boundDevices = listBoundDevices(registryId, gatewayId);
      info("Unbinding %d devices from gateway %s on gateway deletion",
          boundDevices.size(), gatewayId);
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      boundDevices.keySet().forEach(deviceId -> {
        // Clear database entries for the bound device
        registryDeviceRef(registryId, deviceId).delete(BOUND_TO_KEY);
        registryDeviceRef(registryId, deviceId).delete(BIND_STATUS_KEY);
        gatewayBoundRef(registryId, gatewayId).delete(deviceId);

        // Unbind in the broker
        info("Queueing unbind of device %s from gateway %s in broker...", deviceId, gatewayId);
        futures.add(withQueueRetry(() -> broker.unbindGateway(
            clientId(registryId, gatewayId),
            clientId(registryId, deviceId))));
      });
      if (!futures.isEmpty()) {
        info("Waiting for %d unbind operations to complete for gateway %s...",
            futures.size(), gatewayId);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        info("Successfully unbound all %d devices from gateway %s on deletion",
            futures.size(), gatewayId);
      }
    } catch (Exception e) {
      error("Error unbinding devices from gateway %s on deletion: %s",
          gatewayId, friendlyStackTrace(e));
    }
  }

  private CloudModel getReply(String registryId, String deviceId, CloudModel request,
      String deleteId) {
    String numId = deleteId != null ? deleteId
        : registryDeviceRef(registryId, deviceId).get(NUM_ID_PROPERTY);
    CloudModel reply = new CloudModel();
    reply.operation = requireNonNull(request.operation, "missing operation");
    reply.num_id = requireNonNull(numId, "missing num_id");
    return reply;
  }

  private DataRef mungeDevice(String registryId, String deviceId, Map<String, String> map) {
    DataRef properties = registryDeviceRef(registryId, deviceId);
    Map<String, String> puts = map.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    Set<String> deletes = map.entrySet().stream()
        .filter(e -> e.getValue() == null)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    properties.update(puts, deletes);

    if (map.containsKey(AUTH_PASSWORD_PROPERTY)) {
      String password = map.get(AUTH_PASSWORD_PROPERTY);
      boolean isDefaultPass = usePassword != null && usePassword.equals(password);
      boolean hasAuthInfo = map.containsKey(AUTH_KEY_PROPERTY)
          || map.containsKey(AUTH_TYPE_PROPERTY)
          || properties.get(AUTH_KEY_PROPERTY) != null
          || properties.get(AUTH_TYPE_PROPERTY) != null;
      if (hasAuthInfo || !isDefaultPass) {
        withQueueRetry(() -> broker.authorize(clientId(registryId, deviceId), password)).join();
      }
    }
    return properties;
  }

  private DataRef registryDeviceRef(String registryId, String deviceId) {
    return database.ref().registry(registryId).device(deviceId);
  }

  private DataRef registryDevicesRef(String registryId) {
    return database.ref().registry(registryId).collection(DEVICES_ACTIVE);
  }

  private DataRef gatewayBoundRef(String registryId, String gatewayId) {
    return database.ref().registry(registryId).device(gatewayId).collection("bound_devices");
  }

  private void sendConfigUpdate(String registryId, String deviceId, String config) {
    if (isPublishEnabled()) {
      publishMqtt(registryId, deviceId, config);
    } else {
      debug("Skipping MQTT config publish for %s/%s because broker "
          + "credentials are not configured", registryId, deviceId);
    }
  }

  private boolean isPublishEnabled() {
    return brokerUser != null && brokerPass != null;
  }

  private void publishMqtt(String registryId, String deviceId, String payload) {
    if (mqttPipe == null) {
      warn("MQTT pipe not initialized, unable to publish config");
      return;
    }
    try {
      Envelope envelope = new Envelope();
      envelope.deviceRegistryId = registryId;
      envelope.deviceId = deviceId;
      envelope.subType = SubType.CONFIG;
      envelope.source = IotProvider.IMPLICIT.value();

      Bundle bundle = new Bundle(envelope, MessageDispatcher.rawString(payload));
      mqttPipe.publish(bundle);
      debug("Published config to pipe for %s/%s", registryId, deviceId);
    } catch (Exception e) {
      error("While publishing to MQTT pipe for " + registryId + "/" + deviceId
          + ": " + friendlyStackTrace(e));
    }
  }

  private Map<String, String> toDeviceMap(CloudModel cloudModel, String createdAt) {
    Map<String, String> properties = new HashMap<>();
    ifNotNullThen(createdAt, x -> properties.put(CREATED_AT_PROPERTY, createdAt));
    properties.put(RESOURCE_TYPE_PROPERTY,
        ofNullable(cloudModel.resource_type).orElse(DIRECT).toString());
    requireNull(cloudModel.metadata_str, "unexpected metadata_str content");
    properties.put(METADATA_STR_KEY, stringifyTerse(cloudModel.metadata));
    ifNotNullThen(ifNotNullGet(cloudModel.metadata, metadata -> metadata.get("key_bytes")),
        keyBytes -> properties.put(AUTH_KEY_PROPERTY, keyBytes));
    properties.put(BLOCKED_PROPERTY, booleanString(cloudModel.blocked));
    ifNotNullThen(cloudModel.num_id, id -> properties.put(NUM_ID_PROPERTY, id));
    ifNotNullThen(cloudModel.auth_type,
        authType -> properties.put(AUTH_TYPE_PROPERTY, authType.value()));
    ifNotNullThen(cloudModel.credentials, creds -> ifNotTrueThen(creds.isEmpty(), () -> {
      checkState(creds.size() == 1, "only one credential supported");
      Credential cred = creds.get(0);
      checkState(cred.key_format != Key_format.PASSWORD,
          "key type PASSWORD should be in the password field, not credentials");
      properties.put(AUTH_KEY_PROPERTY, cred.key_data);
      properties.put(AUTH_TYPE_PROPERTY, cred.key_format.value());
    }));
    if (cloudModel.password != null) {
      properties.put(AUTH_PASSWORD_PROPERTY, cloudModel.password);
    } else if (usePassword != null) {
      properties.put(AUTH_PASSWORD_PROPERTY, usePassword);
    }
    return properties;
  }

  private String touchDeviceEntry(String registryId, String deviceId) {
    String timestamp = isoConvert();
    registryDevicesRef(registryId).put(deviceId, timestamp);
    return timestamp;
  }

  private void updateDevice(String registryId, String deviceId, CloudModel cloudModel) {
    touchDeviceEntry(registryId, deviceId);
    Map<String, String> map = toDeviceMap(cloudModel, null);
    mungeDevice(registryId, deviceId, map);
  }

  private CompletableFuture<Void> withQueueRetry(Supplier<CompletableFuture<Void>> action) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    withQueueRetryInternal(action, 0, result);
    return result;
  }

  private void withQueueRetryInternal(Supplier<CompletableFuture<Void>> action, int retryCount,
      CompletableFuture<Void> resultFuture) {
    try {
      action.get().whenComplete((res, ex) -> {
        if (ex == null) {
          resultFuture.complete(null);
        } else {
          Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
          if (cause instanceof QueueFullException && retryCount < MAX_QUEUE_RETRIES) {
            long delay = QUEUE_RETRY_DELAY_MS * (long) Math.pow(2, retryCount);
            warn("Queue full, retrying in %dms (retry %d/%d)...", delay, retryCount + 1,
                MAX_QUEUE_RETRIES);
            safeSleep(delay);
            withQueueRetryInternal(action, retryCount + 1, resultFuture);
          } else {
            resultFuture.completeExceptionally(ex);
          }
        }
      });
    } catch (QueueFullException e) {
      if (retryCount < MAX_QUEUE_RETRIES) {
        long delay = QUEUE_RETRY_DELAY_MS * (long) Math.pow(2, retryCount);
        warn("Queue full exception, retrying in %dms (retry %d/%d)...", delay, retryCount + 1,
            MAX_QUEUE_RETRIES);
        safeSleep(delay);
        withQueueRetryInternal(action, retryCount + 1, resultFuture);
      } else {
        resultFuture.completeExceptionally(e);
      }
    } catch (Exception e) {
      resultFuture.completeExceptionally(e);
    }
  }

  @Override
  public void activate() {
    database = UdmiServicePod.getComponent(IMPLICIT_DATABASE_COMPONENT);
    super.activate();
    if (isPublishEnabled()) {
      connectMqttClient();
    }
  }

  private void connectMqttClient() {
    info("Initializing SimpleMqttPipe for ImplicitIotAccessProvider");
    try {

      if (endpointConfig.send_id == null) {
        endpointConfig.send_id = "implicit";
      }

      mqttPipe = new SimpleMqttPipe(endpointConfig);
      info("Initialized SimpleMqttPipe");
    } catch (Exception e) {
      error("Failed to initialize SimpleMqttPipe connecting to broker %s:%s: %s",
          brokerHost, brokerPort, friendlyStackTrace(e));
    }
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
    Map<String, String> properties = registryDeviceRef(registryId, deviceId).entries();
    if (properties == null) {
      return null;
    }
    CloudModel cloudModel = requireNonNull(JsonUtil.convertTo(CloudModel.class, properties));
    cloudModel.metadata = ifNotNullGet(cloudModel.metadata_str, JsonUtil::toStringMapStr);
    cloudModel.metadata_str = null;

    String authType = properties.get(AUTH_TYPE_PROPERTY);
    if (authType != null) {
      cloudModel.auth_type = CloudModel.Auth_type.fromValue(authType);
    }

    String authKey = properties.get(AUTH_KEY_PROPERTY);
    if (authKey != null) {
      Credential credential = new Credential();
      credential.key_data = authKey;
      if (cloudModel.auth_type != null) {
        credential.key_format = Key_format.fromValue(cloudModel.auth_type.value());
      }
      cloudModel.credentials = List.of(credential);
    }

    cloudModel.password = properties.get(AUTH_PASSWORD_PROPERTY);

    if (GATEWAY.toString().equals(properties.get(RESOURCE_TYPE_PROPERTY))) {
      cloudModel.gateway = new GatewayModel();
      cloudModel.gateway.proxy_ids =
          listBoundDevices(registryId, deviceId).keySet().stream().toList();
    }
    cloudModel.operation = READ;
    return cloudModel;
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
  public CloudModel listDevices(String registryId, Consumer<String> progress) {
    Map<String, String> entries = registryDevicesRef(registryId).entries();
    List<String> deviceIds = entries.keySet().stream().toList();
    int total = deviceIds.size();
    ifNotNullThen(progress, p -> p.accept(format("Fetching %d devices...", total)));
    Map<String, CloudModel> deviceIdsMap = new ConcurrentHashMap<>();
    for (int i = 0; i < total; i += DEVICE_FETCH_BATCH_SIZE) {
      List<String> batch = deviceIds.subList(i, Math.min(i + DEVICE_FETCH_BATCH_SIZE, total));
      batch.parallelStream().forEach(id -> {
        CloudModel partial = fetchDevicePartial(registryId, id);
        if (partial != null) {
          deviceIdsMap.put(id, partial);
        }
      });
      int currentCount = Math.min(i + DEVICE_FETCH_BATCH_SIZE, total);
      ifNotNullThen(progress, p -> p.accept(format("Fetched %d devices...", currentCount)));
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.device_ids = deviceIdsMap;
    cloudModel.operation = READ;
    return cloudModel;
  }

  private CloudModel fetchDevicePartial(String registryId, String deviceId) {
    Map<String, String> properties = registryDeviceRef(registryId, deviceId).entries();
    if (properties == null) {
      return null;
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.num_id = properties.get(NUM_ID_PROPERTY);
    String authType = properties.get(AUTH_TYPE_PROPERTY);
    if (authType != null) {
      cloudModel.auth_type = CloudModel.Auth_type.fromValue(authType);
    }
    cloudModel.resource_type = ofNullable(properties.get(RESOURCE_TYPE_PROPERTY))
        .map(Resource_type::fromValue).orElse(DIRECT);
    cloudModel.blocked = "true".equals(properties.get(BLOCKED_PROPERTY)) ? true : null;
    cloudModel.updated_time = JsonUtil.getDate(properties.get(CREATED_AT_PROPERTY));
    return cloudModel;
  }

  private Map<String, CloudModel> listBoundDevices(String registryId, String gatewayId) {
    Set<String> deviceIds = gatewayBoundRef(registryId, gatewayId).entries().keySet();
    Map<String, CloudModel> devices = deviceIds.stream().filter(deviceId -> {
      DataRef deviceRef = registryDeviceRef(registryId, deviceId);
      String boundTo = deviceRef.get(BOUND_TO_KEY);
      String bindStatus = deviceRef.get(BIND_STATUS_KEY);
      return gatewayId.equals(boundTo) && "bound".equals(bindStatus);
    }).collect(Collectors.toMap(id -> id, id -> fetchDevice(registryId, id)));
    List<CloudModel> gateways = devices.values().stream()
        .filter(model -> GATEWAY.equals(model.resource_type)).toList();
    checkState(gateways.isEmpty(),
        format("Gateways found in gateway lookup of %s: %s", gatewayId,
            CSV_JOINER.join(gateways)));
    return devices;
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel,
      Consumer<String> progress) {
    ModelOperation operation = cloudModel.operation;
    Resource_type type = ofNullable(cloudModel.resource_type).orElse(Resource_type.DIRECT);
    checkState(type == DIRECT || type == GATEWAY, "unexpected resource type " + type);
    info("Processing modelDevice %s for %s/%s (type: %s)", operation, registryId, deviceId, type);
    try {
      String deleteNumId = operation != DELETE ? null
          : registryDeviceRef(registryId, deviceId).get(NUM_ID_PROPERTY);
      switch (operation) {
        case CREATE -> createDevice(registryId, deviceId, cloudModel);
        case UPDATE -> updateDevice(registryId, deviceId, cloudModel);
        case DELETE -> deleteDevice(registryId, deviceId, cloudModel);
        case MODIFY -> modifyDevice(registryId, deviceId, cloudModel);
        case BIND -> bindDevicesToGateway(registryId, deviceId, cloudModel, progress);
        case UNBIND -> unbindDevicesFromGateway(registryId, deviceId, cloudModel, progress);
        case BLOCK -> blockDevice(registryId, deviceId, cloudModel);
        default -> throw new RuntimeException("Unknown device operation " + operation);
      }
      info("Completed modelDevice %s for %s/%s", operation, registryId, deviceId);
      return getReply(registryId, deviceId, cloudModel, deleteNumId);
    } catch (Exception e) {
      error("Error during modelDevice %s for %s/%s: %s",
          operation, registryId, deviceId, friendlyStackTrace(e));
      throw new RuntimeException(format("While %sing %s/%s", operation, registryId, deviceId), e);
    }
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    ModelOperation operation = cloudModel.operation;
    try {
      // TODO: Make this update the saved metadata for the registry.
      return getReply(registryId, deviceId, cloudModel, "registry");
    } catch (Exception e) {
      throw new RuntimeException("While " + operation + "ing registry " + registryId, e);
    }
  }

  private void modifyDevice(String registryId, String deviceId, CloudModel cloudModel) {
    CloudModel fetchedModel = fetchDevice(registryId, deviceId);
    Map<String, String> metadataMap = ofNullable(fetchedModel.metadata).orElseGet(HashMap::new);
    metadataMap.putAll(cloudModel.metadata);
    mungeDevice(registryId, deviceId, ImmutableMap.of(METADATA_STR_KEY, stringify(metadataMap)));
  }

  @Override
  public void saveState(String registryId, String deviceId, String stateBlob) {
    DataRef dataRef = registryDeviceRef(registryId, deviceId);
    try (AutoCloseable lock = dataRef.lock()) {
      String existingState = dataRef.get(LAST_STATE_KEY);
      if (existingState != null) {
        StateUpdate existing = JsonUtil.fromString(StateUpdate.class, existingState);
        StateUpdate incoming = JsonUtil.fromString(StateUpdate.class, stateBlob);
        Date existingTime = cleanDate(existing.timestamp);
        Date incomingTime = cleanDate(incoming.timestamp);
        if (existingTime != null && incomingTime != null && incomingTime.before(existingTime)) {
          info("Skipping out-of-order state update for %s/%s: existing %s vs incoming %s",
              registryId, deviceId, isoConvert(existingTime), isoConvert(incomingTime));
          return;
        }
      }
      dataRef.put(LAST_STATE_KEY, stateBlob);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While saving state for %s/%s", registryId, deviceId), e);
    }
  }

  @Override
  public void sendCommandBase(Envelope baseEnvelope, SubFolder folder, String message) {
    if (mqttPipe == null) {
      warn("MQTT pipe not initialized, unable to send command");
      return;
    }
    try {
      Envelope envelope = deepCopy(baseEnvelope);
      envelope.subFolder = folder;
      envelope.subType = SubType.COMMANDS;
      envelope.source = IotProvider.IMPLICIT.value();

      Bundle bundle = new Bundle(envelope, MessageDispatcher.rawString(message));
      mqttPipe.publish(bundle);
      debug("Published command to pipe for %s/%s",
          baseEnvelope.deviceRegistryId, baseEnvelope.deviceId);
    } catch (Exception e) {
      error("Failed to send command for %s/%s: %s",
          baseEnvelope.deviceRegistryId, baseEnvelope.deviceId, friendlyStackTrace(e));
      throw new RuntimeException("While sending command for "
          + baseEnvelope.deviceRegistryId + "/" + baseEnvelope.deviceId, e);
    }
  }

  @Override
  public void shutdown() {
    ifNotNullThen(mqttPipe, pipe -> {
      try {
        pipe.shutdown();
      } catch (Exception e) {
        warn("Error shutting down MQTT pipe: " + e.getMessage());
      }
    });
    connLogger.cancel(true);
    broker.shutdown();
    super.shutdown();
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long prevVersion) {
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
    DataRef dataRef = registryDeviceRef(registryId, deviceId);
    try (AutoCloseable lock = dataRef.lock()) {
      String prev = dataRef.get(CONFIG_VER_KEY);
      if (prevVersion != null && !prevVersion.toString().equals(prev)) {
        throw new RuntimeException("Config version update mismatch");
      }

      String update = ofNullable(prevVersion).map(v -> v + 1)
          .orElseGet(() -> ofNullable(prev).map(Long::parseLong).orElse(1L)).toString();

      Map<String, String> puts = Map.of(
          LAST_CONFIG_KEY, config,
          CONFIG_VER_KEY, update
      );

      boolean success = dataRef.updateIfMatch(CONFIG_VER_KEY, prev, puts, null);
      if (!success) {
        throw new RuntimeException("Concurrent modification of config version detected");
      }

      info("Updated config %s #%s to #%s", dataRef, prev, update);

      String initialAck = dataRef.get(LAST_CONFIG_ACKED);
      sendConfigUpdate(registryId, deviceId, config);

      long timeoutMs = 10000;
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < timeoutMs) {
        String currentAck = dataRef.get(LAST_CONFIG_ACKED);
        if (!Objects.equals(initialAck, currentAck)) {
          debug("Config update #%s acknowledged for %s/%s", update, registryId, deviceId);
          return config;
        }
        safeSleep(100);
      }
      throw new RuntimeException(
          "Timed out waiting for config ACK for " + registryId + "/" + deviceId);
    } catch (Exception e) {
      throw new RuntimeException(
          format("While updating config for %s/%s", registryId, deviceId), e);
    }
  }

}
