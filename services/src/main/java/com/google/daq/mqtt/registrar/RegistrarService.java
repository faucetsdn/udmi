package com.google.daq.mqtt.registrar;

import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.REF_UPDATE_EVENT_FORMAT;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.extractRepoId;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.getValueFromMap;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.parseSourceRepoMessageData;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static com.google.udmi.util.SheetsOutputStream.executeWithSheetLogging;
import static com.google.udmi.util.SourceRepository.AUTHOR_KEY;
import static com.google.udmi.util.SourceRepository.SPREADSHEET_ID_KEY;

import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.AbstractPollingService;
import com.google.udmi.util.SheetsOutputStream;
import com.google.udmi.util.SourceRepository;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to run registrar triggered by source repository updates.
 */
public class RegistrarService extends AbstractPollingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarService.class);
  private static final String SERVICE_NAME = "RegistrarService";
  private static final String SUBSCRIPTION_SUFFIX = "udmi_registrar_source_repo_updates";
  private static final String TRIGGER_BRANCH = "main";
  private static final String REF_NAME_KEY = String.format(REF_UPDATE_EVENT_FORMAT, TRIGGER_BRANCH,
      "refName");
  private static final String UPDATE_TYPE_KEY = String.format(REF_UPDATE_EVENT_FORMAT,
      TRIGGER_BRANCH, "updateType");
  private static final Set<String> TRIGGERING_UPDATE_TYPES = Set.of("CREATE",
      "UPDATE_FAST_FORWARD");
  private static final String OPTIMIZE_ARG = "-o";
  private final String registrarTarget;

  /**
   * Constructs a RegistrarService instance.
   *
   * @param projectTarget Contains information about where the service receives its triggers from.
   *     e.g. //pubsub/bos-platform-dev
   * @param registrarTarget project spec for the registrar e.g. //gbos/bos-platform-dev
   * @param siteModelBaseDir Base directory for cloning site models.
   * @param localOriginDir Optional local directory to use as a git origin.
   */
  public RegistrarService(String projectTarget, String registrarTarget, String siteModelBaseDir,
      String localOriginDir) {
    super(SERVICE_NAME, SUBSCRIPTION_SUFFIX, projectTarget, siteModelBaseDir, localOriginDir);
    this.registrarTarget = registrarTarget;
    LOGGER.info("Starting Registrar Service for project {}, cloning to {}", projectTarget,
        siteModelBaseDir);
  }

  /**
   * Constructs a RegistrarService instance.
   *
   * @param projectTarget Contains information about where the service receives its triggers from.
   *     e.g. //pubsub/bos-platform-dev
   * @param siteModelBaseDir Base directory for cloning site models.
   * @param localOriginDir Optional local directory to use as a git origin.
   */
  public RegistrarService(String projectTarget, String siteModelBaseDir, String localOriginDir) {
    this(projectTarget, projectTarget, siteModelBaseDir, localOriginDir);
  }

  /**
   * Main entry point for the application.
   */
  public static void main(String[] args) {
    if (args.length < 3 || args.length > 4) {
      System.err.println(
          "Usage: RegistrarService <projectTarget> <registrarTarget> <siteModelCloneDir> "
              + "[<localOriginDir>]");
      System.exit(1);
    }

    String projectTarget = args[0];
    String registrarTarget = args[1];
    String siteModelCloneDir = args[2];
    String localOriginDir = (args.length == 4) && isNotEmpty(args[3]) ? args[3] : null;

    RegistrarService service = new RegistrarService(projectTarget, registrarTarget,
        siteModelCloneDir, localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for {}", SERVICE_NAME);
      service.stop();
    }));
  }

  @Override
  protected void handleMessage(PubsubMessage message) throws Exception {
    Map<String, Object> messageData = parseSourceRepoMessageData(message);
    if (!isTriggeringEvent(messageData)) {
      return;
    }

    String repoId = extractRepoId(messageData);
    SourceRepository repository = initRepository(repoId);

    if (repository.clone(TRIGGER_BRANCH)) {
      Map<String, Object> triggerConfig;
      if ((triggerConfig = repository.getRegistrarTriggerConfig()) != null) {
        String spreadsheetId = getValueFromMap(triggerConfig, SPREADSHEET_ID_KEY).orElse(null);
        String author = getValueFromMap(triggerConfig, AUTHOR_KEY).orElse(null);
        runRegistrar(spreadsheetId, repository, author);
      } else {
        LOGGER.info("Skipping. Trigger file does not exist.");
      }
      repository.delete();
    } else {
      LOGGER.error("Failed to clone repository {}", repoId);
    }
  }

  private boolean isTriggeringEvent(Map<String, Object> messageData) {
    String refName = (String) messageData.getOrDefault(REF_NAME_KEY, "");
    String updateType = (String) messageData.getOrDefault(UPDATE_TYPE_KEY, "");

    if (refName.isEmpty() || !TRIGGERING_UPDATE_TYPES.contains(updateType)) {
      return false;
    }
    LOGGER.info("Processing triggering event. Ref: '{}', Type: '{}'", refName, updateType);
    return true;
  }

  private void runRegistrar(String spreadsheetId, SourceRepository repository, String author) {
    Runnable registrarTask = createRegistrarTask(repository, author);

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
  private Runnable createRegistrarTask(SourceRepository repository, String author) {
    return () -> {
      try {
        List<String> argList = List.of(repository.getUdmiModelPath(), registrarTarget,
            OPTIMIZE_ARG);
        new Registrar().processArgs(argList).execute();
        pushRegistrationSummary(repository, author);
      } catch (Exception e) {
        throw new RuntimeException("Registrar execution failed", e);
      }
    };
  }

  private void pushRegistrationSummary(SourceRepository repository, String author) {
    LOGGER.info("Adding registration summary to the source repo...");
    File triggerFile = new File(repository.getRegistrarTriggerFilePath());
    if (triggerFile.delete() && repository.stageRemove(repository.getRegistrarTriggerFilePath())) {
      if (!repository.commitAndPush("Registrar run summary for changes by " + author)) {
        LOGGER.error("Could not add registration summary");
      }
    } else {
      LOGGER.error("Could not delete trigger file, will not push registration summary!");
    }
  }

}
