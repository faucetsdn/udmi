package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static java.util.Optional.ofNullable;

import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.messaging.MessagingClient;
import com.google.udmi.util.messaging.MessagingClientConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base class for a long-running service that polls a Pub/Sub topic for messages.
 * Handles all the boilerplate for service lifecycle, message polling, and configuration. Subclasses
 * must implement the handleMessage method to define their specific business logic.
 */
public abstract class AbstractPollingService {

  // --- Constants ---
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPollingService.class);
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(1000);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SLEEP_ON_ERROR_DURATION = Duration.ofSeconds(5);
  private static final String DEFAULT_MQTT_BROKER = "tcp://localhost:1883";
  private static final Pattern PROJECT_TARGET_REGEX = Pattern.compile(
      "\\/\\/(mqtt|pubsub|gbos|gref)(\\/[^\\/\\s]*){1,2}");

  // --- Configuration ---
  protected final String gcpProject;
  protected final String udmiNamespace;
  protected final String baseCloningDir;
  protected final String localOriginDir; // For local testing

  // --- Service State ---
  private final ExecutorService pollingExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final MessagingClient messagingClient;
  private final String serviceName;

  /**
   * Primary constructor for the abstract service.
   *
   * @param serviceName The name of the concrete service
   * @param subscriptionSuffix The suffix for the Pub/Sub subscription name
   * @param projectTarget Target project specifier
   * @param siteModelBaseDir Base directory for cloning site model Git repositories.
   * @param localOriginDir Optional directory for local git origins
   */
  public AbstractPollingService(String serviceName, String subscriptionSuffix, String projectTarget,
      String siteModelBaseDir, String localOriginDir) {
    if (!projectTarget.matches(PROJECT_TARGET_REGEX.pattern())) {
      throw new IllegalArgumentException("Invalid project target format: " + projectTarget);
    }
    this.serviceName = serviceName;

    ProjectSpec spec = getProjectSpec(projectTarget);
    this.gcpProject = spec.project;
    this.udmiNamespace = spec.udmiNamespace;
    this.baseCloningDir = siteModelBaseDir;
    this.localOriginDir = localOriginDir;

    if (isNotEmpty(this.localOriginDir)) {
      LOGGER.info("Running in local origin mode.");
    }

    String threadName = serviceName + "-poller";
    this.pollingExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));

    String udmiNamespacePrefix = ofNullable(spec.udmiNamespace).map(ns -> ns + "~").orElse("");
    String requestsSubscription = udmiNamespacePrefix + subscriptionSuffix;

    this.messagingClient = MessagingClient.from(new MessagingClientConfig(
        spec.protocol, spec.project, DEFAULT_MQTT_BROKER, null, requestsSubscription));

    prepareCloningDirectory();
  }

  // --- Service Lifecycle Methods ---

  /**
   * Starts the service, beginning to poll for messages in a background thread.
   */
  public void start() {
    if (running.compareAndSet(false, true)) {
      pollingExecutor.submit(this::pollForMessages);
      LOGGER.info("{} poller started.", serviceName);
    }
  }

  /**
   * Stops the service, closing the messaging client and shutting down the background thread.
   */
  public void stop() {
    LOGGER.info("Attempting to stop {}...", serviceName);
    running.set(false);
    if (messagingClient != null) {
      messagingClient.close();
    }
    shutdownExecutorService();
    LOGGER.info("{} stopped.", serviceName);
  }

  // --- Message Processing ---

  /**
   * The core logic to be implemented by subclasses to handle an individual message.
   *
   * @param message The message received from the messaging client.
   */
  protected abstract void handleMessage(PubsubMessage message) throws Exception;

  /**
   * The main loop that polls the messaging client for new requests.
   */
  private void pollForMessages() {
    LOGGER.info("Polling for new messages for {}...", serviceName);
    while (running.get()) {
      try {
        ofNullable(messagingClient.poll(POLL_TIMEOUT)).ifPresent(message -> {
          try {
            handleMessage(message);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      } catch (Exception e) {
        LOGGER.error("Error in processing loop for {}: {}", serviceName, e.getMessage(), e);
        sleepOnError();
      }
    }
    LOGGER.info("{} polling loop finished.", serviceName);
  }

  // --- Utility and Helper Methods ---

  private void shutdownExecutorService() {
    pollingExecutor.shutdown();
    try {
      if (!pollingExecutor.awaitTermination(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        pollingExecutor.shutdownNow();
        if (!pollingExecutor.awaitTermination(SHUTDOWN_TIMEOUT.toSeconds() / 2, TimeUnit.SECONDS)) {
          LOGGER.error("Executor service for {} did not terminate.", serviceName);
        }
      }
    } catch (InterruptedException e) {
      pollingExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void prepareCloningDirectory() {
    LOGGER.info("Ensuring cloning base directory exists: {}", baseCloningDir);
    try {
      Files.createDirectories(Paths.get(this.baseCloningDir));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not create site model clone directory: " + this.baseCloningDir, e);
    }
  }

  private void sleepOnError() {
    try {
      Thread.sleep(SLEEP_ON_ERROR_DURATION.toMillis());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Polling loop interrupted during error backoff, stopping service {}.",
          serviceName);
      running.set(false);
    }
  }

  public ProjectSpec getProjectSpec(String target) {
    String[] parts = target.split("/");
    return new ProjectSpec(parts[2], parts[3], parts.length == 5 ? parts[4] : null);
  }

  protected SourceRepository initRepository(String repoId) {
    String repoDir = Paths.get(baseCloningDir, repoId).toString();
    return new SourceRepository(repoId, repoDir, localOriginDir, gcpProject, udmiNamespace);
  }

  // --- Inner Records for Data Structuring ---

  /**
   * Record to represent project spec.
   *
   * @param protocol e.g. mqtt
   * @param project e.g. localhost
   * @param udmiNamespace GKE Namespace, can be null
   */
  protected record ProjectSpec(String protocol, String project, String udmiNamespace) { }

}