package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.ContainerBase;
import java.io.BufferedReader;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Provider that links directly to a mosquitto broker.
 */
public class MosquittoBroker extends ContainerBase implements ConnectionBroker {

  private static final String UDMI_ROOT = System.getenv("UDMI_ROOT");
  private static final String MOSQUCTL_CLIENT_FMT = UDMI_ROOT + "/bin/mosquctl_client %s %s";
  private static final String MOSQUCTL_LOG_FMT = UDMI_ROOT + "/bin/mosquctl_log %s";
  private static final long EXEC_TIMEOUT_SEC = 10;
  private static final String REVOKE_PASSWORD = "--";
  private static final Pattern LOG_MATCHER =
      Pattern.compile("([0-9]+): (\\S+) (\\S+) (\\S+) (\\S+) (.*)");
  private static final Pattern PUBLISH_MATCHER =
      Pattern.compile("\\(([d01,qr ]+), m([0-9]+), '(\\S+)', .*\\)");
  private static final Pattern PUBACK_MATCHER = Pattern.compile("\\((m|Mid: )([0-9]+), \\S+\\)");
  private final ContainerBase container;
  private final String brokerHost;
  private final String brokerPort;
  private final String brokerUser;
  private final String brokerPass;
  private MqttClient mqttClient;
  private final String clientId =
      format("mosquitto-helper-%08x", (long) (Math.random() * 0x100000000L));

  /**
   * Create a new instance of the broker helper.
   */
  public MosquittoBroker(ContainerBase container, String host, String port, String user,
      String pass) {
    this.container = container;
    this.brokerHost = ofNullable(host).orElse("localhost");
    this.brokerPort = ofNullable(port).orElse("8883");
    this.brokerUser = user;
    this.brokerPass = pass;
  }

  private void consumeLogs(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    info("Starting log consumer for " + clientPrefix);
    mosquctlLog(clientPrefix, eventConsumer);
  }

  private void consumeStream(BufferedReader reader, Consumer<String> consumer) {
    Thread thread = new Thread(() -> {
      String line;
      try {
        while ((line = reader.readLine()) != null) {
          consumer.accept(line);
        }
      } catch (Exception e) {
        warn("Exception consuming stream: " + friendlyStackTrace(e));
        consumer.accept(e.getMessage());
      }
    });
    thread.setDaemon(true);
    thread.start();
  }

  private void mosquctlClient(String clientId, String clientPass) {
    String cmd = format(MOSQUCTL_CLIENT_FMT, clientId, clientPass);
    synchronized (MosquittoBroker.class) {
      try {
        info("Executing command %s", cmd);
        Process exec = Runtime.getRuntime().exec(cmd);
        exec.waitFor(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS);
        exec.errorReader().lines().forEach(container::info);
        exec.inputReader().lines().forEach(container::info);
        int exitValue = exec.exitValue();
        checkState(exitValue == 0, "exit return code " + exitValue);
      } catch (Exception e) {
        throw new RuntimeException("While executing " + cmd, e);
      }
    }
  }

  private void mosquctlLog(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    String cmd = format(MOSQUCTL_LOG_FMT, clientPrefix);
    synchronized (MosquittoBroker.class) {
      try {
        info("Starting log consumer %s", cmd);
        Process exec = Runtime.getRuntime().exec(cmd);
        consumeStream(exec.errorReader(), line -> warn("log error: " + line));
        consumeStream(exec.inputReader(), line -> ifNotNullThen(parseLogLine(line), eventConsumer));
      } catch (Exception e) {
        throw new RuntimeException("While executing " + cmd, e);
      } finally {
        info("Completed log consumer");
      }
    }
  }

  private BrokerEvent parseLogLine(String line) {
    BrokerEvent brokerEvent = new BrokerEvent();
    try {
      if (line == null) {
        return null;
      }

      Matcher matcher = LOG_MATCHER.matcher(line);
      if (!matcher.matches()) {
        return null;
      }

      brokerEvent.timestamp = new Date(Long.parseLong(matcher.group(1)) * 1000);
      brokerEvent.operation = catchToElse(() -> Operation.valueOf(matcher.group(3)),
          Operation.UNKNOWN);
      brokerEvent.clientId = matcher.group(5);
      brokerEvent.direction = catchToNull(() -> Direction.valueOf(matcher.group(2)));
      String detail = matcher.group(6);
      return switch (brokerEvent.operation) {
        case PUBLISH -> parsePublishEvent(brokerEvent, detail);
        case PUBACK -> parsePubackEvent(brokerEvent, detail);
        default -> null;
      };
    } catch (Exception e) {
      brokerEvent.detail = friendlyStackTrace(e);
      brokerEvent.operation = Operation.EXCEPTION;
      return brokerEvent;
    }
  }

  private BrokerEvent parsePubackEvent(BrokerEvent brokerEvent, String detail) {
    Matcher matcher = PUBACK_MATCHER.matcher(detail);
    checkState(matcher.matches(), "puback detail does not match regex " + detail);
    brokerEvent.mesageId = Integer.parseInt(matcher.group(2));
    return brokerEvent;
  }

  private BrokerEvent parsePublishEvent(BrokerEvent brokerEvent, String detail) {
    Matcher matcher = PUBLISH_MATCHER.matcher(detail);
    checkState(matcher.matches(), "publish detail does not match regex " + detail);
    brokerEvent.mesageId = Integer.parseInt(matcher.group(2));
    brokerEvent.detail = matcher.group(3);
    return brokerEvent;
  }

  @Override
  public Future<Void> addEventListener(String clientPrefix,
      Consumer<BrokerEvent> eventConsumer) {
    return CompletableFuture.runAsync(() -> consumeLogs(clientPrefix, eventConsumer));
  }

  @Override
  public void authorize(String clientId, String password) {
    mosquctlClient(clientId, ofNullable(password).orElse(REVOKE_PASSWORD));
  }

  @Override
  public boolean isPublishEnabled() {
    return brokerUser != null && brokerPass != null;
  }

  @Override
  public void activate() {
    super.activate();
    if (brokerUser != null && brokerPass != null) {
      connectMqttClient();
    }
  }

  @Override
  public void shutdown() {
    ifNotNullThen(mqttClient, client -> {
      try {
        client.disconnect();
        client.close();
      } catch (Exception e) {
        warn("Error shutting down MQTT client: " + e.getMessage());
      }
    });
    super.shutdown();
  }

  @Override
  public void publish(String topic, String payload, boolean retain) {
    checkState(mqttClient != null && mqttClient.isConnected(), "MQTT client not connected");
    try {
      MqttMessage message = new MqttMessage();
      message.setPayload(payload.getBytes());
      message.setQos(1);
      message.setRetained(retain);
      mqttClient.publish(topic, message);
      debug("Published to %s: %s", topic, payload);
    } catch (Exception e) {
      throw new RuntimeException("While publishing to MQTT topic " + topic, e);
    }
  }

  private void connectMqttClient() {
    String scheme = "1883".equals(brokerPort) ? "tcp" : "ssl";
    String brokerUrl = format("%s://%s:%s", scheme, brokerHost, brokerPort);
    info("Connecting persistent MQTT client to " + brokerUrl);
    try {
      mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setUserName(brokerUser);
      options.setPassword(brokerPass.toCharArray());
      options.setCleanSession(true);
      options.setAutomaticReconnect(true);
      mqttClient.connect(options);
      info("Connected persistent MQTT client");
    } catch (Exception e) {
      throw new RuntimeException("Failed to connect persistent MQTT client to " + brokerUrl, e);
    }
  }
}
