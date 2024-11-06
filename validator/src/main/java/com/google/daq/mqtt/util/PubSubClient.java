package com.google.daq.mqtt.util;

import static com.google.api.client.util.Preconditions.checkNotNull;
import static com.google.udmi.util.Common.NAMESPACE_SEPARATOR;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.SOURCE_SEPARATOR;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static com.google.udmi.util.PubSubReflector.USER_NAME_DEFAULT;
import static java.lang.String.format;
import static java.time.Instant.ofEpochSecond;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.NotFoundException;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.cloud.Tuple;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SeekRequest;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.SetupUdmiConfig;
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
  public static final String SUBSCRIPTION_ROOT = "udmi_target";

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
  private final boolean useReflector;

  /**
   * Create a simple proxy instance.
   *
   * @param projectId    target project id
   * @param subscription target subscription name
   */
  public PubSubClient(String projectId, String subscription) {
    this(projectId, null, subscription, null, false, false);
  }

  /**
   * Create a new PubSub client.
   *
   * @param projectId    target project id
   * @param registryId   target registry id
   * @param subscription target subscription name
   * @param updateTopic  output PubSub topic for updates (else null)
   * @param reflect      if output messages should be encapsulated
   */
  public PubSubClient(String projectId, String registryId, String subscription,
      String updateTopic, boolean reflect) {
    this(projectId, registryId, subscription, updateTopic, reflect, true);
  }

  /**
   * Create a new PubSub client.
   *
   * @param projectId    target project id
   * @param registryId   target registry id
   * @param subscription target subscription name
   * @param updateTopic  output PubSub topic for updates (else null)
   * @param reflect      if output messages should be encapsulated
   * @param reset        if the connection should be reset before use
   */
  public PubSubClient(String projectId, String registryId, String subscription, String updateTopic,
      boolean reflect, boolean reset) {
    try {
      this.projectId = checkNotNull(projectId, "project id not defined");
      this.registryId = registryId;
      this.useReflector = reflect;
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
      throw new RuntimeException(format(CONNECT_ERROR_FORMAT, projectId), e);
    }
  }

  /**
   * Factory method for a client from a configuration.
   */
  public static MessagePublisher from(ExecutionConfiguration iotConfig,
      BiConsumer<String, String> messageHandler, Consumer<Throwable> errorHandler) {
    ifNotNullThrow(messageHandler, "message handler should be null");
    ifNotNullThrow(errorHandler, "error handler should be null");
    Tuple<String, String> t = getFeedInfo(iotConfig);
    return new PubSubClient(iotConfig.project_id, iotConfig.registry_id, t.x(), t.y(), false);
  }

  /**
   * Get the information for pubsub subscription and topic, extracted from the configuration.
   */
  public static Tuple<String, String> getFeedInfo(ExecutionConfiguration iotConfig) {
    String namespace = ofNullable(iotConfig.udmi_namespace).map(p -> p + NAMESPACE_SEPARATOR)
        .orElse("");
    String topic = namespace + SUBSCRIPTION_ROOT;
    String userName = SOURCE_SEPARATOR + ofNullable(iotConfig.user_name).orElse(USER_NAME_DEFAULT);
    String subscription = topic + userName;
    return Tuple.of(subscription, topic);
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

  @Override
  public void activate() {
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

  @Override
  public Validator.MessageBundle takeNextMessage(QuerySpeed speed) {
    try {
      PubsubMessage message = messages.take();
      long seconds = message.getPublishTime().getSeconds();
      if (flushSubscription && seconds < startTimeSec) {
        System.err.printf("Flushing outdated message from %d seconds ago%n",
            startTimeSec - seconds);
        return null;
      }
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
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = OBJECT_MAPPER.readValue(data, TreeMap.class);
        asMap = dataMap;
      } catch (JsonProcessingException e) {
        asMap = new ErrorContainer(e, getSubscriptionId(), getTimestamp());
      }

      HashMap<String, String> attributes = new HashMap<>(message.getAttributesMap());
      attributes.computeIfAbsent(PUBLISH_TIME_KEY,
          key -> isoConvert(ofEpochSecond(message.getPublishTime().getSeconds())));
      attributes.put(WAS_BASE_64, "" + base64);

      MessageBundle bundle = new MessageBundle();
      bundle.message = asMap;
      bundle.attributes = attributes;
      return bundle;
    } catch (Exception e) {
      throw new RuntimeException("While taking next message", e);
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
    publish(deviceId, mqttTopic, stringify(message));
  }

  @Override
  public void messageLoop() {
    while (isActive()) {
      try {
        handlerHandler(takeNextMessage(QuerySpeed.QUICK));
      } catch (Exception e) {
        System.err.println("Exception processing received message:");
        e.printStackTrace();
      }
    }
  }

  private void ignoreMessage(Envelope attributes, Object message) {
  }

  private void handlerHandler(MessageBundle bundle) {
    if (bundle == null) {
      return;
    }

    Envelope envelope = JsonUtil.convertTo(Envelope.class, bundle.attributes);
    String mapKey = getMapKey(envelope.subType, envelope.subFolder);
    try {
      Class<?> handlerType = typeClasses.computeIfAbsent(mapKey, key -> {
        System.err.println("Ignoring messages of type " + mapKey);
        return Object.class;
      });
      Object messageObject = JsonUtil.convertTo(handlerType, bundle.message);
      HandlerConsumer<Object> handlerConsumer = handlers.computeIfAbsent(mapKey,
          key -> this::ignoreMessage);
      handlerConsumer.accept(envelope, messageObject);
    } catch (Exception e) {
      throw new RuntimeException("While processing message key " + mapKey, e);
    }
  }

  private String getMapKey(SubType subType, SubFolder subFolder) {
    return subFolder + "/" + (subType != null ? subType : SubType.EVENTS);
  }

  @Override
  public String publish(String deviceId, String topic, String data) {
    return useReflector ? publishReflector(deviceId, topic, data)
        : publishDirect(deviceId, topic, data);
  }

  private String publishDirect(String deviceId, String topic, String data) {
    try {
      Envelope envelopedData = makeReflectorMessage(deviceId, topic, null);
      PubsubMessage message = PubsubMessage.newBuilder()
          .setData(ByteString.copyFromUtf8(data))
          .putAllAttributes(toStringMap(envelopedData))
          .build();
      publisher.publish(message);
      System.err.printf("Published to %s/%s/%s%n", registryId, deviceId, topic);
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While publishing direct message", e);
    }
  }

  private String publishReflector(String deviceId, String topic, String data) {
    try {
      Map<String, String> attributesMap = Map.of(
          "projectId", projectId,
          "subFolder", SubFolder.UDMI.toString()
      );
      Envelope envelopedData = makeReflectorMessage(deviceId, topic, data);
      PubsubMessage message = PubsubMessage.newBuilder()
          .setData(ByteString.copyFromUtf8(stringify(envelopedData)))
          .putAllAttributes(attributesMap)
          .build();
      publisher.publish(message);
      System.err.printf("Published to %s/%s/%s%n", registryId, deviceId, topic);
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While publishing reflector message", e);
    }
  }

  private Envelope makeReflectorMessage(String deviceId, String topic, String data) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = checkNotNull(registryId, "registry id not defined");
    envelope.deviceId = deviceId;
    envelope.payload = ifNotNullGet(data, GeneralUtils::encodeBase64);
    String[] parts = topic.split("/");
    envelope.subFolder = SubFolder.fromValue(parts[0]);
    envelope.subType = SubType.fromValue(parts[1]);
    envelope.transactionId = IotReflectorClient.getNextTransactionId();
    envelope.publishTime = new Date();
    return envelope;
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
          format(SUBSCRIPTION_ERROR_FORMAT, subscriptionName), e);
    }
  }

  @Override
  public SetupUdmiConfig getVersionInformation() {
    SetupUdmiConfig setupUdmiConfig = new SetupUdmiConfig();
    setupUdmiConfig.udmi_ref = "PubSub";
    return setupUdmiConfig;
  }

  private class MessageProcessor implements MessageReceiver {

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      messages.offer(message);
      consumer.ack();
    }
  }
}
