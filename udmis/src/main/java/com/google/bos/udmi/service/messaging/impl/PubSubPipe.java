package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
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
import com.google.udmi.util.JsonUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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

  /**
   * Create a new instance based off the configuration.
   */
  public PubSubPipe(EndpointConfiguration configuration) {
    try {
      publisher = getPublisher(configuration.hostname, configuration.send_id);
      subscriber = getSubscriber(configuration.hostname, configuration.recv_id);
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSub pipe", e);
    }
  }

  public static MessagePipe fromConfig(EndpointConfiguration configuration) {
    return new PubSubPipe(configuration);
  }

  @Override
  public void activate(Consumer<Bundle> messageDispatcher) {
    super.activate(messageDispatcher);
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
    Bundle bundle = new Bundle();
    bundle.envelope = JsonUtil.convertToStrict(Envelope.class, message.getAttributesMap());
    bundle.message = toMap(message.getData().toStringUtf8());
    info(format("Received %s/%s", bundle.envelope.subType, bundle.envelope.subFolder));
    sourceQueue.add(stringify(bundle));
    reply.ack();
  }

  Publisher getPublisher(String projectId, String topicName) {
    info(format("Creating publisher for emulator host %s %s/%s", EMULATOR_HOST, projectId,
        topicName));
    String useHost = getFormattedHost();
    ManagedChannel channel = ManagedChannelBuilder.forTarget(useHost).usePlaintext().build();
    try {
      TransportChannelProvider channelProvider =
          FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
      CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

      return Publisher.newBuilder(ProjectTopicName.of(projectId, topicName))
          .setChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("While creating emulator publisher", e);
    }
  }

  private String getFormattedHost() {
    int lastIndex = EMULATOR_HOST.lastIndexOf(":");
    String useHost;
    if (lastIndex < 0) {
      useHost = EMULATOR_HOST;
    } else {
      String hostname = EMULATOR_HOST.substring(0, lastIndex);
      useHost = String.format("%s:%s", "localhost", EMULATOR_HOST.substring(lastIndex + 1));
    }
    return useHost;
  }

  Subscriber getSubscriber(String projectId, String subName) {
    String emulator = System.getenv("PUBSUB_EMULATOR_HOST");
    String useHost = getFormattedHost();
    ManagedChannel channel = ManagedChannelBuilder.forTarget(useHost).usePlaintext().build();
    info(format("Creating subscription for emulator host %s %s/%s", emulator, projectId, subName));
    try {
      TransportChannelProvider channelProvider =
          FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
      CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

      Subscriber subscriber =
          Subscriber.newBuilder(ProjectSubscriptionName.of(projectId, subName), this)
              .setChannelProvider(channelProvider)
              .setCredentialsProvider(credentialsProvider)
              .build();
      return subscriber;
    } catch (Exception e) {
      throw new RuntimeException("While creating emulator publisher", e);
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
