package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.ReflectorConfig;
import udmi.schema.ReflectorState;
import udmi.schema.SetupReflectorConfig;
import udmi.schema.SetupReflectorState;

/**
 * Publish messages using the iot core reflector.
 */
public class IotReflectorClient implements MessagePublisher {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setSerializationInclusion(Include.NON_NULL);

  private static final String IOT_KEY_ALGORITHM = "RS256";
  private static final String UDMS_REFLECT = "UDMS-REFLECT";
  private static final String WAS_BASE_64 = "wasBase64";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";
  private static final Set<String> EXPECTED_CATEGORIES = ImmutableSet.of("commands", "config");
  private static final String UDMI_FOLDER = "udmi";
  private static final String UDMI_TOPIC = "events/" + UDMI_FOLDER;
  private static final Date REFLECTOR_STATE_TIMESTAMP = new Date();
  private final String udmiVersion;
  private final CountDownLatch initialConfigReceived = new CountDownLatch(1);
  private final CountDownLatch initializedStateSent = new CountDownLatch(1);
  private final CountDownLatch validConfigReceived = new CountDownLatch(1);
  private boolean isInstallValid;

  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();

  private final MqttPublisher mqttPublisher;
  private final String subscriptionId;
  private final String registryId;
  private final String projectId;
  private boolean active;

  /**
   * Create a new reflector instance.
   *
   * @param iotConfig configuration file
   */
  public IotReflectorClient(ExecutionConfiguration iotConfig) {
    final byte[] keyBytes;
    checkNotNull(iotConfig.key_file, "missing key file in config");
    try {
      keyBytes = getFileBytes(iotConfig.key_file);
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading key file " + new File(iotConfig.key_file).getAbsolutePath(),
          e);
    }

    registryId = iotConfig.registry_id;
    projectId = iotConfig.project_id;
    udmiVersion = checkNotNull(iotConfig.udmi_version, "udmi_version");
    String cloudRegion =
        iotConfig.reflect_region == null ? iotConfig.cloud_region : iotConfig.reflect_region;
    subscriptionId =
        String.format("%s/%s/%s/%s", projectId, cloudRegion, UDMS_REFLECT,
            iotConfig.registry_id);

    try {
      mqttPublisher = new MqttPublisher(projectId, cloudRegion, UDMS_REFLECT,
          registryId, keyBytes, IOT_KEY_ALGORITHM, this::messageHandler, this::errorHandler);
    } catch (Exception e) {
      throw new RuntimeException("While connecting MQTT endpoint " + subscriptionId, e);
    }

    try {
      initialConfigReceived.await();
      initializeReflectorState();
      initializedStateSent.countDown();
      validConfigReceived.await();

      active = true;
    } catch (Exception e) {
      throw new RuntimeException("Interrupted waiting for initial config", e);
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
    final Map<String, String> attributes = new HashMap<>();
    if (payload.length() == 0) {
      return;
    }
    TreeMap<String, Object> asMap;
    try {
      byte[] rawData = payload.getBytes();
      boolean base64 = rawData[0] != '{';
      String category = parseMessageTopic(topic, attributes);

      if ("null".equals(payload)) {
        asMap = null;
      } else {
        String data = new String(base64 ? Base64.decodeBase64(rawData) : rawData);
        attributes.put(WAS_BASE_64, "" + base64);
        asMap = OBJECT_MAPPER.readValue(data, TreeMap.class);
      }
      if (!EXPECTED_CATEGORIES.contains(category)) {
        return;
      }
    } catch (Exception e) {
      asMap = new ErrorContainer(e, payload, JsonUtil.getTimestamp());
    }

    Validator.MessageBundle messageBundle = new Validator.MessageBundle();
    messageBundle.attributes = attributes;
    messageBundle.message = asMap;

    if (ensureSyncdCloud(messageBundle)) {
      messages.offer(messageBundle);
    }
  }

  private boolean ensureSyncdCloud(Validator.MessageBundle messageBundle) {
    if (!"config".equals(messageBundle.attributes.get("category"))) {
      return isInstallValid;
    }

    try {
      initialConfigReceived.countDown();
      if (initializedStateSent.getCount() > 0) {
        return false;
      }

      ReflectorConfig reflectorConfig = JsonUtil.convertTo(ReflectorConfig.class,
          messageBundle.message);
      System.err.println("UDMIS received reflectorConfig: " + stringify(reflectorConfig));
      SetupReflectorConfig udmisInfo = reflectorConfig.udmis;
      Date lastState = udmisInfo == null ? null : udmisInfo.last_state;
      System.err.println("UDMIS matching against expected state timestamp " + getTimestamp(
          REFLECTOR_STATE_TIMESTAMP));
      isInstallValid = dateEquals(lastState, REFLECTOR_STATE_TIMESTAMP);
      if (isInstallValid) {
        System.err.println("UDMIS version " + reflectorConfig.version);
        if (!udmiVersion.equals(reflectorConfig.version)) {
          System.err.println("UDMIS local/cloud UDMI version mismatch!");
        }

        System.err.println("UDMIS deployed by " + udmisInfo.deployed_by + " at " + getTimestamp(
            udmisInfo.deployed_at));

        int required = 3; // TODO: Make this a parameter.
        System.err.println(String.format("UDMIS functions support versions %s:%s (required %s)",
            udmisInfo.functions_min, udmisInfo.functions_max, required));
        String baseError = String.format("UDMIS required functions version %d not allowed",
            required);
        if (required < udmisInfo.functions_min) {
          throw new RuntimeException(
              String.format("%s, min supported %s. Please update the local install.", baseError,
                  udmisInfo.functions_min));
        }
        if (required > udmisInfo.functions_max) {
          throw new RuntimeException(
              String.format("%s, max supported %s. Please update the cloud install..",
                  baseError, udmisInfo.functions_max));
        }
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

  private String parseMessageTopic(String topic, Map<String, String> attributes) {
    String[] parts = topic.substring(1).split("/");
    assert "devices".equals(parts[0]);
    assert registryId.equals(parts[1]);
    String messageCategory = parts[2];
    attributes.put("category", messageCategory);
    attributes.put("deviceRegistryId", registryId);
    if (messageCategory.equals("commands")) {
      assert "devices".equals(parts[3]);
      attributes.put("deviceId", parts[4]);
      attributes.put("subType", parts[5]);
      attributes.put("subFolder", parts[6]);
      attributes.put("deviceNumId", MOCK_DEVICE_NUM_ID);
      assert parts.length == 7;
    } else {
      assert parts.length == 3;
    }
    attributes.put("projectId", projectId);
    return messageCategory;
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
  public void publish(String deviceId, String topic, String data) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    String[] parts = topic.split("/", 2);
    envelope.subFolder = SubFolder.fromValue(parts[0]);
    envelope.subType = SubType.fromValue(parts[1]);
    envelope.payload = GeneralUtils.encodeBase64(data);
    mqttPublisher.publish(registryId, UDMI_TOPIC, JsonUtil.stringify(envelope));
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
