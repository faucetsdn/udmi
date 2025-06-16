package com.google.udmi.util.messaging;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An MQTT-based implementation of the IMessagingClient interface for local testing.
 */
public class MqttMessagingClient implements IMessagingClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessagingClient.class);
  private static final Gson GSON = new Gson();

  private final MqttClient mqttClient;
  private final BlockingQueue<PubsubMessage> messageQueue = new LinkedBlockingQueue<>();

  /**
   * For MQTT, we expect a JSON message with a specific structure to mimic Pub/Sub.
   * {
   *   "data": "base64-encoded-payload", // or a plain string
   *   "attributes": { "key": "value", ... }
   * }
   */
  private static class MqttWrapper {
    public String data;
    public Map<String, String> attributes;
  }

  public MqttMessagingClient(String brokerUrl, String topic) {
    try {
      String clientId = "bambi-service-client-" + UUID.randomUUID();
      mqttClient = new MqttClient(brokerUrl, clientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);
      LOGGER.info("Connecting to MQTT broker: {}", brokerUrl);
      mqttClient.connect(connOpts);
      LOGGER.info("Connected to MQTT broker.");

      subscribeToTopic(topic);
    } catch (MqttException e) {
      throw new RuntimeException("Failed to initialize MQTT client", e);
    }
  }

  private void subscribeToTopic(String topic) throws MqttException {
    IMqttMessageListener listener = (t, msg) -> {
      try {
        LOGGER.debug("Received MQTT message on topic {}", t);
        String payload = new String(msg.getPayload(), StandardCharsets.UTF_8);

        // Attempt to parse the wrapper, otherwise treat the whole payload as data.
        MqttWrapper wrapper = GSON.fromJson(payload, MqttWrapper.class);

        PubsubMessage.Builder builder = PubsubMessage.newBuilder();
        if (wrapper != null && wrapper.data != null) {
          builder.setData(ByteString.copyFromUtf8(wrapper.data));
          if (wrapper.attributes != null) {
            builder.putAllAttributes(wrapper.attributes);
          }
        } else {
          // Fallback for simple string messages with no attributes
          builder.setData(ByteString.copyFromUtf8(payload));
        }

        messageQueue.offer(builder.build());
      } catch (Exception e) {
        LOGGER.error("Error processing incoming MQTT message", e);
      }
    };

    mqttClient.subscribe(topic, listener);
    LOGGER.info("Subscribed to MQTT topic: {}", topic);
  }

  @Override
  public PubsubMessage poll(long timeout, TimeUnit unit) {
    try {
      return messageQueue.poll(timeout, unit);
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
}