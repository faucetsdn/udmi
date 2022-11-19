package daq.pubber;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 * Tests for simple string list publisher.
 */
public class ListPublisherTest extends TestBase {

  @Test
  public void testPublish() throws InterruptedException {
    ListPublisher listPublisher = new ListPublisher(getTestConfiguration(), null);
    listPublisher.setDeviceTopicPrefix(TEST_DEVICE, TEST_PREFIX);
    final CountDownLatch sent = new CountDownLatch(1);
    listPublisher.publish(TEST_DEVICE, TEST_TOPIC, TEST_MESSAGE, sent::countDown);
    sent.await();
    List<String> messages = listPublisher.getMessages();
    assertEquals("published message count", 1, messages.size());

    String publishedMessage = messages.get(0);
    assertEquals("published message", EXPECTED_MESSAGE_STRING, publishedMessage);
  }
}