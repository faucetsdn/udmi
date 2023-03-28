package com.google.bos.udmi.service.messaging;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Test;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

class SimpleMqttPipeTest extends MessageTestBase {

  public static final String MQTT_TEST_BROKER = "MQTT_TEST_BROKER";
  private static final String BROKER_URL = System.getenv(MQTT_TEST_BROKER);

  @Override
  protected void resetForTest() {

  }

  @Test
  void publishBundle() {
    MessagePipe primaryPipe = getTestMessagePipe(false);
    if (primaryPipe == null) {
      return;
    }
    MessagePipe secondaryPipe = getTestMessagePipe(true);
    primaryPipe.publish(new StateUpdate());
  }

  protected SimpleMqttPipe getTestMessagePipeCore(boolean reversed) {
    if (Strings.isNullOrEmpty(BROKER_URL)) {
      System.err.println("Skipping test because no broker defined in " + MQTT_TEST_BROKER);
      return null;
    }
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.transport = Transport.MQTT;
    messageConfiguration.broker = BROKER_URL;
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = reversed ? TEST_SOURCE : TEST_DESTINATION;
    messageConfiguration.destination = reversed ? TEST_DESTINATION : TEST_SOURCE;
    return new SimpleMqttPipe(messageConfiguration);
  }
}