package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static com.google.udmi.util.GeneralUtils.nullAsNull;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.common.base.Strings;
import com.google.udmi.util.CertManager;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.IotAccess.IotProvider;

/**
 * Simple pipe implementation that uses an MQTT broker.
 */
public class SimpleMqttPipe extends MessageBase {

  private static final int MAX_INFLIGHT = 100;
  private static final String IMPLICIT_CHANNEL = IotProvider.IMPLICIT.value();
  private static final String SEND_CHANNEL_DESIGNATOR = "c";
  private static final String SEND_CHANNEL_PREFIX = SEND_CHANNEL_DESIGNATOR + "/";
  private static final int INITIALIZE_TIME_MS = 1000;
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final long RECONNECT_SEC = 10;
  private static final int DEFAULT_PORT = 8883;
  private static final String LEGACY_TOPIC_PREFIX = "/devices/";
  private static final String IMPLICIT_TOPIC_PREFIX = "/r/";
  private static final Envelope EXCEPTION_ENVELOPE = makeExceptionEnvelope();
  private static final String SUB_BASE_FORMAT = "/r/+/d/+/%s";
  private static final String DEFAULT_NAMESPACE = "default";
  private static final long CONNECT_TIMEOUT_SEC = 10;
  private final String autoId = format("mqtt-%08x", (long) (Math.random() * 0x100000000L));
  private final String clientId;
  private final String namespace;
  private final EndpointConfiguration endpoint;
  private final MqttClient mqttClient;
  private final ScheduledFuture<?> scheduledFuture;
  private final CertManager certManager;
  private final String recvId;
  private final CountDownLatch connectLatch = new CountDownLatch(1);
  private final boolean publishMessages;
  private final String sendTopicChannel;

  /**
   * Create new pipe instance for the given config.
   */
  public SimpleMqttPipe(EndpointConfiguration config) {
    super(config);
    endpoint = config;
    String namespaceRaw = variableSubstitution(endpoint.topic_prefix);
    namespace = ifTrueGet(isNotEmpty(namespaceRaw), namespaceRaw, DEFAULT_NAMESPACE);
    recvId = variableSubstitution(endpoint.recv_id);

    publishMessages = endpoint.send_id != null;
    String sendId = variableSubstitution(endpoint.send_id);
    sendTopicChannel =
        (publishMessages && sendId.startsWith(SEND_CHANNEL_PREFIX)) ? ("/" + sendId) : "";

    clientId = ofNullable(config.client_id).orElse(autoId);
    boolean useSsl = Transport.SSL.equals(endpoint.transport)
        || (endpoint.port != null && endpoint.port == 8883);
    if (useSsl) {
      checkState(isNotEmpty(endpoint.ca_file),
          "Missing required ca_file in endpoint configuration for SSL connection");
      checkState(isNotEmpty(endpoint.cert_file),
          "Missing required cert_file in endpoint configuration for SSL connection");
      checkState(isNotEmpty(endpoint.key_file),
          "Missing required key_file in endpoint configuration for SSL connection");
      String pass = ifNotNullGet(endpoint.auth_provider,
          p -> ifNotNullGet(p.basic, b -> b.password));
      certManager = new CertManager(new File(endpoint.ca_file), new File(endpoint.cert_file),
          new File(endpoint.key_file), endpoint.transport, pass, this::info);
    } else {
      certManager = null;
    }
    mqttClient = createMqttClient();
    tryConnect(false);
    scheduledFuture = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(
        () -> SimpleMqttPipe.this.tryConnect(true), 0, RECONNECT_SEC, TimeUnit.SECONDS);
  }

  private static SubFolder convertSubFolder(String part) {
    return catchToElse(() -> ofNullable(nullAsNull(part)).map(SubFolder::fromValue).orElse(null),
        SubFolder.INVALID);
  }

  private static SubType convertSubType(String part) {
    return catchToElse(() -> ofNullable(nullAsNull(part))
        .map(SubType::fromValue).orElse(null), SubType.INVALID);
  }

  public static MessagePipe fromConfig(EndpointConfiguration config) {
    return new SimpleMqttPipe(config);
  }

  private static Envelope makeExceptionEnvelope() {
    Envelope envelope = new Envelope();
    envelope.subFolder = SubFolder.ERROR;
    envelope.subType = SubType.INVALID;
    return envelope;
  }

  public static boolean isLegacyTopic(String topic) {
    return topic != null && topic.startsWith(LEGACY_TOPIC_PREFIX);
  }

  private static Map<String, String> parseLegacyTopic(String topic) {
    if (topic == null) {
      throw new IllegalArgumentException("Topic cannot be null");
    }
    String cleanTopic = topic.startsWith("/") ? topic.substring(1) : topic;
    String[] parts = cleanTopic.split("/");
    Envelope envelope = new Envelope();
    if (parts.length >= 2 && !parts[1].isEmpty()) {
      envelope.deviceId = nullAsNull(parts[1]);
    }
    if (parts.length >= 3 && !parts[2].isEmpty()) {
      envelope.subType = convertSubType(parts[2]);
    }
    if (parts.length >= 4 && !parts[3].isEmpty()) {
      // TODO: technically the subfolder is the remainder including all slashes until the end.
      envelope.subFolder = convertSubFolder(parts[3]);
    }
    return toStringMap(envelope);
  }

  private static boolean isUufiTopic(String topic) {
    if (topic == null) {
      return false;
    }
    String[] parts = topic.split("/", 12);
    int start = Strings.isNullOrEmpty(parts[0]) ? 1 : 0;
    return parts.length > start && "uufi".equals(parts[start]);
  }

  private static Map<String, String> parseUufiTopic(String topic) {
    String[] parts = topic.split("/", 12);
    int start = Strings.isNullOrEmpty(parts[0]) ? 1 : 0;
    Envelope envelope = new Envelope();

    // UUFI topic: [/namespace]/uufi/[r/REG/d/DEV/]c/TYPE/FOLDER
    int base = start + 1;
    if ("r".equals(parts[base])) {
      envelope.deviceRegistryId = nullAsNull(parts[base + 1]);
      checkState("d".equals(parts[base + 2]), "expected devices");
      envelope.deviceId = nullAsNull(parts[base + 3]);
      base += 4;
    }
    checkState("c".equals(parts[base]), "expected commands");
    envelope.subType = convertSubType(parts[base + 1]);
    envelope.subFolder = convertSubFolder(parts[base + 2]);
    envelope.gatewayId = "uufi";
    return toStringMap(envelope);
  }

  private static Map<String, String> parseImplicitTopic(String topic) {
    if (topic == null) {
      throw new IllegalArgumentException("Topic cannot be null");
    }
    // 0/1/2       /3/4     /5   [/6     [/7      ]]
    //  /r/REGISTRY/d/DEVICE/TYPE[/FOLDER[/GATEWAY]]
    String[] parts = topic.split("/", 12);
    if (parts.length < 6 || parts.length > 10) {
      throw new IllegalArgumentException("Unexpected topic length: " + topic);
    }
    Envelope envelope = new Envelope();
    checkState(Strings.isNullOrEmpty(parts[0]), "non-empty prefix");
    checkState("r".equals(parts[1]), "expected registries");
    envelope.deviceRegistryId = nullAsNull(parts[2]);
    checkState("d".equals(parts[3]), "expected devices");
    envelope.deviceId = nullAsNull(parts[4]);
    int base = parts[5].equals(SEND_CHANNEL_DESIGNATOR) ? 2 : 0;
    if (parts.length < base + 6) {
      throw new IllegalArgumentException("Unexpected topic length for implicit topic: " + topic);
    }
    if (base > 0) {
      envelope.source = parts[6];
    }
    envelope.subType = convertSubType(parts[base + 5]);
    if (parts.length > base + 6 && !parts[base + 6].isEmpty()) {
      envelope.subFolder = convertSubFolder(parts[base + 6]);
    }
    if (parts.length > base + 7 && !parts[base + 7].isEmpty()) {
      envelope.gatewayId = nullAsNull(parts[base + 7]);
    }
    if (parts.length > base + 8) {
      throw new IllegalArgumentException("Unrecognized extra topic arguments: " + parts[base + 8]);
    }
    return toStringMap(envelope);
  }

  /**
   * Parse an MQTT envelope topic (either legacy or implicit format) into attributes map.
   *
   * @param topic The MQTT topic string
   * @return Map of envelope attributes
   */
  public static Map<String, String> parseEnvelopeTopic(String topic) {
    try {
      if (isUufiTopic(topic)) {
        return parseUufiTopic(topic);
      } else if (isLegacyTopic(topic)) {
        return parseLegacyTopic(topic);
      } else if (topic != null && topic.startsWith(IMPLICIT_TOPIC_PREFIX)) {
        return parseImplicitTopic(topic);
      } else {
        throw new IllegalArgumentException("Unrecognized topic structure: " + topic);
      }
    } catch (Exception e) {
      throw new RuntimeException("While parsing envelope topic " + topic, e);
    }
  }

  @Override
  public void publishRaw(Bundle bundle) {
    if (endpoint.send_id == null && !"uufi".equals(bundle.envelope.gatewayId)) {
      trace("Dropping message because no send_id");
      return;
    }
    try {
      String topic = makeMqttTopic(bundle);
      MqttMessage message = makeMqttMessage(bundle);
      String payload = new String(message.getPayload());
      captureMessage(topic, payload, false);
      debug("MQTT publish from %s to %s: %s", clientId, topic, payload);
      mqttClient.publish(topic, message);
      int tokens = mqttClient.getPendingDeliveryTokens().length;
      ifTrueThen(tokens > 2, () ->
          debug("Client has %d inFlight tokens, from %s", tokens, topic));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to mqtt client " + clientId, e);
    }
  }

  private void captureMessage(String topic, String message, boolean incoming) {
    try (PrintWriter out = new PrintWriter(new FileOutputStream(new File("out/udmis_messages.log"), true))) {
      String direction = incoming ? "<<<" : ">>>";
      out.printf("%s %s %s %s: %s%n", JsonUtil.currentIsoMs(), clientId, direction, topic, message);
    } catch (Exception e) {
      // Ignore errors writing to capture log
    }
  }

  private void connect(boolean forceDisconnect) {
    try {
      synchronized (mqttClient) {
        if (mqttClient.isConnected()) {
          return;
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setMaxInflight(MAX_INFLIGHT);
        options.setConnectionTimeout(INITIALIZE_TIME_MS);

        ifNotNullThen(endpoint.auth_provider, provider -> {
          options.setSocketFactory(getSocketFactory());
          Basic basicAuth = checkNotNull(provider.basic, "basic auth not defined");
          options.setUserName(checkNotNull(basicAuth.username, "MQTT username not defined"));
          options.setPassword(
              checkNotNull(basicAuth.password, "MQTT password not defined").toCharArray());
          info("Set MQTT basic auth username as %s for client %s", basicAuth.username, clientId);
        });

        info("Attempting mqtt connection for %s to %s", clientId, mqttClient.getServerURI());
        mqttClient.connect(options);
        info("Established mqtt connection as %s", clientId);
        subscribeToMessages();
      }
    } catch (Exception e) {
      // Sometimes a forced disconnect is necessary else the connection attempt gets stuck somehow.
      ifTrueThen(forceDisconnect, this::forceDisconnect);
      throw new RuntimeException("While connecting mqtt client", e);
    }
  }

  private MqttClient createMqttClient() {
    String broker = makeBrokerUrl(endpoint);
    info(format("Creating new mqtt client %s to %s", clientId, broker));
    try {
      MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
      client.setCallback(new MqttCallbackHandler());
      client.setTimeToWait(INITIALIZE_TIME_MS);
      return client;
    } catch (Exception e) {
      throw new RuntimeException("While creating mqtt client", e);
    }
  }

  private void forceDisconnect() {
    try {
      mqttClient.disconnectForcibly();
    } catch (Exception e) {
      error("Exception during forced disconnect %s: %s", clientId, friendlyStackTrace(e));
    }
  }

  private SocketFactory getSocketFactory() {
    return ofNullable(certManager).map(CertManager::getSocketFactory)
        .orElse(SSLSocketFactory.getDefault());
  }

  private String makeBrokerUrl(EndpointConfiguration endpoint) {
    Transport transport = ofNullable(endpoint.transport).orElse(Transport.SSL);
    int port = ofNullable(endpoint.port).orElse(DEFAULT_PORT);
    return format(BROKER_URL_FORMAT, transport, endpoint.hostname, port);
  }

  private MqttMessage makeMqttMessage(Bundle bundle) {
    MqttMessage message = new MqttMessage();
    message.setPayload(bundle.sendBytes());
    message.setRetained(shouldRetainMessage(bundle));
    return message;
  }

  private String makeMqttTopic(Bundle bundle) {
    Envelope envelope = bundle.envelope;
    return envelope == null ? makeTopic(EXCEPTION_ENVELOPE) : makeTopic(envelope);
  }

  private String makeTopic(Envelope envelope) {
    if ("uufi".equals(envelope.gatewayId)) {
      String topic = isNotEmpty(namespace) && !"default".equals(namespace) ? "/" + namespace : "";
      topic += "/uufi";
      if (envelope.deviceRegistryId != null) {
        topic += "/r/" + envelope.deviceRegistryId;
        if (envelope.deviceId != null) {
          topic += "/d/" + envelope.deviceId;
        }
      }
      topic += "/c/" + envelope.subType + "/" + envelope.subFolder;
      return topic;
    }

    String topic = "";
    if (envelope.gatewayId != null) {
      topic = "/" + envelope.gatewayId + topic;
    }
    if (envelope.subFolder != null || !topic.isEmpty()) {
      topic = "/" + envelope.subFolder + topic;
    }
    if (envelope.subType != null || !topic.isEmpty()) {
      topic = "/" + envelope.subType + topic;
    }
    String channel = IMPLICIT_CHANNEL.equals(envelope.source) ? "" : sendTopicChannel;
    return format("/r/%s/d/%s%s%s", envelope.deviceRegistryId, envelope.deviceId, channel, topic);
  }

  private String makeTransactionId() {
    return format("MP:%08x", (long) (Math.random() * 0x100000000L));
  }

  private boolean shouldRetainMessage(Bundle bundle) {
    return bundle.envelope.subType == SubType.CONFIG;
  }

  private void subscribeToMessages() {
    if (endpoint.recv_id == null) {
      info("No recv_id defined, not subscribing for component " + endpoint.name);
      return;
    }
    String subscribeTopic = recvId.startsWith("/") ? recvId : format(SUB_BASE_FORMAT, recvId);
    try {
      synchronized (mqttClient) {
        boolean connected = mqttClient.isConnected();
        trace("Subscribing %s, active=%s connected=%s", clientId, isActive(), connected);
        if (isActive() && connected) {
          mqttClient.subscribe(subscribeTopic);
          info("Subscribed %s to topic %s", clientId, subscribeTopic);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While subscribing to mqtt topic: " + subscribeTopic, e);
    }
  }

  private void tryConnect(boolean forceDisconnect) {
    try {
      connect(forceDisconnect);
      connectLatch.countDown();
    } catch (Exception e) {
      error("While attempting scheduled connect for %s: %s", clientId, friendlyStackTrace(e));
    }
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    super.activate(bundleConsumer);
    try {
      checkState(connectLatch.await(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS), "connect timeout");
    } catch (Exception e) {
      throw new RuntimeException("Failed initial connection attempt", e);
    }
    subscribeToMessages();
  }

  @Override
  public void shutdown() {
    try {
      scheduledFuture.cancel(false);
      mqttClient.disconnect();
      mqttClient.close();
    } catch (Exception e) {
      throw new RuntimeException("While shutdown of mqtt pipe", e);
    }
    super.shutdown();
  }

  private class MqttCallbackHandler implements MqttCallback {

    @Override
    public void connectionLost(Throwable cause) {
      error("Connection lost for %s: %s", clientId, friendlyStackTrace(cause));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      String payload = new String(message.getPayload());
      captureMessage(topic, payload, true);
      try {
        debug("MQTT message arrived on %s: %s", topic, payload);
        Map<String, String> envelopeMap = parseEnvelopeTopic(topic);
        envelopeMap.put(PUBLISH_TIME_KEY, isoConvert());
        envelopeMap.put(TRANSACTION_KEY, makeTransactionId());
        receiveMessage(envelopeMap, payload);
      } catch (Exception e) {
        error("Exception receiving message on %s: %s", clientId, friendlyStackTrace(e));
        debug("Full error details for %s: %s", topic, stackTraceString(e));
      }
    }
  }

  @Override
  void resetForTest() {
    super.resetForTest();
    shutdown();
  }
}
