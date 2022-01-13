package daq.pubber;

import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.Duration;

public class PubSubClient {

  private final static Logger LOG = LoggerFactory.getLogger(PubSubClient.class);

  private final String subscriptionName;
  private final GrpcSubscriberStub subscriber;

  public PubSubClient(String projectId, String subscriptionId) {
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

  public String pull() {
    PullRequest pullRequest =
        PullRequest.newBuilder()
            .setMaxMessages(1)
            .setSubscription(subscriptionName)
            .build();

    List<ReceivedMessage> messages;
    do {
      PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
      messages = pullResponse.getReceivedMessagesList();
    } while(messages.size() == 0);

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

    return message.getMessage().getData().toStringUtf8();
  }
}