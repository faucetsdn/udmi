package com.google.bos.udmi.service.bridge;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.bos.udmi.service.support.EtcdDataProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

/**
 * A bridge that subscribes to an MQTT topic and publishes messages to a Google Cloud Pub/Sub topic.
 */
public class MqttToPubSubBridge {

  private static final Pattern TOPIC_PATTERN = Pattern.compile("/r/([^/]+)/d/([^/]+)/?(.*)");
  private static final Logger logger = LoggerFactory.getLogger(MqttToPubSubBridge.class);

  // Maximum capacity for the in-memory executor task queue.
  // This acts as a defensive 10x safety buffer above MQTT v5 Receive Maximum (100).
  // Under normal MQTT v5 operation, the broker throttles delivery to at most 100 in-flight
  // unacknowledged messages, so this 1000-element queue is a defensive precaution for bursts,
  // QoS 0 traffic, or MQTT v3.1.1 fallback.
  private static final int MAX_QUEUE_SIZE = 1000;
  private static final int NUM_THREADS = 24;
  public static final int DEFAULT_UNACKED_THRESHOLD = 100;
  public static final long DEFAULT_CIRCUIT_BREAKER_TIMEOUT_MS = 300000L; // 5 minutes

  /**
   * Represents the lifecycle state of an unacknowledged MQTT message.
   */
  public enum MessageState {
    IN_PROCESS,
    ABANDONED
  }

  /**
   * Represents a recorded message entry tracking its state and insertion timestamp.
   */
  public static class MessageRecord {
    private final long timestamp;
    private volatile MessageState state;

    public MessageRecord(MessageState state) {
      this(state, System.currentTimeMillis());
    }

    public MessageRecord(MessageState state, long timestamp) {
      this.state = state;
      this.timestamp = timestamp;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public MessageState getState() {
      return state;
    }

    public void setState(MessageState state) {
      this.state = state;
    }
  }

  private static final Cache<String, String> numIdCache = CacheBuilder.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .maximumSize(10000)
      .build();

  private final ThreadPoolExecutor executor;
  private final ScheduledExecutorService retryScheduler;
  private volatile boolean tripped = false;
  private volatile boolean stopping = false;
  private final ConcurrentHashMap<String, MessageRecord> unackedMessages =
      new ConcurrentHashMap<>();

  public int getUnackedCount() {
    return unackedMessages.size();
  }

  public int getInProcessCount() {
    return (int) unackedMessages.values().stream()
        .filter(r -> r.getState() == MessageState.IN_PROCESS).count();
  }

  public int getAbandonedCount() {
    return (int) unackedMessages.values().stream()
        .filter(r -> r.getState() == MessageState.ABANDONED).count();
  }

  public MessageState getMessageState(String messageKey) {
    MessageRecord record = unackedMessages.get(messageKey);
    return record != null ? record.getState() : null;
  }

  public MessageRecord getMessageRecord(String messageKey) {
    return unackedMessages.get(messageKey);
  }

  void putMessageRecordForTest(String messageKey, MessageRecord record) {
    if (record == null) {
      unackedMessages.remove(messageKey);
    } else {
      unackedMessages.put(messageKey, record);
    }
  }

  static String getMessageHash(String topic, MqttMessage message) {
    byte[] payload = message.getPayload() != null ? message.getPayload() : new byte[0];
    String payloadHash = Hashing.murmur3_128().hashBytes(payload).toString();
    String prefix = topic != null && !topic.isEmpty() ? topic + ":" : "";
    return prefix + message.getId() + ":" + payloadHash;
  }

  static String getMessageHash(MqttMessage message) {
    return getMessageHash("", message);
  }

  static void clearCacheForTest() {
    numIdCache.invalidateAll();
  }

  /**
   * Initializes a new instance of the bridge, configuring the underlying executor.
   */
  public MqttToPubSubBridge() {
    this.executor = new ThreadPoolExecutor(
        NUM_THREADS, NUM_THREADS,
        0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_QUEUE_SIZE),
        new ThreadFactoryBuilder().setNameFormat("mqtt-bridge-%d").setDaemon(true).build(),
        (runnable, exec) -> {
          if (exec.isShutdown()) {
            throw new RejectedExecutionException("Executor is shut down");
          }
          try {
            // Block up to 2 seconds to absorb transient bursts and apply TCP backpressure
            boolean added = exec.getQueue().offer(runnable, 2, TimeUnit.SECONDS);
            if (!added) {
              throw new RejectedExecutionException("Executor queue full after 2s wait");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException(
                "Interrupted while waiting for executor queue capacity", e);
          }
        });
    this.retryScheduler = Executors.newScheduledThreadPool(4,
        new ThreadFactoryBuilder().setNameFormat("mqtt-bridge-retry-%d").setDaemon(true).build());
  }

  /**
   * Checks if the circuit breaker has been tripped.
   *
   * @return true if tripped, false otherwise
   */
  public boolean isTripped() {
    return tripped;
  }

  void setTrippedForTest(boolean tripped) {
    this.tripped = tripped;
  }

  public boolean isStopping() {
    return stopping;
  }

  public void setStopping(boolean stopping) {
    this.stopping = stopping;
  }

  private void startCircuitBreaker(IMqttClient mqttClient) {
    startCircuitBreaker(mqttClient, DEFAULT_UNACKED_THRESHOLD, DEFAULT_CIRCUIT_BREAKER_TIMEOUT_MS);
  }

  void startCircuitBreaker(IMqttClient mqttClient, int abandonedThreshold, long timeoutMs) {
    Thread monitor = new Thread(() -> {
      while (!tripped && !stopping) {
        try {
          Thread.sleep(1000);
          long now = System.currentTimeMillis();

          // 1. Check if any message is stuck in-flight longer than timeoutMs
          boolean hasStuckMessage = unackedMessages.values().stream()
              .filter(record -> record != null && record.getState() == MessageState.IN_PROCESS)
              .anyMatch(record -> (now - record.getTimestamp()) >= timeoutMs);

          // 2. Check if abandoned messages have exhausted capacity (>= abandonedThreshold)
          boolean abandonedExceeded = getAbandonedCount() >= abandonedThreshold;

          if (hasStuckMessage || abandonedExceeded) {
            tripped = true;
            if (hasStuckMessage) {
              logger.error(
                  "Message stuck in unacked queue for >= {}ms! Tripping circuit breaker.",
                  timeoutMs);
            }
            if (abandonedExceeded) {
              logger.error(
                  "Abandoned message threshold ({} messages) reached! Tripping circuit breaker.",
                  abandonedThreshold);
            }
            try {
              mqttClient.disconnectForcibly();
            } catch (Exception e) {
              logger.error("Error disconnecting during circuit breaker trip", e);
            }
            exit(1);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          logger.error("Error in circuit breaker monitor", e);
        }
      }
    });
    monitor.setDaemon(true);
    monitor.setName("mqtt-circuit-breaker");
    monitor.start();
  }

  protected void exit(int status) {
    System.exit(status);
  }

  void startPeriodicReconnect(IMqttClient mqttClient, int baseIntervalSec) {
    if (baseIntervalSec <= 0) {
      return;
    }
    Thread reconnectThread = new Thread(() -> {
      while (!tripped) {
        try {
          // Jitter: +/- 50% random delay around baseIntervalSec
          int jitterSec = (int) (baseIntervalSec * 0.5);
          int delaySec = baseIntervalSec
              + (jitterSec > 0
              ? (ThreadLocalRandom.current().nextInt(jitterSec * 2 + 1) - jitterSec) : 0);
          Thread.sleep(Math.max(1, delaySec) * 1000L);
          if (mqttClient.isConnected() && !tripped) {
            logger.info(
                "Executing scheduled reconnect (interval: {}s) to rebalance shared subscription...",
                delaySec);
            try {
              mqttClient.disconnect(1000L);
            } catch (Exception e) {
              logger.warn("Error disconnecting for scheduled reconnect", e);
            }
            try {
              mqttClient.reconnect();
            } catch (Exception e) {
              logger.error(
                  "FATAL: Error reconnecting during scheduled reconnect. Exiting for K8s restart.",
                  e);
              exit(1);
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          logger.warn("Error during scheduled reconnect", e);
        }
      }
    });
    reconnectThread.setDaemon(true);
    reconnectThread.setName("mqtt-reconnect-timer");
    reconnectThread.start();
  }

  /**
   * Main entry point for the bridge.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {
    CommandLine commandLine;
    try {
      commandLine = parseArgs(args);
      if (commandLine == null) {
        System.exit(0);
      }
    } catch (ParseException e) {
      logger.error("Failed to parse arguments", e);
      System.exit(1);
      return; // Unreachable but satisfies compiler
    }

    String mqttBrokerUrl = commandLine.getOptionValue("mqtt_broker_url", "tcp://localhost:1883");
    String mqttSubscriptionTopic =
        commandLine.getOptionValue("mqtt_subscription_topic", "/r/+/d/#");
    String gcpProjectId = commandLine.getOptionValue("gcp_project_id");
    String pubsubTopicId = commandLine.getOptionValue("pubsub_topic_id");
    String mqttClientId = commandLine.getOptionValue("mqtt_client_id");
    String mqttSessionExpiryInterval = commandLine.getOptionValue("mqtt_session_expiry_interval");
    String mqttKeepAliveInterval = commandLine.getOptionValue("mqtt_keep_alive_interval", "15");
    int keepAliveInterval = Integer.parseInt(mqttKeepAliveInterval);
    boolean mqttTls = commandLine.hasOption("mqtt_tls");
    int reconnectIntervalSec = Integer.parseInt(
        commandLine.getOptionValue("mqtt_reconnect_interval_sec", "900"));
    int circuitBreakerThreshold = Integer.parseInt(
        commandLine.getOptionValue("circuit_breaker_unacked_threshold",
            String.valueOf(DEFAULT_UNACKED_THRESHOLD)));
    long circuitBreakerTimeoutMs = Long.parseLong(
        commandLine.getOptionValue("circuit_breaker_timeout_sec", "300")) * 1000L;
    boolean cleanStart = commandLine.hasOption("mqtt_clean_start")
        || commandLine.hasOption("clean_start");
    String mqttCaPath = commandLine.getOptionValue("mqtt_ca_path");
    String mqttUsername = commandLine.getOptionValue("mqtt_username");
    String mqttPassword = commandLine.getOptionValue("mqtt_password");
    String mqttClientCertPath = commandLine.getOptionValue("mqtt_client_cert_path");
    String mqttClientKeyPath = commandLine.getOptionValue("mqtt_client_key_path");
    String etcdTarget = commandLine.getOptionValue("etcd_target");
    String etcdOptions = getEtcdOptions(commandLine);

    String sourceAttribute = commandLine.getOptionValue("source_attribute", "bridge");
    String sharedSubscription = commandLine.getOptionValue("shared_subscription");

    if (gcpProjectId == null || pubsubTopicId == null || mqttClientId == null) {
      logger.error("gcp_project_id, pubsub_topic_id, and mqtt_client_id are required.");
      System.exit(1);
    }

    Long sessionExpiryInterval = 0xFFFFFFFFL;
    if (mqttSessionExpiryInterval != null) {
      sessionExpiryInterval = Long.parseLong(mqttSessionExpiryInterval);
    }

    Publisher publisher = null;
    IMqttClient mqttClient = null;
    EtcdDataProvider etcdProvider = null;
    MqttToPubSubBridge bridge = null;
    AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    Thread shutdownHook = null;

    try {
      etcdProvider = createEtcdProvider(etcdTarget, etcdOptions);
      publisher = createPublisher(gcpProjectId, pubsubTopicId);

      // Initialize MQTT Client
      mqttClient =
          new MqttClient(
              mqttBrokerUrl, mqttClientId, new MemoryPersistence());
      MqttConnectionOptions connOpts = new MqttConnectionOptions();
      connOpts.setCleanStart(cleanStart);
      connOpts.setSessionExpiryInterval(sessionExpiryInterval);
      connOpts.setKeepAliveInterval(keepAliveInterval);
      connOpts.setAutomaticReconnect(true);
      connOpts.setReceiveMaximum(100);

      if (mqttUsername != null && !mqttUsername.isEmpty()) {
        connOpts.setUserName(mqttUsername);
      }
      if (mqttPassword != null && !mqttPassword.isEmpty()) {
        connOpts.setPassword(mqttPassword.getBytes(StandardCharsets.UTF_8));
      }

      if (mqttTls) {
        connOpts.setSocketFactory(
            getSocketFactory(mqttCaPath, mqttClientCertPath, mqttClientKeyPath, ""));
      }

      logger.debug("Connecting to MQTT broker: {}", mqttBrokerUrl);
      mqttClient.connect(connOpts);
      logger.debug("Connected to MQTT broker.");

      bridge = new MqttToPubSubBridge();

      final MqttToPubSubBridge finalBridge = bridge;
      final IMqttClient finalMqttClient = mqttClient;
      final Publisher finalPublisher = publisher;
      final EtcdDataProvider finalEtcdProvider = etcdProvider;

      Runnable shutdownTask = () -> {
        if (isShuttingDown.compareAndSet(false, true)) {
          gracefulShutdown(finalBridge, finalMqttClient, finalPublisher, finalEtcdProvider);
        }
      };

      shutdownHook = new Thread(shutdownTask, "mqtt-bridge-shutdown-hook");
      Runtime.getRuntime().addShutdownHook(shutdownHook);

      // Start the circuit breaker monitor
      bridge.startCircuitBreaker(mqttClient, circuitBreakerThreshold, circuitBreakerTimeoutMs);
      // Start periodic reconnect with random jitter
      bridge.startPeriodicReconnect(mqttClient, reconnectIntervalSec);

      // Set up MQTT Message Callback
      bridge.setupBridge(mqttClient, publisher, mqttSubscriptionTopic,
          etcdProvider, sourceAttribute, sharedSubscription);

      // Keep the application running
      while (!bridge.isTripped()) {
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

    } catch (MqttException e) {
      logger.error("MQTT Error", e);
      System.exit(1);
    } catch (IOException e) {
      logger.error("Pub/Sub Error", e);
      System.exit(1);
    } catch (Exception e) {
      logger.error("An unexpected error occurred", e);
      System.exit(1);
    } finally {
      if (isShuttingDown.compareAndSet(false, true)) {
        gracefulShutdown(bridge, mqttClient, publisher, etcdProvider);
      }
      if (shutdownHook != null) {
        try {
          Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
          // VM is already shutting down
        }
      }
      if (bridge != null && bridge.isTripped()) {
        System.exit(1);
      }
    }
  }

  static CommandLine parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption(null, "mqtt_broker_url", true, "MQTT broker URL.");
    options.addOption(null, "mqtt_subscription_topic", true, "MQTT subscription topic.");
    options.addOption(null, "gcp_project_id", true, "Google Cloud Project ID.");
    options.addOption(null, "pubsub_topic_id", true, "Google Cloud Pub/Sub topic ID.");
    options.addOption(null, "mqtt_client_id", true, "MQTT client ID.");
    options.addOption(null, "mqtt_session_expiry_interval", true,
        "MQTT session expiry interval (seconds).");
    options.addOption(null, "mqtt_keep_alive_interval", true,
        "MQTT keep-alive interval (seconds).");
    options.addOption(null, "mqtt_tls", false, "Enable TLS for MQTT connection.");
    options.addOption(null, "mqtt_ca_path", true, "Path to CA certificate for TLS.");
    options.addOption(null, "mqtt_username", true, "MQTT username for authentication.");
    options.addOption(null, "mqtt_password", true, "MQTT password for authentication.");
    options.addOption(null, "mqtt_client_cert_path", true, "Path to client certificate for TLS.");
    options.addOption(null, "mqtt_client_key_path", true, "Path to client private key for TLS.");
    options.addOption(null, "etcd_target", true, "etcd endpoint URL.");
    options.addOption(null, "etcd_options", true, "etcd provider options (comma-separated).");
    options.addOption(null, "etcd_ca_path", true, "Path to CA certificate for etcd TLS.");
    options.addOption(null, "etcd_client_cert_path", true,
        "Path to client certificate for etcd TLS.");
    options.addOption(null, "etcd_client_key_path", true,
        "Path to client private key for etcd TLS.");
    options.addOption(null, "source_attribute", true, "Value for the source attribute.");
    options.addOption(null, "shared_subscription", true, "Shared subscription name.");
    options.addOption(null, "mqtt_reconnect_interval_sec", true,
        "Periodic reconnect interval in seconds (default 900, 0 to disable).");
    options.addOption(null, "mqtt_clean_start", false,
        "Enable clean start for MQTT connection (default false for persistent session).");
    options.addOption(null, "clean_start", false,
        "Enable clean start for MQTT connection (alias for mqtt_clean_start).");
    options.addOption(null, "circuit_breaker_unacked_threshold", true,
        "Abandoned message count threshold for circuit breaker (default 100).");
    options.addOption(null, "circuit_breaker_timeout_sec", true,
        "Max in-flight message age in seconds before circuit breaker trips (default 300).");
    options.addOption("h", "help", false, "Print usage info.");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    if (commandLine.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("MqttToPubSubBridge", options);
      return null;
    }

    return commandLine;
  }

  static String getEtcdOptions(CommandLine commandLine) {
    String etcdTarget = commandLine.getOptionValue("etcd_target");
    if (etcdTarget == null) {
      return null;
    }
    String etcdOptions = commandLine.getOptionValue("etcd_options");
    String etcdCaPath = commandLine.getOptionValue("etcd_ca_path");
    String etcdClientCertPath = commandLine.getOptionValue("etcd_client_cert_path");
    String etcdClientKeyPath = commandLine.getOptionValue("etcd_client_key_path");

    StringBuilder optionsBuilder = new StringBuilder(etcdOptions != null ? etcdOptions : "");
    if (etcdCaPath != null) {
      if (optionsBuilder.length() > 0) {
        optionsBuilder.append(",");
      }
      optionsBuilder.append("ca_file=").append(etcdCaPath);
    }
    if (etcdClientCertPath != null) {
      if (optionsBuilder.length() > 0) {
        optionsBuilder.append(",");
      }
      optionsBuilder.append("cert_file=").append(etcdClientCertPath);
    }
    if (etcdClientKeyPath != null) {
      if (optionsBuilder.length() > 0) {
        optionsBuilder.append(",");
      }
      optionsBuilder.append("key_file=").append(etcdClientKeyPath);
    }
    return optionsBuilder.toString();
  }

  /**
   * Sets up the bridge between MQTT and Pub/Sub.
   *
   * @param mqttClient            The MQTT client.
   * @param publisher             The Pub/Sub publisher.
   * @param mqttSubscriptionTopic The MQTT topic to subscribe to.
   * @throws MqttException If an MQTT error occurs.
   */
  public void setupBridge(IMqttClient mqttClient, Publisher publisher,
      String mqttSubscriptionTopic, EtcdDataProvider etcdProvider) throws MqttException {
    setupBridge(mqttClient, publisher, mqttSubscriptionTopic, etcdProvider, "bridge", null);
  }

  /**
   * Sets up the bridge between MQTT and Pub/Sub with a custom source attribute value.
   *
   * @param mqttClient            The MQTT client.
   * @param publisher             The Pub/Sub publisher.
   * @param mqttSubscriptionTopic The MQTT topic to subscribe to.
   * @param sourceAttribute       The value of the source attribute.
   * @throws MqttException If an MQTT error occurs.
   */
  public void setupBridge(IMqttClient mqttClient, Publisher publisher,
      String mqttSubscriptionTopic, EtcdDataProvider etcdProvider, String sourceAttribute)
      throws MqttException {
    setupBridge(mqttClient, publisher, mqttSubscriptionTopic, etcdProvider, sourceAttribute, null);
  }

  /**
   * Sets up the bridge between MQTT and Pub/Sub with a custom source attribute value.
   */
  public void setupBridge(IMqttClient mqttClient, Publisher publisher,
      String mqttSubscriptionTopic, EtcdDataProvider etcdProvider, String sourceAttribute,
      String sharedSubscription)
      throws MqttException {

    final String actualSubscriptionTopic;
    if (sharedSubscription != null && !sharedSubscription.isEmpty()
        && !mqttSubscriptionTopic.startsWith("$share/")) {
      actualSubscriptionTopic = String.format("$share/%s/%s", sharedSubscription,
          mqttSubscriptionTopic);
    } else {
      actualSubscriptionTopic = mqttSubscriptionTopic;
    }

    mqttClient.setManualAcks(true);
    mqttClient.setCallback(
        new MqttCallback() {
          @Override
          public void connectComplete(boolean reconnect, String serverUri) {
            if (reconnect) {
              logger.debug("MQTT automatically reconnected to broker: {}", serverUri);
              executor.submit(() -> {
                if (tripped) {
                  // ABORT REQUEST RECEIVED, ABANDON REQUESTS
                  return;
                }
                try {
                  mqttClient.subscribe(actualSubscriptionTopic, 1);
                  logger.debug("Successfully re-subscribed to topic: {}", actualSubscriptionTopic);
                } catch (MqttException e) {
                  logger.error("Failed to re-subscribe to topic {} after auto-reconnect",
                      actualSubscriptionTopic, e);
                }
              });
            } else {
              logger.debug("Initial MQTT connection established to broker: {}", serverUri);
            }
          }

          @Override
          public void disconnected(MqttDisconnectResponse disconnectResponse) {
            logger.warn("MQTT connection lost", disconnectResponse.getException());
          }

          @Override
          public void mqttErrorOccurred(MqttException exception) {
            logger.warn("MQTT error occurred", exception);
          }

          @Override
          public void messageArrived(String topic, MqttMessage message) {
            if (tripped || stopping) {
              // ABORT OR SHUTDOWN IN PROGRESS, IGNORE NEW MESSAGE ARRIVALS
              return;
            }
            logger.debug(
                "MQTT message received: ID={}, topic={}, queueSize={}, inProcess={},"
                    + " abandoned={}, unackedTotal={}",
                message.getId(), topic, executor.getQueue().size(), getInProcessCount(),
                getAbandonedCount(), getUnackedCount());
            final String receiveTime = java.time.Instant.now().toString();
            try {
              executor.submit(() -> {
                if (tripped || stopping) {
                  // ABORT REQUEST RECEIVED, ABANDON REQUESTS
                  return;
                }
                String messageKey = getMessageHash(topic, message);
                final java.util.concurrent.atomic.AtomicBoolean shouldProcess =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

                unackedMessages.compute(messageKey, (key, existingRecord) -> {
                  if (existingRecord != null
                      && existingRecord.getState() == MessageState.IN_PROCESS) {
                    // Message is currently in-flight, skip duplicate processing
                    return existingRecord;
                  }
                  // New message (existingRecord == null) OR previously ABANDONED
                  // Transition to IN_PROCESS and process
                  shouldProcess.set(true);
                  return new MessageRecord(MessageState.IN_PROCESS);
                });

                if (!shouldProcess.get()) {
                  logger.warn(
                      "MQTT message ID {} (key: {}) is already in process."
                              + " Skipping duplicate execution.",
                      message.getId(), messageKey);
                  return;
                }

                boolean publishInitiated = false;
                try {
                  byte[] payload = message.getPayload();
                  logger.debug("MQTT Message Received - Topic: {}, Payload Length: {}",
                      topic, payload.length);

                  String parsedTopic = topic;
                  // Automatically strip the shared subscription prefix if present
                  if (parsedTopic.startsWith("$share/")) {
                    parsedTopic = parsedTopic.replaceFirst("^\\$share/[^/]+/", "");
                    if (!parsedTopic.startsWith("/")) {
                      parsedTopic = "/" + parsedTopic;
                    }
                  }

                  Matcher matcher = TOPIC_PATTERN.matcher(parsedTopic);
                  String registryId = "unknown";
                  String deviceId = "unknown";
                  String topicSuffix = "";
                  if (matcher.matches()) {
                    registryId = matcher.group(1);
                    deviceId = matcher.group(2);
                    topicSuffix = matcher.group(3);
                  } else {
                    logger.warn("Could not parse registry/device from topic: {}", parsedTopic);
                  }

                  // Prepare Pub/Sub message
                  Map<String, String> attributes = new HashMap<>();
                  attributes.put("mqttTopic", parsedTopic);
                  attributes.put("deviceId", deviceId);
                  attributes.put("deviceRegistryId", registryId);
                  attributes.put("receiveTime", receiveTime);
                  attributes.put("distributorClientId", mqttClient.getClientId());
                  if (sourceAttribute != null) {
                    attributes.put("source", sourceAttribute);
                  }

                  String numId = getDeviceNumId(etcdProvider, registryId, deviceId);
                  if (numId != null) {
                    attributes.put("deviceNumId", numId);
                  }

                  if (topicSuffix != null && topicSuffix.startsWith("events/")) {
                    List<String> parts = Splitter.on('/').splitToList(topicSuffix);
                    if (parts.size() >= 2) {
                      attributes.put("subFolder", parts.get(1));
                    }
                  }

                  ByteString data = ByteString.copyFrom(payload);
                  PubsubMessage.Builder pubsubMessageBuilder =
                      PubsubMessage.newBuilder().setData(data);

                  PubsubMessage pubsubMessage =
                      pubsubMessageBuilder.putAllAttributes(attributes).build();

                  // Publish with 5x retry + exponential backoff
                  publishWithRetry(publisher, pubsubMessage, message, messageKey,
                      topic, mqttClient, 1);
                  publishInitiated = true;
                } catch (Exception e) {
                  logger.warn("Error processing MQTT message", e);
                } finally {
                  if (!publishInitiated) {
                    unackedMessages.remove(messageKey);
                  }
                }
              });
            } catch (Exception e) {
              String messageKey = getMessageHash(topic, message);
              // In QoS 1, if executor rejected, message remains unacknowledged in session.
              // Mark as ABANDONED so the circuit breaker tracks session capacity exhaustion.
              unackedMessages.put(messageKey, new MessageRecord(MessageState.ABANDONED));
              if (e instanceof RejectedExecutionException) {
                logger.warn(
                    "Unable to queue MQTT message ID {} (queue saturated, marked as ABANDONED"
                        + " in session)",
                    message.getId(), e);
              } else {
                logger.error("Error submitting message ID {} to executor", message.getId(), e);
              }
            }
          }

          @Override
          public void deliveryComplete(IMqttToken token) {}

          @Override
          public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        });

    logger.debug("Subscribing to MQTT topic: {}", actualSubscriptionTopic);
    mqttClient.subscribe(actualSubscriptionTopic, 1);
    logger.debug("Subscribed. Waiting for messages...");
  }

  private void publishWithRetry(Publisher publisher, PubsubMessage pubsubMessage,
      MqttMessage mqttMessage, String messageKey, String topic, IMqttClient mqttClient,
      int attempt) {
    if (tripped) {
      // ABORT REQUEST RECEIVED, ABANDON REQUESTS
      return;
    }
    try {
      ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
      ApiFutures.addCallback(messageIdFuture, new ApiFutureCallback<String>() {
        @Override
        public void onSuccess(String msgId) {
          if (tripped) {
            // ABORT REQUEST RECEIVED, ABANDON REQUESTS
            return;
          }
          logger.debug("Published to Pub/Sub with message ID: {}", msgId);
          unackedMessages.remove(messageKey);
          try {
            mqttClient.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
          } catch (MqttException e) {
            logger.error("Failed to ACK MQTT message, it will be redelivered", e);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          handlePublishFailure(
              publisher, pubsubMessage, mqttMessage, messageKey, topic, mqttClient, attempt, t);
        }
      }, MoreExecutors.directExecutor());
    } catch (Exception e) {
      handlePublishFailure(
          publisher, pubsubMessage, mqttMessage, messageKey, topic, mqttClient, attempt, e);
    }
  }

  private void handlePublishFailure(Publisher publisher, PubsubMessage pubsubMessage,
      MqttMessage mqttMessage, String messageKey, String topic, IMqttClient mqttClient,
      int attempt, Throwable t) {
    if (attempt < 5 && !tripped) {
      long baseMs = (1L << (attempt - 1)) * 800L;
      int backoffMs = (int) baseMs
          + ThreadLocalRandom.current().nextInt((int) (baseMs * 0.5));
      logger.warn("Error publishing to Pub/Sub (attempt {}/5), retrying in {}ms...",
          attempt, backoffMs, t);
      retryScheduler.schedule(() -> {
        if (tripped) {
          // ABORT REQUEST RECEIVED, ABANDON REQUESTS
          return;
        }
        publishWithRetry(publisher, pubsubMessage, mqttMessage, messageKey,
            topic, mqttClient, attempt + 1);
      }, backoffMs, TimeUnit.MILLISECONDS);
    } else {
      logger.error(
          "Failed to publish to Pub/Sub after 5 attempts."
              + " Leaving message ID {} unacked (marked as ABANDONED).",
          mqttMessage.getId(), t);
      unackedMessages.compute(messageKey, (key, existingRecord) -> {
        if (existingRecord != null) {
          existingRecord.setState(MessageState.ABANDONED);
          return existingRecord;
        }
        return new MessageRecord(MessageState.ABANDONED);
      });
    }
  }

  private static SSLSocketFactory getSocketFactory(
      String caCertificatePath, String clientCertPath, String clientKeyPath, final String password)
      throws Exception {
    Security.addProvider(new BouncyCastleProvider());

    // Load client private key and certificates that are sent to server so it can authenticate us
    X509Certificate clientCert = getCertificateFromFile(clientCertPath);
    KeyPair clientKey = getClientPrivateKeyFromFile(clientKeyPath, password);
    KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    clientKeyStore.load(null, null);
    clientKeyStore.setCertificateEntry("certificate", clientCert);
    clientKeyStore.setKeyEntry(
        "private-key",
        clientKey.getPrivate(),
        password.toCharArray(),
        new Certificate[] {clientCert});
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(clientKeyStore, password.toCharArray());

    // CA certificate is used to authenticate server
    X509Certificate caCert = getCertificateFromFile(caCertificatePath);
    KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    caKeyStore.load(null, null);
    caKeyStore.setCertificateEntry("ca-certificate", caCert);
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
    trustManagerFactory.init(caKeyStore);

    // Once CA TrustManagerFactory and Client KeyManagerFactory is ready, create SSL socket factory
    SSLContext context = SSLContext.getInstance("TLSv1.2");
    context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

    return context.getSocketFactory();
  }

  private static X509Certificate getCertificateFromFile(String filePath) throws Exception {
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    X509Certificate certificate;
    try (InputStream certInputStream = new FileInputStream(filePath)) {
      certificate = (X509Certificate) certificateFactory.generateCertificate(certInputStream);
    }
    if (certificate == null) {
      throw new CertificateException("Null certificate returned for " + filePath);
    }
    return certificate;
  }

  private static KeyPair getClientPrivateKeyFromFile(String path, String password)
      throws Exception {
    InputStream keyInputStream = new FileInputStream(path);
    PEMParser pemParser =
        new PEMParser(
            new BufferedReader(new InputStreamReader(keyInputStream, StandardCharsets.UTF_8)));

    Object pemObject = pemParser.readObject();
    PEMDecryptorProvider decryptorProvider =
        new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

    KeyPair clientKey;
    if (pemObject instanceof PEMEncryptedKeyPair encryptedKeyPair) {
      clientKey = converter.getKeyPair(encryptedKeyPair.decryptKeyPair(decryptorProvider));
    } else if (pemObject instanceof PEMKeyPair pemKeyPair) {
      clientKey = converter.getKeyPair(pemKeyPair);
    } else {
      PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) pemObject;
      clientKey = converter.getKeyPair(convertPrivateKeyFromPkcs8ToPkcs1(privateKeyInfo));
    }
    pemParser.close();

    return clientKey;
  }

  private static PEMKeyPair convertPrivateKeyFromPkcs8ToPkcs1(PrivateKeyInfo privateKeyInfo)
      throws IOException {
    // Parse the key wrapping to determine the internal key structure
    ASN1Encodable asn1PrivateKey = privateKeyInfo.parsePrivateKey();
    // Convert the parsed key to an RSA private key
    RSAPrivateKey rsaPrivateKey = RSAPrivateKey.getInstance(asn1PrivateKey);
    // Create the RSA public key from the modulus and exponent
    RSAPublicKey rsaPublicKey =
        new RSAPublicKey(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent());
    // Create an algorithm identifier for forming the key pair
    AlgorithmIdentifier algId =
        new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
    // Create the key pair container
    return new PEMKeyPair(
        new SubjectPublicKeyInfo(algId, rsaPublicKey), new PrivateKeyInfo(algId, rsaPrivateKey));
  }

  private static EtcdDataProvider createEtcdProvider(String target, String options) {
    if (target == null) {
      return null;
    }
    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.project_id = target;
    iotAccess.options = options;
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);
    logger.debug("EtcdDataProvider initialized for target: {}", target);
    return provider;
  }

  private static Publisher createPublisher(String projectId, String topicId) {
    ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
    Publisher.Builder publisherBuilder = Publisher.newBuilder(topicName);
    String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
    if (emulatorHost != null && !emulatorHost.isEmpty()) {
      int lastIndex = emulatorHost.lastIndexOf(":");
      String useHost = lastIndex < 0 ? emulatorHost
          : String.format("localhost:%s", emulatorHost.substring(lastIndex + 1));
      ManagedChannel channel = ManagedChannelBuilder.forTarget(useHost).usePlaintext().build();
      publisherBuilder.setChannelProvider(
          FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)));
      publisherBuilder.setCredentialsProvider(NoCredentialsProvider.create());
      logger.debug("Routing Pub/Sub Publisher to emulator host: {}", useHost);
    }
    try {
      Publisher publisher = publisherBuilder.build();
      logger.debug("Pub/Sub Publisher initialized for topic: {}", topicName);
      return publisher;
    } catch (IOException e) {
      throw new RuntimeException("Failed to build Pub/Sub Publisher", e);
    }
  }

  private static String getDeviceNumId(EtcdDataProvider etcdProvider, String registryId,
      String deviceId) {
    if (etcdProvider == null || "unknown".equals(registryId) || "unknown".equals(deviceId)) {
      return null;
    }

    String cacheKey = registryId + "/" + deviceId;
    String cachedNumId = numIdCache.getIfPresent(cacheKey);
    if (cachedNumId != null) {
      return cachedNumId.isEmpty() ? null : cachedNumId;
    }

    try {
      String numId = etcdProvider.ref()
          .registry(registryId)
          .device(deviceId)
          .getAsSerializable("num_id");
      if (numId != null) {
        logger.debug("Found numId {} in etcd for device {}/{}", numId, registryId, deviceId);
        numIdCache.put(cacheKey, numId);
      } else {
        logger.debug("numId not found in etcd for device {}/{}", registryId, deviceId);
        numIdCache.put(cacheKey, ""); // Cache empty string for negative lookups
      }
      return numId;
    } catch (Exception e) {
      // etcd returning a device ID is CLEAN for a NULL/No Value - not an error case
      logger.debug("No numId value or error reading from etcd for device {}/{}",
          registryId, deviceId);
      numIdCache.put(cacheKey, ""); // Cache empty string for negative lookups
      return null;
    }
  }

  static void gracefulShutdown(MqttToPubSubBridge bridge, IMqttClient mqttClient,
      Publisher publisher, EtcdDataProvider etcdProvider) {
    logger.info("Initiating graceful shutdown of MqttToPubSubBridge...");
    // 1. Signal bridge to ignore newly arriving MQTT messages
    if (bridge != null) {
      bridge.setStopping(true);
    }

    // 2. Shut down bridge executors and drain in-flight message processing
    // Keep MQTT client connected while draining so PUBACKs can be returned to broker
    if (bridge != null) {
      try {
        bridge.shutdown();
        logger.debug("Bridge executors drained and shut down.");
      } catch (Exception e) {
        logger.warn("Error draining bridge executors", e);
      }
    }

    // 3. Shut down Pub/Sub publisher and await pending publish futures
    if (publisher != null) {
      try {
        publisher.shutdown();
        if (!publisher.awaitTermination(10, TimeUnit.SECONDS)) {
          logger.warn("Pub/Sub publisher did not terminate within 10 seconds");
        }
        logger.debug("Pub/Sub Publisher shut down.");
      } catch (Exception e) {
        logger.warn("Error shutting down Pub/Sub publisher", e);
      }
    }

    // 4. Disconnect MQTT client after in-flight message ACKs have completed
    if (mqttClient != null && mqttClient.isConnected()) {
      try {
        mqttClient.disconnect();
        logger.debug("MQTT client disconnected.");
      } catch (Exception e) {
        logger.warn("Error disconnecting MQTT client during shutdown", e);
      }
    }

    // 5. Shut down Etcd data provider
    if (etcdProvider != null) {
      try {
        etcdProvider.shutdown();
        logger.debug("EtcdDataProvider shut down.");
      } catch (Exception e) {
        logger.warn("Error shutting down EtcdDataProvider", e);
      }
    }
    logger.info("Graceful shutdown of MqttToPubSubBridge completed.");
  }

  /**
   * Shuts down the bridge executors with a default timeout of 10 seconds.
   */
  public void shutdown() {
    shutdown(10, TimeUnit.SECONDS);
  }

  /**
   * Shuts down the bridge executors awaiting termination up to the specified timeout.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   */
  public void shutdown(long timeout, TimeUnit unit) {
    executor.shutdown();
    retryScheduler.shutdown();
    try {
      long halfTimeout = Math.max(1, timeout / 2);
      if (!executor.awaitTermination(halfTimeout, unit)) {
        logger.warn("Executor did not terminate within {} {}, forcing shutdown",
            halfTimeout, unit);
        executor.shutdownNow();
      }
      if (!retryScheduler.awaitTermination(halfTimeout, unit)) {
        logger.warn("Retry scheduler did not terminate within {} {}, forcing shutdown",
            halfTimeout, unit);
        retryScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      retryScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
