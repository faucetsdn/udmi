package com.google.bos.udmi.service.support;

import java.util.Date;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Interface for interaction with auth control over broker connections.
 */
public interface ConnectionBroker {

  void authorize(String clientId, String password);

  Future<Void> addEventListener(String clientPrefix, Consumer<BrokerEvent> eventConsumer);

  /**
   * Simple event for connection broker happenings.
   */
  class BrokerEvent {
    public String clientId;
    public Operation operation;
    public Date timestamp;
    public int mesageId;
    public String detail;
    public Direction direction;
  }

  /**
   * Enum representing the broker event.
   */
  enum Operation {
    UNKNOWN,
    EXCEPTION,
    PUBLISH,
    PUBACK
  }

  /**
   * Direction of the transaction.
   */
  enum Direction {
    Sending,
    Received
  }
}
