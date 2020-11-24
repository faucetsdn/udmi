package com.google.bos.iot.core.proxy;

import com.google.api.core.ApiService.Listener;
import com.google.api.core.ApiService.State;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PubSubClient {

  private static final Logger LOG = LoggerFactory.getLogger(PubSubClient.class);
  private static final String CONNECT_ERROR_FORMAT = "While connecting to %s/%s";

  private final AtomicBoolean active = new AtomicBoolean();
  private final BlockingQueue<Object> messages = new LinkedBlockingDeque<>();

  private final Subscriber subscriber;

  PubSubClient(String projectId, String subscriptionName) {
    try {
      ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionName);
      MessageReceiver receiver = new MessageReceiver();
      subscriber = Subscriber.newBuilder(subName, receiver).build();
      subscriber.addListener(new SubscriberListener(), MoreExecutors.directExecutor());
      subscriber.startAsync().awaitRunning();
      active.set(true);
      LOG.info("PubSubClient on subscription " + subName);
    } catch (Exception e) {
      throw new RuntimeException(String.format(CONNECT_ERROR_FORMAT, projectId, subscriptionName), e);
    }
  }

  boolean isActive() {
    return active.get();
  }

  void processMessage(BiConsumer<String, Map<String, String>> handler) {
    try {
      Object maybeMessage = messages.take();
      if (maybeMessage instanceof Throwable) {
        throw new RuntimeException("PubSub Failed", (Throwable) maybeMessage);
      }
      PubsubMessage message = (PubsubMessage) maybeMessage;
      Map<String, String> attributes = message.getAttributesMap();
      String data = message.getData().toStringUtf8();
      handler.accept(data, attributes);
    } catch (Exception e) {
      throw new RuntimeException("While processing pubsub message from " + getSubscriptionId(), e);
    }
  }

  public void stop() {
    if (subscriber != null) {
      active.set(false);
      subscriber.stopAsync();
      messages.offer(new RuntimeException("Publisher terminated"));
    }
  }

  String getSubscriptionId() {
    return subscriber.getSubscriptionNameString();
  }

  private class MessageReceiver implements com.google.cloud.pubsub.v1.MessageReceiver {
    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      messages.offer(message);
      consumer.ack();
    }
  }

  private class SubscriberListener extends Listener {
    @Override
    public void failed(State from, Throwable failure) {
      active.set(false);
      messages.offer(failure);
    }
  }
}
