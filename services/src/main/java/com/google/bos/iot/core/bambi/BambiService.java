package com.google.bos.iot.core.bambi;

import static com.google.udmi.util.SheetsOutputStream.executeWithSheetLogging;

import com.google.bos.iot.core.bambi.auth.IdVerificationConfig;
import com.google.bos.iot.core.bambi.auth.IdVerifier;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.AbstractPollingService;
import com.google.udmi.util.SheetsOutputStream;
import com.google.udmi.util.SourceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BAMBI Backend Service: A long-running service that listens for requests on a Pub/Sub topic
 * to import or export data between a Git repository (the "site model") and a Google Sheet.
 * It streams its operational logs back to the requesting spreadsheet for visibility.
 */
public class BambiService extends AbstractPollingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BambiService.class);
  private static final String APP_NAME = "BAMBI";
  private static final String SERVICE_NAME = "BambiService";
  private static final String SUBSCRIPTION_SUFFIX = "bambi-requests";

  // --- Bambi-specific constants ---
  private static final String DEFAULT_IMPORT_BRANCH = "main";
  private static final String IMPORT_REQUEST = "import";
  private static final String EXPORT_REQUEST = "merge";
  private static final Pattern SPREADSHEET_ID_PATTERN = Pattern.compile("/d/([^/]+)");

  private final IdVerifier idVerifier;


  /**
   * Main entry point for the BAMBI service application.
   *
   * @param args Command line args: $projectTarget $siteModelCloneDir [$localOriginDir] e.g.,
   *     //pubsub/gcp-project/udmis /tmp/udmi/sites/
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

    LOGGER.info("Requesting BAMBI Service for target {}, cloning to {}", projectTarget,
        siteModelBaseDir);
    if (localOriginDir != null) {
      LOGGER.info("Using local git origin for testing: {}", localOriginDir);
    }

    BambiService service = new BambiService(projectTarget, siteModelBaseDir, localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for " + SERVICE_NAME);
      service.stop();
    }));
  }

  /**
   * Primary constructor for the BambiService.
   *
   * @param projectTarget Target project specifier, e.g., "//pubsub/gcp-project/udmi-namespace"
   * @param siteModelBaseDir Base directory for cloning site model Git repositories.
   * @param localOriginDir Optional directory for local git origins (for testing).
   */
  public BambiService(String projectTarget, String siteModelBaseDir, String localOriginDir) {
    super(SERVICE_NAME, SUBSCRIPTION_SUFFIX, projectTarget, siteModelBaseDir, localOriginDir);
    ProjectSpec spec = getProjectSpec(projectTarget);
    this.idVerifier = IdVerifier.from(spec.protocol());
    LOGGER.info("Starting BAMBI Service for target {}, cloning to {}",
        projectTarget, siteModelBaseDir);
  }

  /**
   * Handles an individual message from the Pub/Sub topic.
   */
  @Override
  protected void handleMessage(PubsubMessage message) {
    // Verify the message identity to ensure it's from a trusted source.
    // TODO: After the BAMBI plugin is GA, add audience id below
    if (!idVerifier.verify(new IdVerificationConfig(message.getData().toStringUtf8(), null))) {
      LOGGER.warn("Message identity verification failed. Ignoring.");
      return;
    }

    Map<String, String> attributes = message.getAttributesMap();
    if (attributes.isEmpty()) {
      LOGGER.warn("Received message with no attributes. Ignoring. Payload (if any): {}",
          message.getData().toString(StandardCharsets.UTF_8));
      return;
    }

    LOGGER.info("Message received. Processing... Attributes: {}", attributes);
    BambiRequestParams params = getRequestParams(attributes);
    processSyncRequest(params);
  }

  /**
   * Process request parameters.
   * It's wrapped in a logging context that streams
   * output to a Google Sheet.
   *
   * @param params The structured parameters for the request.
   */
  private void processSyncRequest(BambiRequestParams params) {
    if (!params.areValid()) {
      LOGGER.warn("Missing required attributes in request. Skipping. Params: {}", params);
      return;
    }

    Optional<String> spreadsheetIdOpt = getSpreadsheetId(params.source);
    if (spreadsheetIdOpt.isEmpty()) {
      LOGGER.error("Cannot extract spreadsheet_id from source: {}. Skipping.", params.source);
      return;
    }
    String spreadsheetId = spreadsheetIdOpt.get();

    String timestamp = new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date());
    String outputSheetTitle = String.format("bambi_log.%s.%s", params.requestType, timestamp);

    try (SheetsOutputStream stream = new SheetsOutputStream(APP_NAME, spreadsheetId,
        outputSheetTitle)) {
      executeWithSheetLogging(stream, () -> {
        LOGGER.info("Processing '{}' request for registry '{}', initiated by '{}'.",
            params.requestType, params.registryId, params.user);
        LOGGER.info("Logs are streamed to spreadsheet '{}', sheet '{}'", spreadsheetId,
            outputSheetTitle);

        String repoDir = Paths.get(baseCloningDir, params.registryId).toString();
        SourceRepository repository = new SourceRepository(params.registryId, repoDir,
            localOriginDir, gcpProject, udmiNamespace);
        if (!repository.clone(params.importBranch)) {
          throw new RuntimeException("Could not clone repository " + params.registryId);
        }

        String udmiModelPath = Paths.get(repository.getDirectory(), "udmi").toString();
        switch (params.requestType) {
          case IMPORT_REQUEST -> handleImport(spreadsheetId, udmiModelPath);
          case EXPORT_REQUEST -> handleExport(spreadsheetId, udmiModelPath, repository, timestamp);
          default -> LOGGER.error("Invalid request type '{}'", params.requestType);
        }
        repository.delete(); // free up space after task
      });
    } catch (Exception e) {
      LOGGER.error("Failed to process bambi request for registry {}: {}", params.registryId,
          e.getMessage(), e);
    }
  }

  // --- Core Task Handlers ---

  /**
   * Handles an 'import' request: copies data from the Git repo to the Google Sheet.
   */
  private void handleImport(String spreadsheetId, String udmiModelPath) {
    LOGGER.info("Populating Google Sheet {} from site model at {}", spreadsheetId, udmiModelPath);
    new BambiSync(spreadsheetId, udmiModelPath).execute();
    LOGGER.info("Import operation complete.");
  }

  /**
   * Handles a 'merge' (export) request: copies data from the Google Sheet to the Git repo, then
   * commits and pushes the changes to a new branch.
   */
  private void handleExport(String spreadsheetId, String udmiModelPath,
      SourceRepository repository, String timestamp) {
    String exportBranch = String.format("proposal/%s/%s", spreadsheetId, timestamp);
    if (!repository.checkoutNewBranch(exportBranch)) {
      throw new RuntimeException("Unable to create and checkout export branch " + exportBranch);
    }

    LOGGER.info("Merging data from Google Sheet {} to local site model at {}", spreadsheetId,
        udmiModelPath);
    new LocalDiskSync(spreadsheetId, udmiModelPath).execute();

    LOGGER.info("Committing and pushing changes to branch {}", exportBranch);
    if (!repository.commitAndPush("Merge changes from BAMBI spreadsheet " + spreadsheetId)) {
      throw new RuntimeException("Unable to commit and push changes to branch " + exportBranch);
    }
    LOGGER.info("Export operation complete.");
  }

  // --- Utility and Helper Methods ---

  private BambiRequestParams getRequestParams(Map<String, String> attributes) {
    return new BambiRequestParams(
        attributes.get("request_type"),
        attributes.get("source"),
        attributes.get("user"),
        attributes.get("registry_id"),
        attributes.getOrDefault("import_branch", DEFAULT_IMPORT_BRANCH));
  }

  private Optional<String> getSpreadsheetId(String sourceUrl) {
    Matcher matcher = SPREADSHEET_ID_PATTERN.matcher(sourceUrl);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  // --- Inner Records for Data Structuring ---

  private record BambiRequestParams(String requestType, String source, String user,
                                    String registryId, String importBranch) {
    /**
     * Helper to validate that all required parameters are present.
     */
    public boolean areValid() {
      return requestType != null && source != null && user != null && registryId != null;
    }
  }
}