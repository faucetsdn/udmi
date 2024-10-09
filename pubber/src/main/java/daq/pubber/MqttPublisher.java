package daq.pubber;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.SEC_TO_MS;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.udmi.util.CertManager;
import com.google.udmi.util.NanSerializer;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
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
import udmi.schema.Config;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Jwt;
import udmi.schema.PubberConfiguration;

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 */
public class MqttPublisher implements Publisher {

  public static final String EMPTY_STRING = "";
  static final int DEFAULT_CONFIG_WAIT_SEC = 10;
  private static final String DEFAULT_TOPIC_PREFIX = "/devices/";
  private static final Logger LOG = LoggerFactory.getLogger(MqttPublisher.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new ISO8601DateFormat())
      .registerModule(NanSerializer.TO_NAN)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  // Indicate if this message should be a MQTT 'retained' message.
  private static final boolean DO_NOT_RETAIN = false;
  private static final String UNUSED_ACCOUNT_NAME = "unused";
  private static final int INITIALIZE_TIME_MS = 10000;
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final int PUBLISH_THREAD_COUNT = 10;
  private static final String HANDLER_KEY_FORMAT = "%s/%s";
  private static final int TOKEN_EXPIRY_MINUTES = 60;
  private static final int QOS_AT_MOST_ONCE = 0;
  private static final int QOS_AT_LEAST_ONCE = 1;
  private static final String EVENT_MARK_PREFIX = "events/";
  private static final Map<String, AtomicInteger> EVENT_SERIAL = new HashMap<>();
  private static final String GCP_CLIENT_PREFIX = "projects/";
  private static final Integer DEFAULT_MQTT_PORT = 8883;
  private static final long ATTACH_DELAY_MS = 1000;
  private static final String LOCAL_MQTT_PREFIX = "/r/";

  private final Semaphore connectionLock = new Semaphore(1);

  private final Map<String, MqttClient> mqttClients = new ConcurrentHashMap<>();
  private final Map<String, Instant> reauthTimes = new ConcurrentHashMap<>();

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
  private final String deviceId;
  private final CertManager certManager;
  private CountDownLatch connectionLatch;
  private String topicPrefixPrefix;

  MqttPublisher(PubberConfiguration configuration, Consumer<Exception> onError,
      CertManager certManager) {
    this.configuration = configuration;
    this.certManager = certManager;
    if (isGcpIotCore(configuration)) {
      ClientInfo clientIdParts = SiteModel.parseClientId(configuration.endpoint.client_id);
      this.projectId = clientIdParts.iotProject;
      this.cloudRegion = clientIdParts.cloudRegion;
      this.registryId = clientIdParts.registryId;
      this.deviceId = clientIdParts.deviceId;
    } else {
      this.projectId = null;
      this.cloudRegion = null;
      this.registryId = null;
      this.deviceId = null;
    }
    this.onError = onError;
    validateCloudIotOptions();
  }

  private boolean isGcpIotCore(PubberConfiguration configuration) {
    return configuration.endpoint.client_id != null && configuration.endpoint.client_id.startsWith(
        GCP_CLIENT_PREFIX);
  }

  private String getClientId(String targetId) {
    // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
    // Google Cloud IoT, it must be in the format below.
    if (isGcpIotCore(configuration)) {
      ClientInfo clientInfo = SiteModel.parseClientId(configuration.endpoint.client_id);
      return SiteModel.getClientId(clientInfo.iotProject, clientInfo.cloudRegion,
          clientInfo.registryId, targetId);
    } else if (configuration.endpoint.client_id != null) {
      return configuration.endpoint.client_id;
    }
    return SiteModel.getClientId(projectId, cloudRegion, registryId, targetId);
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
      mapped.put("timestamp", timestamp.replace("Z", format(".%03dZ", serialNo % 1000)));
      return mapped;
    } catch (Exception e) {
      throw new RuntimeException("While decorating message", e);
    }
  }

  @Override
  public void setDeviceTopicPrefix(String deviceId, String topicPrefix) {
    checkState(topicPrefix.endsWith(deviceId), "topic prefix does not end with device id");
    topicPrefixPrefix = topicPrefix.substring(0, topicPrefix.length() - deviceId.length());
  }

  private void publishCore(String deviceId, String topicSuffix, Object data, Runnable callback) {
    try {
      String payload = getMessagePayload(data);
      String sendTopic = getSendTopic(deviceId, getMessageTopic(topicSuffix, data));
      debug("Sending message to " + sendTopic);
      sendMessage(deviceId, sendTopic, payload.getBytes());
      if (callback != null) {
        callback.run();
      }
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      warn(format("Publish %s failed for %s: %s", topicSuffix, deviceId, e));
      if (getGatewayId(deviceId) == null) {
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

  private String getMessageTopic(String deviceId, String topic) {
    return ofNullable(topicPrefixPrefix).orElse(DEFAULT_TOPIC_PREFIX) + deviceId + "/" + topic;
  }

  @SuppressWarnings("unchecked")
  private String getMessageTopic(String topicSuffix, Object data) {
    String altTopic = data instanceof Map
        ? ((Map<String, String>) data).remove(InjectedMessage.REPLACE_TOPIC_KEY)
        : null;
    return altTopic != null ? altTopic : topicSuffix;
  }

  @SuppressWarnings("unchecked")
  private String getMessagePayload(Object data) throws JsonProcessingException {
    String stringMessage =
        data instanceof InjectedMessage ? ((InjectedMessage) data).REPLACE_MESSAGE_WITH : null;
    String altMessage = data instanceof Map
        ? ((Map<String, String>) data).remove(InjectedMessage.REPLACE_MESSAGE_KEY)
        : stringMessage;
    return altMessage != null ? altMessage : OBJECT_MAPPER.writeValueAsString(data);
  }

  private String getSendTopic(String deviceId, String topicSuffix) {
    return Objects.requireNonNullElseGet(configuration.endpoint.send_id,
        () -> getMessageTopic(deviceId, topicSuffix));
  }

  private void closeMqttClient(String deviceId) {
    synchronized (mqttClients) {
      MqttClient removed = cleanClients(deviceId);
      if (removed != null) {
        try {
          if (removed.isConnected()) {
            removed.disconnect();
          }
          removed.close();
        } catch (Exception e) {
          error("Error closing MQTT client: " + e, deviceId, null, "stop", e);
        }
      }
    }
  }

  private MqttClient cleanClients(String deviceId) {
    MqttClient remove = mqttClients.remove(deviceId);
    mqttClients.entrySet().stream().filter(entry -> entry.getValue() == remove).map(Entry::getKey)
        .toList().forEach(mqttClients::remove);
    return remove;
  }

  @Override
  public void close() {
    try {
      warn("Closing publisher connection");
      mqttClients.keySet().forEach(this::closeMqttClient);
    } catch (Exception e) {
      error("While closing publisher", deviceId, null, "close", e);
    }
  }

  @Override
  public void shutdown() {
    if (isActive()) {
      publisherExecutor.shutdown();
    }
  }

  private void validateCloudIotOptions() {
    try {
      checkNotNull(configuration.endpoint.hostname, "endpoint hostname");
      checkNotNull(configuration.endpoint.client_id, "endpoint client_id");
      checkNotNull(configuration.keyBytes, "keyBytes");
      checkNotNull(configuration.algorithm, "algorithm");
    } catch (Exception e) {
      throw new IllegalStateException("Invalid Cloud IoT Options", e);
    }
  }

  private MqttClient newProxyClient(String deviceId) {
    String gatewayId = getGatewayId(deviceId);
    info(format("Connecting device %s through gateway %s", deviceId, gatewayId));
    final MqttClient mqttClient = getConnectedClient(gatewayId, true);
    long timeToWait = mqttClient.getTimeToWait();
    try {
      startupLatchWait(connectionLatch, "gateway startup exchange");
      String topic = getMessageTopic(deviceId, MqttDevice.ATTACH_TOPIC);
      info("Publishing attach message " + topic);
      byte[] mqttMessage = EMPTY_STRING.getBytes(StandardCharsets.UTF_8);
      mqttClient.setTimeToWait(ATTACH_DELAY_MS);
      mqttClientPublish(mqttClient, topic, mqttMessage);
      subscribeToUpdates(mqttClient, deviceId);
      mqttClient.setTimeToWait(timeToWait);
      return mqttClient;
    } catch (Exception e) {
      throw new RuntimeException("While binding client " + deviceId, e);
    } finally {
      mqttClient.setTimeToWait(timeToWait);
    }
  }

  private void mqttClientPublish(MqttClient mqttClient, String topic, byte[] mqttMessage)
      throws MqttException {
    mqttClient.publish(topic, mqttMessage, QOS_AT_LEAST_ONCE, DO_NOT_RETAIN);
  }

  private void startupLatchWait(CountDownLatch gatewayLatch, String designator) {
    try {
      int waitTimeSec = ofNullable(configuration.endpoint.config_sync_sec)
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

  private MqttClient newDirectClient(String deviceId) {
    try {
      if (!connectionLock.tryAcquire(INITIALIZE_TIME_MS, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Timeout waiting for connection lock");
      }
      MqttClient mqttClient = newMqttClient(deviceId);

      mqttClient.setCallback(new MqttCallbackHandler(deviceId));
      mqttClient.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      configureAuth(options);
      reauthTimes.put(deviceId, Instant.now().plusSeconds(TOKEN_EXPIRY_MINUTES * 60 / 2));
      connectionLatch = new CountDownLatch(1);

      info("Attempting connection to " + getClientId(deviceId));
      mqttClient.connect(options);

      subscribeToUpdates(mqttClient, deviceId);
      return mqttClient;
    } catch (Exception e) {
      throw new RuntimeException("While connecting mqtt client " + deviceId, e);
    } finally {
      connectionLock.release();
    }
  }

  private SocketFactory getSocketFactory() {
    return ofNullable(certManager).map(CertManager::getSocketFactory)
        .orElse(SSLSocketFactory.getDefault());
  }

  private void configureAuth(MqttConnectOptions options) throws Exception {
    options.setSocketFactory(getSocketFactory());
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
    Transport trans = ofNullable(configuration.endpoint.transport).orElse(Transport.SSL);
    return format(BROKER_URL_FORMAT, trans, configuration.endpoint.hostname,
        ofNullable(configuration.endpoint.port).orElse(DEFAULT_MQTT_PORT));
  }

  private void subscribeToUpdates(MqttClient client, String deviceId) {
    int configQos =
        isTrue(configuration.options.noConfigAck) ? QOS_AT_MOST_ONCE : QOS_AT_LEAST_ONCE;
    if (configuration.endpoint.recv_id == null) {
      subscribeTopic(client, getMessageTopic(deviceId, MqttDevice.CONFIG_TOPIC), configQos);
      subscribeTopic(client, getMessageTopic(deviceId, MqttDevice.ERRORS_TOPIC), QOS_AT_MOST_ONCE);
    } else {
      subscribeTopic(client, configuration.endpoint.recv_id, configQos);
    }
  }

  private void subscribeTopic(MqttClient client, String updateTopic, int mqttQos) {
    try {
      client.subscribe(updateTopic, mqttQos);
      info(format("Subscribed to mqtt topic %s (qos %d)", updateTopic, mqttQos));
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
      info(format("Removing handler %s", handlerKey));
      handlers.remove(handlerKey);
      handlersType.remove(handlerKey);
    } else if (handlers.put(handlerKey, (Consumer<Object>) handler) == null) {
      info(format("Registered handler for %s as %s", handlerKey, messageType.getSimpleName()));
      handlersType.put(handlerKey, (Class<Object>) messageType);
    } else {
      throw new IllegalStateException("Overwriting existing handler " + handlerKey);
    }
  }

  private String getHandlerKey(String topic) {
    return format(HANDLER_KEY_FORMAT, registryId, topic);
  }

  private String getMessageType(String topic) {
    // {site}/devices/{device}/{type}
    // /r/{registry}/d/{device}/{type}
    int splitIndex = topic.startsWith(LOCAL_MQTT_PREFIX) ? 5 : 3;
    return topic.split("/")[splitIndex];
  }

  private String getDeviceId(String topic) {
    // {site}/devices/{device}/{type}
    // /r/{registry}/d/{device}/{type}
    int splitIndex = topic.startsWith(LOCAL_MQTT_PREFIX) ? 4 : 2;
    return topic.split("/")[splitIndex];
  }

  public void connect(String targetId, boolean clean) {
    ifTrueThen(clean, () -> closeMqttClient(targetId));
    getConnectedClient(targetId, true);
  }

  private void success(String message, String deviceId, String type, String phase) {
    onError.accept(new PublisherException(message, deviceId, type, phase, null));
  }

  private void error(String message, String deviceId, String type, String phase, Exception e) {
    LOG.error(message, e);
    onError.accept(new PublisherException(message, deviceId, type, phase, e));
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
    mqttClientPublish(connectedClient, mqttTopic, mqttMessage);
    publishCounter.incrementAndGet();
  }

  private MqttClient getActiveClient(String targetId) {
    while (true) {
      checkAuthentication(targetId);
      MqttClient connectedClient = getConnectedClient(targetId, false);
      if (connectedClient != null && connectedClient.isConnected()) {
        return connectedClient;
      }
      info(format("Client %s not active, deferring message...", targetId));
      safeSleep(DEFAULT_CONFIG_WAIT_SEC * SEC_TO_MS);
    }
  }

  private void safeSleep(long timeoutMs) {
    try {
      Thread.sleep(timeoutMs);
    } catch (Exception e) {
      throw new RuntimeException("Interrupted sleep", e);
    }
  }

  private void checkAuthentication(String targetId) {
    String authId = ofNullable(getGatewayId(targetId)).orElse(targetId);
    Instant reAuthTime = reauthTimes.get(authId);
    if (reAuthTime == null || Instant.now().isBefore(reAuthTime)) {
      return;
    }
    warn("Authentication retry time reached for " + authId);
    reauthTimes.remove(authId);
    synchronized (mqttClients) {
      MqttClient client = cleanClients(authId);
      try {
        client.disconnect();
        client.close();
      } catch (Exception e) {
        throw new RuntimeException("While trying to reconnect mqtt client", e);
      }
    }
  }

  private MqttClient getConnectedClient(String deviceId, boolean proxyActiveOnly) {
    try {
      synchronized (mqttClients) {
        if (isProxyDevice(deviceId)) {
          if (!proxyActiveOnly && !mqttClients.containsKey(deviceId)) {
            return null;
          }
          return mqttClients.computeIfAbsent(deviceId, this::newProxyClient);
        }
        return mqttClients.computeIfAbsent(deviceId, this::newDirectClient);
      }
    } catch (Exception e) {
      throw new RuntimeException("While getting mqtt client " + deviceId + ": " + e, e);
    }
  }

  private boolean isProxyDevice(String targetId) {
    String gatewayId = getGatewayId(targetId);
    return gatewayId != null && !gatewayId.equals(targetId);
  }

  private String getGatewayId(String targetId) {
    return Pubber.getGatewayId(targetId, configuration);
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

    final String deviceId;
    final String type;
    final String phase;

    /**
     * Exception encountered during publishing a message.
     *
     * @param message  Error message
     * @param deviceId Target deviceId
     * @param type     Type of message being published
     * @param phase    Which phase of execution
     * @param cause    Cause of the exception
     */
    public PublisherException(String message, String deviceId, String type, String phase,
        Throwable cause) {
      super(message, cause);
      this.deviceId = deviceId;
      this.type = type;
      this.phase = phase;
    }
  }

  static class InjectedMessage {

    private static final String REPLACE_MESSAGE_KEY = "REPLACE_MESSAGE_WITH";
    private static final String REPLACE_TOPIC_KEY = "REPLACE_TOPIC_WITH";
    public String version;
    public Date timestamp;
    public String field;
    @SuppressWarnings({"MemberName", "AbbreviationAsWordInName"})
    public String REPLACE_MESSAGE_WITH;
    @SuppressWarnings({"MemberName", "AbbreviationAsWordInName"})
    public String REPLACE_TOPIC_WITH;
  }

  /**
   * Marker class for sending using a bad topic not defined by a SubType/SubFolder.
   */
  static class FakeTopic {
    public String version;
    public Date timestamp;
  }

  static class InjectedState extends InjectedMessage {

  }

  private class MqttCallbackHandler implements MqttCallback {

    private final String deviceId;

    MqttCallbackHandler(String deviceId) {
      this.deviceId = deviceId;
    }

    @Override
    public void connectionLost(Throwable cause) {
      boolean connected = cleanClients(deviceId).isConnected();
      warn("MQTT Connection Lost: " + connected + cause);
      close();
      onError.accept(new ConnectionClosedException());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      synchronized (MqttPublisher.this) {
        try {
          messageArrivedCore(topic, message);
        } catch (Exception e) {
          error("While processing message", deviceId, null, "handle", e);
        }
      }
    }

    private void messageArrivedCore(String topic, MqttMessage message) {
      String messageType = getMessageType(topic);
      String handlerKey = getHandlerKey(topic);
      String deviceId = getDeviceId(topic);
      connectionLatch.countDown();
      Consumer<Object> handler = handlers.get(handlerKey);
      Class<Object> type = handlersType.get(handlerKey);
      if (handler == null) {
        error("Missing handler " + handlerKey, deviceId, messageType, "receive",
            new RuntimeException("No registered handler for topic " + topic));
        handlersType.put(handlerKey, Object.class);
        handlers.put(handlerKey, this::ignoringHandler);
        return;
      }
      success("Received config", deviceId, messageType, "receive");

      final Object payload;
      try {
        if (message.toString().length() == 0) {
          payload = null;
        } else {
          payload = OBJECT_MAPPER.readValue(message.toString(), type);
          nukeProxyIdsIfNull(message.toString(), payload);
        }
      } catch (Exception e) {
        error("Processing message", deviceId, messageType, "parse", e);
        return;
      }
      success("Parsed message", deviceId, messageType, "parse");
      handler.accept(payload);
    }

    /**
     * Hack of a function to clean up annoying Jackson POJO implicit empty collection creation.
     */
    private void nukeProxyIdsIfNull(String message, Object payload) {
      try {
        if (payload instanceof Config configPayload) {
          JsonNode jsonNode = OBJECT_MAPPER.readTree(message);
          JsonNode gateway = jsonNode.get("gateway");
          JsonNode proxyIds = ifNotNullGet(gateway, g -> g.get("proxy_ids"));
          if (gateway != null && proxyIds == null) {
            configPayload.gateway.proxy_ids = null;
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Error nuking null gateway proxy_id", e);
      }
    }

    private void ignoringHandler(Object message) {
      // Do nothing, just ignore everything.
    }
  }
}
