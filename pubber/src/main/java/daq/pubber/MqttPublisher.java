package daq.pubber;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.ClientInfo;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.http.ConnectionClosedException;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Jwt;
import udmi.schema.PubberConfiguration;

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 */
public class MqttPublisher implements Publisher {

  private static final String TOPIC_PREFIX_FMT = "/devices/%s";
  private static final Logger LOG = LoggerFactory.getLogger(MqttPublisher.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  // Indicate if this message should be a MQTT 'retained' message.
  private static final boolean SHOULD_RETAIN = false;
  private static final String UNUSED_ACCOUNT_NAME = "unused";
  private static final int INITIALIZE_TIME_MS = 20000;
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final int PUBLISH_THREAD_COUNT = 10;
  private static final String HANDLER_KEY_FORMAT = "%s/%s";
  private static final int TOKEN_EXPIRY_MINUTES = 60;
  private static final int QOS_AT_MOST_ONCE = 0;
  private static final int QOS_AT_LEAST_ONCE = 1;
  private static final int DEFAULT_CONFIG_WAIT_SEC = 10;
  private static final String EVENT_MARK_PREFIX = "events/";
  private static final Map<String, AtomicInteger> EVENT_SERIAL = new HashMap<>();

  private final Semaphore connectionLock = new Semaphore(1);

  private final Map<String, MqttClient> mqttClients = new ConcurrentHashMap<>();
  private final Map<String, Instant> reauthTimes = new ConcurrentHashMap<>();
  private final Map<String, String> topicPrefixMap = new HashMap<>();

  private final ExecutorService publisherExecutor =
      Executors.newFixedThreadPool(PUBLISH_THREAD_COUNT);

  private final PubberConfiguration configuration;
  private final String registryId;
  private final String projectId;
  private final String cloudRegion;

  private final AtomicInteger publishCounter = new AtomicInteger(0);
  private final AtomicInteger errorCounter = new AtomicInteger(0);
  private final Map<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
  private final Map<String, Class<Object>> handlersType = new ConcurrentHashMap<>();
  private final Consumer<Exception> onError;
  private CountDownLatch gatewayLatch;

  MqttPublisher(PubberConfiguration configuration, Consumer<Exception> onError) {
    this.configuration = configuration;
    if (isGcpIotCore(configuration)) {
      ClientInfo clientIdParts = SiteModel.parseClientId(configuration.endpoint.client_id);
      this.projectId = clientIdParts.projectId;
      this.cloudRegion = clientIdParts.cloudRegion;
      this.registryId = clientIdParts.registryId;
    } else {
      this.projectId = null;
      this.cloudRegion = null;
      this.registryId = null;
    }
    this.onError = onError;
    validateCloudIotOptions();
  }

  private boolean isGcpIotCore(PubberConfiguration configuration) {
    return configuration.endpoint.auth_provider == null
        || configuration.endpoint.auth_provider.jwt != null;
  }

  private String getClientId(String deviceId) {
    // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
    // Google Cloud IoT, it must be in the format below.
    if (isGcpIotCore(configuration)) {
      ClientInfo clientInfo = SiteModel.parseClientId(configuration.endpoint.client_id);
      return SiteModel.getClientId(clientInfo.projectId, clientInfo.cloudRegion,
          clientInfo.registryId, deviceId);
    } else if (configuration.endpoint.client_id != null) {
      return configuration.endpoint.client_id;
    }
    return SiteModel.getClientId(projectId, cloudRegion, registryId, deviceId);
  }

  @Override
  public boolean isActive() {
    return !publisherExecutor.isShutdown();
  }

  @Override
  public void publish(String deviceId, String topicSuffix, Object data, Runnable callback) {
    Preconditions.checkNotNull(deviceId, "publish deviceId");
    debug("Publishing in background " + topicSuffix);
    Object marked =
        topicSuffix.startsWith(EVENT_MARK_PREFIX) ? decorateMessage(topicSuffix, data) : data;
    try {
      publisherExecutor.submit(() -> publishCore(deviceId, topicSuffix, marked, callback));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to topic suffix " + topicSuffix, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Object decorateMessage(String topic, Object data) {
    try {
      Map<String, Object> mapped = OBJECT_MAPPER.convertValue(data, Map.class);
      String timestamp = (String) mapped.get("timestamp");
      int serialNo = EVENT_SERIAL
          .computeIfAbsent(topic, key -> new AtomicInteger()).incrementAndGet();
      mapped.put("timestamp", timestamp.replace("Z", String.format(".%03dZ", serialNo % 1000)));
      return mapped;
    } catch (Exception e) {
      throw new RuntimeException("While decorating message", e);
    }
  }

  @Override
  public void setDeviceTopicPrefix(String deviceId, String topicPrefix) {
    topicPrefixMap.put(deviceId, topicPrefix);
  }

  private String getMessageTopic(String deviceId, String topic) {
    return
        topicPrefixMap.computeIfAbsent(deviceId, key -> String.format(TOPIC_PREFIX_FMT, deviceId))
            + "/" + topic;
  }

  private void publishCore(String deviceId, String topicSuffix, Object data,
      Runnable callback) {
    try {
      String payload = OBJECT_MAPPER.writeValueAsString(data);
      debug("Sending message to " + topicSuffix);
      sendMessage(deviceId, getMessageTopic(deviceId, topicSuffix), payload.getBytes());
      if (callback != null) {
        callback.run();
      }
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      warn(String.format("Publish failed for %s: %s", deviceId, e));
      if (configuration.gatewayId == null) {
        closeMqttClient(deviceId);
        if (mqttClients.isEmpty()) {
          warn("Last client closed, shutting down connection.");
          close();
        }
      } else {
        close();
      }
    }
  }

  private void closeMqttClient(String deviceId) {
    MqttClient removed = mqttClients.remove(deviceId);
    if (removed != null) {
      try {
        if (removed.isConnected()) {
          removed.disconnect();
        }
        removed.close();
      } catch (Exception e) {
        error("Error closing MQTT client: " + e, null, "stop", e);
      }
    }
  }

  @Override
  public void close() {
    try {
      warn("Closing publisher connection");
      publisherExecutor.shutdown();
      mqttClients.keySet().forEach(this::closeMqttClient);
    } catch (Exception e) {
      error("While closing publisher", null, "close", e);
    }
  }

  private void validateCloudIotOptions() {
    try {
      checkNotNull(configuration.endpoint.hostname, "endpoint hostname");
      checkNotNull(configuration.endpoint.port, "endpoint port");
      checkNotNull(configuration.endpoint.client_id, "endpoint client_id");
      checkNotNull(configuration.keyBytes, "keyBytes");
      checkNotNull(configuration.algorithm, "algorithm");
    } catch (Exception e) {
      throw new IllegalStateException("Invalid Cloud IoT Options", e);
    }
  }

  private MqttClient newBoundClient(String deviceId) {
    try {
      String gatewayId = configuration.gatewayId;
      debug("Connecting through gateway " + gatewayId);
      gatewayLatch = new CountDownLatch(1);
      MqttClient mqttClient = getConnectedClient(gatewayId);
      startupLatchWait(gatewayLatch, "gateway startup exchange");
      String topic = getMessageTopic(deviceId, MqttDevice.ATTACH_TOPIC);
      String payload = "";
      info("Publishing attach message " + topic);
      mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8.name()), QOS_AT_LEAST_ONCE,
          SHOULD_RETAIN);
      subscribeToUpdates(mqttClient, deviceId);
      return mqttClient;
    } catch (Exception e) {
      throw new RuntimeException("While binding client " + deviceId, e);
    }
  }

  @Override
  public void startupLatchWait(CountDownLatch gatewayLatch, String designator) {
    try {
      int waitTimeSec = Optional.ofNullable(configuration.endpoint.config_sync_sec)
          .orElse(DEFAULT_CONFIG_WAIT_SEC);
      int useWaitTime = waitTimeSec == 0 ? DEFAULT_CONFIG_WAIT_SEC : waitTimeSec;
      if (useWaitTime > 0 && !gatewayLatch.await(useWaitTime, TimeUnit.SECONDS)) {
        throw new RuntimeException("Latch timeout " + designator);
      }
    } catch (Exception e) {
      throw new RuntimeException("While waiting for " + designator, e);
    }
  }

  private MqttClient newMqttClient(String deviceId) {
    try {
      Preconditions.checkNotNull(deviceId, "deviceId is null");
      String clientId = getClientId(deviceId);
      String brokerUrl = getBrokerUrl();
      MqttClient mqttClient = getMqttClient(clientId, brokerUrl);
      info("Creating new client to " + brokerUrl + " as " + clientId);
      return mqttClient;
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      throw new RuntimeException("Creating new MQTT client " + deviceId, e);
    }
  }

  MqttClient getMqttClient(String clientId, String brokerUrl) throws MqttException {
    return new MqttClient(brokerUrl, clientId, new MemoryPersistence());
  }

  private MqttClient connectMqttClient(String deviceId) {
    try {
      if (!connectionLock.tryAcquire(INITIALIZE_TIME_MS, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Timeout waiting for connection lock");
      }
      MqttClient mqttClient = newMqttClient(deviceId);
      info("Attempting connection to " + getClientId(deviceId));

      mqttClient.setCallback(new MqttCallbackHandler(deviceId));
      mqttClient.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      configureAuth(options);
      reauthTimes.put(deviceId, Instant.now().plusSeconds(TOKEN_EXPIRY_MINUTES * 60 / 2));

      mqttClient.connect(options);

      subscribeToUpdates(mqttClient, deviceId);
      return mqttClient;
    } catch (Exception e) {
      throw new RuntimeException("While connecting mqtt client " + deviceId, e);
    } finally {
      connectionLock.release();
    }
  }

  private void configureAuth(MqttConnectOptions options) throws Exception {
    if (configuration.endpoint.auth_provider == null) {
      info("No endpoint auth_provider found, using gcp defaults");
      configureAuth(options, (Jwt) null);
    } else if (configuration.endpoint.auth_provider.jwt != null) {
      configureAuth(options, configuration.endpoint.auth_provider.jwt);
    } else if (configuration.endpoint.auth_provider.basic != null) {
      configureAuth(options, configuration.endpoint.auth_provider.basic);
    } else {
      throw new IllegalArgumentException("Unknown auth provider");
    }
  }

  private void configureAuth(MqttConnectOptions options, Jwt jwt) throws Exception {
    String audience = jwt == null ? projectId : jwt.audience;
    info("Auth using audience " + audience);
    options.setUserName(UNUSED_ACCOUNT_NAME);
    info("Key hash " + Hashing.sha256().hashBytes((byte[]) configuration.keyBytes));
    options.setPassword(createJwt(audience, (byte[]) configuration.keyBytes,
        configuration.algorithm).toCharArray());
  }

  private void configureAuth(MqttConnectOptions options, Basic basic) {
    info("Auth using username " + basic.username);
    options.setUserName(basic.username);
    options.setPassword(basic.password.toCharArray());
  }

  /**
   * Create a Cloud IoT JWT for the given project id, signed with the given private key.
   */
  private String createJwt(String audience, byte[] privateKeyBytes, String algorithm)
      throws Exception {
    DateTime now = new DateTime();
    // Create a JWT to authenticate this device. The device will be disconnected after the token
    // expires, and will have to reconnect with a new token. The audience field should always be set
    // to the GCP project id.
    JwtBuilder jwtBuilder =
        Jwts.builder()
            .setIssuedAt(now.toDate())
            .setExpiration(now.plusMinutes(TOKEN_EXPIRY_MINUTES).toDate())
            .setAudience(audience);

    if (algorithm.equals("RS256") || algorithm.equals("RS256_X509")) {
      PrivateKey privateKey = loadKeyBytes(privateKeyBytes, "RSA");
      return jwtBuilder.signWith(SignatureAlgorithm.RS256, privateKey).compact();
    } else if (algorithm.equals("ES256") || algorithm.equals("ES256_X509")) {
      PrivateKey privateKey = loadKeyBytes(privateKeyBytes, "EC");
      return jwtBuilder.signWith(SignatureAlgorithm.ES256, privateKey).compact();
    } else {
      throw new IllegalArgumentException(
          "Invalid algorithm " + algorithm + ". Should be one of 'RS256' or 'ES256'.");
    }
  }

  private String getBrokerUrl() {
    // Build the connection string for Google's Cloud IoT MQTT server. Only SSL connections are
    // accepted. For server authentication, the JVM's root certificates are used.
    Transport trans = Optional.ofNullable(configuration.endpoint.transport).orElse(Transport.SSL);
    return String.format(BROKER_URL_FORMAT, trans, configuration.endpoint.hostname,
        configuration.endpoint.port);
  }

  private void subscribeToUpdates(MqttClient client, String deviceId) {
    boolean noConfigAck = (configuration.options.noConfigAck != null
        && configuration.options.noConfigAck);
    int configQos = noConfigAck ? QOS_AT_MOST_ONCE : QOS_AT_LEAST_ONCE;
    subscribeTopic(client, getMessageTopic(deviceId, MqttDevice.CONFIG_TOPIC), configQos);
    subscribeTopic(client, getMessageTopic(deviceId, MqttDevice.ERRORS_TOPIC), QOS_AT_MOST_ONCE);
    info("Updates subscribed");
  }

  private void subscribeTopic(MqttClient client, String updateTopic, int mqttQos) {
    try {
      client.subscribe(updateTopic, mqttQos);
    } catch (MqttException e) {
      throw new RuntimeException("While subscribing to MQTT topic " + updateTopic, e);
    }
  }

  /**
   * Register a message handler.
   *
   * @param <T>         Param of the message type
   * @param deviceId    Sending device id
   * @param mqttSuffix  Mqtt topic suffix
   * @param handler     Message received handler
   * @param messageType Type of the message for this handler
   */
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(String deviceId, String mqttSuffix,
      Consumer<T> handler, Class<T> messageType) {
    String mqttTopic = getMessageTopic(deviceId, mqttSuffix);
    String handlerKey = getHandlerKey(mqttTopic);
    if (handler == null) {
      handlers.remove(handlerKey);
      handlersType.remove(handlerKey);
    } else if (handlers.put(handlerKey, (Consumer<Object>) handler) == null) {
      handlersType.put(handlerKey, (Class<Object>) messageType);
    } else {
      throw new IllegalStateException("Overwriting existing handler " + handlerKey);
    }
  }

  private String getHandlerKey(String topic) {
    return String.format(HANDLER_KEY_FORMAT, registryId, topic);
  }

  private String getMessageType(String topic) {
    // {site}/devices/{device}/{type}
    return topic.split("/")[3];
  }

  private String getDeviceId(String topic) {
    // {site}/devices/{device}/{type}
    return topic.split("/")[2];
  }

  public void connect(String deviceId) {
    getConnectedClient(deviceId);
  }

  private void success(String message, String type, String phase) {
    onError.accept(new PublisherException(message, type, phase, null));
  }

  private void error(String message, String type, String phase, Exception e) {
    LOG.error(message, e);
    onError.accept(new PublisherException(message, type, phase, e));
  }

  private void warn(String message) {
    LOG.warn(message);
  }

  private void info(String message) {
    LOG.info(message);
  }

  private void debug(String message) {
    LOG.debug(message);
  }

  private void sendMessage(String deviceId, String mqttTopic,
      byte[] mqttMessage) throws Exception {
    MqttClient connectedClient = getActiveClient(deviceId);
    connectedClient.publish(mqttTopic, mqttMessage, QOS_AT_LEAST_ONCE, SHOULD_RETAIN);
    publishCounter.incrementAndGet();
  }

  private MqttClient getActiveClient(String deviceId) {
    while (true) {
      checkAuthentication(deviceId);
      MqttClient connectedClient = getConnectedClient(deviceId);
      if (connectedClient.isConnected()) {
        return connectedClient;
      }
      info("Client not active, deferring message...");
      safeSleep(DEFAULT_CONFIG_WAIT_SEC);
    }
  }

  private void safeSleep(long timeoutMs) {
    try {
      Thread.sleep(timeoutMs);
    } catch (Exception e) {
      throw new RuntimeException("Interrupted sleep", e);
    }
  }

  private void checkAuthentication(String deviceId) {
    String authId = isProxyDevice(deviceId) ? configuration.gatewayId : deviceId;
    Instant reauthTime = reauthTimes.get(authId);
    if (reauthTime == null || (reauthTime != null && Instant.now().isBefore(reauthTime))) {
      return;
    }
    warn("Authentication retry time reached for " + authId);
    reauthTimes.remove(authId);
    synchronized (mqttClients) {
      MqttClient client = mqttClients.remove(authId);
      if (client == null) {
        return;
      }
      Set<String> removeSet = mqttClients.entrySet().stream()
          .filter(entry -> entry.getValue() == client).map(Entry::getKey)
          .collect(Collectors.toSet());
      removeSet.forEach(mqttClients::remove);
      try {
        client.disconnect();
        client.close();
      } catch (Exception e) {
        throw new RuntimeException("While trying to reconnect mqtt client", e);
      }
    }
  }

  private MqttClient getConnectedClient(String deviceId) {
    try {
      synchronized (mqttClients) {
        if (isProxyDevice(deviceId)) {
          return mqttClients.computeIfAbsent(deviceId, this::newBoundClient);
        }
        return mqttClients.computeIfAbsent(deviceId, this::connectMqttClient);
      }
    } catch (Exception e) {
      throw new RuntimeException("While getting mqtt client " + deviceId + ": " + e, e);
    }
  }

  private boolean isProxyDevice(String deviceId) {
    String gatewayId = configuration.gatewayId;
    return gatewayId != null && !gatewayId.equals(deviceId);
  }

  /**
   * Load a PKCS8 encoded keyfile from the given path.
   */
  private PrivateKey loadKeyBytes(byte[] keyBytes, String algorithm) throws Exception {
    try {
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
      KeyFactory kf = KeyFactory.getInstance(algorithm);
      return kf.generatePrivate(spec);
    } catch (Exception e) {
      throw new IllegalArgumentException("Loading key bytes", e);
    }
  }

  /**
   * Exception class for errors during publishing.
   */
  public static class PublisherException extends RuntimeException {

    final String type;
    final String phase;

    /**
     * Exception encountered during publishing a message.
     *
     * @param message Error message
     * @param type    Type of message being published
     * @param phase   Which phase of execution
     * @param cause   Cause of the exception
     */
    public PublisherException(String message, String type, String phase, Throwable cause) {
      super(message, cause);
      this.type = type;
      this.phase = phase;
    }
  }

  private class MqttCallbackHandler implements MqttCallback {

    private final String deviceId;

    MqttCallbackHandler(String deviceId) {
      this.deviceId = deviceId;
    }

    @Override
    public void connectionLost(Throwable cause) {
      boolean connected = mqttClients.remove(deviceId).isConnected();
      warn("MQTT Connection Lost: " + connected + cause);
      onError.accept(new ConnectionClosedException());
      close();
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      synchronized (MqttPublisher.this) {
        String messageType = getMessageType(topic);
        String handlerKey = getHandlerKey(topic);
        String deviceId = getDeviceId(topic);
        if (deviceId.equals(configuration.gatewayId)) {
          gatewayLatch.countDown();
        }
        Consumer<Object> handler = handlers.get(handlerKey);
        Class<Object> type = handlersType.get(handlerKey);
        if (handler == null) {
          error("Missing handler", messageType, "receive",
              new RuntimeException("No registered handler for " + handlerKey));
          return;
        }
        success("Received config", messageType, "receive");

        final Object payload;
        try {
          if (message.toString().length() == 0) {
            payload = null;
          } else {
            payload = OBJECT_MAPPER.readValue(message.toString(), type);
          }
        } catch (Exception e) {
          error("Processing message", messageType, "parse", e);
          return;
        }
        success("Parsed message", messageType, "parse");
        handler.accept(payload);
      }
    }
  }
}
