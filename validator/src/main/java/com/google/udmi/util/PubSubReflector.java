package com.google.udmi.util;

import static com.google.api.client.util.Preconditions.checkNotNull;
import static com.google.bos.iot.core.proxy.ProxyTarget.STATE_TOPIC;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.CATEGORY_PROPERTY_KEY;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SOURCE_KEY;
import static com.google.udmi.util.Common.SOURCE_SEPARATOR;
import static com.google.udmi.util.Common.SOURCE_SEPARATOR_REGEX;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static com.google.udmi.util.SiteModel.DEFAULT_GBOS_HOSTNAME;
import static java.lang.String.format;
import static java.time.Instant.ofEpochSecond;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.Base64;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.NotFoundException;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SeekRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.SetupUdmiConfig;

/**
 * Message publisher that uses PubSub.
 */
public class PubSubReflector implements MessagePublisher {

  private static final String CONNECT_ERROR_FORMAT = "While connecting to project %s";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(
      SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(Include.NON_NULL);
  private static final String SUBSCRIPTION_ERROR_FORMAT = "While accessing subscription %s";

  private static final long SUBSCRIPTION_RACE_DELAY_MS = 10000;
  private static final String WAS_BASE_64 = "wasBase64";
  private static final String UDMI_REFLECT_TOPIC = "udmi_reflect";
  private static final String UDMI_REPLY_TOPIC = "udmi_reply";
  public static final String USER_NAME_DEFAULT = "debug";
  private static final AtomicInteger sessionCount = new AtomicInteger();
  private static String subscriptionId;
  private static PubSubReflector reflectorClient;
  private static final Map<String, BiConsumer<String, String>> messageHandlers
      = new ConcurrentHashMap<>();
  private static final Map<String, Consumer<Throwable>> errorHandlers = new ConcurrentHashMap<>();
  private static BiConsumer<String, String> defaultMessageHandler;
  private static Consumer<Throwable> defaultErrorHandler;

  private final AtomicBoolean activated = new AtomicBoolean();
  private final AtomicBoolean active = new AtomicBoolean();
  private final long startTimeSec = System.currentTimeMillis() / 1000;

  private final String projectId;
  private final String registryId;

  private final Subscriber subscriber;
  private final Publisher publisher;
  private final boolean flushSubscription;
  private final String userName;

  /**
   * Create a new PubSub client.
   *
   * @param projectId      target project id
   * @param registryId     target registry id
   * @param updateTopic    output PubSub topic for updates (else null)
   * @param userName       user id running this operation
   * @param subscriptionId target subscription name
   */
  public PubSubReflector(String projectId, String registryId, String updateTopic, String userName,
      String subscriptionId) {
    this(projectId, registryId, updateTopic, userName, subscriptionId, true);
  }

  /**
   * Create a new PubSub client.
   *
   * @param projectId      target project id
   * @param registryId     target registry id
   * @param updateTopic    output PubSub topic for updates (else null)
   * @param userName       user id running this operation
   * @param subscriptionId target subscription name
   * @param reset          if the connection should be reset before use
   */
  public PubSubReflector(String projectId, String registryId, String updateTopic, String userName,
      String subscriptionId, boolean reset) {
    try {
      checkState(sessionCount.incrementAndGet() == 1, "multiple internal sessions not supported");
      this.projectId = checkNotNull(projectId, "project id not defined");
      this.registryId = registryId;
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId,
          subscriptionId);
      this.flushSubscription = reset;
      if (reset) {
        resetSubscription(subscriptionName);
      }
      subscriber = Subscriber.newBuilder(subscriptionName, new MessageProcessor()).build();
      this.userName = userName;

      if (updateTopic != null) {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, updateTopic);
        System.err.println("Sending reflector messages to " + topicName);
        publisher = Publisher.newBuilder(topicName).build();
      } else {
        publisher = null;
      }
    } catch (Exception e) {
      throw new RuntimeException(format(CONNECT_ERROR_FORMAT, projectId), e);
    }
  }

  /**
   * Create a new instance factory method.
   *
   * @param iotConfig      basic execution configuration
   * @param messageHandler message handler callback
   * @param errorHandler   error/exception handler
   */
  public static MessagePublisher from(ExecutionConfiguration iotConfig,
      BiConsumer<String, String> messageHandler, Consumer<Throwable> errorHandler) {
    String registryActual = SiteModel.getRegistryActual(iotConfig);
    String namespacePrefix = getNamespacePrefix(iotConfig.udmi_namespace);
    String userName = ofNullable(iotConfig.user_name).orElse(USER_NAME_DEFAULT);
    String newId = namespacePrefix + UDMI_REPLY_TOPIC + SOURCE_SEPARATOR + userName;
    checkState(subscriptionId == null || subscriptionId.equals(newId),
        "unsupported difference in subscription id");
    subscriptionId = newId;
    checkState(messageHandlers.put(registryActual, messageHandler) == null, "duplicate handler");
    checkState(errorHandlers.put(registryActual, errorHandler) == null, "duplicate handler");

    ifNullThen(reflectorClient, () -> {
      defaultMessageHandler = messageHandler;
      defaultErrorHandler = errorHandler;
      ExecutionConfiguration reflectorConfig = IotReflectorClient.makeReflectConfiguration(
          iotConfig, registryActual);
      String registryId = MessagePublisher.getRegistryId(reflectorConfig);
      String projectId = requireNonNull(iotConfig.project_id, "missing project id");
      String topicId = namespacePrefix + UDMI_REFLECT_TOPIC;
      reflectorClient = new PubSubReflector(projectId, registryId, topicId, userName,
          subscriptionId);
    });

    return reflectorClient;
  }

  /**
   * Activate this instance if it hasn't already been activated.
   */
  public void activate() {
    if (!activated.getAndSet(true)) {
      subscriber.startAsync().awaitRunning();
      active.set(true);

      // TODO: Make this trigger both the config & state to be queried.
      // publish(UDMI_REFLECT, UPDATE_QUERY_TOPIC, EMPTY_JSON);
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

  @Override
  public MessageBundle takeNextMessage(QuerySpeed speed) {
    throw new RuntimeException("Not implemented for PubSubReflector");
  }

  @Nullable
  private MessageBundle processMessage(PubsubMessage message) {
    long seconds = message.getPublishTime().getSeconds();
    if (flushSubscription && seconds < startTimeSec) {
      System.err.printf("Flushing outdated message from %d seconds ago%n", startTimeSec - seconds);
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
      @SuppressWarnings("unchecked") Map<String, Object> dataMap = OBJECT_MAPPER.readValue(data,
          TreeMap.class);
      asMap = dataMap;
    } catch (JsonProcessingException e) {
      asMap = new ErrorContainer(e, getSubscriptionId(), GeneralUtils.getTimestamp());
    }

    HashMap<String, String> attributes = new HashMap<>(message.getAttributesMap());
    attributes.computeIfAbsent(PUBLISH_TIME_KEY,
        key -> isoConvert(ofEpochSecond(message.getPublishTime().getSeconds())));
    attributes.put(WAS_BASE_64, "" + base64);

    MessageBundle bundle = new MessageBundle();
    bundle.message = asMap;
    bundle.attributes = attributes;
    return bundle;
  }

  @Override
  public String publish(String deviceId, String topic, String data) {
    if (deviceId == null) {
      System.err.printf("Refusing to publish to %s due to unspecified device%n", topic);
      return null;
    }
    try {
      if (!active.get()) {
        throw new RuntimeException("Publishing to shutdown reflector");
      }
      Envelope envelope = new Envelope();
      envelope.deviceId = deviceId;
      envelope.deviceRegistryId = registryId;
      envelope.projectId = projectId;
      envelope.source = Common.SOURCE_SEPARATOR + userName;
      envelope.subFolder = STATE_TOPIC.equals(topic) ? null : SubFolder.UDMI;
      Map<String, String> map = toStringMap(envelope);
      PubsubMessage message = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(data))
          .putAllAttributes(map).build();
      ApiFuture<String> publish = publisher.publish(message);
      publish.get(); // Wait for publish to complete.
    } catch (Exception e) {
      throw new RuntimeException("While publishing message", e);
    }
    return null;
  }

  @Override
  public void close() {
    active.set(false);
    if (subscriber != null) {
      subscriber.stopAsync().awaitTerminated();
    }
    if (publisher != null) {
      try {
        publisher.publishAllOutstanding();
        publisher.shutdown();
      } catch (Exception e) {
        System.err.println("Error shutting down publisher: " + friendlyStackTrace(e));
      }
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
      throw new RuntimeException(format(SUBSCRIPTION_ERROR_FORMAT, subscriptionName), e);
    }
  }

  @Override
  public SetupUdmiConfig getVersionInformation() {
    SetupUdmiConfig setupUdmiConfig = new SetupUdmiConfig();
    setupUdmiConfig.udmi_ref = subscriber.getSubscriptionNameString();
    return setupUdmiConfig;
  }

  @Override
  public String getBridgeHost() {
    // TODO: Figure out how to properly determine the endpoint connection host.
    return DEFAULT_GBOS_HOSTNAME;
  }

  private class MessageProcessor implements MessageReceiver {

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      final MessageBundle messageBundle;
      String deviceRegistryId;
      try {
        consumer.ack();
        messageBundle = processMessage(message);
        if (messageBundle == null) {
          return;
        }
        // Since this is a reflector, pull the registry id from the device id field.
        deviceRegistryId = requireNonNull(messageBundle.attributes.get(DEVICE_ID_KEY));
      } catch (Exception e) {
        defaultErrorHandler.accept(e);
        return;
      }

      try {
        Map<String, String> attributes = messageBundle.attributes;
        String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
        String suffix = ofNullable(subFolder).map(folder -> "/" + folder).orElse("");
        String topic = format("/devices/%s/%s%s", attributes.get(DEVICE_ID_KEY),
            attributes.get(CATEGORY_PROPERTY_KEY), suffix);
        String messageSource = attributes.remove(SOURCE_KEY);
        if (messageSource == null) {
          return;
        }

        Object dstSource = messageBundle.message.remove(SOURCE_KEY);
        String[] source = messageSource.split(SOURCE_SEPARATOR_REGEX, 3);
        if (source.length == 1) {
          attributes.put(SOURCE_KEY, source[0]);
        } else if (source.length == 2 && source[0].isEmpty()) {
          topic += messageSource;
          ifNotNullThen(dstSource, () -> messageBundle.message.put(SOURCE_KEY, source[1]));
        } else {
          System.err.println("Discarding message with malformed source: " + messageSource);
        }
        ofNullable(messageHandlers.get(deviceRegistryId)).orElse(defaultMessageHandler)
            .accept(topic, stringify(messageBundle.message));
      } catch (Exception e) {
        ofNullable(errorHandlers.get(deviceRegistryId)).orElse(defaultErrorHandler).accept(e);
      }
    }
  }
}
