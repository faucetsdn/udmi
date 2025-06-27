package com.google.daq.mqtt.registrar;

import static com.google.bos.iot.core.bambi.BambiService.SPREADSHEET_ID_KEY;
import static com.google.bos.iot.core.bambi.BambiService.TRIGGER_FILE_NAME;
import static com.google.udmi.util.JsonUtil.flattenNestedMap;
import static com.google.udmi.util.SheetsOutputStream.executeWithSheetLogging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.AbstractPollingService;
import com.google.udmi.util.SheetsOutputStream;
import com.google.udmi.util.SourceRepository;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to run registrar triggered by source repository updates.
 */
public class RegistrarService extends AbstractPollingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarService.class);
  private static final String SERVICE_NAME = "RegistrarService";
  private static final String SUBSCRIPTION_SUFFIX = "source-repo-updates-registrar";
  private static final String TRIGGER_BRANCH = "main";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String REF_UPDATE_EVENT_FORMAT =
      "refUpdateEvent.refUpdates.refs/heads/%s.%s";
  private static final String REF_NAME_KEY = String.format(REF_UPDATE_EVENT_FORMAT, TRIGGER_BRANCH,
      "refName");
  private static final String UPDATE_TYPE_KEY = String.format(REF_UPDATE_EVENT_FORMAT,
      TRIGGER_BRANCH, "updateType");
  private static final String REPO_NAME_KEY = "name";
  private static final Set<String> TRIGGERING_UPDATE_TYPES = Set.of("CREATE",
      "UPDATE_FAST_FORWARD");
  private final String registrarTarget;

  /**
   * Constructs a RegistrarService instance.
   *
   * @param projectTarget GCP project ID for the service.
   * @param siteModelBaseDir Base directory for cloning site models.
   * @param localOriginDir Optional local directory to use as a git origin.
   */
  public RegistrarService(String projectTarget, String siteModelBaseDir, String localOriginDir) {
    super(SERVICE_NAME, SUBSCRIPTION_SUFFIX, projectTarget, siteModelBaseDir, localOriginDir);
    registrarTarget = projectTarget;
    LOGGER.info("Starting Registrar Service for project {}, cloning to {}", projectTarget,
        siteModelBaseDir);
  }

  /**
   * Main entry point for the application.
   */
  public static void main(String[] args) {
    if (args.length < 2 || args.length > 3) {
      System.err.println(
          "Usage: RegistrarService <projectTarget> <siteModelCloneDir> [<localOriginDir>]");
      System.exit(1);
    }

    String projectTarget = args[0];
    String siteModelCloneDir = args[1];
    String localOriginDir = (args.length == 3) ? args[2] : null;

    RegistrarService service = new RegistrarService(projectTarget, siteModelCloneDir,
        localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for {}", SERVICE_NAME);
      service.stop();
    }));
  }

  @Override
  protected void handleMessage(PubsubMessage message) {
    try {
      Map<String, Object> messageData = parseMessageData(message);
      if (!isTriggeringEvent(messageData)) {
        return;
      }

      RepositoryInfo repoInfo = new RepositoryInfo(messageData);
      SourceRepository repository = setupRepository(repoInfo);

      if (repository.clone(TRIGGER_BRANCH)) {
        String udmiModelPath = Paths.get(repository.getDirectory(), "udmi").toString();

        if (checkTriggerFileExists(udmiModelPath)) {
          runRegistrar(udmiModelPath, getSpreadsheetId(udmiModelPath).orElse(null), repository);
        } else {
          LOGGER.info("Skipping. Trigger file does not exist.");
        }
      } else {
        LOGGER.error("Failed to clone repository {}", repoInfo.id());
      }

    } catch (Exception e) {
      LOGGER.error("Could not complete registrar run request", e);
    }
  }

  private Map<String, Object> parseMessageData(PubsubMessage message) throws IOException {
    String messageJson = message.getData().toString(StandardCharsets.UTF_8);
    TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
    };
    Map<String, Object> rawMap = OBJECT_MAPPER.readValue(messageJson, typeRef);
    return flattenNestedMap(rawMap, ".");
  }

  private boolean isTriggeringEvent(Map<String, Object> messageData) {
    String refName = (String) messageData.getOrDefault(REF_NAME_KEY, "");
    String updateType = (String) messageData.getOrDefault(UPDATE_TYPE_KEY, "");

    if (refName.isEmpty() || !TRIGGERING_UPDATE_TYPES.contains(updateType)) {
      LOGGER.info("Ignoring non-triggering event. Ref: '{}', Type: '{}'", refName, updateType);
      return false;
    }
    LOGGER.info("Processing triggering event. Ref: '{}', Type: '{}'", refName, updateType);
    return true;
  }

  private SourceRepository setupRepository(RepositoryInfo repoInfo) {
    String repoDir = Paths.get(baseCloningDir, repoInfo.id()).toString();
    return new SourceRepository(repoInfo.id(), repoDir, localOriginDir, gcpProject, udmiNamespace);
  }

  private void runRegistrar(String udmiModelPath, String spreadsheetId,
      SourceRepository repository) {
    Runnable registrarTask = createRegistrarTask(udmiModelPath, repository);

    if (spreadsheetId != null) {
      String timestamp = new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date());
      String outputTabTitle = String.format("registrar_log.%s", timestamp);

      try (SheetsOutputStream stream = new SheetsOutputStream(SERVICE_NAME, spreadsheetId,
          outputTabTitle)) {
        LOGGER.info("Starting registrar run with logging to Spreadsheet {}", spreadsheetId);
        executeWithSheetLogging(stream, registrarTask);
      } catch (IOException e) {
        LOGGER.error("Failed to create spreadsheet stream. Falling back to console logging.", e);
        executeWithSheetLogging(null, registrarTask);
      } catch (Exception e) {
        LOGGER.error("Error during sheet-logged registrar execution", e);
      }
    } else {
      LOGGER.info("Starting registrar run with console logging only.");
      executeWithSheetLogging(null, registrarTask);
    }
  }

  /**
   * Creates a runnable task for executing the main registrar process.
   */
  private Runnable createRegistrarTask(String udmiModelPath, SourceRepository repository) {
    return () -> {
      try {
        List<String> argList = List.of(udmiModelPath, registrarTarget);
        new Registrar().processArgs(argList).execute();
        pushRegistrationSummary(repository);
      } catch (Exception e) {
        throw new RuntimeException("Registrar execution failed", e);
      }
    };
  }

  private void pushRegistrationSummary(SourceRepository repository) {
    LOGGER.info("Adding registration summary to the source repo...");
    Path triggerFilePath = Paths.get(repository.getDirectory(), "udmi", TRIGGER_FILE_NAME);
    File triggerFile = new File(triggerFilePath.toUri());
    if (triggerFile.delete() && repository.stageRemove("udmi/" + TRIGGER_FILE_NAME)) {
      if (!repository.commitAndPush("Add registration summary")) {
        LOGGER.error("Could not add registration summary");
      }
    } else {
      LOGGER.error("Could not delete trigger file, will not push registration summary!");
    }
  }

  private boolean checkTriggerFileExists(String udmiModelPath) {
    Path triggerFilePath = Paths.get(udmiModelPath, TRIGGER_FILE_NAME);
    return Files.exists(triggerFilePath);
  }

  private Optional<String> getSpreadsheetId(String udmiModelPath) {
    Path triggerFilePath = Paths.get(udmiModelPath, TRIGGER_FILE_NAME);
    try {
      String jsonContent = Files.readString(triggerFilePath);
      if (jsonContent.isBlank()) {
        LOGGER.warn("Trigger file is empty: {}", triggerFilePath);
        return Optional.empty();
      }
      Map<String, String> triggerConfig = OBJECT_MAPPER.readValue(jsonContent,
          new TypeReference<>() {
          });
      String spreadsheetId = triggerConfig.get(SPREADSHEET_ID_KEY);

      if (spreadsheetId == null || spreadsheetId.isEmpty()) {
        LOGGER.warn("Spreadsheet ID key '{}' not found in {}", SPREADSHEET_ID_KEY, triggerFilePath);
        return Optional.empty();
      }

      LOGGER.info("Extracted spreadsheetId '{}' from trigger file.", spreadsheetId);
      return Optional.of(spreadsheetId);

    } catch (IOException e) {
      LOGGER.error("Failed to read or parse trigger file: {}", triggerFilePath, e);
      return Optional.empty();
    }
  }

  private record RepositoryInfo(String id, String directory) {

    RepositoryInfo(Map<String, Object> messageData) {
      this(extractRepoId(messageData), null);
    }

    private static String extractRepoId(Map<String, Object> data) {
      String repositoryName = (String) data.get(REPO_NAME_KEY);
      if (repositoryName == null || repositoryName.isEmpty()) {
        throw new IllegalArgumentException("Repository name not found in message data.");
      }
      String[] parts = repositoryName.split("/");
      return parts[parts.length - 1];
    }
  }
}
