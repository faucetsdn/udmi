package com.google.daq.mqtt.util;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Interface for publishing messages as raw maps.
 */
public interface MessagePublisher {

  void publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  boolean isActive();

  void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator);
}
