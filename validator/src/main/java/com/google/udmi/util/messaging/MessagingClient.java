package com.google.udmi.util.messaging;

import com.google.pubsub.v1.PubsubMessage;
import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import udmi.schema.IotAccess.IotProvider;

/**
 * An interface for a generic messaging client, abstracting away the specifics of the underlying
 * technology (e.g., Google Pub/Sub, MQTT).
 */
public interface MessagingClient extends Closeable {

  /**
   * Get appropriate messaging client from supplied config.
   */
  static MessagingClient from(MessagingClientConfig config) {
    IotProvider provider;
    try {
      provider = IotProvider.fromValue(config.protocol());
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException(
          "Unsupported messaging protocol " + config.protocol(), e);
    }

    return switch (provider) {
      case MQTT -> MqttMessagingClient.from(config);
      case PUBSUB -> GenericPubSubClient.from(config);
      default -> throw new UnsupportedOperationException(
          "Unsupported messaging protocol " + config.protocol());
    };
  }

  /**
   * Polls for a message from the subscription, waiting up to the specified timeout.
   *
   * @param timeout The maximum time to wait.
   * @return The received PubsubMessage, or null if the timeout is reached.
   */
  PubsubMessage poll(Duration timeout);

  /**
   * Publishes a string message with attributes to the configured topic.
   *
   * @param messagePayload The string payload to publish.
   * @param attributes A map of attributes for the message.
   */
  void publish(String messagePayload, Map<String, String> attributes);

  /**
   * Closes the client and releases all resources.
   */
  @Override
  void close();
}