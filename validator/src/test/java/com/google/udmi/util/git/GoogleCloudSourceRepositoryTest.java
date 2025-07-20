package com.google.udmi.util.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.messaging.GenericPubSubClient;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for GoogleCloudSourceRepository class.
 */
@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudSourceRepositoryTest {

  private static final String TEST_PROJECT_ID = "test-project";
  private static final String TEST_NAMESPACE = "test-namespace";
  private static final String PR_TITLE = "New Feature";
  private static final String PR_BODY = "Adds a cool new feature.";
  private static final String SOURCE_BRANCH = "feature/new-thing";
  private static final String TARGET_BRANCH = "main";


  private MockedStatic<GenericPubSubClient> mockStaticPubSubClient;
  private RepositoryConfig mockConfig;

  /**
   * Mock the Generic Pub Sub Client.
   */
  @Before
  public void setUp() {
    mockStaticPubSubClient = Mockito.mockStatic(GenericPubSubClient.class);
    mockConfig = new RepositoryConfig(
        "http://fake.repo/url", "/tmp/fake/path", RepositoryType.GOOGLE_CLOUD_SOURCE,
        null, TEST_PROJECT_ID);
  }

  /**
   * Ensure to close the mocked client.
   */
  @After
  public void tearDown() {
    mockStaticPubSubClient.close();
  }

  @Test
  public void constructor_withNamespace_buildsCorrectTopicAndSubscriptionNames() {
    // Arrange
    String expectedTopic = TEST_NAMESPACE + "~udmi_pr_reviews";
    String expectedSubscription = TEST_NAMESPACE + "~udmi_pr_reviews_subscription";

    // Act
    new GoogleCloudSourceRepository(mockConfig, TEST_NAMESPACE);

    // Assert: Verify that the existence checks were called with the correctly prefixed names
    mockStaticPubSubClient.verify(
        () -> GenericPubSubClient.topicExists(TEST_PROJECT_ID, expectedTopic));
    mockStaticPubSubClient.verify(
        () -> GenericPubSubClient.subscriptionExists(TEST_PROJECT_ID, expectedSubscription));
  }

  @Test
  public void constructor_withoutNamespace_buildsCorrectTopicAndSubscriptionNames() {
    // Arrange
    String expectedTopic = "udmi_pr_reviews";
    String expectedSubscription = "udmi_pr_reviews_subscription";

    // Act
    // Pass null for the namespace to test the "not set" case.
    new GoogleCloudSourceRepository(mockConfig, null);

    // Assert: Verify that the existence checks were called with the base names
    mockStaticPubSubClient.verify(
        () -> GenericPubSubClient.topicExists(TEST_PROJECT_ID, expectedTopic));
    mockStaticPubSubClient.verify(
        () -> GenericPubSubClient.subscriptionExists(TEST_PROJECT_ID, expectedSubscription));
  }

  @Test
  public void createPullRequest_whenTopicExists_publishesMessage() {
    // Arrange
    mockStaticPubSubClient.when(() -> GenericPubSubClient.topicExists(anyString(), anyString()))
        .thenReturn(true);

    String expectedJsonPayload = String.format(
        "{\"title\":\"%s\", \"body\":\"%s\", \"sourceBranch\":\"%s\", "
            + "\"targetBranch\":\"%s\", \"author\":\"%s\", \"commitUrl\":\"%s\"}",
        PR_TITLE, PR_BODY, SOURCE_BRANCH, TARGET_BRANCH, null, null
    );

    try (MockedConstruction<GenericPubSubClient> mockConstruction = Mockito.mockConstruction(
        GenericPubSubClient.class)) {
      GoogleCloudSourceRepository repo = new GoogleCloudSourceRepository(mockConfig, null);

      // Act & Assert
      String result = repo.createPullRequest(PR_TITLE, PR_BODY, SOURCE_BRANCH, TARGET_BRANCH);
      assertTrue(result.contains("Pull request message published"));

      assertEquals(1, mockConstruction.constructed().size());
      GenericPubSubClient mockPublisher = mockConstruction.constructed().get(0);

      ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockPublisher).publish(payloadCaptor.capture(), any());
      assertEquals(expectedJsonPayload, payloadCaptor.getValue());

      verify(mockPublisher).close();
    }
  }

  @Test
  public void createPullRequest_whenTopicMissing_returnsLocalLogMessage() {
    // Arrange
    mockStaticPubSubClient.when(() -> GenericPubSubClient.topicExists(anyString(), anyString()))
        .thenReturn(false);

    try (MockedConstruction<GenericPubSubClient> mockConstruction = Mockito.mockConstruction(
        GenericPubSubClient.class)) {

      GoogleCloudSourceRepository repo = new GoogleCloudSourceRepository(mockConfig, null);
      String result = repo.createPullRequest(PR_TITLE, PR_BODY, SOURCE_BRANCH, TARGET_BRANCH);

      // Assert
      assertEquals(0, mockConstruction.constructed().size());
      assertEquals("Pull request details logged locally as topic was not found.", result);
    }
  }

  @Test
  public void listOpenPullRequests_whenSubscriptionExists_returnsPulledMessages() {
    // Arrange
    mockStaticPubSubClient.when(
            () -> GenericPubSubClient.subscriptionExists(anyString(), anyString()))
        .thenReturn(true);

    String messageContent1 = "{\"title\":\"PR 1\"}";
    String messageContent2 = "{\"title\":\"PR 2\"}";
    PubsubMessage pubsubMessage1 = PubsubMessage.newBuilder()
        .setData(ByteString.copyFromUtf8(messageContent1)).build();
    PubsubMessage pubsubMessage2 = PubsubMessage.newBuilder()
        .setData(ByteString.copyFromUtf8(messageContent2)).build();

    try (MockedConstruction<GenericPubSubClient> mockConstruction = Mockito.mockConstruction(
        GenericPubSubClient.class, (mock, context) -> {
          doAnswer(invocation -> {
            List<PubsubMessage> messageList = invocation.getArgument(0);
            messageList.add(pubsubMessage1);
            messageList.add(pubsubMessage2);
            return null;
          }).when(mock).drainTo(any());
        })) {

      GoogleCloudSourceRepository repo = new GoogleCloudSourceRepository(mockConfig, null);

      // Act
      List<String> openPrs = repo.listOpenPullRequests(TARGET_BRANCH);

      // Assert
      assertEquals(1, mockConstruction.constructed().size());

      assertEquals(2, openPrs.size());
      assertTrue(openPrs.contains(messageContent1));
      assertTrue(openPrs.contains(messageContent2));

      GenericPubSubClient mockSubscriber = mockConstruction.constructed().get(0);
      verify(mockSubscriber).close();
    }
  }

  @Test
  public void listOpenPullRequests_whenSubscriptionMissing_returnsEmptyList() {
    // Arrange
    mockStaticPubSubClient.when(
            () -> GenericPubSubClient.subscriptionExists(anyString(), anyString()))
        .thenReturn(false);

    try (MockedConstruction<GenericPubSubClient> mockConstruction = Mockito.mockConstruction(
        GenericPubSubClient.class)) {

      GoogleCloudSourceRepository repo = new GoogleCloudSourceRepository(mockConfig, null);
      List<String> openPrs = repo.listOpenPullRequests(TARGET_BRANCH);

      // Assert
      assertEquals(0, mockConstruction.constructed().size());
      assertTrue(openPrs.isEmpty());
    }
  }
}