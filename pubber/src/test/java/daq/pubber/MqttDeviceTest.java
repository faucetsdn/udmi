package daq.pubber;

import static daq.pubber.ListPublisher.getMessageString;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import udmi.schema.DiscoveryCommand;
import udmi.schema.EndpointConfiguration;
import udmi.schema.PubberConfiguration;

/**
 * Unit tests for a mqtt device abstraction.
 */
public class MqttDeviceTest {

  private static final String TEST_TOPIC = "test_topic";
  private static final Object TEST_MESSAGE = new DiscoveryCommand();
  private static final String TOPIC_PREFIX = "test_prefix";
  private static final String TEST_DEVICE = "test_device";
  private static final String EXPECTED_TOPIC = String.format("%s/%s/%s",
      TOPIC_PREFIX, TEST_DEVICE, TEST_TOPIC);

  @Test
  public void publishTopicPrefix() throws InterruptedException {
    final CountDownLatch sent = new CountDownLatch(1);
    MqttDevice mqttDevice = getTestInstance(sent);

    mqttDevice.publish(TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();

    ListPublisher mockPublisher = mqttDevice.getMockPublisher();
    List<String> messages = mockPublisher.getMessages();
    assertEquals("published message count", 1, messages.size());

    String publishedMessage = messages.get(0);
    String expectedString = getMessageString(TEST_DEVICE, EXPECTED_TOPIC, TEST_MESSAGE);
    assertEquals("published message", expectedString, publishedMessage);
  }

  private MqttDevice getTestInstance(CountDownLatch sent) {
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.projectId = MqttDevice.TEST_PROJECT;
    configuration.deviceId = TEST_DEVICE;
    configuration.endpoint = new EndpointConfiguration();
    configuration.endpoint.topic_prefix = TOPIC_PREFIX;
    return new MqttDevice(configuration, exception -> sent.countDown());
  }
}