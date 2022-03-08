package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.function.BiConsumer;
import org.apache.http.ConnectionClosedException;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 */
public class MqttPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttPublisher.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  // Indicate if this message should be a MQTT 'retained' message.
  private static final boolean SHOULD_RETAIN = false;

  private static final int MQTT_QOS = 1;
  private static final String CONFIG_UPDATE_TOPIC_FMT = "/devices/%s/config";
  private static final String ERRORS_TOPIC_FMT = "/devices/%s/errors";
  private static final String UNUSED_ACCOUNT_NAME = "unused";
  private static final int INITIALIZE_TIME_MS = 20000;

  private static final String MESSAGE_TOPIC_FORMAT = "/devices/%s/%s";
  private static final String BROKER_URL_FORMAT = "ssl://%s:%s";
  private static final String CLIENT_ID_FORMAT = "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final int PUBLISH_THREAD_COUNT = 10;
  private static final String HANDLER_KEY_FORMAT = "%s/%s";
  private static final int TOKEN_EXPIRY_MINUTES = 60;

  private final Semaphore connectionLock = new Semaphore(1);

  private final Map<String, MqttClient> mqttClients = new ConcurrentHashMap<>();
  private final Map<String, Instant> reauthTimes = new ConcurrentHashMap<>();

  private final ExecutorService publisherExecutor =
      Executors.newFixedThreadPool(PUBLISH_THREAD_COUNT);

  private final Configuration configuration;
  private final String registryId;

  private final AtomicInteger publishCounter = new AtomicInteger(0);
  private final AtomicInteger errorCounter = new AtomicInteger(0);
  private final Map<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
  private final Map<String, Class<Object>> handlersType = new ConcurrentHashMap<>();
  private final Consumer<Exception> onError;

  MqttPublisher(Configuration configuration, Consumer<Exception> onError) {
    this.configuration = configuration;
    this.registryId = configuration.registryId;
    this.onError = onError;
    validateCloudIoTOptions();
  }

  void publish(String deviceId, String topic, Object data) {
    Preconditions.checkNotNull(deviceId, "publish deviceId");
    if (publisherExecutor.isShutdown()) {
      return;
    }
    debug("Publishing in background " + registryId + "/" + deviceId);
    publisherExecutor.submit(() -> publishCore(deviceId, topic, data));
  }

  private void publishCore(String deviceId, String topic, Object data) {
    try {
      String payload = OBJECT_MAPPER.writeValueAsString(data);
      sendMessage(deviceId, getMessageTopic(deviceId, topic), payload.getBytes());
      debug("Publishing complete " + registryId + "/" + deviceId);
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      warn(String.format("Publish failed for %s: %s", deviceId, e));
      if (configuration.gatewayId == null) {
        closeMqttClient(deviceId);
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

  void close() {
    try {
      publisherExecutor.shutdown();
      if (!publisherExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
        throw new RuntimeException("Could not terminate executor");
      }
      mqttClients.keySet().forEach(this::closeMqttClient);
    } catch (Exception e) {
      throw new RuntimeException("While closing publisher");
    }
  }

  long clientCount() {
    return mqttClients.size();
  }

  private void validateCloudIoTOptions() {
    try {
      checkNotNull(configuration.bridgeHostname, "bridgeHostname");
      checkNotNull(configuration.bridgePort, "bridgePort");
      checkNotNull(configuration.projectId, "projectId");
      checkNotNull(configuration.cloudRegion, "cloudRegion");
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
      MqttClient mqttClient = getConnectedClient(gatewayId);
      String topic = String.format("/devices/%s/attach", deviceId);
      String payload = "";
      info("Publishing attach message " + topic);
      mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8.name()), MQTT_QOS, SHOULD_RETAIN);
      subscribeToUpdates(mqttClient, deviceId);
      return mqttClient;
    } catch (Exception e) {
      throw new RuntimeException("While binding client " + deviceId, e);
    }
  }

  private MqttClient newMqttClient(String deviceId) {
    try {
      Preconditions.checkNotNull(registryId, "registryId is null");
      Preconditions.checkNotNull(deviceId, "deviceId is null");
      String clientId = getClientId(deviceId);
      info("Creating new mqtt client for " + clientId);
      MqttClient mqttClient = new MqttClient(getBrokerUrl(), clientId,
          new MemoryPersistence());
      return mqttClient;
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      throw new RuntimeException("Creating new MQTT client " + deviceId, e);
    }
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
      options.setUserName(UNUSED_ACCOUNT_NAME);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      info("Password hash " + Hashing.sha256().hashBytes(configuration.keyBytes).toString());
      options.setPassword(createJwt());
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

  private char[] createJwt() throws Exception {
    return createJwt(configuration.projectId, configuration.keyBytes, configuration.algorithm)
        .toCharArray();
  }

  private String getClientId(String deviceId) {
    // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
    // Google Cloud IoT, it must be in the format below.
    return String.format(CLIENT_ID_FORMAT, configuration.projectId, configuration.cloudRegion,
        registryId, deviceId);
  }

  private String getBrokerUrl() {
    // Build the connection string for Google's Cloud IoT MQTT server. Only SSL connections are
    // accepted. For server authentication, the JVM's root certificates are used.
    return String.format(BROKER_URL_FORMAT, configuration.bridgeHostname, configuration.bridgePort);
  }

  private String getMessageTopic(String deviceId, String topic) {
    return String.format(MESSAGE_TOPIC_FORMAT, deviceId, topic);
  }

  private void subscribeToUpdates(MqttClient client, String deviceId) {
    subscribeTopic(client, String.format(CONFIG_UPDATE_TOPIC_FMT, deviceId));
    subscribeTopic(client, String.format(ERRORS_TOPIC_FMT, deviceId));
  }

  private void subscribeTopic(MqttClient client, String updateTopic) {
    try {
      client.subscribe(updateTopic);
    } catch (MqttException e) {
      throw new RuntimeException("While subscribing to MQTT topic " + updateTopic, e);
    }
  }

  public PublisherStats getStatistics() {
    return new PublisherStats();
  }

  @SuppressWarnings("unchecked")
  public <T> void registerHandler(String deviceId, String mqttTopic,
      Consumer<T> handler, Class<T> messageType) {
    String key = getHandlerKey(getMessageTopic(deviceId, mqttTopic));
    if (handler == null) {
      handlers.remove(key);
      handlersType.remove(key);
    } else if (handlers.put(key, (Consumer<Object>) handler) == null) {
      handlersType.put(key, (Class<Object>) messageType);
    } else {
      throw new IllegalStateException("Overwriting existing handler for " + key);
    }
  }

  private String getHandlerKey(String topic) {
    return String.format(HANDLER_KEY_FORMAT, registryId, topic);
  }

  private String getMessageType(String topic) {
    // {site}/devices/{device}/{type}
    return topic.split("/")[3];
  }

  public void connect(String deviceId) {
    getConnectedClient(deviceId);
  }

  private class MqttCallbackHandler implements MqttCallback {

    private final String deviceId;

    MqttCallbackHandler(String deviceId) {
      this.deviceId = deviceId;
    }

    /**
     * @see MqttCallback#connectionLost(Throwable)
     */
    public void connectionLost(Throwable cause) {
      warn("MQTT Connection Lost: " + cause);
      onError.accept(new ConnectionClosedException());
    }

    /**
     * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
     */
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    /**
     * @see MqttCallback#messageArrived(String, MqttMessage)
     */
    public void messageArrived(String topic, MqttMessage message) {
      synchronized (MqttPublisher.this) {
        String messageType = getMessageType(topic);
        String handlerKey = getHandlerKey(topic);
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

  private synchronized void sendMessage(String deviceId, String mqttTopic,
      byte[] mqttMessage) throws Exception {
    debug("Sending message to " + mqttTopic);
    checkAuthentication(deviceId);
    getConnectedClient(deviceId).publish(mqttTopic, mqttMessage, MQTT_QOS, SHOULD_RETAIN);
    publishCounter.incrementAndGet();
  }

  private void checkAuthentication(String deviceId) {
    if (Instant.now().isBefore(reauthTimes.get(deviceId))) {
      return;
    }
    warn("Authentication retry time reached for " + deviceId);
    reauthTimes.remove(deviceId);
    MqttClient client = mqttClients.remove(deviceId);
    try {
      client.disconnect();
      client.close();
    } catch (Exception e) {
      throw new RuntimeException("While trying to reconnect mqtt client", e);
    }
  }

  private MqttClient getConnectedClient(String deviceId) {
    try {
      String gatewayId = configuration.gatewayId;
      if (gatewayId != null && !gatewayId.equals(deviceId)) {
        return mqttClients.computeIfAbsent(deviceId, this::newBoundClient);
      }
      return mqttClients.computeIfAbsent(deviceId, this::connectMqttClient);
    } catch (Exception e) {
      throw new RuntimeException("While getting mqtt client " + deviceId + ": " + e.toString(), e);
    }
  }

  /** Load a PKCS8 encoded keyfile from the given path. */
  private PrivateKey loadKeyBytes(byte[] keyBytes, String algorithm) throws Exception {
    try {
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
      KeyFactory kf = KeyFactory.getInstance(algorithm);
      return kf.generatePrivate(spec);
    } catch (Exception e) {
      throw new IllegalArgumentException("Loading key bytes", e);
    }
  }

  /** Create a Cloud IoT JWT for the given project id, signed with the given private key */
  protected String createJwt(String projectId, byte[] privateKeyBytes, String algorithm)
      throws Exception {
    DateTime now = new DateTime();
    // Create a JWT to authenticate this device. The device will be disconnected after the token
    // expires, and will have to reconnect with a new token. The audience field should always be set
    // to the GCP project id.
    JwtBuilder jwtBuilder =
        Jwts.builder()
            .setIssuedAt(now.toDate())
            .setExpiration(now.plusMinutes(TOKEN_EXPIRY_MINUTES).toDate())
            .setAudience(projectId);

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

  public class PublisherStats {
    public long clientCount = mqttClients.size();
    public int publishCount = publishCounter.getAndSet(0);
    public int errorCount = errorCounter.getAndSet(0);
  }

  public static class PublisherException extends RuntimeException {

    final String type;
    final String phase;

    public PublisherException(String message, String type, String phase, Throwable cause) {
      super(message, cause);
      this.type = type;
      this.phase = phase;
    }
  }
}
