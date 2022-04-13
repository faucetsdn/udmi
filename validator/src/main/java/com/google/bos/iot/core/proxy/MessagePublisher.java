package com.google.bos.iot.core.proxy;

import java.util.Map;
import java.util.function.BiConsumer;

public interface MessagePublisher {

  void publish(String deviceId, String topic, String data);

  void close();

  String getSubscriptionId();

  boolean isActive();

  void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator);
}
