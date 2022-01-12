package daq.pubber;

import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.List;

public class PubSubClient {

  private final static Logger LOG = LoggerFactory.getLogger(PubSubClient.class);

  private final String subscriptionName;
  private final GrpcSubscriberStub subscriber;

  public PubSubClient(String projectId, String subscriptionId) {
    subscriptionName = ProjectSubscriptionName.format(projectId, subscriptionId);
    LOG.info("Using PubSub subscription " = subscriptionName);
    try {
      SubscriberStubSettings subscriberStubSettings =
          SubscriberStubSettings.newBuilder()
              .setTransportChannelProvider(
                  SubscriberStubSettings.defaultGrpcTransportProviderBuilder()
                      .setMaxInboundMessageSize(20 * 1024 * 1024) // 20MB (maximum message size).
                      .build())
              .build();

      subscriber = GrpcSubscriberStub.create(subscriberStubSettings);
    } catch (Exception e) {
      throw new RuntimeException("While connecting to subscription " + subscriptionName, e);
    }
  }

  public String pull() {
    PullRequest pullRequest =
        PullRequest.newBuilder()
            .setMaxMessages(1)
            .setSubscription(subscriptionName)
            .build();

    PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
    List<ReceivedMessage> messages = pullResponse.getReceivedMessagesList();
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

    return message.getMessage().toString();
  }
}