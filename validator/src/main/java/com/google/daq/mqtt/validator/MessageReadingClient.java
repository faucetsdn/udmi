package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.MessagePublisher;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Client to read messages from a directory of captured messages.
 */
public class MessageReadingClient implements MessagePublisher {

  private static final String PLAYBACK_PROJECT_ID = "playback-project";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final Pattern filenamePattern = Pattern.compile("[0-9]+_([a-z]+)_([a-z]+)\\.json");
  private final File messageDir;
  private final String registryId;
  int messageCount;

  boolean isActive = true;
  Map<String, List<String>> deviceMessageLists = new HashMap<>();
  Map<String, Map<String, Object>> deviceMessages = new HashMap<>();
  Map<String, Map<String, String>> deviceAttributes = new HashMap<>();
  Map<String, String> deviceNextTimestamp = new HashMap<>();
  private final List<OutputBundle> outputMessages = new ArrayList<>();

  /**
   * Create a new client.
   *
   * @param registryId registry to use for attribute creation
   * @param dirStr     directory containing message trace
   */
  public MessageReadingClient(String registryId, String dirStr) {
    this.registryId = registryId;
    messageDir = new File(dirStr);
    if (!messageDir.exists() || !messageDir.isDirectory()) {
      throw new RuntimeException("Message directory not found " + messageDir.getAbsolutePath());
    }
    Arrays.stream(Objects.requireNonNull(messageDir.list())).forEach(this::prepDevice);
  }

  private void prepDevice(String deviceId) {
    File deviceDir = new File(messageDir, deviceId);
    List<String> messages = Arrays.stream(Objects.requireNonNull(deviceDir.list())).sorted()
        .collect(Collectors.toList());
    deviceMessageLists.put(deviceId, messages);
    prepNextMessage(deviceId);
  }

  @SuppressWarnings("unchecked")
  private void prepNextMessage(String deviceId) {
    try {
      File deviceDir = new File(messageDir, deviceId);
      if (deviceMessageLists.get(deviceId).isEmpty()) {
        return;
      }
      String msgName = deviceMessageLists.get(deviceId).remove(0);
      File msgFile = new File(deviceDir, msgName);
      Map<String, Object> msgObj = OBJECT_MAPPER.readValue(msgFile, TreeMap.class);
      deviceMessages.put(deviceId, msgObj);
      deviceAttributes.put(deviceId, makeAttributes(deviceId, msgName));
      String timestamp = Objects.requireNonNull((String) msgObj.get("timestamp"));
      deviceNextTimestamp.put(deviceId, timestamp);
    } catch (Exception e) {
      throw new RuntimeException("While handling next message for " + deviceId, e);
    }
  }

  private Map<String, String> makeAttributes(String deviceId, String msgName) {
    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("deviceId", deviceId);
      attributes.put("deviceNumId", getNumId(deviceId));
      attributes.put("projectId", PLAYBACK_PROJECT_ID);
      attributes.put("deviceRegistryId", registryId);

      Matcher matcher = filenamePattern.matcher(msgName);
      if (matcher.matches()) {
        attributes.put("subType", matcher.group(1));
        attributes.put("subFolder", matcher.group(2));
      } else {
        throw new RuntimeException("Malformed filename " + msgName);
      }
      return attributes;
    } catch (Exception e) {
      throw new RuntimeException("While creating attributes for " + deviceId + " " + msgName, e);
    }
  }

  private String getNumId(String deviceId) {
    return String.format("%014d", deviceId.hashCode());
  }

  @Override
  @SuppressWarnings("unchecked")
  public void publish(String deviceId, String topic, String data) {
    try {
      OutputBundle outputBundle = new OutputBundle();
      outputBundle.deviceId = deviceId;
      outputBundle.topic = topic;
      outputBundle.message = OBJECT_MAPPER.readValue(data, TreeMap.class);
      outputMessages.add(outputBundle);
    } catch (Exception e) {
      throw new RuntimeException("While converting message data", e);
    }
  }

  List<OutputBundle> getOutputMessages() {
    return outputMessages;
  }

  @Override
  public void close() {
    isActive = false;
  }

  @Override
  public String getSubscriptionId() {
    return messageDir.getAbsolutePath();
  }

  @Override
  public boolean isActive() {
    return isActive;
  }

  @Override
  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator) {
    String deviceId = getNextDevice();
    Map<String, Object> message = deviceMessages.remove(deviceId);
    Map<String, String> attributes = deviceAttributes.remove(deviceId);
    String timestamp = deviceNextTimestamp.remove(deviceId);
    System.out.printf("Replay %s for %s%n", timestamp, deviceId);
    messageCount++;
    validator.accept(message, attributes);
    prepNextMessage(deviceId);
    if (deviceMessages.isEmpty()) {
      isActive = false;
    }
  }

  private String getNextDevice() {
    String nextDevice = deviceNextTimestamp.keySet().iterator().next();
    String nextTimestamp = deviceNextTimestamp.get(nextDevice);
    for (String deviceId : deviceNextTimestamp.keySet()) {
      String deviceTimestamp = deviceNextTimestamp.get(deviceId);
      if (deviceTimestamp.compareTo(nextTimestamp) < 0) {
        nextDevice = deviceId;
        nextTimestamp = deviceTimestamp;
      }
    }
    return nextDevice;
  }

  static class OutputBundle {

    public String deviceId;
    public String topic;
    public TreeMap<String, Object> message;
  }
}
