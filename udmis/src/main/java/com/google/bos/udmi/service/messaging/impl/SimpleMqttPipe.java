package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
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

/**
 * Simple pipe implementation that uses an MQTT broker.
 */
public class SimpleMqttPipe extends MessageBase {

  public static final String KEY_FILE = "rsa_private.pkcs8";
  private static final int INITIALIZE_TIME_MS = 1000;
  private static final int PUBLISH_THREAD_COUNT = 2;
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final long RECONNECT_SEC = 10;
  private static final int DEFAULT_PORT = 8883;
  private static final Envelope EXCEPTION_ENVELOPE = makeExceptionEnvelope();
  private static final String SUB_BASE_FORMAT = "/r/+/d/+/%s";
  private static final String SSL_SECRETS_DIR = System.getenv("SSL_SECRETS_DIR");
  private static final String CA_CERT_FILE = "ca.crt";
  private static final String DEFAULT_NAMESPACE = "default";
  private final String autoId = format("mqtt-%08x", (long) (Math.random() * 0x100000000L));
  private final String clientId;
  private final String namespace;
  private final EndpointConfiguration endpoint;
  private final MqttClient mqttClient;
  private final ScheduledFuture<?> scheduledFuture;
  private final CertManager certManager;
  private final String recvId;

  /**
   * Create new pipe instance for the given config.
   */
  public SimpleMqttPipe(EndpointConfiguration config) {
    super(config);
    endpoint = config;
    String namespaceRaw = variableSubstitution(endpoint.msg_prefix);
    namespace = ifTrueGet(isNotEmpty(namespaceRaw), namespaceRaw, DEFAULT_NAMESPACE);
    recvId = variableSubstitution(endpoint.recv_id);
    clientId = ofNullable(config.client_id).orElse(autoId);
    File secretsDir = ifTrueGet(isNotEmpty(SSL_SECRETS_DIR), () -> new File(SSL_SECRETS_DIR));
    certManager = ifNotNullGet(secretsDir,
        secrets -> new CertManager(new File(secrets, CA_CERT_FILE), secrets, endpoint,
            endpoint.auth_provider.basic.password, this::info));
    mqttClient = createMqttClient();
    tryConnect(false);
    scheduledFuture = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(
        () -> SimpleMqttPipe.this.tryConnect(true), 0, RECONNECT_SEC, TimeUnit.SECONDS);
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

  @Override
  protected void publishRaw(Bundle bundle) {
    try {
      String topic = makeMqttTopic(bundle);
      MqttMessage message = makeMqttMessage(bundle);
      mqttClient.publish(topic, message);
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
        debug("Attempting connection of mqtt client %s", clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
        options.setConnectionTimeout(INITIALIZE_TIME_MS);

        ifNotNullThen(endpoint.auth_provider, provider -> {
          options.setSocketFactory(getSocketFactory());
          Basic basicAuth = checkNotNull(provider.basic, "basic auth not defined");
          options.setUserName(checkNotNull(basicAuth.username, "MQTT username not defined"));
          options.setPassword(
              checkNotNull(basicAuth.password, "MQTT password not defined").toCharArray());
        });

        mqttClient.connect(options);
        info("Connection established to mqtt server as " + clientId);
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
    return message;
  }

  private String makeMqttTopic(Bundle bundle) {
    Envelope envelope = bundle.envelope;
    return envelope == null ? makeTopic(EXCEPTION_ENVELOPE) : makeTopic(envelope);
  }

  private String makeTopic(Envelope envelope) {
    return format("/r/%s/d/%s/%s/%s/%s", envelope.deviceRegistryId, envelope.deviceId,
        envelope.subType, envelope.subFolder, envelope.gatewayId);
  }

  static Map<String, String> parseEnvelopeTopic(String topic) {
    // 0/1/2       /3/4     /5   [/6     [/7      ]]
    //  /r/REGISTRY/d/DEVICE/TYPE[/FOLDER[/GATEWAY]]
    String[] parts = topic.split("/", 12);
    if (parts.length < 6 || parts.length > 8) {
      throw new RuntimeException("Unexpected topic length: " + topic);
    }
    Envelope envelope = new Envelope();
    checkState(Strings.isNullOrEmpty(parts[0]), "non-empty prefix");
    checkState("r".equals(parts[1]), "expected registries");
    envelope.deviceRegistryId = nullAsNull(parts[2]);
    checkState("d".equals(parts[3]), "expected devices");
    envelope.deviceId = nullAsNull(parts[4]);
    envelope.subType = ofNullable(nullAsNull(parts[5])).map(SubType::fromValue).orElse(null);
    if (parts.length > 6) {
      envelope.subFolder = ofNullable(nullAsNull(parts[6])).map(SubFolder::fromValue).orElse(null);
    }
    if (parts.length > 7) {
      envelope.gatewayId = nullAsNull(parts[7]);
    }
    return toStringMap(envelope);
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
    } catch (Exception e) {
      error("While attempting scheduled connect for %s: %s", clientId, friendlyStackTrace(e));
    }
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    super.activate(bundleConsumer);
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
