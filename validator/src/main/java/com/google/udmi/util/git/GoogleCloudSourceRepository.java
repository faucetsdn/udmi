package com.google.udmi.util.git;

import static com.google.udmi.util.messaging.GenericPubSubClient.subscriptionExists;
import static com.google.udmi.util.messaging.GenericPubSubClient.topicExists;

import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.messaging.GenericPubSubClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage interactions with a Google Cloud Source Repository.
 */
public class GoogleCloudSourceRepository extends GenericGitRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCloudSourceRepository.class);
  private static final String BASE_TOPIC = "udmi_pr_reviews";
  private static final String BASE_SUBSCRIPTION = BASE_TOPIC + "_subscription";
  private static final int PULL_REQUEST_GATHER_TIME_MS = 2000;
  private static final String COMMIT_REF = "https://source.cloud.google.com/%s/%s/+/%s";
  private final String topicId;
  private final String subscriptionId;
  private final boolean topicExists;
  private final boolean subscriptionExists;
  private final RepositoryConfig repositoryConfig;

  /**
   * Initialize a Google cloud source repository.
   */
  public GoogleCloudSourceRepository(RepositoryConfig config, String udmiNamespace) {
    super(config);
    this.repositoryConfig = config;
    String udmiNamespacePrefix = Optional.ofNullable(udmiNamespace).map(ns -> ns + "~").orElse("");
    this.topicId = udmiNamespacePrefix + BASE_TOPIC;
    this.subscriptionId = udmiNamespacePrefix + BASE_SUBSCRIPTION;

    this.topicExists = topicExists(config.projectId(), topicId);
    this.subscriptionExists = subscriptionExists(config.projectId(), subscriptionId);

    if (!topicExists) {
      LOGGER.debug("Pub/Sub topic {} not found. Creating PRs will only be logged locally.",
          this.topicId);
    }
    if (!subscriptionExists) {
      LOGGER.debug("Pub/Sub subscription {} not found. Listing PRs will not be possible.",
          this.subscriptionId);
    }
  }

  public GoogleCloudSourceRepository(RepositoryConfig config) {
    this(config, System.getenv("UDMI_NAMESPACE"));
  }

  @Override
  public String getCommitUrl(String branch) {
    try {
      return String.format(COMMIT_REF, repositoryConfig.projectId(), getRepoId(),
          getCommitHashForBranch(branch));
    } catch (Exception e) {
      LOGGER.error("Cannot get commit URL", e);
      return null;
    }
  }

  @Override
  public String createPullRequest(String title, String body, String sourceBranch,
      String targetBranch, String author) {
    if (topicExists) {
      try (GenericPubSubClient publisher = new GenericPubSubClient(repositoryConfig.projectId(),
          null, topicId)) {
        String payload = String.format(
            "{\"title\":\"%s\", \"body\":\"%s\", \"sourceBranch\":\"%s\", "
                + "\"targetBranch\":\"%s\", \"author\":\"%s\", \"commitUrl\":\"%s\"}",
            title, body, sourceBranch, targetBranch, author, getCommitUrl(sourceBranch)
        );
        LOGGER.info("Published PR review request {}", payload);
        publisher.publish(payload, null);
        LOGGER.info("Published PR review request from {} to topic {}", author, topicId);
        return "Pull request message published to " + topicId;
      }
    } else {
      LOGGER.error("Pub/Sub topic {} not found. Logging PR details locally instead of publishing.",
          topicId);
      LOGGER.info("PR Details: title='{}', body='{}', sourceBranch='{}', targetBranch='{}'", title,
          body, sourceBranch, targetBranch);
      return "Pull request details logged locally as topic was not found.";
    }
  }

  @Override
  public List<String> listOpenPullRequests(String targetBranch) {
    if (subscriptionExists) {
      List<PubsubMessage> receivedMessages = new ArrayList<>();
      LOGGER.info("Temporarily subscribing to {} to peek at messages without acknowledging...",
          subscriptionId);

      try (GenericPubSubClient nackSubscriber = new GenericPubSubClient(
          repositoryConfig.projectId(), subscriptionId, null, false, false)) {

        Thread.sleep(PULL_REQUEST_GATHER_TIME_MS);

        nackSubscriber.drainTo(receivedMessages);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for PR messages", e);
      }

      LOGGER.info("Pulled and nacked {} messages from subscription {}", receivedMessages.size(),
          subscriptionId);
      return receivedMessages.stream()
          .map(message -> message.getData().toStringUtf8())
          .collect(Collectors.toList());
    } else {
      LOGGER.error("Pub/Sub subscription {} not found. Cannot list open PRs.", subscriptionId);
      return Collections.emptyList();
    }
  }

  private String getRepoId() {
    String remoteUrl = repositoryConfig.remoteUrl();
    int lastSlashIndex = remoteUrl.lastIndexOf('/');

    if (lastSlashIndex != -1) {
      return remoteUrl.substring(lastSlashIndex + 1);
    } else {
      return "";
    }
  }
}