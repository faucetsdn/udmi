package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;

import com.google.api.client.util.Base64;
import com.google.common.base.Preconditions;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import udmi.schema.SetupUdmiConfig;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Publish messages using the iot core reflector.
 */
public class IotReflectorClient implements MessagePublisher {

  private static final int MIN_REQUIRED_VERSION = 8;
  private static final String IOT_KEY_ALGORITHM = "RS256";
  private static final String UDMS_REFLECT = "UDMS-REFLECT";
  private static final String UDMS_REGION = "us-central1";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";
  private static final String UDMI_FOLDER = "udmi";
  private static final String UDMI_TOPIC = "events/" + UDMI_FOLDER;
  private static final Date REFLECTOR_STATE_TIMESTAMP = new Date();
  private static final String CONFIG_CATEGORY = "config";
  private static final String COMMANDS_CATEGORY = "commands";
  private static final long CONFIG_TIMEOUT_SEC = 10;
  private static final long MESSAGE_POLL_TIME_SEC = 10;
  private final String udmiVersion;
  private final CountDownLatch initialConfigReceived = new CountDownLatch(1);
  private final CountDownLatch initializedStateSent = new CountDownLatch(1);
  private final CountDownLatch validConfigReceived = new CountDownLatch(1);
  private final int requiredVersion;
  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();
  private final MqttPublisher mqttPublisher;
  private final String subscriptionId;
  private final String registryId;
  private final String projectId;
  private boolean isInstallValid;
  private boolean active;
  private String prevTransactionId;
  private Exception syncFailure;

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

    Preconditions.checkState(requiredVersion >= MIN_REQUIRED_VERSION,
        format("Min required version %s not satisfied by tools version %s", MIN_REQUIRED_VERSION,
            requiredVersion));
    this.requiredVersion = requiredVersion;
    registryId = SiteModel.getRegistryActual(iotConfig);
    projectId = iotConfig.project_id;
    udmiVersion = Optional.ofNullable(iotConfig.udmi_version).orElseGet(Common::getUdmiVersion);
    String cloudRegion =
        iotConfig.reflect_region == null ? iotConfig.cloud_region : iotConfig.reflect_region;
    subscriptionId =
        format("%s/%s/%s/%s", projectId, UDMS_REGION, UDMS_REFLECT, registryId);

    try {
      mqttPublisher = new MqttPublisher(makeReflectConfiguration(iotConfig), keyBytes, IOT_KEY_ALGORITHM,
          this::messageHandler, this::errorHandler
      );
    } catch (Exception e) {
      throw new RuntimeException("While connecting MQTT endpoint " + subscriptionId, e);
    }

    try {
      System.err.println("Starting initial UDMI setup process");
      if (!initialConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
        System.err.println("Ignoring initial config received timeout (config likely empty)");
      }
      initializeReflectorState();
      initializedStateSent.countDown();
      if (!validConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException(
            "Config sync timeout expired. Investigate UDMI cloud functions install.", syncFailure);
      }

      active = true;
    } catch (Exception e) {
      mqttPublisher.close();
      throw new RuntimeException("Waiting for initial config", e);
    }
  }

  private static ExecutionConfiguration makeReflectConfiguration(ExecutionConfiguration iotConfig) {
    ExecutionConfiguration reflectConfiguration = GeneralUtils.deepCopy(iotConfig);
    // The reflect registry uses slightly different mappings, where the registry ID and region are
    // fixed, while the device in said registry is the registry proper of the site itself.
    reflectConfiguration.device_id = iotConfig.registry_id;
    reflectConfiguration.registry_id = UDMS_REFLECT;
    reflectConfiguration.cloud_region = UDMS_REGION;
    return reflectConfiguration;
  }

  private void initializeReflectorState() {
    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = System.getenv("USER");
    try {
      System.err.printf("Setting state version %s timestamp %s%n",
          udmiVersion, getTimestamp(REFLECTOR_STATE_TIMESTAMP));
      setReflectorState(udmiState);
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
  }

  private void setReflectorState(UdmiState udmiState) {
    Map<String, Object> map = new HashMap<>();
    map.put(TIMESTAMP_KEY, REFLECTOR_STATE_TIMESTAMP);
    map.put(VERSION_KEY, udmiVersion);
    map.put(SubFolder.UDMI.value(), udmiState);

    mqttPublisher.publish(registryId, SubType.STATE.toString(), stringify(map));
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
      List<String> parts = parseMessageTopic(topic);
      String category = parts.get(0);

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
    String payload = (String) messageMap.remove("payload");
    String decoded = GeneralUtils.decodeBase64(payload);
    Map<String, Object> message = asMap(decoded);
    handleReceivedMessage(attributes, message);
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

      // Check for LEGACY UDMIS folder, and use that instead for backwards compatability. Once
      // UDMI version 1.4.2+ is firmly established, this can be simplified to just UDMI.
      boolean legacyConfig = message.containsKey("udmis");
      final UdmiConfig reflectorConfig;
      if (legacyConfig) {
        System.err.println("UDMI using LEGACY config format, function install upgrade required");
        reflectorConfig = new UdmiConfig();
        Map<String, Object> udmisMessage = toMap(message.get("udmis"));
        SetupUdmiConfig udmis = Optional.ofNullable(
                convertTo(SetupUdmiConfig.class, udmisMessage))
            .orElseGet(SetupUdmiConfig::new);
        reflectorConfig.last_state = getDate((String) udmisMessage.get("last_state"));
        reflectorConfig.setup = udmis;
      } else {
        reflectorConfig = Optional.ofNullable(
                convertTo(UdmiConfig.class, message.get(SubFolder.UDMI.value())))
            .orElseGet(UdmiConfig::new);
      }
      System.err.println("UDMI received reflectorConfig: " + stringify(reflectorConfig));
      Date lastState = reflectorConfig.last_state;
      System.err.println("UDMI matching against expected state timestamp " + getTimestamp(
          REFLECTOR_STATE_TIMESTAMP));
      boolean configMatch = dateEquals(lastState, REFLECTOR_STATE_TIMESTAMP);
      if (configMatch) {
        String deployedVersion = reflectorConfig.setup.udmi_version;
        System.err.println("UDMI deployed version: " + deployedVersion);
        if (!udmiVersion.equals(deployedVersion)) {
          System.err.println("UDMI version mismatch: " + udmiVersion);
        }

        SetupUdmiConfig udmiInfo = reflectorConfig.setup;
        System.err.println("UDMI deployed by " + udmiInfo.deployed_by + " at " + getTimestamp(
            udmiInfo.deployed_at));

        System.err.printf("UDMI functions support versions %s:%s (required %s)%n",
            udmiInfo.functions_min, udmiInfo.functions_max, requiredVersion);
        String baseError = format("UDMI required functions version %d not allowed",
            requiredVersion);
        if (requiredVersion < udmiInfo.functions_min) {
          throw new RuntimeException(
              format("%s: min supported %s. Please update local UDMI install.", baseError,
                  udmiInfo.functions_min));
        }
        if (requiredVersion > udmiInfo.functions_max) {
          throw new RuntimeException(
              format("%s: max supported %s. Please update cloud UDMI install.",
                  baseError, udmiInfo.functions_max));
        }
        isInstallValid = true;
        validConfigReceived.countDown();
      } else {
        System.err.println(
            "UDMI ignoring mismatching config timestamp " + getTimestamp(lastState));
      }
    } catch (Exception e) {
      syncFailure = e;
    }

    // Even through setup might be valid, return false to not process this config message.
    return false;
  }

  private List<String> parseMessageTopic(String topic) {
    List<String> parts = new ArrayList<>(Arrays.asList(topic.substring(1).split("/")));
    checkState("devices".equals(parts.remove(0)), "unknown parsed path field");
    // Next field is registry, not device, since the reflector device holds the site registry.
    checkState(registryId.equals(parts.remove(0)), "unexpected parsed registry id");
    return parts;
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
  public Validator.MessageBundle takeNextMessage(boolean enableTimeout) {
    try {
      if (enableTimeout) {
        return messages.poll(MESSAGE_POLL_TIME_SEC, TimeUnit.SECONDS);
      } else {
        return messages.take();
      }
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

  static class MessageBundle {

    Map<String, Object> message;
    Map<String, String> attributes;
  }

}
