package com.google.bos.udmi.service.messaging;

import com.google.udmi.util.Common;
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
  private final MqttClient mqttClient;
  private final String clientId;

  public SimpleMqttPipe(MessageConfiguration config) {
    this.clientId = makeClientId();
    mqttClient = connectMqttClient(config.broker);
  }

  private String makeClientId() {
    return "client-" + System.currentTimeMillis();
  }

  static MessagePipe from(MessageConfiguration config) {
    return new SimpleMqttPipe(config);
  }

  private MqttClient connectMqttClient(String brokerUrl) {
    try {
      info(String.format("Connecting new mqtt client %s on %s", clientId, brokerUrl));
      MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

      client.setCallback(new MqttCallbackHandler());
      client.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      // options.setUserName();
      // options.setPassword(basic.password.toCharArray());

      client.connect(options);
      return client;
    } catch (Exception e) {
      throw new RuntimeException("While connecting mqtt client", e);
    }
  }

  protected void publishBundle(Bundle bundle) {

  }

  @Override
  public void activate() {

  }

  private class MqttCallbackHandler implements MqttCallback {

    @Override
    public void connectionLost(Throwable cause) {
      info("Connection lost: " + Common.getExceptionMessage(cause));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      info("Message arrived");

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
      info("Delivery complete");
    }
  }
}
