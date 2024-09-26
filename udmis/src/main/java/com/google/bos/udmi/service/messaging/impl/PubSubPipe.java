package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.SOURCE_SEPARATOR;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.time.Instant.ofEpochSecond;
import static java.util.Optional.ofNullable;

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiService;
import com.google.api.core.ApiService.Listener;
import com.google.api.core.ApiService.State;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.Common;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Message pipe using GCP PubSub messages.
 */
public class PubSubPipe extends MessageBase implements MessageReceiver {

  public static final String EMULATOR_HOST_ENV = "PUBSUB_EMULATOR_HOST";
  public static final String EMULATOR_HOST = System.getenv(EMULATOR_HOST_ENV);
  public static final String GCP_HOST = "gcp";
  public static final String PS_TXN_PREFIX = "PS:";
  public static final int MS_PER_SEC = 1000;
  public static final String SOURCE_KEY = "source";
  public static final String PUBSUB_SOURCE = "pubsub";
  private final Publisher publisher;
  private final String projectId;
  private final String topicId;
  private final Set<String> subscriberSet;
  private List<Subscriber> subscribers;
  private final AtomicInteger publisherQueueSize = new AtomicInteger();

  /**
   * Create a new instance based off the configuration.
   */
  public PubSubPipe(EndpointConfiguration configuration) {
    super(configuration);
    try {
      projectId = variableSubstitution(configuration.hostname,
          "no project id defined in configuration as 'hostname'");
      topicId = variableSubstitution(configuration.send_id);
      publisher = ifNotNullGet(topicId, this::getPublisher);
      ifNotNullThen(publisher, this::checkPublisher);
      subscriberSet = multiSubstitution(configuration.recv_id);
      initializeSubscribers();
      String subscriptionNames = subscribers.stream().map(Subscriber::getSubscriptionNameString)
          .collect(Collectors.joining(", "));
      String topicName = ifNotNullGet(publisher, Publisher::getTopicNameString);
      debug("PubSub %s %s -> %s", super.toString(), subscriptionNames, topicName);
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSub pipe", e);
    }
  }

  private static void checkSubscription(ProjectSubscriptionName subscriptionName) {
    try (SubscriptionAdminClient client = SubscriptionAdminClient.create()) {
      client.getSubscription(subscriptionName).getAckDeadlineSeconds();
    } catch (Exception e) {
      throw new RuntimeException("Checking subscription " + subscriptionName, e);
    }
  }

  public static MessagePipe fromConfig(EndpointConfiguration configuration) {
    return new PubSubPipe(configuration);
  }

  /**
   * Get the appropriate host to use with the PubSub emulator.
   */
  public static String getEmulatorHost() {
    if (EMULATOR_HOST == null) {
      return null;
    }
    int lastIndex = EMULATOR_HOST.lastIndexOf(":");
    String useHost;
    if (lastIndex < 0) {
      useHost = EMULATOR_HOST;
    } else {
      useHost = String.format("%s:%s", "localhost", EMULATOR_HOST.substring(lastIndex + 1));
    }
    return useHost;
  }

  @NotNull
  public static TransportChannelProvider getTransportChannelProvider(String useHost) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(useHost).usePlaintext().build();
    return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
  }

  @Override
  protected double getPublishQueueSize() {
    // TODO: Ensure parity with actual pubSub publisher queue size.
    return publisherQueueSize.get() / (double) queueCapacity;
  }

  @Override
  protected void publishRaw(Bundle bundle) {
    if (publisher == null) {
      trace("Dropping message because publisher is null");
      return;
    }
    throttleQueue();
    try {
      publisherQueueSize.incrementAndGet();
      Envelope envelope = ofNullable(bundle.envelope).orElse(new Envelope());
      Map<String, String> stringMap = toMap(envelope).entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, entry -> (String) entry.getValue()));
      PubsubMessage message = PubsubMessage.newBuilder()
          .putAllAttributes(stringMap)
          .setData(ByteString.copyFromUtf8(stringify(bundle.message)))
          .build();
      randomlyFail();
      ApiFuture<String> publish = publisher.publish(message);
      Thread.sleep(publishDelaySec * MS_PER_SEC);
      String publishedId = publish.get();
      String publishedTransactionId = PS_TXN_PREFIX + publishedId;
      debug(format("Published PubSub %s/%s to %s as %s/%s %s -> %s",
          stringMap.get(SUBTYPE_PROPERTY_KEY), stringMap.get(SUBFOLDER_PROPERTY_KEY),
          topicId, envelope.deviceRegistryId, envelope.deviceId, envelope.transactionId,
          publishedTransactionId));
    } catch (Exception e) {
      throw new RuntimeException("While publishing bundle to " + publisher.getTopicNameString(), e);
    } finally {
      publisherQueueSize.decrementAndGet();
      throttleQueue();
    }
  }

  private void awaitTerminated() {
    stopAsyncSubscribers().forEach(ApiService::awaitTerminated);
  }

  private void checkPublisher() {
    publish(makeHelloBundle());
  }

  private List<Subscriber> getSubscribers(Set<String> names) {
    return names.stream().map(this::getSubscriber).toList();
  }

  private void initializeSubscribers() {
    subscribers = ifNotNullGet(subscriberSet, this::getSubscribers);
  }

  private List<ApiService> stopAsyncSubscribers() {
    List<ApiService> apiServices = subscribers.stream().map(AbstractApiService::stopAsync).toList();
    subscribers = null;
    return apiServices;
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    super.activate(bundleConsumer);
    subscribers.forEach(Subscriber::startAsync);
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer reply) {
    // Ack first to prevent a recurring loop of processing a faulty message.
    reply.ack();
    Map<String, String> attributesMap = new HashMap<>(message.getAttributesMap());
    String messageId = message.getMessageId();
    attributesMap.computeIfAbsent("publishTime",
        key -> isoConvert(ofEpochSecond(message.getPublishTime().getSeconds())));
    attributesMap.computeIfAbsent(Common.TRANSACTION_KEY, key -> PS_TXN_PREFIX + messageId);

    String source = attributesMap.get(SOURCE_KEY);
    debug("TAP Received %s from %s", source, messageId);
    String fullSource =
        (source != null && source.endsWith(SOURCE_SEPARATOR)) ? source + PUBSUB_SOURCE : source;
    attributesMap.put(SOURCE_KEY, fullSource);
    debug("TAP received source %s, mapped to %s", source, fullSource);

    receiveMessage(attributesMap, message.getData().toStringUtf8());
  }

  @Override
  public void shutdown() {
    awaitTerminated();
    super.shutdown();
  }

  Publisher getPublisher(String topicName) {
    try {
      ProjectTopicName projectTopicName = ProjectTopicName.of(projectId, topicName);
      Publisher.Builder builder = Publisher.newBuilder(projectTopicName);
      String emu = getEmulatorHost();
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      info(format("Publisher %s to %s:%s", containerId, ofNullable(emu).orElse(GCP_HOST),
          projectTopicName));
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException("While creating publisher", e);
    }
  }

  Subscriber getSubscriber(String subName) {
    try {
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subName);
      FlowControlSettings flowControlSettings = FlowControlSettings.newBuilder()
          .setMaxOutstandingElementCount((long) queueCapacity).build();
      Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, this)
          .setParallelPullCount(EXECUTION_THREADS)
          .setFlowControlSettings(flowControlSettings);
      String emu = getEmulatorHost();
      ifNullThen(emu, () -> checkSubscription(subscriptionName));
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      Subscriber built = builder.build();
      info(format("Subscriber %s:%s", ofNullable(emu).orElse(GCP_HOST), subscriptionName));
      built.addListener(new Listener() {
        @Override
        public void failed(State from, Throwable failure) {
          debug("Subscriber state %s: %s", from, friendlyStackTrace(failure));
        }
      }, Executors.newSingleThreadExecutor());
      return built;
    } catch (Exception e) {
      throw new RuntimeException("While creating subscriber", e);
    }
  }

  @Override
  void resetForTest() {
    super.resetForTest();
    try {
      awaitTerminated();
      publisher.shutdown();
    } catch (Exception e) {
      throw new RuntimeException("While shutting down connections", e);
    }
  }
}
