package com.google.bos.udmi.service.support;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Interface for interaction with auth control over broker connections.
 */
public interface ConnectionBroker {

  void authorize(String clientId, String password);

  Future<Void> addEventListener(String clientPrefix, Consumer<ConnectionEvent> eventConsumer);

  /**
   * Simple event for connection broker happenings.
   */
  class ConnectionEvent {
    public String clientId;
    public String operation;
    public String flow;
    public String detail;
  }
}
