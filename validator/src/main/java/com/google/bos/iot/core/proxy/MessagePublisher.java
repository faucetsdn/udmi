package com.google.bos.iot.core.proxy;

public interface MessagePublisher {
  void publish(String deviceId, String topic, String data);
  void close();
}
