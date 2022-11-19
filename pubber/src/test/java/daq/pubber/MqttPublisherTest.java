package daq.pubber;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.Test;
import org.mockito.Mockito;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.PubberConfiguration;

/**
 * Test cases for MqttPublisher.
 */
public class MqttPublisherTest extends TestBase {

  private String publishedTopic;

  @Test
  public void testPublish() throws InterruptedException {
    PubberConfiguration configuration = getEndpointConfiguration();
    MqttPublisher mqttPublisher = new MockPublisher(configuration, null);
    mqttPublisher.setDeviceTopicPrefix(TEST_DEVICE, TEST_PREFIX);
    final CountDownLatch sent = new CountDownLatch(1);
    mqttPublisher.publish(TEST_DEVICE, TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();
    String expected = TEST_PREFIX + "/" + TEST_TOPIC;
    assertEquals("published topic", expected, publishedTopic);
  }

  private PubberConfiguration getEndpointConfiguration() {
    PubberConfiguration configuration = getTestConfiguration();
    configuration.endpoint.auth_provider = new Auth_provider();
    configuration.endpoint.auth_provider.basic = new Basic();
    configuration.endpoint.auth_provider.basic.username = "username";
    configuration.endpoint.auth_provider.basic.password = "username";
    configuration.endpoint.hostname = "endpoint hostname";
    configuration.endpoint.port = 9217312;
    configuration.endpoint.client_id = "endpoint client_id";
    configuration.keyBytes = new byte[10];
    configuration.algorithm = "algorithm";
    return configuration;
  }

  class MockPublisher extends MqttPublisher {

    public MockPublisher(PubberConfiguration configuration,
        Consumer<Exception> onError) {
      super(configuration, onError);
    }

    MqttClient getMqttClient(String clientId, String brokerUrl) throws MqttException {
      MqttClient mocked = Mockito.mock(MqttClient.class);
      Mockito.when(mocked.isConnected()).thenReturn(true);
      Mockito.doAnswer(invocation -> {
        publishedTopic = invocation.getArgument(0, String.class);
        return null;
      }).when(mocked)
      .publish(Mockito.anyString(), Mockito.any(), Mockito.anyInt(), Mockito.anyBoolean());
      return mocked;
    }
  }
}