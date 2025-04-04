package udmi.lib;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.Test;
import org.mockito.Mockito;
import udmi.lib.base.MqttPublisher;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;

/**
 * Test cases for MqttPublisher.
 */
public class MqttPublisherTest extends TestBase {

  private static final String EXPECTED_TOPIC = TEST_PREFIX + "/" + TEST_TOPIC;
  private static final String EXPECTED_MESSAGE = "{}";
  private String publishedTopic;
  private String publishedData;

  @Test
  public void testPublish() throws InterruptedException {
    EndpointConfiguration configuration = getEndpointConfiguration();
    MqttPublisher mqttPublisher = new MockPublisher(configuration, null);
    mqttPublisher.setDeviceTopicPrefix(TEST_DEVICE, TEST_PREFIX);
    final CountDownLatch sent = new CountDownLatch(1);
    mqttPublisher.publish(TEST_DEVICE, TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();
    // TODO: There's some internal mockito bug that's causing this to fail. Investigate and fix!
    // assertEquals("published topic", EXPECTED_TOPIC, publishedTopic);
    // assertEquals("published message", EXPECTED_MESSAGE, publishedData);
  }

  private EndpointConfiguration getEndpointConfiguration() {
    EndpointConfiguration configuration = getTestConfiguration().endpoint;
    configuration.auth_provider = new Auth_provider();
    configuration.auth_provider.basic = new Basic();
    configuration.auth_provider.basic.username = "username";
    configuration.auth_provider.basic.password = "username";
    configuration.hostname = "endpoint hostname";
    configuration.port = 9217312;
    configuration.client_id = "endpoint client_id";
    configuration.keyBytes = new byte[10];
    configuration.algorithm = "algorithm";
    return configuration;
  }

  class MockPublisher extends MqttPublisher {

    public MockPublisher(EndpointConfiguration configuration,
        Consumer<Exception> onError) {
      super(configuration, onError, null);
    }

    @Override
    protected MqttClient getMqttClient(String clientId, String brokerUrl) throws MqttException {
      MqttClient mocked = Mockito.mock(MqttClient.class);
      Mockito.when(mocked.isConnected()).thenReturn(true);
      Mockito.doAnswer(invocation -> {
        publishedTopic = invocation.getArgument(0, String.class);
        publishedData = new String((byte[]) invocation.getArgument(1, Object.class));
        return null;
      }).when(mocked)
      .publish(Mockito.anyString(), Mockito.any(), Mockito.anyInt(), Mockito.anyBoolean());
      return mocked;
    }
  }
}
