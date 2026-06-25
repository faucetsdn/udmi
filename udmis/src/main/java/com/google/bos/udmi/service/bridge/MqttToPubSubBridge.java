package com.google.bos.udmi.service.bridge;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.bos.udmi.service.support.EtcdDataProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Splitter;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
public final class MqttToPubSubBridge {

  private static final Pattern TOPIC_PATTERN = Pattern.compile("/r/([^/]+)/d/([^/]+)/?(.*)");
  private static final Logger logger = LoggerFactory.getLogger(MqttToPubSubBridge.class);

  private static final int MAX_QUEUE_SIZE = 99;
  private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors() * 2;
  // Change rejection policy to AbortPolicy to throw RejectedExecutionException when queue is full.
  private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
      NUM_THREADS, NUM_THREADS,
      0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(MAX_QUEUE_SIZE),
      new ThreadPoolExecutor.AbortPolicy());

  private static volatile boolean tripped = false;

  private static void startCircuitBreaker(IMqttClient mqttClient, ThreadPoolExecutor executor) {
    Thread monitor = new Thread(() -> {
      long fullStartTime = 0;
      while (!tripped) {
        try {
          Thread.sleep(1000);
          // Check if we've received the maximum number of inflight messages (100)
          if (executor.getActiveCount() + executor.getQueue().size() >= 100) {
            if (fullStartTime == 0) {
              fullStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - fullStartTime >= 60000) {
              tripped = true;
              logger.error("Queue saturated for 60 seconds! Tripping circuit breaker.");
              try {
                mqttClient.disconnect();
              } catch (Exception e) {
                logger.error("Error disconnecting during circuit breaker trip", e);
              }
              System.exit(1);
            }
          } else {
            fullStartTime = 0; // reset
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
    monitor.setDaemon(true);
    monitor.start();
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
    boolean mqttTls = commandLine.hasOption("mqtt_tls");
    String mqttCaPath = commandLine.getOptionValue("mqtt_ca_path");
    String mqttUsername = commandLine.getOptionValue("mqtt_username");
    String mqttPassword = commandLine.getOptionValue("mqtt_password");
    String mqttClientCertPath = commandLine.getOptionValue("mqtt_client_cert_path");
    String mqttClientKeyPath = commandLine.getOptionValue("mqtt_client_key_path");
    String etcdTarget = commandLine.getOptionValue("etcd_target");
    String etcdOptions = commandLine.getOptionValue("etcd_options");
    String sourceAttribute = commandLine.getOptionValue("source_attribute", "bridge");
    String sharedSubscription = commandLine.getOptionValue("shared_subscription");

    if (gcpProjectId == null || pubsubTopicId == null) {
      logger.error("gcp_project_id and pubsub_topic_id are required.");
      System.exit(1);
    }

    Publisher publisher = null;
    IMqttClient mqttClient = null;
    EtcdDataProvider etcdProvider = null;

    try {
      etcdProvider = createEtcdProvider(etcdTarget, etcdOptions);
      publisher = createPublisher(gcpProjectId, pubsubTopicId);

      // Initialize MQTT Client
      String mqttClientId = System.getenv("MQTT_CLIENT_ID");
      if (mqttClientId == null || mqttClientId.isEmpty()) {
        mqttClientId = java.util.UUID.randomUUID().toString();
      }

      mqttClient =
          new MqttClient(
              mqttBrokerUrl, mqttClientId, new MemoryPersistence());
      MqttConnectionOptions connOpts = new MqttConnectionOptions();
      connOpts.setCleanStart(false);
      connOpts.setSessionExpiryInterval(0xFFFFFFFFL); // No session expiry
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

      // Start the circuit breaker monitor
      startCircuitBreaker(mqttClient, executor);

      // Set up MQTT Message Callback
      setupBridge(mqttClient, publisher, mqttSubscriptionTopic,
          etcdProvider, sourceAttribute, sharedSubscription);

      // Keep the application running
      while (true) {
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

    } catch (MqttException e) {
      logger.error("MQTT Error", e);
    } catch (IOException e) {
      logger.error("Pub/Sub Error", e);
    } catch (Exception e) {
      logger.error("An unexpected error occurred", e);
    } finally {
      // Shutdown
      if (etcdProvider != null) {
        try {
          etcdProvider.shutdown();
          logger.debug("EtcdDataProvider shut down.");
        } catch (Exception e) {
          logger.warn("Error shutting down EtcdDataProvider", e);
        }
      }
      if (mqttClient != null && mqttClient.isConnected()) {
        try {
          mqttClient.disconnect();
          logger.debug("MQTT client disconnected.");
        } catch (MqttException e) {
          logger.warn("Error disconnecting MQTT client", e);
        }
      }
      if (publisher != null) {
        try {
          publisher.shutdown();
          logger.debug("Pub/Sub Publisher shut down.");
        } catch (Exception e) {
          logger.warn("Error shutting down Pub/Sub publisher", e);
        }
      }
    }
  }

  private static CommandLine parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption(null, "mqtt_broker_url", true, "MQTT broker URL.");
    options.addOption(null, "mqtt_subscription_topic", true, "MQTT subscription topic.");
    options.addOption(null, "gcp_project_id", true, "Google Cloud Project ID.");
    options.addOption(null, "pubsub_topic_id", true, "Google Cloud Pub/Sub topic ID.");
    options.addOption(null, "mqtt_tls", false, "Enable TLS for MQTT connection.");
    options.addOption(null, "mqtt_ca_path", true, "Path to CA certificate for TLS.");
    options.addOption(null, "mqtt_username", true, "MQTT username for authentication.");
    options.addOption(null, "mqtt_password", true, "MQTT password for authentication.");
    options.addOption(null, "mqtt_client_cert_path", true, "Path to client certificate for TLS.");
    options.addOption(null, "mqtt_client_key_path", true, "Path to client private key for TLS.");
    options.addOption(null, "etcd_target", true, "etcd endpoint URL.");
    options.addOption(null, "etcd_options", true, "etcd provider options (comma-separated).");
    options.addOption(null, "source_attribute", true, "Value for the source attribute.");
    options.addOption(null, "shared_subscription", true, "Shared subscription name.");
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

  /**
   * Sets up the bridge between MQTT and Pub/Sub.
   *
   * @param mqttClient            The MQTT client.
   * @param publisher             The Pub/Sub publisher.
   * @param mqttSubscriptionTopic The MQTT topic to subscribe to.
   * @throws MqttException If an MQTT error occurs.
   */
  public static void setupBridge(IMqttClient mqttClient, Publisher publisher,
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
  public static void setupBridge(IMqttClient mqttClient, Publisher publisher,
      String mqttSubscriptionTopic, EtcdDataProvider etcdProvider, String sourceAttribute)
      throws MqttException {
    setupBridge(mqttClient, publisher, mqttSubscriptionTopic, etcdProvider, sourceAttribute, null);
  }

  /**
   * Sets up the bridge between MQTT and Pub/Sub with a custom source attribute value.
   */
  public static void setupBridge(IMqttClient mqttClient, Publisher publisher,
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
              try {
                mqttClient.subscribe(actualSubscriptionTopic, 1);
                logger.debug("Successfully re-subscribed to topic: {}", actualSubscriptionTopic);
              } catch (MqttException e) {
                logger.error("Failed to re-subscribe to topic {} after auto-reconnect",
                    mqttSubscriptionTopic, e);
              }
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
            try {
              final String recieveTime = java.time.Instant.now().toString();
              executor.submit(() -> {
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
                  attributes.put("recieveTime", recieveTime);
                  attributes.put("distributeClientId", mqttClient.getClientId());
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

                  // Publish to Pub/Sub with local retries
                  long backoff = 100;
                  while (!tripped) {
                    try {
                      ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
                      String msgId = messageIdFuture.get(); // blocks until complete
                      logger.debug("Published to Pub/Sub with message ID: {}", msgId);
                      mqttClient.messageArrivedComplete(message.getId(), message.getQos());
                      break; // success
                    } catch (Exception e) {
                      // Note: Poison pills (e.g., consistently failing serialization) could
                      // permanently block this thread since we retry indefinitely,
                      // but schema validation happens earlier in the pipeline.
                      logger.warn("Error publishing to Pub/Sub, retrying in {} ms", backoff, e);
                      try {
                        Thread.sleep(backoff);
                      } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                      }
                      backoff = Math.min(backoff * 2, 10000);
                    }
                  }

                } catch (RuntimeException e) {
                  logger.warn("Error processing MQTT message", e);
                }
              });
            } catch (RejectedExecutionException e) {
              logger.error("Execution rejected, queue full! Dropping MQTT connection.", e);
              try {
                mqttClient.disconnectForcibly();
              } catch (MqttException me) {
                logger.error("Error while forcing disconnect", me);
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
    try {
      String numId = etcdProvider.ref()
          .registry(registryId)
          .device(deviceId)
          .get("num_id");
      if (numId != null) {
        logger.debug("Found numId {} in etcd for device {}/{}", numId, registryId, deviceId);
      } else {
        logger.debug("numId not found in etcd for device {}/{}", registryId, deviceId);
      }
      return numId;
    } catch (Exception e) {
      // etcd returning a device ID is CLEAN for a NULL/No Value - not an error case
      logger.debug("No numId value or error reading from etcd for device {}/{}",
          registryId, deviceId);
      return null;
    }
  }

  private MqttToPubSubBridge() {}
}
