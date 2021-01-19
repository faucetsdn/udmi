package com.google.bos.iot.core.proxy;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.daq.mqtt.util.CloudIotConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiConsumer;

public class IotCoreClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setSerializationInclusion(Include.NON_NULL);

  private static final String IOT_KEY_ALGORITHM = "RS256";
  private static final String UDMS_REFLECT = "UDMS-REFLECT";
  private static final String WAS_BASE_64 = "wasBase64";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";

  private final BlockingQueue<MessageBundle> messages = new LinkedBlockingDeque<>();

  private final MqttPublisher mqttPublisher;
  private final String subscriptionId;
  private final String siteName;
  private final String projectId;
  private boolean active;

  public IotCoreClient(String projectId, CloudIotConfig iotConfig, String keyFile) {
    byte[] keyBytes = getFileBytes(keyFile);
    siteName = iotConfig.registry_id;
    this.projectId = projectId;
    mqttPublisher = new MqttPublisher(projectId, iotConfig.cloud_region, UDMS_REFLECT,
        siteName, keyBytes, IOT_KEY_ALGORITHM, this::messageHandler, this::errorHandler);
    subscriptionId =
        String.format("%s/%s/%s/%s", projectId, iotConfig.cloud_region, UDMS_REFLECT, iotConfig.registry_id);
    active = true;
  }

  private void messageHandler(String topic, String payload) {
    final Map<String, String> attributes = new HashMap<>();
    TreeMap<String, Object> asMap;
    try {
      byte[] rawData = payload.getBytes();
      boolean base64 = rawData[0] != '{';
      attributes.put(WAS_BASE_64, "" + base64);

      final String data = new String(base64 ? Base64.decodeBase64(rawData) : rawData);
      asMap = OBJECT_MAPPER.readValue(data, TreeMap.class);
      String category = parseMessageTopic(topic, attributes);
      if (!"commands".equals(category)) {
        return;
      }
    } catch (Exception e) {
      asMap = new ErrorContainer(e, topic, payload);
    }

    MessageBundle messageBundle = new MessageBundle();
    messageBundle.attributes = attributes;
    messageBundle.message = asMap;

    messages.offer(messageBundle);
  }

  private String parseMessageTopic(String topic, Map<String, String> attributes) {
    String[] parts = topic.substring(1).split("/");
    assert "devices".equals(parts[0]);
    assert siteName.equals(parts[1]);
    String messageCategory = parts[2];
    attributes.put("deviceRegistryId", siteName);
    if (messageCategory.equals("commands")) {
      assert "devices".equals(parts[3]);
      attributes.put( "deviceId", parts[4]);
      attributes.put("subFolder", parts[5]);
      attributes.put("subType", parts[6]);
      assert parts.length == 7;
    } else {
      assert parts.length == 3;
    }
    attributes.put("projectId", projectId);
    attributes.put("deviceNumId", MOCK_DEVICE_NUM_ID);
    return messageCategory;
  }

  private void errorHandler(MqttPublisher mqttPublisher, Throwable throwable) {
    System.err.println("mqtt client error: " + throwable.getMessage());
    active = false;
    mqttPublisher.close();
  }

  private byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public boolean isActive() {
    return active;
  }

  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator) {
    try {
      MessageBundle message = messages.take();
      validator.accept(message.message, message.attributes);
    } catch (Exception e) {
      throw new RuntimeException("While processing message on subscription " + subscriptionId, e);
    }
  }

  static class MessageBundle {
    Map<String, Object> message;
    Map<String, String> attributes;
  }

  static class ErrorContainer extends TreeMap<String, Object> {
    ErrorContainer(Exception e, String topic, String message) {
      put("exception", e.toString());
      put("topic", topic);
      put("message", message);
    }
  }
}
