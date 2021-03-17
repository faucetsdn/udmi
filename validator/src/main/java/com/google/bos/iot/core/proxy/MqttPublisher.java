package com.google.bos.iot.core.proxy;

import static com.google.bos.iot.core.proxy.ProxyTarget.STATE_TOPIC;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 */
class MqttPublisher implements MessagePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(MqttPublisher.class);

  private static final boolean MQTT_SHOULD_RETAIN = false;

  private static final long STATE_RATE_LIMIT_MS = 1000 * 2;
  private static final int MQTT_QOS = 1;
  private static final String CONFIG_UPDATE_TOPIC_FMT = "/devices/%s/config";
  private static final String ERROR_TOPIC_FMT = "/devices/%s/errors";
  private static final String COMMAND_TOPIC_FMT = "/devices/%s/commands/#";
  private static final int COMMANDS_QOS = 0;
  private static final String UNUSED_ACCOUNT_NAME = "unused";
  private static final int INITIALIZE_TIME_MS = 20000;

  private static final String MESSAGE_TOPIC_FORMAT = "/devices/%s/%s";
  private static final String BROKER_URL_FORMAT = "ssl://%s:%s";
  private static final String CLIENT_ID_FORMAT = "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final int PUBLISH_THREAD_COUNT = 10;
  private static final String ATTACH_MESSAGE_FORMAT = "/devices/%s/attach";
  private static final int TOKEN_EXPIRATION_SEC = 60 * 60 * 1;
  private final int TOKEN_EXPIRATION_MS = TOKEN_EXPIRATION_SEC * 1000;

  private final ExecutorService publisherExecutor =
      Executors.newFixedThreadPool(PUBLISH_THREAD_COUNT);

  private final Semaphore connectWait = new Semaphore(0);

  private final AtomicInteger publishCounter = new AtomicInteger(0);
  private final AtomicInteger errorCounter = new AtomicInteger(0);
  private final Map<String, Long> lastStateTime = Maps.newConcurrentMap();

  private final MqttClient mqttClient;
  private final Set<String> attachedClients = new ConcurrentSkipListSet<>();

  private final BiConsumer<String, String> onMessage;
  private final BiConsumer<MqttPublisher, Throwable> onError;
  private final String deviceId;
  private final byte[] keyBytes;
  private final String algorithm;
  private final String registryId;
  private final String projectId;
  private final String cloudRegion;
  private MqttConnectOptions mqttConnectOptions;
  private long mqttTokenSetTimeMs;
  public static final String BRIDGE_HOSTNAME = "mqtt.googleapis.com";
  public static final String BRIDGE_PORT = "8883";

  MqttPublisher(String projectId, String cloudRegion, String registryId,
      String deviceId, byte[] keyBytes, String algorithm, BiConsumer<String, String> onMessage,
      BiConsumer<MqttPublisher, Throwable> onError) {
    this.onMessage = onMessage;
    this.onError = onError;
    this.projectId = projectId;
    this.cloudRegion = cloudRegion;
    this.registryId = registryId;
    this.deviceId = deviceId;
    this.algorithm = algorithm;
    this.keyBytes = keyBytes;
    LOG.info(deviceId + " token expiration sec " + TOKEN_EXPIRATION_SEC);
    mqttClient = newMqttClient(deviceId);
    connectMqttClient(deviceId);
  }

  public void publish(String deviceId, String topic, String data) {
    Preconditions.checkNotNull(deviceId, "publish deviceId");
    LOG.debug(this.deviceId + " publishing in background " + registryId + "/" + deviceId);
    publisherExecutor.submit(() -> publishCore(deviceId, topic, data));
  }

  private void publishCore(String deviceId, String topic, String payload) {
    try {
      if (!connectWait.tryAcquire(INITIALIZE_TIME_MS, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Timeout waiting for connection");
      }
    } catch (Exception e) {
      throw new RuntimeException("Error acquiring lock", e);
    }
    try {
      if (!mqttClient.isConnected()) {
        throw new RuntimeException("MQTT Client not connected");
      }
      maybeRefreshJwt();
      if (!attachedClients.contains(deviceId)) {
        attachedClients.add(deviceId);
        attachClient(deviceId);
      }
      if (STATE_TOPIC.equals(topic)) {
        delayStateUpdate(deviceId);
      }
      sendMessage(getMessageTopic(deviceId, topic), payload.getBytes());
      LOG.debug(this.deviceId + " publishing complete " + registryId + "/" + deviceId);
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      throw new RuntimeException(String.format("Publish failed for %s: %s", deviceId, e), e);
    } finally {
      connectWait.release();
    }
  }

  private synchronized void delayStateUpdate(String deviceId) {
    long now = System.currentTimeMillis();
    long last = lastStateTime.get(deviceId);
    long delta = now - last;
    long delay = STATE_RATE_LIMIT_MS - delta;
    if (delay > 0) {
      LOG.warn("Delaying state message by " + delay);
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        LOG.warn("Interrupted sleep", e);
      }
      now += delay;
    } else {
      LOG.debug("Delta for state message was " + delta);
    }
    lastStateTime.put(deviceId, now);
  }

  private void sendMessage(String mqttTopic, byte[] mqttMessage) throws Exception {
    LOG.debug(deviceId + " sending message to " + mqttTopic);
    mqttClient.publish(mqttTopic, mqttMessage, MQTT_QOS, MQTT_SHOULD_RETAIN);
    publishCounter.incrementAndGet();
  }

  public void close() {
    try {
      publisherExecutor.shutdownNow();
      publisherExecutor.awaitTermination(INITIALIZE_TIME_MS, TimeUnit.MILLISECONDS);
      if (mqttClient.isConnected()) {
        mqttClient.disconnect();
      }
      mqttClient.close();
    } catch (Exception e) {
      LOG.error(deviceId + " while terminating client: " + e.toString(), e);
    }
  }

  @Override
  public String getSubscriptionId() {
    return mqttClient.getClientId();
  }

  @Override
  public boolean isActive() {
    return mqttClient.isConnected();
  }

  @Override
  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator) {
    throw new IllegalStateException("Can't process message");
  }

  private void attachClient(String deviceId) {
    try {
      LOG.info(this.deviceId + " attaching " + deviceId);
      String topic = String.format(ATTACH_MESSAGE_FORMAT, deviceId);
      String payload = "";
      sendMessage(topic, payload.getBytes());
    } catch (Exception e) {
      LOG.error(this.deviceId + String.format(" error while binding client %s: %s", deviceId, e.toString()));
    }
  }

  private MqttClient newMqttClient(String deviceId) {
    try {
      String brokerUrl = getBrokerUrl();
      String clientId = getClientId(deviceId);
      MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      LOG.info(this.deviceId + " creating client " + clientId + " on " + brokerUrl);
      return mqttClient;
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      throw new RuntimeException("Creating new MQTT client " + deviceId, e);
    }
  }

  private void connectMqttClient(String deviceId) {
    try {
      if (mqttClient.isConnected()) {
        return;
      }
      mqttClient.setCallback(new MqttCallbackHandler());
      mqttClient.setTimeToWait(INITIALIZE_TIME_MS);
      mqttClient.setManualAcks(false);

      mqttConnectOptions = new MqttConnectOptions();
      // Note that the the Google Cloud IoT only supports MQTT 3.1.1, and Paho requires that we
      // explicitly set this. If you don't set MQTT version, the server will immediately close its
      // connection to your device.
      mqttConnectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      mqttConnectOptions.setUserName(UNUSED_ACCOUNT_NAME);
      mqttConnectOptions.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      connectAndSetupMqtt();
      connectWait.release();
    } catch (Exception e) {
      errorCounter.incrementAndGet();
      throw new RuntimeException("Connecting MQTT client " + deviceId, e);
    }
  }

  private void connectAndSetupMqtt() throws Exception {
    LOG.info(deviceId + " creating new jwt");
    mqttConnectOptions.setPassword(createJwt());
    mqttTokenSetTimeMs = System.currentTimeMillis();
    LOG.info(deviceId + " connecting to mqtt server");
    mqttClient.connect(mqttConnectOptions);
    attachedClients.clear();
    attachedClients.add(deviceId);
    LOG.info(deviceId + " adding subscriptions");
    subscribeToUpdates(deviceId);
    subscribeToErrors(deviceId);
    subscribeToCommands(deviceId);
    LOG.info(deviceId + " done with setup connection");
  }

  private void maybeRefreshJwt() {
    long refreshTime = mqttTokenSetTimeMs + TOKEN_EXPIRATION_MS / 2;
    long currentTimeMillis = System.currentTimeMillis();
    long remaining = refreshTime - currentTimeMillis;
    LOG.debug(deviceId + " remaining until refresh " + remaining);
    if (remaining < 0 && mqttClient.isConnected()) {
      try {
        mqttClient.disconnect();
        long disconnectTime = System.currentTimeMillis() - currentTimeMillis;
        LOG.info(deviceId + " disconnect took " + disconnectTime);
        connectAndSetupMqtt();
      } catch (Exception e) {
        throw new RuntimeException("While processing disconnect", e);
      }
    }
  }

  private String getClientId(String deviceId) {
    // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
    // Google Cloud IoT, it must be in the format below.
    return String.format(CLIENT_ID_FORMAT, projectId, cloudRegion, registryId, deviceId);
  }

  private String getBrokerUrl() {
    // Build the connection string for Google's Cloud IoT MQTT server. Only SSL connections are
    // accepted. For server authentication, the JVM's root certificates are used.
    return String.format(BROKER_URL_FORMAT, BRIDGE_HOSTNAME, BRIDGE_PORT);
  }

  private String getMessageTopic(String deviceId, String topic) {
    return String.format(MESSAGE_TOPIC_FORMAT, deviceId, topic);
  }

  private void subscribeToUpdates(String deviceId) {
    String updateTopic = String.format(CONFIG_UPDATE_TOPIC_FMT, deviceId);
    try {
      mqttClient.subscribe(updateTopic);
    } catch (MqttException e) {
      throw new RuntimeException("While subscribing to MQTT topic " + updateTopic, e);
    }
  }

  private void subscribeToErrors(String deviceId) {
    String updateTopic = String.format(ERROR_TOPIC_FMT, deviceId);
    try {
      mqttClient.subscribe(updateTopic);
    } catch (MqttException e) {
      throw new RuntimeException("While subscribing to MQTT topic " + updateTopic, e);
    }
  }

  private void subscribeToCommands(String deviceId) {
    String updateTopic = String.format(COMMAND_TOPIC_FMT, deviceId);
    try {
      mqttClient.subscribe(updateTopic, COMMANDS_QOS);
    } catch (MqttException e) {
      throw new RuntimeException("While subscribing to MQTT topic " + updateTopic, e);
    }
  }

  public String getDeviceId() {
    return deviceId;
  }

  private class MqttCallbackHandler implements MqttCallback {

    MqttCallbackHandler() {
    }

    /**
     * @see MqttCallback#connectionLost(Throwable)
     */
    public void connectionLost(Throwable cause) {
      LOG.warn("MQTT connection lost " + deviceId, cause);
      connectWait.release();
      onError.accept(MqttPublisher.this, cause);
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
      onMessage.accept(topic, message.toString());
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

  private char[] createJwt() {
    try {
      return createJwt(projectId, keyBytes, algorithm).toCharArray();
    } catch (Throwable t) {
      throw new RuntimeException("While creating jwt", t);
    }
  }

  /** Create a Cloud IoT JWT for the given project id, signed with the given private key */
  private String createJwt(String projectId, byte[] privateKeyBytes, String algorithm)
      throws Exception {
    DateTime now = new DateTime();

    JwtBuilder jwtBuilder =
        Jwts.builder()
            .setIssuedAt(now.toDate())
            .setExpiration(now.plusMillis(TOKEN_EXPIRATION_MS).toDate())
            .setAudience(projectId);

    switch (algorithm) {
      case "RS256": {
        PrivateKey privateKey = loadKeyBytes(privateKeyBytes, "RSA");
        return jwtBuilder.signWith(SignatureAlgorithm.RS256, privateKey).compact();
      }
      case "ES256": {
        PrivateKey privateKey = loadKeyBytes(privateKeyBytes, "EC");
        return jwtBuilder.signWith(SignatureAlgorithm.ES256, privateKey).compact();
      }
      default:
        throw new IllegalArgumentException(
            "Invalid algorithm " + algorithm + ". Should be one of 'RS256' or 'ES256'.");
    }
  }
}
