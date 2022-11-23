package daq.pubber;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 * Unit tests for a mqtt device abstraction.
 */
public class MqttDeviceTest extends TestBase {

  @Test
  public void publishTopicPrefix() throws InterruptedException {
    final CountDownLatch sent = new CountDownLatch(1);
    MqttDevice mqttDevice = new MqttDevice(getTestConfiguration(), exception -> sent.countDown());

    mqttDevice.publish(TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();

    ListPublisher mockPublisher = mqttDevice.getMockPublisher();
    List<String> messages = mockPublisher.getMessages();
    assertEquals("published message count", 1, messages.size());

    String publishedMessage = messages.get(0);
    assertEquals("published message", EXPECTED_MESSAGE_STRING, publishedMessage);
  }

}