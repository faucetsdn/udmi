package com.google.daq.mqtt.util;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.threeten.bp.Duration;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

public class PubSubPusher {

  private final Publisher publisher;
  private final String outTopic;
  private final String projectId;

  public PubSubPusher(String projectId, String outTopic) {
    try {
      Preconditions.checkNotNull(projectId, "PubSub projectId");
      Preconditions.checkNotNull(outTopic, "PubSub publish topic");
      this.projectId = projectId;
      this.outTopic = outTopic;
      ProjectTopicName topicName = ProjectTopicName.of(projectId, outTopic);
      publisher = Publisher.newBuilder(topicName).build();
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSubPublisher", e);
    }
  }

  public String sendMessage(Map<String, String> attributes, String body) {
    try {
      PubsubMessage message = PubsubMessage.newBuilder()
          .setData(ByteString.copyFrom(body, Charset.defaultCharset()))
          .putAllAttributes(attributes)
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      return publish.get();
    } catch (Exception e) {
      throw new RuntimeException("While sending to topic " + outTopic, e);
    }
  }

  public void shutdown() {
    try {
      publisher.publishAllOutstanding();
      publisher.shutdown();
      System.err.println("Done with PubSubPusher");
    } catch (Exception e) {
      throw new RuntimeException("While shutting down publisher" + outTopic, e);
    }
  }

  public boolean isEmpty() {
    String subscriptionName = ProjectSubscriptionName.format(projectId, outTopic);
    System.err.println("Using PubSub subscription " + subscriptionName);
    GrpcSubscriberStub subscriber;
    try {
      SubscriberStubSettings.Builder subSettingsBuilder =
          SubscriberStubSettings.newBuilder();
      subSettingsBuilder
          .pullSettings()
          .setSimpleTimeoutNoRetries(Duration.ofDays(1))
          .build();
      SubscriberStubSettings build = subSettingsBuilder.build();
      subscriber = GrpcSubscriberStub.create(build);
    } catch (Exception e) {
      throw new RuntimeException("While connecting to subscription " + subscriptionName, e);
    }

    PullRequest pullRequest =
        PullRequest.newBuilder()
            .setMaxMessages(1)
            .setSubscription(subscriptionName)
            .build();

    PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
    List<ReceivedMessage> messages = pullResponse.getReceivedMessagesList();

    subscriber.shutdown();

    return messages.isEmpty();
  }
}
