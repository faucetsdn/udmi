package com.google.bos.iot.core.proxy;

import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Very dumb publisher that just drops all messages.
 */
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
  public Validator.MessageBundle takeNextMessage() {
    throw new IllegalStateException("Can't receive messages");
  }

  @Override
  public void processMessage(Consumer<Validator.MessageBundle> validator) {
    throw new IllegalStateException("Can't process message");
  }
}
