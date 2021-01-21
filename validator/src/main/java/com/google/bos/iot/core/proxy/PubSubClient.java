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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PubSubClient {

  private static final Logger LOG = LoggerFactory.getLogger(PubSubClient.class);
  private static final String CONNECT_ERROR_FORMAT = "While connecting to %s/%s";

  private static final long MESSAGE_REPORT_INTERVAL_MS = 1000 * 60 * 10;

  private final AtomicInteger messageCount = new AtomicInteger();
  private final AtomicLong lastUpdateTime = new AtomicLong();
  private final AtomicBoolean active = new AtomicBoolean();
  private final BlockingQueue<Object> messages = new LinkedBlockingDeque<>();

  private final Subscriber subscriber;
  private final String clientName;

  PubSubClient(String projectId, String subscriptionName) {
    try {
      ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionName);
      clientName = subName.toString();
      LOG.info("Creating subscription " + clientName);
      MessageReceiver receiver = new MessageReceiver();
      subscriber = Subscriber.newBuilder(subName, receiver).build();
      subscriber.addListener(new SubscriberListener(), MoreExecutors.directExecutor());
      subscriber.startAsync().awaitRunning();
      active.set(true);
    } catch (Exception e) {
      throw new RuntimeException(String.format(CONNECT_ERROR_FORMAT, projectId, subscriptionName), e);
    }
  }

  boolean isActive() {
    return active.get();
  }

  void processMessage(BiConsumer<Map<String, String>, String> handler, long pollDelayMs) {
    try {
      Object maybeMessage = messages.poll(pollDelayMs, TimeUnit.MILLISECONDS);
      if (maybeMessage == null) {
        return;
      }
      if (maybeMessage instanceof Throwable) {
        throw new RuntimeException("PubSub Failed", (Throwable) maybeMessage);
      }
      long now = System.currentTimeMillis();
      int thisCount = messageCount.incrementAndGet();
      if (now - lastUpdateTime.get() > MESSAGE_REPORT_INTERVAL_MS) {
        lastUpdateTime.set(now);
        LOG.info("Processing message #" + thisCount);
      }
      PubsubMessage message = (PubsubMessage) maybeMessage;
      Map<String, String> attributes = message.getAttributesMap();
      String data = message.getData().toStringUtf8();
      handler.accept(attributes, data);
    } catch (Exception e) {
      throw new RuntimeException("While processing pubsub message from " + getSubscriptionId(), e);
    }
  }

  public void stop() {
    if (subscriber != null) {
      active.set(false);
      subscriber.stopAsync();
      try {
        messages.put(new RuntimeException("Publisher terminated"));
      } catch (Exception e) {
        LOG.error("Ignored exception stopping queue " + clientName, e);
      }
    }
  }

  String getSubscriptionId() {
    return subscriber.getSubscriptionNameString();
  }

  private class MessageReceiver implements com.google.cloud.pubsub.v1.MessageReceiver {
    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      if (!messages.offer(message)) {
        LOG.warn("Dropping message for full queue " + clientName);
      }
      consumer.ack();
    }
  }

  private class SubscriberListener extends Listener {
    @Override
    public void failed(State from, Throwable failure) {
      active.set(false);
      try {
        messages.put(failure);
      } catch (Exception e) {
        LOG.error("Ignored exception failing queue " + clientName, e);
      }
    }
  }
}
