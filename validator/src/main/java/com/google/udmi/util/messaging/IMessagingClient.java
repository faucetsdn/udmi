package com.google.udmi.util.messaging;

import com.google.pubsub.v1.PubsubMessage;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * An interface for a generic messaging client, abstracting away the specifics
 * of the underlying technology (e.g., Google Pub/Sub, MQTT).
 */
public interface IMessagingClient extends Closeable {

  /**
   * Polls for a message from the subscription, waiting up to the specified timeout.
   *
   * @param timeout The maximum time to wait.
   * @param unit The time unit of the timeout argument.
   * @return The received PubsubMessage, or null if the timeout is reached.
   */
  PubsubMessage poll(long timeout, TimeUnit unit);

  /**
   * Closes the client and releases all resources.
   */
  @Override
  void close();
}