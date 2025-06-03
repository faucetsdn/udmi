package com.google.bos.iot.core.bambi;

import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SheetsAppender;
import com.google.udmi.util.SheetsOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
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

public class BambiService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BambiService.class);
  private static final String DEFAULT_IMPORT_BRANCH = "main";
  private static final Pattern SPREADSHEET_ID_PATTERN = Pattern.compile("/d/([^/]+)");

  private final PubSubClient pubSubClient;
  private final ExecutorService pollingExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final String serviceName = "BambiService";

  private final String gcpProject;
  private final String siteModelCloneDir;

  public BambiService(String gcpProject, String udmiNamespace, String siteModelBaseDir) {
    this.gcpProject = gcpProject;
    this.siteModelCloneDir = siteModelBaseDir;

    String udmiNamespacePrefix = Optional.ofNullable(udmiNamespace).map(ns -> ns + "~").orElse("");
    String requestsSubscription = udmiNamespacePrefix + "bambi-requests";

    this.pubSubClient =
        new PubSubClient(gcpProject, null, requestsSubscription, null, false, false);
    this.pollingExecutor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, serviceName + "-poller"));

    LOGGER.info(
        serviceName
            + " initialized for project "
            + gcpProject
            + " and subscription: "
            + pubSubClient.getSubscriptionId());
    LOGGER.info("Site model clone base directory: " + siteModelCloneDir);

    try {
      Files.createDirectories(Paths.get(this.siteModelCloneDir));
    } catch (IOException e) {
      throw new RuntimeException(
          "Could not create site model clone directory: " + this.siteModelCloneDir, e);
    }
  }

  public static void main(String[] args) {
    String gcpProject = args[0];
    String siteModelBaseDir = args[1];
    String udmiNamespace = System.getenv("UDMI_NAMESPACE");

    LOGGER.info(
        "Using GCP_PROJECT {}, UDMI_NAMESPACE {}, CLONE_DIR {}",
        gcpProject,
        udmiNamespace == null ? "<not set>" : udmiNamespace,
        siteModelBaseDir);

    BambiService service = new BambiService(gcpProject, udmiNamespace, siteModelBaseDir);
    service.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOGGER.info("Shutdown hook triggered for " + service.serviceName);
                  service.stop();
                }));
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      pollingExecutor.submit(this::pollForMessages);
      LOGGER.info(serviceName + " poller started.");
    }
  }

  private void pollForMessages() {
    while (running.get() && pubSubClient.isActive()) {
      try {
        MessageBundle bundle = pubSubClient.takeNextMessage(MessagePublisher.QuerySpeed.QUICK);

        if (bundle != null && bundle.attributes != null && !bundle.attributes.isEmpty()) {
          LOGGER.info("Message received. Processing...");
          LOGGER.info("Attributes: " + bundle.attributes);

          processBambiRequest(bundle.attributes);

        } else if (bundle != null) {
          LOGGER.info("Received message with no attributes or empty attributes map. Ignoring.");
          LOGGER.info("Payload (if any): " + JsonUtil.stringify(bundle.message));
        } else {
          LOGGER.info("Polled but received null bundle.");
        }
      } catch (Exception e) {
        LOGGER.error("Error in polling/processing loop: " + e.getMessage());
        e.printStackTrace();
        try {
          TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
      LOGGER.info("Polling for new messages...");
    }
    LOGGER.info("Polling loop finished.");
  }

  private void processBambiRequest(Map<String, String> attributes) {
    String requestType = attributes.get("request_type");
    String source = attributes.get("source");
    String user = attributes.get("user");
    String registryId = attributes.get("registry_id");
    String importBranch = attributes.getOrDefault("import_branch", DEFAULT_IMPORT_BRANCH);

    if (requestType == null || source == null || user == null || registryId == null) {
      LOGGER.warn(
          "Missing one or more required attributes (request_type, source, user, registry_id)."
              + " Skipping.");
      attributes.forEach((k, v) -> LOGGER.info("  " + k + ": " + v));
      return;
    }

    Matcher matcher = SPREADSHEET_ID_PATTERN.matcher(source);
    String spreadsheetId = null;
    if (matcher.find()) {
      spreadsheetId = matcher.group(1);
    }

    if (spreadsheetId == null) {
      LOGGER.error("Could not extract spreadsheet_id from source: " + source + ". Skipping.");
      return;
    }

    String repoDir = Paths.get(siteModelCloneDir, registryId).toString();
    String date = new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date());
    String exportBranch = "proposal." + spreadsheetId + "." + date;
    String outputSheetTitle = "bambi_service." + requestType + "." + date;

    try (SheetsOutputStream stream =
        new SheetsOutputStream("BAMBI", spreadsheetId, outputSheetTitle)) {

      // Stream logs from LOGGER to sheets
      SheetsAppender.setSheetsOutputStream(stream);

      try {
        LOGGER.info(
            String.format(
                "Processing '%s' request from '%s' for registry '%s', spreadsheet '%s'",
                requestType, user, registryId, spreadsheetId));

        executeCliCommand(null, "rm", "-rf", repoDir);

        if (executeCliCommand(
            null,
            "gcloud",
            "source",
            "repos",
            "clone",
            registryId,
            repoDir,
            "--project=" + gcpProject)
            .exitCode
            == 0) {
          if (executeCliCommand(repoDir, "git", "switch", importBranch).exitCode == 0) {
            if ("import".equals(requestType)) {
              LOGGER.info("Populating site model in bambi sheet");
              BambiSync sync = new BambiSync(spreadsheetId, repoDir + "/udmi");
              sync.execute();
            } else if ("merge".equals(requestType)) {
              LOGGER.info("Merging data from BAMBI to site model on disk");
              if (executeCliCommand(repoDir, "git", "checkout", "-b", exportBranch).exitCode == 0) {
                LocalDiskSync sync = new LocalDiskSync(spreadsheetId, repoDir + "/udmi");
                sync.execute();
                executeCliCommand(repoDir, "git", "add", ".");
                CommandResult commitResult =
                    executeCliCommand(repoDir, "git", "commit", "-m", "Merge changes from BAMBI");
                if (commitResult.exitCode == 0
                    && !commitResult.stdout.contains(
                    "nothing to commit")) { // Check if commit actually happened
                  CommandResult pushResult =
                      executeCliCommand(
                          repoDir, "git", "push", "origin", exportBranch + ":" + exportBranch);
                  if (pushResult.exitCode != 0) {
                    LOGGER.warn(
                        String.format(
                            "Could not push branch %s. stdout: %s, stderr: %s",
                            exportBranch, pushResult.stdout, pushResult.stderr));
                  }
                } else if (commitResult.stdout.contains("nothing to commit")) {
                  LOGGER.info("No changes to commit after sync from BAMBI.");
                } else {
                  LOGGER.warn(
                      String.format(
                          "Git commit failed. stdout: %s, stderr: %s",
                          commitResult.stdout, commitResult.stderr));
                }
              } else {
                LOGGER.error("Unable to create branch " + exportBranch);
              }
            } else {
              LOGGER.warn("Unknown request_type: " + requestType);
            }
          } else {
            LOGGER.warn(
                "Skipping request because branch "
                    + importBranch
                    + " does not exist or could not be switched to in "
                    + repoDir);
          }
        } else {
          LOGGER.warn(
              "Skipping request, could not clone repository " + registryId + " to " + repoDir);
        }
      } catch (Exception e) {
        LOGGER.error("Exception during processing request: " + e.getMessage());
        e.printStackTrace();
      } finally {
        LOGGER.info("Finished processing request for " + registryId);

        // Ensure the last logs are flushed before closing
        stream.appendToSheet();

        SheetsAppender.setSheetsOutputStream(null);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CommandResult executeCliCommand(String workingDir, String... command) {
    LOGGER.info(
        "[COMMAND] Executing: "
            + String.join(" ", command)
            + (workingDir != null ? " in " + workingDir : ""));
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    if (workingDir != null) {
      processBuilder.directory(new File(workingDir));
    }

    StringBuilder stdoutBuilder = new StringBuilder();
    StringBuilder stderrBuilder = new StringBuilder();
    int exitCode = -1;

    try {
      Process process = processBuilder.start();

      try (ExecutorService stdoutExecutor = Executors.newSingleThreadExecutor()) {
        stdoutExecutor.submit(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  LOGGER.info("[COMMAND] stdout: " + line);
                  stdoutBuilder.append(line).append(System.lineSeparator());
                }
              } catch (IOException e) {
                LOGGER.error("[COMMAND] error reading stdout: " + e.getMessage());
              }
            });
        stdoutExecutor.shutdown();
        stdoutExecutor.awaitTermination(1, TimeUnit.MINUTES);
      }

      try (ExecutorService stderrExecutor = Executors.newSingleThreadExecutor()) {
        stderrExecutor.submit(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  LOGGER.info("[COMMAND] stderr: " + line);
                  stderrBuilder.append(line).append(System.lineSeparator());
                }
              } catch (IOException e) {
                LOGGER.error("[COMMAND] error reading stderr: " + e.getMessage());
              }
            });
        stderrExecutor.shutdown();
        stderrExecutor.awaitTermination(1, TimeUnit.MINUTES);
      }

      boolean finished = process.waitFor(5, TimeUnit.MINUTES);

      if (finished) {
        exitCode = process.exitValue();
        LOGGER.info("[COMMAND] finished with exit code: " + exitCode);
      } else {
        LOGGER.error("[COMMAND] error: Command timed out: " + String.join(" ", command));
        process.destroyForcibly();
        exitCode = -100;
      }

    } catch (IOException | InterruptedException e) {
      LOGGER.error(
          "[COMMAND] error executing command " + String.join(" ", command) + ": " + e.getMessage());
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      stderrBuilder.append("Execution exception: ").append(e.getMessage());
    }
    return new CommandResult(exitCode, stdoutBuilder.toString(), stderrBuilder.toString());
  }

  public void stop() {
    LOGGER.info("Attempting to stop poller...");
    running.set(false);
    if (pubSubClient != null) {
      pubSubClient.close();
    }
    pollingExecutor.shutdown();
    try {
      if (!pollingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        pollingExecutor.shutdownNow();
      }
      LOGGER.info("Poller stopped.");
    } catch (InterruptedException e) {
      pollingExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private record CommandResult(int exitCode, String stdout, String stderr) {}
}
