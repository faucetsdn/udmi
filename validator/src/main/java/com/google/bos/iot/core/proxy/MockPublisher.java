package com.google.bos.iot.core.proxy;

import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Message publisher used for testing.
 */
public class MockPublisher implements MessagePublisher {

  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();
  private boolean active = true;

  /**
   * Create a mock publisher that can optionally fail fast on any operation.
   *
   * @param failFast true to fail immediately
   */
  public MockPublisher(boolean failFast) {
    active = !failFast;
  }

  @Override
  public void publish(String deviceId, String topic, String message) {
    try {
      Validator.MessageBundle bundle = new Validator.MessageBundle();
      bundle.message = JsonUtil.asMap(message);
      bundle.attributes = new HashMap<>();
      bundle.timestamp = JsonUtil.getTimestamp();
      messages.put(bundle);
    } catch (Exception e) {
      throw new RuntimeException("While publishing mock message", e);
    }
  }

  @Override
  public void close() {
    active = false;
  }

  @Override
  public String getSubscriptionId() {
    return SiteModel.MOCK_PROJECT;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public Validator.MessageBundle takeNextMessage() {
    try {
      return messages.take();
    } catch (Exception e) {
      throw new RuntimeException("While taking next message", e);
    }
  }
}
