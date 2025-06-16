package com.google.bos.iot.core.bambi;

import static com.google.udmi.util.git.RepositoryConfig.forRemote;
import static com.google.udmi.util.git.RepositoryConfig.fromGoogleCloudSourceRepoName;
import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.SheetsAppender;
import com.google.udmi.util.SheetsOutputStream;
import com.google.udmi.util.git.GenericGitRepository;
import com.google.udmi.util.git.GoogleCloudSourceRepository;
import com.google.udmi.util.messaging.GenericPubSubClient;
import com.google.udmi.util.messaging.MessagingClient;
import com.google.udmi.util.messaging.MqttMessagingClient;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BAMBI Backend Service - polls a PubSub topic for import/export requests and streams logs of the
 * operations back to the requesting spreadsheet.
 */
public class BambiService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BambiService.class);

  private static final String APP_NAME = "BAMBI";
  private static final String SERVICE_NAME = "BambiService";
  private static final String DEFAULT_IMPORT_BRANCH = "main";
  private static final String IMPORT_REQUEST = "import";
  private static final String EXPORT_REQUEST = "merge";
  private static final Pattern SPREADSHEET_ID_PATTERN = Pattern.compile("/d/([^/]+)");
  private static final long POLL_TIMEOUT_MS = 1000;
  private static final String DEFAULT_MQTT_BROKER = "tcp://localhost:1883";
  private static final String PROJECT_TARGET_REGEX = "\\/\\/(mqtt|gbos)(\\/[^\\/\\s]*){1,2}";
  private static final String MQTT = "mqtt";

  private final MessagingClient messagingClient;
  private final ExecutorService pollingExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);

  private final String protocol;
  private final String gcpProject;
  private final String udmiNamespace;
  private final String baseCloningDir;
  private final String localOriginDir;

  /**
   * Main constructor for the BambiService.
   *
   * @param projectTarget Target project specifier, e.g., "//gbos/gcp-project/udmi-namespace"
   * @param siteModelBaseDir Base directory for cloning site models.
   * @param localOriginDir Optional directory for local git origins (for testing).
   */
  public BambiService(String projectTarget, String siteModelBaseDir, String localOriginDir) {
    if (!projectTarget.matches(PROJECT_TARGET_REGEX)) {
      throw new IllegalArgumentException("Invalid project target format: " + projectTarget);
    }

    ProjectSpec spec = getProjectSpec(projectTarget);
    this.protocol = spec.protocol;
    this.udmiNamespace = spec.udmiNamespace;
    this.baseCloningDir = siteModelBaseDir;
    this.localOriginDir = localOriginDir;
    this.pollingExecutor = Executors.newSingleThreadExecutor(
        r -> new Thread(r, SERVICE_NAME + "-poller"));

    String udmiNamespacePrefix = ofNullable(spec.udmiNamespace).map(ns -> ns + "~").orElse("");
    String requestsSubscription = udmiNamespacePrefix + "bambi-requests";

    if (MQTT.equals(protocol)) {
      this.gcpProject = null;
      this.messagingClient = createMqttClient(requestsSubscription);
    } else { // GBOS
      this.gcpProject = spec.project;
      this.messagingClient = createGcpPubSubClient(gcpProject, requestsSubscription);
    }

    initializeCloningDirectory();
  }

  public BambiService(String projectTarget, String siteModelBaseDir) {
    this(projectTarget, siteModelBaseDir, null);
  }

  /**
   * BAMBI backend service to support management of site models through Google Sheets.
   *
   * @param args command line args e.g. //gbos/bos-platform-dev/udmis /tmp/udmi/sites/
   */
  public static void main(String[] args) {
    if (args.length < 2 || args.length > 3) {
      System.err.println(
          "Usage: BambiService <projectTarget> <siteModelCloneDir> [<localOriginDir>]");
      System.exit(1);
    }

    String projectTarget = args[0];
    String siteModelBaseDir = args[1];
    String localOriginDir = (args.length == 3) ? args[2] : null;

    LOGGER.info("Using target {}, CLONE_DIR {}", projectTarget, siteModelBaseDir);
    if (localOriginDir != null) {
      LOGGER.info("Running in local origin mode with origin directory: {}", localOriginDir);
    }

    BambiService service = new BambiService(projectTarget, siteModelBaseDir, localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for " + SERVICE_NAME);
      service.stop();
    }));
  }

  /**
   * Start the service.
   */
  public void start() {
    if (running.compareAndSet(false, true)) {
      pollingExecutor.submit(this::pollForMessages);
      LOGGER.info("{} poller started.", SERVICE_NAME);
    }
  }

  /**
   * Stop ther service.
   */
  public void stop() {
    LOGGER.info("Attempting to stop {}...", SERVICE_NAME);
    running.set(false);
    if (messagingClient != null) {
      messagingClient.close();
    }
    shutdownExecutorService();
    LOGGER.info("{} stopped.", SERVICE_NAME);
  }

  private void shutdownExecutorService() {
    pollingExecutor.shutdown();
    try {
      if (!pollingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        pollingExecutor.shutdownNow();
        if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          LOGGER.error("Executor service did not terminate.");
        }
      }
    } catch (InterruptedException e) {
      pollingExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void initializeCloningDirectory() {
    LOGGER.info("Cloning base directory: {}", baseCloningDir);
    try {
      Files.createDirectories(Paths.get(this.baseCloningDir));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not create site model clone directory: " + this.baseCloningDir, e);
    }
  }

  private MessagingClient createMqttClient(String requestsSubscription) {
    LOGGER.info("Running in LOCAL MQTT mode.");
    MqttMessagingClient client = new MqttMessagingClient(DEFAULT_MQTT_BROKER, requestsSubscription);
    LOGGER.info("{} initialized for MQTT broker {} and topic {}", SERVICE_NAME, DEFAULT_MQTT_BROKER,
        requestsSubscription);
    return client;
  }

  private MessagingClient createGcpPubSubClient(String gcpProject, String requestsSubscription) {
    LOGGER.info("Running in GCP Pub/Sub mode.");
    if (!GenericPubSubClient.subscriptionExists(gcpProject, requestsSubscription)) {
      throw new IllegalStateException(String.format(
          "Subscription %s does not exist in project %s. Please ensure it exists and retry!",
          requestsSubscription, gcpProject));
    }
    GenericPubSubClient client = new GenericPubSubClient(gcpProject, requestsSubscription, null);
    LOGGER.info("{} initialized for project {} and subscription {}", SERVICE_NAME, gcpProject,
        requestsSubscription);
    return client;
  }

  private void pollForMessages() {
    LOGGER.info("Polling for new messages...");
    while (running.get()) {
      try {
        ofNullable(messagingClient.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)).ifPresent(
            this::handleMessage);
      } catch (Exception e) {
        LOGGER.error("Error in processing loop: {}", e.getMessage(), e);
        sleepOnError();
      }
    }
    LOGGER.info("Polling loop finished.");
  }

  private void sleepOnError() {
    try {
      TimeUnit.SECONDS.sleep(5);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Polling loop interrupted during error backoff, stopping service.");
      running.set(false);
    }
  }

  private void handleMessage(PubsubMessage message) {
    if (!verifyIdentityToken(message.getData().toStringUtf8())) {
      LOGGER.warn("Message identity verification failed. Ignoring.");
      return;
    }

    Map<String, String> attributes = message.getAttributesMap();
    if (attributes.isEmpty()) {
      LOGGER.warn("Received message with no attributes. Ignoring. Payload (if any): {}",
          message.getData().toString(StandardCharsets.UTF_8));
      return;
    }

    LOGGER.info("Message received. Processing...");
    attributes.forEach((k, v) -> LOGGER.info("  {}: {}", k, v));
    processBambiRequest(getRequestParams(attributes));
  }

  private boolean verifyIdentityToken(String identityTokenString) {
    if (MQTT.equals(protocol)) {
      return true;
    }
    if (identityTokenString == null || identityTokenString.isEmpty()) {
      LOGGER.warn("Request missing identity token.");
      return false;
    }
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
        new GsonFactory()).build();
    try {
      GoogleIdToken token = verifier.verify(identityTokenString);
      if (token != null && token.getPayload().getEmailVerified()) {
        LOGGER.info("Received verified request from {}", token.getPayload().getEmail());
        return true;
      }
      LOGGER.warn("Could not verify identity token; request will not be processed.");
      return false;
    } catch (GeneralSecurityException | IOException e) {
      LOGGER.error("Exception while verifying identity; request will not be processed.", e);
      return false;
    }
  }

  private BambiRequestParams getRequestParams(Map<String, String> attributes) {
    return new BambiRequestParams(
        attributes.get("request_type"),
        attributes.get("source"),
        attributes.get("user"),
        attributes.get("registry_id"),
        attributes.getOrDefault("import_branch", DEFAULT_IMPORT_BRANCH));
  }

  private void processBambiRequest(BambiRequestParams params) {
    if (params.requestType == null || params.source == null || params.user == null
        || params.registryId == null) {
      LOGGER.warn(
          "Missing required attribute(s) - request_type, source, user, registry_id. Skipping.");
      return;
    }

    Optional<String> spreadsheetIdOpt = getSpreadsheetId(params.source);
    if (spreadsheetIdOpt.isEmpty()) {
      LOGGER.error("Cannot extract spreadsheet_id from source: {}. Skipping.", params.source);
      return;
    }
    String spreadsheetId = spreadsheetIdOpt.get();

    String date = new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date());
    String outputSheetTitle = String.format("bambi_service.%s.%s", params.requestType, date);

    try (SheetsOutputStream stream = new SheetsOutputStream(APP_NAME, spreadsheetId,
        outputSheetTitle)) {
      executeWithSheetLogging(stream, () -> {
        LOGGER.info("Processing '{}' request from '{}' for registry '{}', spreadsheet '{}'",
            params.requestType, params.user, params.registryId, spreadsheetId);
        processRequest(params, spreadsheetId, date);
      });
    } catch (Exception e) {
      LOGGER.error("Failed to process bambi request for registry {}: {}", params.registryId,
          e.getMessage(), e);
    }
  }

  /**
   * Wraps an action with Sheets logging, ensuring that all logs from the action are streamed to the
   * provided sheet and flushed at the end.
   */
  private void executeWithSheetLogging(SheetsOutputStream stream, Runnable action) {
    SheetsAppender.setSheetsOutputStream(stream);
    try {
      action.run();
    } catch (Exception e) {
      LOGGER.error("Exception during processing request: {}", e.getMessage(), e);
    } finally {
      LOGGER.info("Finished processing request for this sheet.");
      stream.appendToSheet();
      SheetsAppender.setSheetsOutputStream(null);
    }
  }

  private void processRequest(BambiRequestParams params, String spreadsheetId, String date) {
    String repoDir = Paths.get(baseCloningDir, params.registryId).toString();
    try {
      prepareDirectory(repoDir);
      try (GenericGitRepository repository = getRepository(params.registryId, repoDir)) {
        repository.cloneRepo(params.importBranch);
        handleTask(params, repository, spreadsheetId, date);
      }
    } catch (Exception e) {
      LOGGER.error("Could not complete git operation, failed with error: {}", e.getMessage(), e);
    }
  }

  private void handleTask(BambiRequestParams params, GenericGitRepository repository,
      String spreadsheetId, String date) throws Exception {
    String udmiModelPath = Paths.get(repository.getDirectory(), "udmi").toString();
    if (IMPORT_REQUEST.equals(params.requestType)) {
      handleImport(spreadsheetId, udmiModelPath);
    } else if (EXPORT_REQUEST.equals(params.requestType)) {
      handleExport(spreadsheetId, udmiModelPath, repository,
          String.format("proposal.%s.%s", spreadsheetId, date));
    }
  }

  private void handleImport(String spreadsheetId, String udmiModelPath) {
    LOGGER.info("Populating site model in bambi sheet from {}", udmiModelPath);
    new BambiSync(spreadsheetId, udmiModelPath).execute();
  }

  private void handleExport(String spreadsheetId, String udmiModelPath,
      GenericGitRepository repository, String exportBranch) throws Exception {
    LOGGER.info("Merging data from BAMBI to site model on disk at {}", udmiModelPath);
    repository.createAndCheckoutBranch(exportBranch);
    new LocalDiskSync(spreadsheetId, udmiModelPath).execute();

    if (repository.isWorkingTreeClean()) {
      LOGGER.info("Working tree is clean, nothing to commit.");
    } else {
      LOGGER.info("Committing and pushing changes to branch {}", exportBranch);
      repository.add(".");
      repository.commit("Merge changes from BAMBI");
      repository.push();
      LOGGER.info("Push successful.");
    }
  }

  private Optional<String> getSpreadsheetId(String sourceUrl) {
    Matcher matcher = SPREADSHEET_ID_PATTERN.matcher(sourceUrl);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private void prepareDirectory(String directory) throws IOException {
    File dirFile = new File(directory);
    if (dirFile.exists()) {
      LOGGER.info("Deleting existing directory {}", directory);
      deleteDirectory(dirFile);
    }
  }

  private GenericGitRepository getRepository(String repoName, String repoClonePath)
      throws GeneralSecurityException, IOException {
    if (localOriginDir != null) {
      String remotePath = Paths.get(localOriginDir, repoName).toString();
      LOGGER.info("Using local git origin at {}", remotePath);
      return new GenericGitRepository(forRemote(remotePath, repoClonePath));
    }
    LOGGER.info("Using Google Cloud Source Repository {} in project {}", repoName, gcpProject);
    return new GoogleCloudSourceRepository(
        fromGoogleCloudSourceRepoName(repoName, repoClonePath, gcpProject), udmiNamespace);
  }

  private ProjectSpec getProjectSpec(String target) {
    String[] parts = target.split("/");
    return new ProjectSpec(parts[2], parts[3], parts.length == 5 ? parts[4] : null);
  }

  private record ProjectSpec(String protocol, String project, String udmiNamespace) {

  }

  private record BambiRequestParams(String requestType, String source, String user,
                                    String registryId, String importBranch) {

  }
}
