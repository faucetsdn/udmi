package daq.pubber;

import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Duration;

/**
 * Wrapper class for a PubSub client.
 */
public class PubberPubSubClient {

  private static final Logger LOG = LoggerFactory.getLogger(PubberPubSubClient.class);

  private final String subscriptionName;
  private final GrpcSubscriberStub subscriber;

  /**
   * Subscribe to the given subscription.
   *
   * @param projectId      GCP project id
   * @param subscriptionId PubSub subscription
   */
  public PubberPubSubClient(String projectId, String subscriptionId) {
    subscriptionName = ProjectSubscriptionName.format(projectId, subscriptionId);
    LOG.info("Using PubSub subscription " + subscriptionName);
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
  }

  /**
   * Pull a message from the subscription.
   *
   * @return Bundle of pulled message.
   */
  public Bundle pull() {
    PullRequest pullRequest =
        PullRequest.newBuilder()
            .setMaxMessages(1)
            .setSubscription(subscriptionName)
            .build();

    List<ReceivedMessage> messages;
    do {
      PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
      messages = pullResponse.getReceivedMessagesList();
    } while (messages.size() == 0);

    if (messages.size() != 1) {
      throw new RuntimeException("Did not receive singular message");
    }
    ReceivedMessage message = messages.get(0);

    AcknowledgeRequest acknowledgeRequest =
        AcknowledgeRequest.newBuilder()
            .setSubscription(subscriptionName)
            .addAckIds(message.getAckId())
            .build();

    subscriber.acknowledgeCallable().call(acknowledgeRequest);

    Bundle bundle = new Bundle();
    bundle.body = message.getMessage().getData().toStringUtf8();
    bundle.attributes = message.getMessage().getAttributesMap();
    return bundle;
  }

  static class Bundle {

    String body;
    Map<String, String> attributes;
  }
}
