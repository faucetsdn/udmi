package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
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
import java.io.File;
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

  private static final int MAX_INFLIGHT = 10;
  private static final String IMPLICIT_CHANNEL = IotProvider.IMPLICIT.value();
  private static final String SEND_CHANNEL_DESIGNATOR = "c";
  private static final String SEND_CHANNEL_PREFIX = SEND_CHANNEL_DESIGNATOR + "/";
  private static final int INITIALIZE_TIME_MS = 1000;
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final long RECONNECT_SEC = 10;
  private static final int DEFAULT_PORT = 8883;
  private static final Envelope EXCEPTION_ENVELOPE = makeExceptionEnvelope();
  private static final String SUB_BASE_FORMAT = "/r/+/d/+/%s";
  private static final String SSL_SECRETS_DIR = System.getenv("SSL_SECRETS_DIR");
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
    File secretsDir = ifTrueGet(isNotEmpty(SSL_SECRETS_DIR), () -> new File(SSL_SECRETS_DIR));
    certManager = ifNotNullGet(secretsDir,
        secrets -> new CertManager(new File(secrets, CertManager.CA_CERT_FILE), secrets,
            endpoint.transport, endpoint.auth_provider.basic.password, this::info));
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

  static Map<String, String> parseEnvelopeTopic(String topic) {
    try {
      // 0/1/2       /3/4     /5   [/6     [/7      ]]
      //  /r/REGISTRY/d/DEVICE/TYPE[/FOLDER[/GATEWAY]]
      String[] parts = topic.split("/", 12);
      if (parts.length < 6 || parts.length > 10) {
        throw new RuntimeException("Unexpected topic length: " + topic);
      }
      Envelope envelope = new Envelope();
      checkState(Strings.isNullOrEmpty(parts[0]), "non-empty prefix");
      checkState("r".equals(parts[1]), "expected registries");
      envelope.deviceRegistryId = nullAsNull(parts[2]);
      checkState("d".equals(parts[3]), "expected devices");
      envelope.deviceId = nullAsNull(parts[4]);
      int base = parts[5].equals(SEND_CHANNEL_DESIGNATOR) ? 2 : 0;
      if (base > 0) {
        envelope.source = parts[6];
      }
      envelope.subType = convertSubType(parts[base + 5]);
      if (parts.length > base + 6) {
        envelope.subFolder = convertSubFolder(parts[base + 6]);
      }
      if (parts.length > base + 7) {
        envelope.gatewayId = nullAsNull(parts[base + 7]);
      }
      if (parts.length > base + 8) {
        throw new RuntimeException("Unrecognized extra topic arguments: " + parts[base + 8]);
      }
      return toStringMap(envelope);
    } catch (Exception e) {
      throw new RuntimeException("While parsing envelope topic " + topic, e);
    }
  }

  @Override
  protected void publishRaw(Bundle bundle) {
    if (!publishMessages) {
      trace("Dropping message because no send_id");
      return;
    }
    try {
      String topic = makeMqttTopic(bundle);
      MqttMessage message = makeMqttMessage(bundle);
      mqttClient.publish(topic, message);
      int tokens = mqttClient.getPendingDeliveryTokens().length;
      ifTrueThen(tokens > 2, () ->
          debug("Client has %d inFlight tokens, from %s", tokens, topic));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to mqtt client " + clientId, e);
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
          debug("Set MQTT basic auth username/password as %s/%s", basicAuth.username,
              basicAuth.password);
        });

        debug("Attempting mqtt connection as %s", clientId);
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
    String subscribeTopic = format(SUB_BASE_FORMAT, recvId);
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
      try {
        Map<String, String> envelopeMap = parseEnvelopeTopic(topic);
        envelopeMap.put(PUBLISH_TIME_KEY, isoConvert());
        envelopeMap.put(TRANSACTION_KEY, makeTransactionId());
        receiveMessage(envelopeMap, new String(message.getPayload()));
      } catch (Exception e) {
        error("Exception receiving message on %s: %s", clientId, friendlyStackTrace(e));
      }
    }
  }

  @Override
  void resetForTest() {
    super.resetForTest();
    shutdown();
  }
}
