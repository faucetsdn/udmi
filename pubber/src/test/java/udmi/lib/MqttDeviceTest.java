package udmi.lib;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import udmi.lib.base.ListPublisher;
import udmi.lib.base.MqttDevice;
import udmi.schema.EndpointConfiguration;

/**
 * Unit tests for a mqtt device abstraction.
 */
public class MqttDeviceTest extends TestBase {

  @Test
  public void publishTopicPrefix() throws InterruptedException {
    final CountDownLatch sent = new CountDownLatch(1);
    EndpointConfiguration endpoint = getTestConfiguration().endpoint;
    endpoint.deviceId = TEST_DEVICE;
    MqttDevice mqttDevice = new MqttDevice(endpoint,
        exception -> sent.countDown(), null, false);

    mqttDevice.publish(TEST_DEVICE, TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();

    ListPublisher mockPublisher = mqttDevice.getMockPublisher();
    List<String> messages = mockPublisher.getMessages();
    assertEquals("published message count", 1, messages.size());

    String publishedMessage = messages.get(0);
    assertEquals("published message", EXPECTED_MESSAGE_STRING, publishedMessage);
  }

}
