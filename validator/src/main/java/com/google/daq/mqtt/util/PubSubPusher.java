package com.google.daq.mqtt.util;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import java.nio.charset.Charset;
import java.util.Map;
import org.threeten.bp.Duration;

/**
 * Push a pubsub message.
 */
public class PubSubPusher {

  private final Publisher publisher;
  private final String outTopic;
  private final String projectId;

  /**
   * Create something with the intended topic target.
   *
   * @param projectId Target project
   * @param outTopic Target topic
   */
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
    final GrpcSubscriberStub subscriber;
    try {
      SubscriberStubSettings.Builder subSettingsBuilder =
          SubscriberStubSettings.newBuilder();
      subSettingsBuilder
          .pullSettings()
          .setSimpleTimeoutNoRetries(Duration.ofSeconds(5))
          .build();
      SubscriberStubSettings build = subSettingsBuilder.build();
      subscriber = GrpcSubscriberStub.create(build);
    } catch (Exception e) {
      throw new RuntimeException("While connecting to subscription " + subscriptionName, e);
    }

    try {
      PullRequest pullRequest =
          PullRequest.newBuilder()
              .setMaxMessages(1)
              .setSubscription(subscriptionName)
              .build();

      PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
      return pullResponse.getReceivedMessagesList().isEmpty();
    } catch (DeadlineExceededException e) {
      // If there is nothing there the request will timeout, so equivalent to empty.
      return true;
    } finally {
      subscriber.shutdown();
    }
  }
}
