package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalNotification;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 */
public class MqttPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttPublisher.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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

  private final Semaphore connectionLock = new Semaphore(1);

  private final Map<String, MqttClient> mqttClients = new ConcurrentHashMap<>();

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
    LOG.debug("Publishing in background " + registryId + "/" + deviceId);
    publisherExecutor.submit(() -> publishCore(deviceId, topic, data));
  }

  private void publishCore(String deviceId, String topic, Object data) {
    try {
      String payload = OBJECT_MAPPER.writeValueAsString(data);
      sendMessage(deviceId, getMessageTopic(deviceId, topic), payload.getBytes());
      LOG.debug("Publishing complete " + registryId + "/" + deviceId);
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      LOG.warn(String.format("Publish failed for %s: %s", deviceId, e));
      if (configuration.gatewayId == null) {
        closeDeviceClient(deviceId);
      } else {
        close();
      }
    }
  }

  private void closeDeviceClient(String deviceId) {
    MqttClient removed = mqttClients.remove(deviceId);
    if (removed != null) {
      try {
        removed.close();
      } catch (Exception e) {
        LOG.error("Error closing MQTT client: " + e.toString());
      }
    }
  }

  void close() {
    Set<String> clients = mqttClients.keySet();
    for (String client : clients) {
      closeDeviceClient(client);
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
      LOG.debug("Connecting through gateway " + gatewayId);
      MqttClient mqttClient = getConnectedClient(gatewayId);
      String topic = String.format("/devices/%s/attach", deviceId);
      String payload = "";
      LOG.info("Publishing attach message " + topic);
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
      LOG.info("Creating new mqtt client for " + clientId);
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
      if (mqttClient.isConnected()) {
        return mqttClient;
      }
      LOG.info("Attempting connection to " + registryId + ":" + deviceId);

      mqttClient.setCallback(new MqttCallbackHandler(deviceId));
      mqttClient.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setUserName(UNUSED_ACCOUNT_NAME);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      options.setPassword(createJwt());

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

  private String getHandlerKey(String configTopic) {
    return String.format(HANDLER_KEY_FORMAT, registryId, configTopic);
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
      LOG.warn("MQTT Connection Lost", cause);
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
      String handlerKey = getHandlerKey(topic);
      Consumer<Object> handler = handlers.get(handlerKey);
      Class<Object> type = handlersType.get(handlerKey);
      if (handler == null) {
        onError.accept(new RuntimeException("No registered handler for " + handlerKey));
      } else if (message.toString().length() == 0) {
        LOG.warn("Received message is empty for " + handlerKey);
        handler.accept(null);
      } else {
        try {
          handler.accept(OBJECT_MAPPER.readValue(message.toString(), type));
        } catch (Exception e) {
          onError.accept(e);
        }
      }
    }
  }

  private void sendMessage(String deviceId, String mqttTopic,
      byte[] mqttMessage) throws Exception {
    LOG.debug("Sending message to " + mqttTopic);
    getConnectedClient(deviceId).publish(mqttTopic, mqttMessage, MQTT_QOS, SHOULD_RETAIN);
    publishCounter.incrementAndGet();
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
            .setExpiration(now.plusMinutes(60).toDate())
            .setAudience(projectId);

    if (algorithm.equals("RS256")) {
      PrivateKey privateKey = loadKeyBytes(privateKeyBytes, "RSA");
      return jwtBuilder.signWith(SignatureAlgorithm.RS256, privateKey).compact();
    } else if (algorithm.equals("ES256")) {
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
}
