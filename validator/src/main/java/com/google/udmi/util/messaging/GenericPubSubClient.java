package com.google.udmi.util.messaging;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SeekRequest;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A general-purpose Google Cloud Pub/Sub client for publishing and receiving messages. This class
 * is schema-agnostic and works with raw Pub/Sub messages. It is independent of the UDMI-specific
 * PubSubClient.
 */
public final class GenericPubSubClient implements MessagingClient, Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericPubSubClient.class);

  private static final long SUBSCRIPTION_RACE_DELAY_MS = 10000;

  private final String subscriptionNameString;
  private final BlockingQueue<PubsubMessage> messageQueue = new LinkedBlockingDeque<>();
  private final AtomicBoolean active = new AtomicBoolean(true);

  private final Subscriber subscriber;
  private final Publisher publisher;

  /**
   * Creates a new generic Pub/Sub client that automatically acknowledges messages.
   *
   * @param projectId The GCP project ID.
   * @param subscriptionId The subscription to pull messages from (can be null for publish-only).
   * @param topicId The topic to publish messages to (can be null for subscribe-only).
   */
  public GenericPubSubClient(String projectId, String subscriptionId, String topicId) {
    this(projectId, subscriptionId, topicId, false, true);
  }

  /**
   * Creates a new generic Pub/Sub client with configurable acknowledgment.
   *
   * @param projectId The GCP project ID.
   * @param subscriptionId The subscription to pull messages from (can be null for publish-only).
   * @param topicId The topic to publish messages to (can be null for subscribe-only).
   * @param flush If true, and a subscription is provided, seeks the subscription to the current
   *     time on startup.
   * @param autoAck If true, messages are automatically acknowledged. If false, they are negatively
   *     acknowledged (nacked) and will be redelivered by Pub/Sub.
   */
  public GenericPubSubClient(String projectId, String subscriptionId, String topicId,
      boolean flush, boolean autoAck) {
    if (subscriptionId == null && topicId == null) {
      throw new IllegalArgumentException("Either subscriptionId or topicId must be provided.");
    }

    try {
      if (subscriptionId != null) {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId,
            subscriptionId);
        this.subscriptionNameString = subscriptionName.toString();

        if (flush) {
          flushSubscription(subscriptionName);
        }

        MessageReceiver receiver = new MessageProcessor(autoAck);
        this.subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        this.subscriber.startAsync().awaitRunning();
      } else {
        this.subscriptionNameString = null;
        this.subscriber = null;
      }

      if (topicId != null) {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
        this.publisher = Publisher.newBuilder(topicName).build();
      } else {
        this.publisher = null;
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize PubSub client", e);
    }
  }

  /**
   * Get GenericPubSubClient from supplied config.
   */
  public static MessagingClient from(MessagingClientConfig config) {
    Objects.requireNonNull(config.projectId());
    if (config.subscriptionId() != null && !subscriptionExists(config.projectId(),
        config.subscriptionId())) {
      throw new IllegalStateException(String.format(
          "Subscription %s does not exist in project %s. Please ensure it exists and retry!",
          config.subscriptionId(), config.projectId()));
    }

    if (config.publishTopicId() != null && !topicExists(config.projectId(),
        config.publishTopicId())) {
      throw new IllegalStateException(String.format(
          "Topic %s does not exist in project %s. Please ensure it exists and retry!",
          config.publishTopicId(), config.projectId()));
    }

    LOGGER.info("Creating GCP PubSub Client for project {}, subscription {}, and topic {}",
        config.projectId(), config.subscriptionId(), config.publishTopicId());

    return new GenericPubSubClient(config.projectId(), config.subscriptionId(),
        config.publishTopicId());
  }

  /**
   * Checks if a Pub/Sub topic exists.
   *
   * @param projectId The GCP project ID.
   * @param topicId The ID of the topic.
   * @return true if the topic exists, false otherwise.
   */
  public static boolean topicExists(String projectId, String topicId) {
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
      topicAdminClient.getTopic(topicName);
      return true;
    } catch (NotFoundException e) {
      return false;
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to check existence of topic %s in project %s", topicId, projectId),
          e);
    }
  }

  /**
   * Checks if a Pub/Sub subscription exists.
   *
   * @param projectId The GCP project ID.
   * @param subscriptionId The ID of the subscription.
   * @return true if the subscription exists, false otherwise.
   */
  public static boolean subscriptionExists(String projectId, String subscriptionId) {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId,
          subscriptionId);
      subscriptionAdminClient.getSubscription(subscriptionName);
      return true;
    } catch (NotFoundException e) {
      return false;
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to check existence of subscription %s in project %s",
              subscriptionId, projectId),
          e);
    }
  }

  /**
   * Publishes a string message with attributes to the configured topic.
   *
   * @param messagePayload The string payload to publish.
   * @param attributes A map of attributes for the message.
   */
  @Override
  public void publish(String messagePayload, Map<String, String> attributes) {
    if (publisher == null) {
      throw new IllegalStateException("Client is not configured with a topic to publish to.");
    }
    ByteString data = ByteString.copyFromUtf8(messagePayload);
    PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder().setData(data);
    if (attributes != null) {
      messageBuilder.putAllAttributes(attributes);
    }
    publisher.publish(messageBuilder.build());
  }

  /**
   * Polls for a message from the subscription, waiting up to the specified timeout.
   *
   * @param timeout The maximum time to wait.
   * @return The received PubsubMessage, or null if the timeout is reached before a message arrives.
   */
  @Override
  public PubsubMessage poll(Duration timeout) {
    if (subscriber == null) {
      throw new IllegalStateException("Client is not configured with a subscription to poll from.");
    }
    try {
      return messageQueue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while polling for message", e);
    }
  }

  /**
   * Retrieves and removes the head of the message queue, waiting if necessary until an element
   * becomes available.
   *
   * @return the head of this queue
   * @throws InterruptedException if interrupted while waiting
   */
  public PubsubMessage take() throws InterruptedException {
    if (subscriber == null) {
      throw new IllegalStateException("Client is not configured with a subscription to take from.");
    }
    return messageQueue.take();
  }

  /**
   * Drains all available messages from the internal queue into the provided collection.
   *
   * @param collection the collection to drain messages into.
   * @return the number of messages drained.
   */
  public int drainTo(Collection<PubsubMessage> collection) {
    if (subscriber == null) {
      throw new IllegalStateException(
          "Client is not configured with a subscription to drain from.");
    }
    return messageQueue.drainTo(collection);
  }

  @Override
  public void close() {
    if (active.getAndSet(false)) {
      if (subscriber != null) {
        subscriber.stopAsync().awaitTerminated();
      }
      if (publisher != null) {
        try {
          publisher.shutdown();
          publisher.awaitTermination(1, TimeUnit.MINUTES);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private void flushSubscription(ProjectSubscriptionName subscriptionName) {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      LOGGER.info("Flushing subscription by seeking to current time: " + subscriptionName);
      Timestamp now = Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build();
      SeekRequest seekRequest = SeekRequest.newBuilder()
          .setSubscription(subscriptionName.toString())
          .setTime(now)
          .build();
      subscriptionAdminClient.seek(seekRequest);
      Thread.sleep(SUBSCRIPTION_RACE_DELAY_MS);
    } catch (NotFoundException e) {
      LOGGER.error(
          "Subscription not found for flushing, will be created on connect: " + subscriptionName);
    } catch (Exception e) {
      throw new RuntimeException("Failed to flush subscription " + subscriptionName, e);
    }
  }

  private class MessageProcessor implements MessageReceiver {

    private final boolean autoAck;

    public MessageProcessor(boolean autoAck) {
      this.autoAck = autoAck;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      messageQueue.offer(message);
      if (autoAck) {
        consumer.ack();
      } else {
        consumer.nack();
      }
    }
  }
}