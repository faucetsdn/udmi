package com.google.bos.udmi.service.bridge;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Splitter;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ProjectTopicName;
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
import java.util.concurrent.ExecutionException;
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
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bridge that subscribes to an MQTT topic and publishes messages to a Google Cloud Pub/Sub topic.
 */
public final class MqttToPubSubBridge {

  private static final Pattern TOPIC_PATTERN = Pattern.compile("/r/([^/]+)/d/([^/]+)/?(.*)");
  private static final Logger logger = LoggerFactory.getLogger(MqttToPubSubBridge.class);

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
    String mqttSubscriptionTopic = commandLine.getOptionValue("mqtt_subscription_topic", "/r/+/d/#");
    String gcpProjectId = commandLine.getOptionValue("gcp_project_id");
    String pubsubTopicId = commandLine.getOptionValue("pubsub_topic_id");
    boolean mqttTls = commandLine.hasOption("mqtt_tls");
    String mqttCaPath = commandLine.getOptionValue("mqtt_ca_path");
    String mqttUsername = commandLine.getOptionValue("mqtt_username");
    String mqttPassword = commandLine.getOptionValue("mqtt_password");
    String mqttClientCertPath = commandLine.getOptionValue("mqtt_client_cert_path");
    String mqttClientKeyPath = commandLine.getOptionValue("mqtt_client_key_path");

    if (gcpProjectId == null || pubsubTopicId == null) {
      logger.error("gcp_project_id and pubsub_topic_id are required.");
      System.exit(1);
    }

    Publisher publisher = null;
    IMqttClient mqttClient = null;

    try {
      // Initialize Pub/Sub Publisher
      ProjectTopicName topicName = ProjectTopicName.of(gcpProjectId, pubsubTopicId);
      publisher = Publisher.newBuilder(topicName).build();
      logger.info("Pub/Sub Publisher initialized for topic: {}", topicName);

      // Initialize MQTT Client
      mqttClient =
          new MqttClient(mqttBrokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);

      if (mqttUsername != null && !mqttUsername.isEmpty()) {
        connOpts.setUserName(mqttUsername);
      }
      if (mqttPassword != null && !mqttPassword.isEmpty()) {
        connOpts.setPassword(mqttPassword.toCharArray());
      }

      if (mqttTls) {
        connOpts.setSocketFactory(
            getSocketFactory(mqttCaPath, mqttClientCertPath, mqttClientKeyPath, ""));
      }

      logger.info("Connecting to MQTT broker: {}", mqttBrokerUrl);
      mqttClient.connect(connOpts);
      logger.info("Connected to MQTT broker.");

      // Set up MQTT Message Callback
      setupBridge(mqttClient, publisher, mqttSubscriptionTopic);

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
      if (mqttClient != null && mqttClient.isConnected()) {
        try {
          mqttClient.disconnect();
          logger.info("MQTT client disconnected.");
        } catch (MqttException e) {
          logger.warn("Error disconnecting MQTT client", e);
        }
      }
      if (publisher != null) {
        try {
          publisher.shutdown();
          logger.info("Pub/Sub Publisher shut down.");
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

  public static void setupBridge(IMqttClient mqttClient, Publisher publisher, String mqttSubscriptionTopic) throws MqttException {
    mqttClient.setCallback(
        new MqttCallback() {
          @Override
          public void connectionLost(Throwable cause) {
            logger.warn("MQTT connection lost", cause);
          }

          @Override
          public void messageArrived(String topic, MqttMessage message) {
            try {
              byte[] payload = message.getPayload();
              logger.info(
                  "MQTT Message Received - Topic: {}, Payload Length: {}", topic, payload.length);

              Matcher matcher = TOPIC_PATTERN.matcher(topic);
              String registryId = "unknown";
              String deviceId = "unknown";
              String topicSuffix = "";
              if (matcher.matches()) {
                registryId = matcher.group(1);
                deviceId = matcher.group(2);
                topicSuffix = matcher.group(3);
              } else {
                logger.warn("Could not parse registry/device from topic: {}", topic);
              }

              // Prepare Pub/Sub message
              ByteString data = ByteString.copyFrom(payload);
              PubsubMessage.Builder pubsubMessageBuilder =
                  PubsubMessage.newBuilder().setData(data);

              Map<String, String> attributes = new HashMap<>();
              attributes.put("mqttTopic", topic);
              attributes.put("deviceId", deviceId);
              attributes.put("deviceRegistryId", registryId);

              if (topicSuffix != null && topicSuffix.startsWith("events/")) {
                List<String> parts = Splitter.on('/').splitToList(topicSuffix);
                if (parts.size() >= 2) {
                  attributes.put("subFolder", parts.get(1));
                }
              }

              PubsubMessage pubsubMessage =
                  pubsubMessageBuilder.putAllAttributes(attributes).build();

              // Publish to Pub/Sub
              ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
              messageIdFuture.addListener(
                  () -> {
                    try {
                      logger.info(
                          "Published to Pub/Sub with message ID: {}", messageIdFuture.get());
                    } catch (InterruptedException | ExecutionException e) {
                      logger.warn("Error publishing to Pub/Sub", e);
                    }
                  },
                  directExecutor());

            } catch (RuntimeException e) {
              logger.warn("Error processing MQTT message", e);
            }
          }

          @Override
          public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {}
        });

    logger.info("Subscribing to MQTT topic: {}", mqttSubscriptionTopic);
    mqttClient.subscribe(mqttSubscriptionTopic);
    logger.info("Subscribed. Waiting for messages...");
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

  private MqttToPubSubBridge() {}
}
