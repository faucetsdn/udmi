package com.google.bos.udmi.service.messaging;

import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import udmi.schema.MessageConfiguration;

public class SimpleMqttPipe extends MessageBase {

  private static final int INITIALIZE_TIME_MS = 1000;
  private static final int PUBLISH_THREAD_COUNT = 2;
  private static final String TEST_USERNAME = "scrumptus";
  private static final String TEST_PASSWORD = "aardvark";
  public static final String TOPIC_FORMAT = "%s/%s/%s";
  private static final Object TOPIC_WILDCARD = "+";
  private final MqttClient mqttClient;
  private final String clientId;
  private final String namespace;

  public SimpleMqttPipe(MessageConfiguration config) {
    clientId = makeClientId();
    namespace = config.namespace;
    mqttClient = connectMqttClient(config.broker);
  }

  private String makeClientId() {
    return "client-" + System.currentTimeMillis();
  }

  static MessagePipe from(MessageConfiguration config) {
    return new SimpleMqttPipe(config);
  }

  private MqttClient connectMqttClient(String brokerUrl) {
    String message = String.format("Connecting new mqtt client %s on %s", clientId, brokerUrl);
    try {
      info(message);
      MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

      client.setCallback(new MqttCallbackHandler());
      client.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      options.setUserName(TEST_USERNAME);
      options.setPassword(TEST_PASSWORD.toCharArray());

      client.connect(options);
      return client;
    } catch (Exception e) {
      throw new RuntimeException(message, e);
    }
  }

  protected void publishBundle(Bundle bundle) {
    try {
      mqttClient.publish(getMqttTopic(bundle), getMqttMessage(bundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to mqtt client", e);
    }
  }

  private MqttMessage getMqttMessage(Bundle bundle) {
    MqttMessage message = new MqttMessage();
    message.setPayload(JsonUtil.stringify(bundle).getBytes());
    return message;
  }

  private String getMqttTopic(Bundle bundle) {
    return String.format(TOPIC_FORMAT, namespace, bundle.envelope.subType, bundle.envelope.subFolder);
  }

  @Override
  public void activate() {
    super.activate();
    try {
      mqttClient.subscribe(String.format(TOPIC_FORMAT, namespace, TOPIC_WILDCARD, TOPIC_WILDCARD));
    } catch (Exception e) {
      throw new RuntimeException("While subscribing to mqtt topics", e);
    }
  }

  private class MqttCallbackHandler implements MqttCallback {

    @Override
    public void connectionLost(Throwable cause) {
      info("Connection lost: " + Common.getExceptionMessage(cause));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      sourceQueue.add(message.toString());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
      info("Delivery complete");
    }
  }
}
