package com.google.udmi.util.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An MQTT-based implementation of the IMessagingClient interface for local testing.
 */
public class MqttMessagingClient implements MessagingClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessagingClient.class);
  private static final Gson GSON = new Gson();

  private final MqttClient mqttClient;
  private final BlockingQueue<PubsubMessage> messageQueue = new LinkedBlockingQueue<>();
  private final String subscriptionTopic;
  private final String publishTopic;

  /**
   * Initialize a generic MQTT Messaging client which mimics a Pub/Sub client.
   *
   * @param brokerUrl broker URL e.g. "tcp://localhost:1883"
   * @param subscriptionTopic subscription topic
   * @param publishTopic topic where messages should be published
   */
  public MqttMessagingClient(String brokerUrl, String subscriptionTopic, String publishTopic) {
    if (subscriptionTopic == null && publishTopic == null) {
      throw new IllegalArgumentException(
          "Either subscriptionTopic or publishTopic must be provided.");
    }
    try {
      String clientId = "mqtt-client-" + UUID.randomUUID();
      mqttClient = new MqttClient(brokerUrl, clientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);
      LOGGER.info("Connecting to MQTT broker: {}", brokerUrl);
      mqttClient.connect(connOpts);
      LOGGER.info("Connected to MQTT broker.");

      this.subscriptionTopic = subscriptionTopic;
      this.publishTopic = publishTopic;
      subscribeToTopic(this.subscriptionTopic);
    } catch (MqttException e) {
      throw new RuntimeException("Failed to initialize MQTT client", e);
    }
  }

  /**
   * Get MqttMessagingClient from supplied config.
   */
  public static MessagingClient from(MessagingClientConfig config) {
    Objects.requireNonNull(config.brokerUrl());
    LOGGER.info("Creating MQTT client for broker {}, subscription {}, and topic {}",
        config.brokerUrl(), config.subscriptionId(), config.publishTopicId());

    return new MqttMessagingClient(config.brokerUrl(), config.subscriptionId(),
        config.publishTopicId());
  }


  private void subscribeToTopic(String topic) throws MqttException {
    IMqttMessageListener listener = (t, msg) -> {
      String payload = new String(msg.getPayload(), StandardCharsets.UTF_8);
      LOGGER.debug("Received MQTT message on topic {}", t);

      try {
        PubsubMessage.Builder builder = PubsubMessage.newBuilder();
        boolean messageProcessed = false;

        if (payload.trim().startsWith("{")) {
          try {
            MqttWrapper wrapper = GSON.fromJson(payload, MqttWrapper.class);
            if (wrapper != null && wrapper.data != null) {
              builder.setData(ByteString.copyFromUtf8(wrapper.data));
              if (wrapper.attributes != null) {
                builder.putAllAttributes(wrapper.attributes);
              }
              messageProcessed = true;
            } else {
              LOGGER.warn("Received valid JSON that is not a valid MqttWrapper: {}", payload);
            }
          } catch (JsonSyntaxException e) {
            LOGGER.error("Error parsing malformed JSON MQTT message: " + payload, e);
          }
        } else {
          builder.setData(ByteString.copyFromUtf8(payload));
          messageProcessed = true;
        }

        if (messageProcessed) {
          messageQueue.offer(builder.build());
        }

      } catch (Exception e) {
        LOGGER.error("Unexpected error processing incoming MQTT message", e);
      }
    };

    mqttClient.subscribe(topic, listener);
    LOGGER.info("Subscribed to MQTT topic: {}", topic);
  }

  /**
   * Publishes a string message with attributes to the configured topic.
   *
   * @param messagePayload The string payload to publish.
   * @param attributes A map of attributes for the message.
   */
  @Override
  public void publish(String messagePayload, Map<String, String> attributes) {
    if (publishTopic == null) {
      throw new IllegalStateException("Client is not configured with a topic to publish to.");
    }
    try {
      MqttWrapper wrapper = new MqttWrapper();
      wrapper.data = messagePayload;
      wrapper.attributes = attributes;

      String payload = GSON.toJson(wrapper);
      mqttClient.publish(publishTopic, payload.getBytes(StandardCharsets.UTF_8), 0, false);
      LOGGER.debug("Published message to topic {}: {}", publishTopic, payload);
    } catch (MqttException e) {
      LOGGER.error("Failed to publish message to topic " + publishTopic, e);
      throw new RuntimeException("Failed to publish MQTT message", e);
    }
  }

  @Override
  public PubsubMessage poll(Duration timeout) {
    if (subscriptionTopic == null) {
      throw new IllegalStateException("Client is not configured with a subscription to poll from.");
    }
    try {
      return messageQueue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Interrupted while polling for MQTT message");
      return null;
    }
  }

  @Override
  public void close() {
    try {
      if (mqttClient.isConnected()) {
        mqttClient.disconnect();
        LOGGER.info("Disconnected from MQTT broker.");
      }
      mqttClient.close();
    } catch (MqttException e) {
      LOGGER.error("Error while closing MQTT client", e);
    }
  }

  /**
   * For MQTT, we expect a JSON message with a specific structure to mimic Pub/Sub. { "data":
   * "base64-encoded-payload", or a plain string with "attributes": { "key": "value", ... } }
   */
  private static class MqttWrapper {

    public String data;
    public Map<String, String> attributes;
  }
}