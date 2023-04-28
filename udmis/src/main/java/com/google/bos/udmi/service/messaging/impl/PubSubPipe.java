package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

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
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.GeneralUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
  private final Subscriber subscriber;
  private final Publisher publisher;
  private final String projectId;

  /**
   * Create a new instance based off the configuration.
   */
  public PubSubPipe(EndpointConfiguration configuration) {
    try {
      projectId = requireNonNull(configuration.hostname,
          "no project id defined in configuration as 'hostname'");
      publisher = ifNotNullGet(configuration.send_id, this::getPublisher);
      subscriber = ifNotNullGet(configuration.recv_id, this::getSubscriber);
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSub pipe", e);
    }
  }

  public static MessagePipe fromConfig(EndpointConfiguration configuration) {
    return new PubSubPipe(configuration);
  }

  private String getEmulatorHost() {
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
  private TransportChannelProvider getTransportChannelProvider(String useHost) {
    info(format("Using pubsub emulator host %s", useHost));
    ManagedChannel channel = ManagedChannelBuilder.forTarget(useHost).usePlaintext().build();
    return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
  }

  @Override
  public void activate(Consumer<Bundle> bundleConsumer) {
    super.activate(bundleConsumer);
    subscriber.startAsync();
  }

  @Override
  public void publish(Bundle bundle) {
    try {
      Envelope envelope = Optional.ofNullable(bundle.envelope).orElse(new Envelope());
      Map<String, String> stringMap = toMap(envelope).entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, entry -> (String) entry.getValue()));
      PubsubMessage message = PubsubMessage.newBuilder()
          .putAllAttributes(stringMap)
          .setData(ByteString.copyFromUtf8(stringify(bundle.message)))
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      info("Published PubSub " + publish.get());
    } catch (Exception e) {
      throw new RuntimeException("While publishing pubsub bundle", e);
    }
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer reply) {
    Bundle bundle = new Bundle(
        convertToStrict(Envelope.class, message.getAttributesMap()),
        toMap(message.getData().toStringUtf8()));
    info(format("Received %s/%s", bundle.envelope.subType, bundle.envelope.subFolder));
    receiveBundle(bundle);
    reply.ack();
  }

  @Override
  public String toString() {
    return String.format("PubSub %s -> %s", subscriber.getSubscriptionNameString(),
        publisher.getTopicNameString());
  }

  Publisher getPublisher(String topicName) {
    try {
      ProjectTopicName projectTopicName = ProjectTopicName.of(projectId, topicName);
      Publisher.Builder builder = Publisher.newBuilder(projectTopicName);
      String emu = getEmulatorHost();
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      info(format("Publisher %s:%s", emu, projectTopicName));
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException("While creating emulator publisher", e);
    }
  }

  Subscriber getSubscriber(String subName) {
    try {
      ProjectSubscriptionName subscription = ProjectSubscriptionName.of(projectId, subName);
      Subscriber.Builder builder = Subscriber.newBuilder(subscription, this);
      String emu = getEmulatorHost();
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      info(format("Subscriber %s:%s", emu, subscription));
      Subscriber built = builder.build();
      built.addListener(new Listener() {
        @Override
        public void failed(State from, Throwable failure) {
          debug(format("Subscriber state %s: %s", from, GeneralUtils.stackTraceString(failure)));
        }
      }, Executors.newSingleThreadExecutor());
      return built;
    } catch (Exception e) {
      throw new RuntimeException("While creating emulator subscriber", e);
    }
  }

  @Override
  void resetForTest() {
    try {
      subscriber.stopAsync();
      subscriber.awaitTerminated();
      publisher.shutdown();
    } catch (Exception e) {
      throw new RuntimeException("While shutting down connections", e);
    }
  }
}
