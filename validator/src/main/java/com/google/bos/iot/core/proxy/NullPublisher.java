package com.google.bos.iot.core.proxy;

import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullPublisher implements MessagePublisher {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyTarget.class);

  public NullPublisher() {
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    LOG.info(String.format("Dropping message for %s/%s", deviceId, topic));
  }

  @Override
  public void close() {
  }

  @Override
  public String getSubscriptionId() {
    return null;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator) {
    throw new IllegalStateException("Can't process message");
  }
}
