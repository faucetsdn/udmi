package com.google.bos.iot.core.reconcile;

import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.REF_UPDATE_EVENT_FORMAT;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.extractRepoId;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.getValueFromMap;
import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.parseSourceRepoMessageData;
import static com.google.udmi.util.SourceRepository.AUTHOR_KEY;

import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.AbstractPollingService;
import com.google.udmi.util.SourceRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes newly created proposal branches in the source repository and creates pull requests
 * for them.
 */
public class ReconcilerService extends AbstractPollingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReconcilerService.class);
  private static final String SERVICE_NAME = "ReconcilerService";
  private static final String SUBSCRIPTION_SUFFIX = "udmi_reconciler_source_repo_updates";
  private static final String TRIGGER_BRANCH = "proposal";
  private static final String DEFAULT_TARGET_BRANCH = "main";

  private static final String REF_NAME_KEY_REGEX = String.format(REF_UPDATE_EVENT_FORMAT,
      TRIGGER_BRANCH + ".*", "refName");

  private static final String UPDATE_TYPE_KEY = "refUpdateEvent.refUpdates.%s.updateType";


  /**
   * Primary constructor for the Reconciler service.
   *
   * @param projectTarget Target project specifier
   * @param siteModelBaseDir Base directory for cloning site model Git repositories.
   * @param localOriginDir Optional directory for local git origins
   */
  public ReconcilerService(String projectTarget, String siteModelBaseDir, String localOriginDir) {
    super(SERVICE_NAME, SUBSCRIPTION_SUFFIX, projectTarget, siteModelBaseDir, localOriginDir);
    LOGGER.info("Starting Reconciler Service for project {}, cloning to {}", projectTarget,
        siteModelBaseDir);
  }

  /**
   * Main entry point for the application.
   */
  public static void main(String[] args) {
    if (args.length < 2 || args.length > 3) {
      LOGGER.error(
          "Usage: ReconcilerService <projectTarget> <siteModelCloneDir> [<localOriginDir>]");
      System.exit(1);
    }

    String projectTarget = args[0];
    String siteModelCloneDir = args[1];
    String localOriginDir = (args.length == 3) ? args[2] : null;

    ReconcilerService service = new ReconcilerService(projectTarget, siteModelCloneDir,
        localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for {}", SERVICE_NAME);
      service.stop();
    }));
  }

  @Override
  protected void handleMessage(PubsubMessage message) throws Exception {
    Map<String, Object> messageData = parseSourceRepoMessageData(message);
    String refName = getNewProposalBranch(messageData);

    if (refName != null) {
      String repoId = extractRepoId(messageData);
      String branch = refName.substring("refs/heads/".length());
      LOGGER.info("Processing new proposal for repository {}, branch {}", repoId, branch);

      SourceRepository repository = initRepository(repoId);
      if (repository.clone(branch)) {
        Map<String, Object> triggerKeys = repository.getRegistrarTriggerConfig();

        String author = getValueFromMap(triggerKeys, AUTHOR_KEY).orElse(null);
        if (!repository.createPullRequest("Proposal " + branch, null, branch, "main", author)) {
          LOGGER.error(
              "Could not create pull request! "
                  + "Details: \\{ sourceBranch: {}, targetBranch: {}, author: {} \\}",
              branch, DEFAULT_TARGET_BRANCH, author);
        }
        repository.delete();
      } else {
        LOGGER.error("Could not clone repository! PR message was not published!");
      }
    }
  }

  private String getNewProposalBranch(Map<String, Object> messageData) {
    for (String key : messageData.keySet()) {
      if (key.matches(REF_NAME_KEY_REGEX)) {
        String refName = messageData.get(key).toString();
        String updateType = messageData.getOrDefault(String.format(UPDATE_TYPE_KEY, refName), "")
            .toString();
        return "CREATE".equals(updateType) ? refName : null;
      }
    }
    return null;
  }
}
