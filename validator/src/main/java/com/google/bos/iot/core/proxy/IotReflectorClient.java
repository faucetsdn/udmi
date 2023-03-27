package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.api.client.util.Base64;
import com.google.daq.mqtt.sequencer.SequenceRunner;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.ReflectorConfig;
import udmi.schema.ReflectorState;
import udmi.schema.SequenceValidationState.FeatureStage;
import udmi.schema.SetupReflectorConfig;
import udmi.schema.SetupReflectorState;

/**
 * Publish messages using the iot core reflector.
 */
public class IotReflectorClient implements MessagePublisher {

  private static final String IOT_KEY_ALGORITHM = "RS256";
  private static final String UDMS_REFLECT = "UDMS-REFLECT";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";
  private static final String UDMI_FOLDER = "udmi";
  private static final String UDMI_TOPIC = "events/" + UDMI_FOLDER;
  private static final Date REFLECTOR_STATE_TIMESTAMP = new Date();
  private static final String CONFIG_CATEGORY = "config";
  private static final String COMMANDS_CATEGORY = "commands";
  private static final long CONFIG_TIMEOUT_SEC = 10;
  private final String udmiVersion;
  private final CountDownLatch initialConfigReceived = new CountDownLatch(1);
  private final CountDownLatch initializedStateSent = new CountDownLatch(1);
  private final CountDownLatch validConfigReceived = new CountDownLatch(1);
  private final int requiredVersion;
  private boolean isInstallValid;

  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();

  private final MqttPublisher mqttPublisher;
  private final String subscriptionId;
  private final String registryId;
  private final String projectId;
  private boolean active;
  private String prevTransactionId;

  /**
   * Create a new reflector instance.
   *
   * @param iotConfig       configuration file
   * @param requiredVersion version of the functions that are required by the tools
   */
  public IotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion) {
    final byte[] keyBytes;
    checkNotNull(iotConfig.key_file, "missing key file in config");
    try {
      keyBytes = getFileBytes(iotConfig.key_file);
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading key file " + new File(iotConfig.key_file).getAbsolutePath(), e);
    }

    this.requiredVersion = requiredVersion;
    registryId = SiteModel.getRegistryActual(iotConfig);
    projectId = iotConfig.project_id;
    udmiVersion = Optional.ofNullable(iotConfig.udmi_version).orElseGet(Common::getUdmiVersion);
    String cloudRegion =
        iotConfig.reflect_region == null ? iotConfig.cloud_region : iotConfig.reflect_region;
    subscriptionId =
        String.format("%s/%s/%s/%s", projectId, cloudRegion, UDMS_REFLECT, registryId);

    try {
      mqttPublisher = new MqttPublisher(projectId, cloudRegion, UDMS_REFLECT,
          registryId, keyBytes, IOT_KEY_ALGORITHM, this::messageHandler, this::errorHandler);
    } catch (Exception e) {
      throw new RuntimeException("While connecting MQTT endpoint " + subscriptionId, e);
    }

    try {
      System.err.println("Starting initial UDMIS setup process");
      if (!initialConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
        System.err.println("Ignoring initial config received timeout (config likely empty)");
      }
      initializeReflectorState();
      initializedStateSent.countDown();
      if (!validConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException(
            "Config sync timeout expired. Investigate UDMIS cloud functions install.");
      }

      active = true;
    } catch (Exception e) {
      mqttPublisher.close();
      throw new RuntimeException("Waiting for initial config", e);
    }
  }

  private void initializeReflectorState() {
    ReflectorState reflectorState = new ReflectorState();
    reflectorState.timestamp = REFLECTOR_STATE_TIMESTAMP;
    reflectorState.version = udmiVersion;
    reflectorState.setup = new SetupReflectorState();
    reflectorState.setup.user = System.getenv("USER");
    try {
      System.err.printf("Setting state version %s timestamp %s%n",
          udmiVersion, getTimestamp(REFLECTOR_STATE_TIMESTAMP));
      setReflectorState(stringify(reflectorState));
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
  }

  private void messageHandler(String topic, String payload) {
    if (payload.length() == 0) {
      return;
    }
    byte[] rawData = payload.getBytes();
    boolean base64 = rawData[0] != '{';

    if ("null".equals(payload)) {
      return;
    }
    Map<String, Object> messageMap = asMap(
        new String(base64 ? Base64.decodeBase64(rawData) : rawData));
    try {
      String category = parseMessageTopic(topic);

      if (CONFIG_CATEGORY.equals(category)) {
        ensureCloudSync(messageMap);
      } else if (COMMANDS_CATEGORY.equals(category)) {
        handleCommandEnvelope(messageMap);
      } else {
        throw new RuntimeException("Unknown message category " + category);
      }
    } catch (Exception e) {
      if (isInstallValid) {
        handleReceivedMessage(extractAttributes(messageMap),
            new ErrorContainer(e, payload, JsonUtil.getTimestamp()));
      } else {
        throw e;
      }
    }
  }

  private void handleCommandEnvelope(Map<String, Object> messageMap) {
    if (!isInstallValid) {
      return;
    }
    Map<String, String> attributes = extractAttributes(messageMap);
    String payload = GeneralUtils.decodeBase64((String) messageMap.get("payload"));
    handleReceivedMessage(attributes, asMap(payload));
  }

  @NotNull
  private Map<String, String> extractAttributes(Map<String, Object> messageMap) {
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("projectId", projectId);
    attributes.put("deviceRegistryId", registryId);
    attributes.put("deviceId", (String) messageMap.get("deviceId"));
    attributes.put("subType", (String) messageMap.get("subType"));
    attributes.put("subFolder", (String) messageMap.get("subFolder"));
    attributes.put("transactionId", (String) messageMap.get("transactionId"));
    attributes.put("deviceNumId", MOCK_DEVICE_NUM_ID);
    return attributes;
  }

  private void handleReceivedMessage(Map<String, String> attributes,
      Map<String, Object> message) {
    Validator.MessageBundle messageBundle = new Validator.MessageBundle();
    messageBundle.attributes = attributes;
    messageBundle.message = message;
    messages.offer(messageBundle);
  }

  private boolean ensureCloudSync(Map<String, Object> message) {
    try {
      initialConfigReceived.countDown();
      if (initializedStateSent.getCount() > 0) {
        return false;
      }

      ReflectorConfig reflectorConfig = convertTo(ReflectorConfig.class, message);
      System.err.println("UDMIS received reflectorConfig: " + stringify(reflectorConfig));
      SetupReflectorConfig udmisInfo = reflectorConfig.udmis;
      Date lastState = udmisInfo == null ? null : udmisInfo.last_state;
      System.err.println("UDMIS matching against expected state timestamp " + getTimestamp(
          REFLECTOR_STATE_TIMESTAMP));
      boolean configMatch = dateEquals(lastState, REFLECTOR_STATE_TIMESTAMP);
      if (configMatch) {
        System.err.println("UDMIS version " + reflectorConfig.version);
        if (!udmiVersion.equals(reflectorConfig.version)) {
          System.err.println("UDMIS local/cloud UDMI version mismatch!");
        }

        System.err.println("UDMIS deployed by " + udmisInfo.deployed_by + " at " + getTimestamp(
            udmisInfo.deployed_at));

        System.err.printf("UDMIS functions support versions %s:%s (required %s)%n",
            udmisInfo.functions_min, udmisInfo.functions_max, requiredVersion);
        String baseError = String.format("UDMIS required functions version %d not allowed",
            requiredVersion);
        if (requiredVersion < udmisInfo.functions_min) {
          throw new RuntimeException(
              String.format("%s: min supported %s. Please update local UDMI install.", baseError,
                  udmisInfo.functions_min));
        }
        if (requiredVersion > udmisInfo.functions_max) {
          throw new RuntimeException(
              String.format("%s: max supported %s. Please update cloud UDMIS install.",
                  baseError, udmisInfo.functions_max));
        }
        isInstallValid = true;
        validConfigReceived.countDown();
      } else {
        System.err.println(
            "UDMIS ignoring mismatching config timestamp " + getTimestamp(lastState));
      }
    } catch (Exception e) {
      throw new RuntimeException("While waiting for initial config synchronization", e);
    }

    // Even through setup might be valid, return false to not process this config message.
    return false;
  }

  private String parseMessageTopic(String topic) {
    String[] parts = topic.substring(1).split("/", 3);
    checkState("devices".equals(parts[0]), "unknown parsed path field");
    checkState(registryId.equals(parts[1]), "unexpected parsed registry id");
    return parts[2];
  }

  private void errorHandler(MqttPublisher mqttPublisher, Throwable throwable) {
    System.err.println("mqtt client error: " + throwable.getMessage());
    close();
  }

  private byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  @Override
  public String getSubscriptionId() {
    return subscriptionId;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public Validator.MessageBundle takeNextMessage() {
    try {
      return messages.take();
    } catch (Exception e) {
      throw new RuntimeException("While taking next message", e);
    }
  }

  @Override
  public String publish(String deviceId, String topic, String data) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    String[] parts = topic.split("/", 2);
    envelope.subFolder = SubFolder.fromValue(parts[0]);
    envelope.subType = SubType.fromValue(parts[1]);
    envelope.payload = GeneralUtils.encodeBase64(data);
    String transactionId = getNextTransactionId();
    envelope.transactionId = transactionId;
    mqttPublisher.publish(registryId, UDMI_TOPIC, JsonUtil.stringify(envelope));
    return transactionId;
  }

  /**
   * Get a new unique (not the same as previous one) transaction id.
   *
   * @return new unique transaction id
   */
  private String getNextTransactionId() {
    String transactionId;
    do {
      transactionId = Long.toString(System.currentTimeMillis());
    } while (transactionId.equals(prevTransactionId));
    prevTransactionId = transactionId;
    return transactionId;
  }

  @Override
  public void close() {
    active = false;
    mqttPublisher.close();
  }

  public void setReflectorState(String stateData) {
    mqttPublisher.publish(registryId, "state", stateData);
  }

  static class MessageBundle {

    Map<String, Object> message;
    Map<String, String> attributes;
  }

}
