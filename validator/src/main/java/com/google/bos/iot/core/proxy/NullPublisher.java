package com.google.bos.iot.core.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullPublisher implements MessagePublisher {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyTarget.class);

  final String deviceId;

  public NullPublisher(String deviceId) {
    this.deviceId = deviceId;
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    LOG.info(String.format("Dropping message for %s/%s", deviceId, topic));
  }

  @Override
  public void close() {
  }
}
