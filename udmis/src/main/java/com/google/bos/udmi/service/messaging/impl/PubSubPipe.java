package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.time.Instant.ofEpochSecond;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiService.Listener;
import com.google.api.core.ApiService.State;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
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
  private final List<Subscriber> subscribers;
  private final Publisher publisher;
  private final String projectId;

  /**
   * Create a new instance based off the configuration.
   */
  public PubSubPipe(EndpointConfiguration configuration) {
    try {
      projectId = variableSubstitution(configuration.hostname,
          "no project id defined in configuration as 'hostname'");
      publisher = ifNotNullGet(variableSubstitution(configuration.send_id), this::getPublisher);
      ifNotNullThen(publisher, this::checkPublisher);
      subscribers = ifNotNullGet(multiSubstitution(configuration.recv_id), this::getSubscribers);
      String subscriptionNames = subscribers.stream().map(Subscriber::getSubscriptionNameString)
          .collect(Collectors.joining(", "));
      String topicName = ifNotNullGet(publisher, Publisher::getTopicNameString);
      debug("PubSub %s -> %s", super.toString(), subscriptionNames, topicName);
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSub pipe", e);
    }
  }

  private List<Subscriber> getSubscribers(Set<String> names) {
    return names.stream().map(this::getSubscriber).toList();
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

  private void checkPublisher() {
    publish(makeHelloBundle());
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    super.activate(bundleConsumer);
    subscribers.forEach(Subscriber::startAsync);
  }

  @Override
  public void publish(Bundle bundle) {
    if (publisher == null) {
      trace("Dropping message because publisher is null");
      return;
    }
    try {
      Envelope envelope = Optional.ofNullable(bundle.envelope).orElse(new Envelope());
      Map<String, String> stringMap = toMap(envelope).entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, entry -> (String) entry.getValue()));
      PubsubMessage message = PubsubMessage.newBuilder()
          .putAllAttributes(stringMap)
          .setData(ByteString.copyFromUtf8(stringify(bundle.message)))
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      String publishedId = publish.get();
      debug(format("Published PubSub %s/%s to %s as %s", stringMap.get(SUBTYPE_PROPERTY_KEY),
          stringMap.get(SUBFOLDER_PROPERTY_KEY), publisher.getTopicNameString(),
          PS_TXN_PREFIX + publishedId));
    } catch (Exception e) {
      throw new RuntimeException("While publishing bundle to " + publisher.getTopicNameString(), e);
    }
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer reply) {
    final Instant start = Instant.now();
    Map<String, String> attributesMap = new HashMap<>(message.getAttributesMap());
    // Ack first to prevent a recurring loop of processing a faulty message.
    reply.ack();
    String messageId = message.getMessageId();
    attributesMap.computeIfAbsent("publishTime",
        key -> isoConvert(ofEpochSecond(message.getPublishTime().getSeconds())));
    attributesMap.computeIfAbsent(Common.TRANSACTION_KEY, key -> PS_TXN_PREFIX + messageId);
    receiveMessage(attributesMap, message.getData().toStringUtf8());
    Instant end = Instant.now();
    long seconds = Duration.between(start, end).getSeconds();
    if (seconds > 1) {
      warn("Receive message took %ss", seconds);
    }
  }

  private void stopAndWait(Subscriber subscriber) {
    subscriber.stopAsync().awaitTerminated();
  }

  @Override
  public void shutdown() {
    subscribers.forEach(this::stopAndWait);
    super.shutdown();
  }

  Publisher getPublisher(String topicName) {
    try {
      ProjectTopicName projectTopicName = ProjectTopicName.of(projectId, topicName);
      Publisher.Builder builder = Publisher.newBuilder(projectTopicName);
      String emu = getEmulatorHost();
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      info(format("Publisher %s:%s", Optional.ofNullable(emu).orElse(GCP_HOST), projectTopicName));
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException("While creating publisher", e);
    }
  }

  Subscriber getSubscriber(String subName) {
    try {
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subName);
      Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, this);
      String emu = getEmulatorHost();
      ifNullThen(emu, () -> checkSubscription(subscriptionName));
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      builder.setParallelPullCount(EXECUTION_THREADS);
      Subscriber built = builder.build();
      info(format("Subscriber %s:%s", Optional.ofNullable(emu).orElse(GCP_HOST), subscriptionName));
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
      subscribers.forEach(this::stopAndWait);
      publisher.shutdown();
    } catch (Exception e) {
      throw new RuntimeException("While shutting down connections", e);
    }
  }
}
