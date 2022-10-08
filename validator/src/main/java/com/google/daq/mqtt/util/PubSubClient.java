package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Preconditions;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SeekRequest;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SystemState;

/**
 * Message publisher that uses PubSub.
 */
public class PubSubClient implements MessagePublisher, MessageHandler {

  private static final String CONNECT_ERROR_FORMAT = "While connecting to project %s";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setSerializationInclusion(Include.NON_NULL);
  private static final String SUBSCRIPTION_ERROR_FORMAT = "While accessing subscription %s";

  private static final long SUBSCRIPTION_RACE_DELAY_MS = 10000;
  private static final String WAS_BASE_64 = "wasBase64";

  private final AtomicBoolean active = new AtomicBoolean();
  private final BlockingQueue<PubsubMessage> messages = new LinkedBlockingDeque<>();
  private final long startTimeSec = System.currentTimeMillis() / 1000;

  private final String projectId;
  private final String registryId;

  private final Subscriber subscriber;
  private final Publisher publisher;
  private final boolean flushSubscription;
  private final Map<String, HandlerConsumer<Object>> handlers = new HashMap<>();
  private final BiMap<String, Class<?>> typeClasses = HashBiMap.create();
  private final Map<Class<?>, SimpleEntry<SubType, SubFolder>> classTypes = new HashMap<>();

  /**
   * Create a simple proxy instance.
   *
   * @param projectId    target project id
   * @param subscription target subscription name
   */
  public PubSubClient(String projectId, String subscription) {
    this(projectId, null, subscription, null);
  }

  /**
   * Create a new PubSub client.
   *
   * @param projectId    target project id
   * @param registryId   target registry id
   * @param subscription target subscription name
   * @param updateTopic  output PubSub topic for updates (else null)
   */
  public PubSubClient(String projectId, String registryId, String subscription,
      String updateTopic) {
    this(projectId, registryId, subscription, updateTopic, true);
  }

  /**
   * Create a new PubSub client.
   *
   * @param projectId    target project id
   * @param registryId   target registry id
   * @param subscription target subscription name
   * @param updateTopic  output PubSub topic for updates (else null)
   * @param reset        if the connection should be reset before use
   */
  public PubSubClient(String projectId, String registryId, String subscription, String updateTopic,
      boolean reset) {
    try {
      this.projectId = Preconditions.checkNotNull(projectId, "project id not defined");
      this.registryId = registryId;
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId,
          subscription);
      this.flushSubscription = reset;
      if (reset) {
        resetSubscription(subscriptionName);
      }
      subscriber = Subscriber.newBuilder(subscriptionName, new MessageProcessor()).build();
      subscriber.startAsync().awaitRunning();

      if (updateTopic != null) {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, updateTopic);
        System.err.println("Sending validation updates to " + topicName);
        publisher = Publisher.newBuilder(topicName).build();
      } else {
        publisher = null;
      }

      initializeHandlerTypes();

      active.set(true);
    } catch (Exception e) {
      throw new RuntimeException(String.format(CONNECT_ERROR_FORMAT, projectId), e);
    }
  }

  private void initializeHandlerTypes() {
    Arrays.stream(SubType.values()).forEach(type -> Arrays.stream(SubFolder.values())
        .forEach(folder -> registerHandlerType(type, folder)));
  }

  private void registerHandlerType(SubType type, SubFolder folder) {
    String mapKey = getMapKey(type, folder);
    Class<?> messageClass = getMessageClass(type, folder);
    if (messageClass != null) {
      typeClasses.put(mapKey, messageClass);
      classTypes.put(messageClass, new SimpleEntry<>(type, folder));
    }
  }

  private Class<?> getMessageClass(SubType type, SubFolder folder) {
    String typeName = Common.capitalize(folder.value()) + Common.capitalize(type.value());
    String className = SystemState.class.getPackageName() + "." + typeName;
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private SeekRequest getCurrentTimeSeekRequest(String subscription) {
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000)
        .build();
    return SeekRequest.newBuilder().setSubscription(subscription).setTime(timestamp).build();
  }

  /**
   * Check if the client is active.
   *
   * @return true if the client is currently active
   */
  public boolean isActive() {
    return active.get();
  }

  /**
   * Process the given message with a timeout.
   *
   * @param handler   the handler to use for processing the message
   * @param timeoutMs timeout in ms waiting for message
   */
  public void processMessage(BiConsumer<Map<String, String>, String> handler, long timeoutMs) {
    throw new RuntimeException("This hasn't been implemented yet");
  }

  /**
   * Process the given message.
   *
   * @param handler the handler to use for processing the message
   */
  @SuppressWarnings("unchecked")
  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> handler) {
    try {
      PubsubMessage message = messages.take();
      long seconds = message.getPublishTime().getSeconds();
      if (flushSubscription && seconds < startTimeSec) {
        System.err.println(String.format("Flushing outdated message from %d seconds ago",
            startTimeSec - seconds));
        return;
      }
      Map<String, String> attributes = message.getAttributesMap();
      byte[] rawData = message.getData().toByteArray();
      final String data;
      boolean base64 = rawData[0] != '{';
      if (base64) {
        data = new String(Base64.decodeBase64(rawData));
      } else {
        data = new String(rawData);
      }
      Map<String, Object> asMap;
      try {
        asMap = OBJECT_MAPPER.readValue(data, TreeMap.class);
      } catch (JsonProcessingException e) {
        asMap = new ErrorContainer(e, data);
      }

      attributes = new HashMap<>(attributes);
      attributes.put(WAS_BASE_64, "" + base64);

      handler.accept(asMap, attributes);
    } catch (Exception e) {
      throw new RuntimeException("Processing pubsub message for " + getSubscriptionId(), e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> clazz, HandlerConsumer<T> handler) {
    String mapKey = typeClasses.inverse().get(clazz);
    if (handlers.put(mapKey, (HandlerConsumer<Object>) handler) != null) {
      throw new RuntimeException("Type handler already defined for " + mapKey);
    }
  }

  @Override
  public void publishMessage(String deviceId, Object message) {
    SimpleEntry<SubType, SubFolder> typePair = classTypes.get(message.getClass());
    String mqttTopic = getMapKey(typePair.getKey(), typePair.getValue());
    publish(deviceId, mqttTopic, JsonUtil.stringify(message));
  }

  @Override
  public void messageLoop() {
    while (isActive()) {
      try {
        processMessage(this::handlerHandler);
      } catch (Exception e) {
        System.err.println("Exception processing received message:");
        e.printStackTrace();
      }
    }
  }

  private void ignoreMessage(Envelope attributes, Object message) {
  }

  private void handlerHandler(Map<String, Object> message, Map<String, String> attributes) {
    Envelope envelope = JsonUtil.convertTo(Envelope.class, attributes);
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      Class<?> handlerType = typeClasses.computeIfAbsent(mapKey, key -> {
        System.err.println("Ignoring messages of type " + mapKey);
        return Object.class;
      });
      Object messageObject = JsonUtil.convertTo(handlerType, message);
      HandlerConsumer<Object> handlerConsumer = handlers.computeIfAbsent(mapKey,
          key -> this::ignoreMessage);
      handlerConsumer.accept(envelope, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  private String getMapKey(SubType subType, SubFolder subFolder) {
    return subFolder + "/" + (subType != null ? subType : SubType.EVENT);
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    try {
      if (deviceId == null) {
        System.err.printf("Refusing to publish to %s due to unspecified device%n", topic);
        return;
      }
      String subFolder = String.format("events/%s/%s", deviceId, topic);
      Preconditions.checkNotNull(registryId, "registry id not defined");
      Map<String, String> attributesMap = Map.of(
          "projectId", projectId,
          "subFolder", subFolder,
          "deviceId", registryId // intentional b/c of udmi_reflect function
      );
      PubsubMessage message = PubsubMessage.newBuilder()
          .setData(ByteString.copyFromUtf8(data))
          .putAllAttributes(attributesMap)
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      publish.get(); // Wait for publish to complete.
      System.err.printf("Published to %s/%s%n", registryId, subFolder);
    } catch (Exception e) {
      throw new RuntimeException("While publishing message", e);
    }
  }

  @Override
  public void close() {
    if (subscriber != null) {
      active.set(false);
      subscriber.stopAsync();
    }
  }

  public String getSubscriptionId() {
    return subscriber.getSubscriptionNameString();
  }

  private void resetSubscription(ProjectSubscriptionName subscriptionName) {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      System.err.println("Resetting existing subscription " + subscriptionName);
      subscriptionAdminClient.seek(getCurrentTimeSeekRequest(subscriptionName.toString()));
      Thread.sleep(SUBSCRIPTION_RACE_DELAY_MS);
    } catch (NotFoundException e) {
      throw new RuntimeException("Missing subscription for " + subscriptionName);
    } catch (Exception e) {
      throw new RuntimeException(
          String.format(SUBSCRIPTION_ERROR_FORMAT, subscriptionName), e);
    }
  }

  static class ErrorContainer extends TreeMap<String, Object> {

    ErrorContainer(Exception e, String message) {
      put("exception", e.toString());
      put("message", message);
    }
  }

  private class MessageProcessor implements MessageReceiver {

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      messages.offer(message);
      consumer.ack();
    }
  }
}
