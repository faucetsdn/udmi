package com.google.bos.udmi.service.messaging;

import com.google.common.base.Strings;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

class SimpleMqttPipeTest extends MessageTestBase {

  public static final String MQTT_TEST_BROKER = "MQTT_TEST_BROKER";
  // Ex. broker URL: MQTT_TEST_BROKER=tcp://localhost:1883
  private static final String BROKER_URL = System.getenv(MQTT_TEST_BROKER);

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(BROKER_URL);
    if (!environmentEnabled) {
      System.err.println("Skipping test because no broker defined in " + MQTT_TEST_BROKER);
    }
    return environmentEnabled;
  }

  protected MessageBase getTestMessagePipeCore(boolean reversed) {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.transport = Transport.MQTT;
    messageConfiguration.broker = BROKER_URL;
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = reversed ? TEST_SOURCE : TEST_DESTINATION;
    messageConfiguration.destination = reversed ? TEST_DESTINATION : TEST_SOURCE;
    return new SimpleMqttPipe(messageConfiguration);
  }
}