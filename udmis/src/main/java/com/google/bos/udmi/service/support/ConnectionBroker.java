package com.google.bos.udmi.service.support;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Interface for interaction with auth control over broker connections.
 */
public interface ConnectionBroker {

  void authorize(String clientId, String password);

  Future<Boolean> addEventListener(String clientPrefix, Consumer<ConnectionEvent> eventConsumer);

  /**
   * Simple event for connection broker happenings.
   */
  class ConnectionEvent {
    String clientId;
    String operation;
    String flow;
  }
}
