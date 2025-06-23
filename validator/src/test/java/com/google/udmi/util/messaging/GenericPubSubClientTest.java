package com.google.udmi.util.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiService;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for GenericPubSubClient class.
 */
@RunWith(MockitoJUnitRunner.class)
public class GenericPubSubClientTest {

  private static final String TEST_PROJECT_ID = "test-project";
  private static final String TEST_SUB_ID = "test-sub";
  private static final String TEST_TOPIC_ID = "test-topic";
  private static final String TEST_PAYLOAD = "hello world";

  @Mock
  private TopicAdminClient mockTopicAdminClient;
  @Mock
  private SubscriptionAdminClient mockSubscriptionAdminClient;
  @Mock
  private Publisher mockPublisher;
  @Mock
  private Subscriber mockSubscriber;
  @Mock
  private ApiFuture<String> mockApiFuture;
  @Mock
  private ApiService mockApiService;

  private MockedStatic<TopicAdminClient> topicAdminClientMockedStatic;
  private MockedStatic<SubscriptionAdminClient> subscriptionAdminClientMockedStatic;
  private MockedConstruction<Publisher.Builder> publisherBuilderMockedConstruction;
  private MockedStatic<Subscriber> subscriberMockedStatic;

  private ArgumentCaptor<MessageReceiver> receiverCaptor;

  /**
   * Set up by mocking google cloud interactions.
   */
  @Before
  public void setUp() throws IOException {
    topicAdminClientMockedStatic = Mockito.mockStatic(TopicAdminClient.class);
    when(TopicAdminClient.create()).thenReturn(mockTopicAdminClient);

    subscriptionAdminClientMockedStatic = Mockito.mockStatic(SubscriptionAdminClient.class);
    when(SubscriptionAdminClient.create()).thenReturn(mockSubscriptionAdminClient);

    publisherBuilderMockedConstruction = Mockito.mockConstruction(Publisher.Builder.class,
        (mock, context) -> when(mock.build()).thenReturn(mockPublisher));

    subscriberMockedStatic = Mockito.mockStatic(Subscriber.class);
    Subscriber.Builder mockSubscriberBuilder = mock(Subscriber.Builder.class);
    receiverCaptor = ArgumentCaptor.forClass(MessageReceiver.class);
    subscriberMockedStatic.when(
            () -> Subscriber.newBuilder(any(ProjectSubscriptionName.class),
                receiverCaptor.capture()))
        .thenReturn(mockSubscriberBuilder);
    when(mockSubscriberBuilder.build()).thenReturn(mockSubscriber);

    when(mockPublisher.publish(any(PubsubMessage.class))).thenReturn(mockApiFuture);

    when(mockSubscriber.startAsync()).thenReturn(mockApiService);
    when(mockSubscriber.stopAsync()).thenReturn(mockApiService);
  }

  /**
   * Ensure to close the mocked functionalities.
   */
  @After
  public void tearDown() {
    topicAdminClientMockedStatic.close();
    subscriptionAdminClientMockedStatic.close();
    publisherBuilderMockedConstruction.close();
    subscriberMockedStatic.close();
  }

  @Test
  public void topicExists_whenTopicFound_returnsTrue() {
    assertTrue(GenericPubSubClient.topicExists(TEST_PROJECT_ID, TEST_TOPIC_ID));
    verify(mockTopicAdminClient).getTopic(ProjectTopicName.of(TEST_PROJECT_ID, TEST_TOPIC_ID));
  }

  @Test
  public void topicExists_whenNotFound_returnsFalse() {
    doThrow(mock(NotFoundException.class)).when(mockTopicAdminClient)
        .getTopic(any(ProjectTopicName.class));
    assertFalse(GenericPubSubClient.topicExists(TEST_PROJECT_ID, TEST_TOPIC_ID));
  }

  @Test(expected = RuntimeException.class)
  public void topicExists_whenIoException_throwsRuntimeException() throws IOException {
    doThrow(IOException.class).when(mockTopicAdminClient).getTopic(any(ProjectTopicName.class));
    GenericPubSubClient.topicExists(TEST_PROJECT_ID, TEST_TOPIC_ID);
  }

  @Test
  public void subscriptionExists_whenSubFound_returnsTrue() {
    assertTrue(GenericPubSubClient.subscriptionExists(TEST_PROJECT_ID, TEST_SUB_ID));
    verify(mockSubscriptionAdminClient).getSubscription(
        ProjectSubscriptionName.of(TEST_PROJECT_ID, TEST_SUB_ID));
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructor_withNoIds_throwsException() {
    new GenericPubSubClient(TEST_PROJECT_ID, null, null);
  }

  @Test
  public void constructor_publishOnly_initializesPublisher() throws IOException {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, null,
        TEST_TOPIC_ID)) {
      assertNotNull(client);

      assertEquals(1, publisherBuilderMockedConstruction.constructed().size());
      verify(publisherBuilderMockedConstruction.constructed().get(0)).build();

      verify(mockSubscriber, never()).startAsync();
    }
  }

  @Test
  public void constructor_subscribeOnly_initializesSubscriber() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID,
        null)) {
      assertNotNull(client);

      verify(mockSubscriber, times(1)).startAsync();

      assertEquals(0, publisherBuilderMockedConstruction.constructed().size());
    }
  }

  @Test
  public void constructor_withFlush_seeksToNow() {
    new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID, null, true, true);
    verify(mockSubscriptionAdminClient, times(1)).seek(any());
  }

  @Test
  public void publish_onPublishClient_sendsMessage() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, null,
        TEST_TOPIC_ID)) {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("key", "value");

      client.publish(TEST_PAYLOAD, attributes);

      ArgumentCaptor<PubsubMessage> messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
      verify(mockPublisher).publish(messageCaptor.capture());

      PubsubMessage sentMessage = messageCaptor.getValue();
      assertEquals(TEST_PAYLOAD, sentMessage.getData().toStringUtf8());
      assertEquals("value", sentMessage.getAttributesMap().get("key"));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void publish_onSubscribeOnlyClient_throwsException() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID,
        null)) {
      client.publish(TEST_PAYLOAD, null);
    }
  }

  @Test
  public void poll_onSubscribeClient_returnsMessage() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID,
        null)) {
      MessageReceiver receiver = receiverCaptor.getValue();
      AckReplyConsumer mockConsumer = mock(AckReplyConsumer.class);
      PubsubMessage testMessage = PubsubMessage.newBuilder()
          .setData(ByteString.copyFromUtf8(TEST_PAYLOAD)).build();
      receiver.receiveMessage(testMessage, mockConsumer);

      PubsubMessage receivedMessage = client.poll(Duration.ofSeconds(1));

      assertNotNull(receivedMessage);
      assertEquals(TEST_PAYLOAD, receivedMessage.getData().toStringUtf8());
      verify(mockConsumer).ack();
    }
  }

  @Test
  public void receiveMessage_withNack_nacksMessage() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID, null,
        false, false)) {
      MessageReceiver receiver = receiverCaptor.getValue();
      AckReplyConsumer mockConsumer = mock(AckReplyConsumer.class);
      PubsubMessage testMessage = PubsubMessage.newBuilder().build();

      receiver.receiveMessage(testMessage, mockConsumer);

      verify(mockConsumer, never()).ack();
      verify(mockConsumer).nack();
    }
  }

  @Test
  public void poll_whenNoMessage_returnsNull() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID,
        null)) {
      PubsubMessage receivedMessage = client.poll(Duration.ofMillis(10));
      assertNull(receivedMessage);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void poll_onPublishOnlyClient_throwsException() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, null,
        TEST_TOPIC_ID)) {
      client.poll(Duration.ofSeconds(1));
    }
  }

  @Test
  public void drainTo_movesMessagesToCollection() {
    try (GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID, null)) {
      MessageReceiver receiver = receiverCaptor.getValue();
      AckReplyConsumer mockConsumer = mock(AckReplyConsumer.class);

      receiver.receiveMessage(PubsubMessage.newBuilder().setMessageId("1").build(), mockConsumer);
      receiver.receiveMessage(PubsubMessage.newBuilder().setMessageId("2").build(), mockConsumer);

      List<PubsubMessage> drainedMessages = new ArrayList<>();
      int count = client.drainTo(drainedMessages);

      assertEquals(2, count);
      assertEquals(2, drainedMessages.size());
      assertEquals("1", drainedMessages.get(0).getMessageId());

      assertNull(client.poll(Duration.ofMillis(1)));
    }
  }

  @Test
  public void close_shutsDownClients() throws Exception {
    GenericPubSubClient client = new GenericPubSubClient(TEST_PROJECT_ID, TEST_SUB_ID,
        TEST_TOPIC_ID);
    client.close();

    verify(mockSubscriber).stopAsync();
    verify(mockPublisher).shutdown();

    client.close();
    verify(mockSubscriber, times(1)).stopAsync();
    verify(mockPublisher, times(1)).shutdown();
  }
}