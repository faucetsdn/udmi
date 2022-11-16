package com.google.bos.iot.core.proxy;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import udmi.schema.ExecutionConfiguration;

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

  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingDeque<>();

  private final MqttPublisher mqttPublisher;
  private final String subscriptionId;
  private final String siteName;
  private final String projectId;
  private boolean active;

  /**
   * Create a new reflector instance.
   *
   * @param projectId target project
   * @param iotConfig configuration file
   * @param keyFile   auth key file
   */
  public IotReflectorClient(String projectId, ExecutionConfiguration iotConfig, String keyFile) {
    final byte[] keyBytes;
    try {
      keyBytes = getFileBytes(keyFile);
    } catch (Exception e) {
      throw new RuntimeException("While loading key file " + new File(keyFile).getAbsolutePath(),
          e);
    }

    siteName = iotConfig.registry_id;
    this.projectId = projectId;
    String cloudRegion =
        iotConfig.reflect_region == null ? iotConfig.cloud_region : iotConfig.reflect_region;
    subscriptionId =
        String.format("%s/%s/%s/%s", projectId, cloudRegion, UDMS_REFLECT,
            iotConfig.registry_id);

    try {
      mqttPublisher = new MqttPublisher(projectId, cloudRegion, UDMS_REFLECT,
          siteName, keyBytes, IOT_KEY_ALGORITHM, this::messageHandler, this::errorHandler);
    } catch (Exception e) {
      throw new RuntimeException("While connecting MQTT endpoint " + subscriptionId, e);
    }

    active = true;
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
      asMap = new ErrorContainer(e, topic, payload);
    }

    Validator.MessageBundle messageBundle = new Validator.MessageBundle();
    messageBundle.attributes = attributes;
    messageBundle.message = asMap;

    messages.offer(messageBundle);
  }

  private String parseMessageTopic(String topic, Map<String, String> attributes) {
    String[] parts = topic.substring(1).split("/");
    assert "devices".equals(parts[0]);
    assert siteName.equals(parts[1]);
    String messageCategory = parts[2];
    attributes.put("category", messageCategory);
    attributes.put("deviceRegistryId", siteName);
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
    throw new RuntimeException("Not implemented for file data sink");
  }

  @Override
  public void processMessage(Consumer<Validator.MessageBundle> validator) {
    try {
      Validator.MessageBundle message = messages.take();
      validator.accept(message);
    } catch (Exception e) {
      throw new RuntimeException("While processing message on subscription " + subscriptionId, e);
    }
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    String reflectorTopic = String.format("events/devices/%s/%s", deviceId, topic);
    mqttPublisher.publish(siteName, reflectorTopic, data);
  }

  @Override
  public void close() {
    active = false;
    mqttPublisher.close();
  }

  public void setReflectorState(String stateData) {
    mqttPublisher.publish(siteName, "state", stateData);
  }

  static class MessageBundle {

    Map<String, Object> message;
    Map<String, String> attributes;
  }

}
