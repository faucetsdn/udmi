package com.google.daq.mqtt.validator;

import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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
  private static final String TRACE_FILE_SUFFIX = ".json";
  public static final String MSG_SOURCE = "msgSource";
  private final File messageDir;
  private final String registryId;
  private final Map<String, List<String>> deviceMessageLists = new HashMap<>();
  private final Map<String, Map<String, Object>> deviceMessages = new HashMap<>();
  private final Map<String, Map<String, String>> deviceAttributes = new HashMap<>();
  private final Map<String, String> deviceNextTimestamp = new HashMap<>();
  private final Map<String, String> deviceLastTimestamp = new HashMap<>();
  private final List<OutputBundle> outputMessages = new ArrayList<>();
  int messageCount;
  private boolean isActive;
  private String lastValidTimestamp;

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
    List<String> messages = Arrays.stream(Objects.requireNonNull(deviceDir.list()))
        .filter(filename -> filename.endsWith(TRACE_FILE_SUFFIX))
        .sorted()
        .collect(Collectors.toList());
    deviceMessageLists.put(deviceId, messages);
    prepNextMessage(deviceId);
  }

  private void prepNextMessage(String deviceId) {
    try {
      if (deviceMessageLists.get(deviceId).isEmpty()) {
        return;
      }
      String msgName = deviceMessageLists.get(deviceId).remove(0);
      Map<String, String> attributes = makeAttributes(deviceId, msgName);
      deviceAttributes.put(deviceId, attributes);
      Map<String, Object> msgObj = getMessageObject(deviceId, msgName);
      deviceMessages.put(deviceId, msgObj);
      if (!msgObj.containsKey("timestamp")) {
        msgObj.put("timestamp", deviceLastTimestamp.get(deviceId));
      }
      String timestamp = Objects.requireNonNull((String) msgObj.get("timestamp"));
      deviceNextTimestamp.put(deviceId, timestamp);
    } finally {
      isActive = !deviceMessages.isEmpty();
    }
  }

  private Map<String, Object> getMessageObject(String deviceId, String msgName) {
    try {
      File deviceDir = new File(messageDir, deviceId);
      File msgFile = new File(deviceDir, msgName);
      @SuppressWarnings("unchecked")
      Map<String, Object> treeMap = OBJECT_MAPPER.readValue(msgFile, TreeMap.class);
      return treeMap;
    } catch (Exception e) {
      return new ErrorContainer(e, msgName, lastValidTimestamp);
    }
  }

  private Map<String, String> makeAttributes(String deviceId, String msgName) {
    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put(MSG_SOURCE, msgName);
      attributes.put("deviceId", deviceId);
      attributes.put("deviceNumId", getNumId(deviceId));
      attributes.put("projectId", PLAYBACK_PROJECT_ID);
      attributes.put("deviceRegistryId", registryId);

      Matcher matcher = filenamePattern.matcher(msgName);
      if (matcher.matches()) {
        attributes.put(SUBTYPE_PROPERTY_KEY, matcher.group(1));
        attributes.put(SUBFOLDER_PROPERTY_KEY, matcher.group(2));
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
  public String publish(String deviceId, String topic, String data) {
    try {
      OutputBundle outputBundle = new OutputBundle();
      outputBundle.deviceId = deviceId;
      outputBundle.topic = topic;
      outputBundle.message = OBJECT_MAPPER.readValue(data, TreeMap.class);
      outputMessages.add(outputBundle);
      return null;
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
  public Validator.MessageBundle takeNextMessage() {
    final String deviceId = getNextDevice();
    final Map<String, Object> message = deviceMessages.remove(deviceId);
    final Map<String, String> attributes = deviceAttributes.remove(deviceId);
    lastValidTimestamp = deviceNextTimestamp.remove(deviceId);
    deviceLastTimestamp.put(deviceId, lastValidTimestamp);
    prepNextMessage(deviceId);
    String messageName = attributes.get(MSG_SOURCE);
    System.out.printf("Replay %s %s for %s%n", messageName, lastValidTimestamp, deviceId);
    messageCount++;
    MessageBundle bundle = new MessageBundle();
    bundle.message = message;
    bundle.attributes = attributes;
    bundle.timestamp = lastValidTimestamp;
    return bundle;
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
