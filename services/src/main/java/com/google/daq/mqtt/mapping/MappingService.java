package com.google.daq.mqtt.mapping;

import static com.google.bos.iot.core.reconcile.SourceRepoMessageUtils.parseSourceRepoMessageDataToRawMap;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;

import com.google.daq.mqtt.registrar.Registrar;
import com.google.pubsub.v1.PubsubMessage;
import com.google.udmi.util.AbstractPollingService;
import com.google.udmi.util.SourceRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapping Service that
 * fetches the site mode, internally calls registrar, then the mapping process
 * and then pushes the changes in the different discovery branch.
 */
public class MappingService extends AbstractPollingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MappingService.class);
  private static final String SERVICE_NAME = "MappingService";
  private static final String SUBSCRIPTION_SUFFIX = "udmi_target_mapping_updates";
  private static final String EVENT_NUMBER_FIELD = "event_no";
  private static final String FAMILY_FIELD = "family";
  private static final String REGISTRY_ID_FIELD = "deviceRegistryId";
  private static final String DISCOVERY_NODE_DEVICE_ID_FIELD = "deviceId";
  private static final String DISCOVERY_TIMESTAMP = "generation";
  private static final String TRIGGER_BRANCH = "discovery";
  private static final String TRIGGER_BRANCH_IoT = "discovery_iot";
  private static final String DEFAULT_TARGET_BRANCH = "main";
  private static final String GATEWAY_ID_FIELD = "gatewayId";
  private final String projectSpec;

  /**
   * Main entry point for the application.
   */
  public static void main(String[] args) {
    if (args.length < 3 || args.length > 4) {
      LOGGER.error(
          "Usage: MappingService <projectTarget> <registrarTarget> <siteModelCloneDir> "
              + "[<localOriginDir>]");
      System.exit(1);
    }

    String projectTarget = args[0];
    String projectSpec = args[1];
    String siteModelCloneDir = args[2];
    String localOriginDir = (args.length == 4) && isNotEmpty(args[3]) ? args[3] : null;

    MappingService service = new MappingService(projectTarget, projectSpec, siteModelCloneDir,
        localOriginDir);
    service.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown hook triggered for {}", SERVICE_NAME);
      service.stop();
    }));
  }

  /**
   * Primary constructor for the MappingService.
   *
   * @param projectTarget Target project specifier, e.g., "//pubsub/gcp-project/udmi-namespace"
   * @param siteModelBaseDir Base directory for cloning site model Git repositories.
   * @param localOriginDir Optional directory for local git origins (for testing).
   */
  public MappingService(String projectTarget, String projectSpec, String siteModelBaseDir,
      String localOriginDir) {
    super(SERVICE_NAME, SUBSCRIPTION_SUFFIX, projectTarget, siteModelBaseDir, localOriginDir);
    this.projectSpec = projectSpec;
    LOGGER.info("Starting Mapping Service for project {}, cloning to {}", projectTarget,
        siteModelBaseDir);
  }

  @Override
  protected void handleMessage(PubsubMessage message) throws Exception {
    Map<String, Object> messageData = parseSourceRepoMessageDataToRawMap(message);
    Integer eventNumber = (Integer) messageData.getOrDefault(EVENT_NUMBER_FIELD, 0);
    if (eventNumber < 0) {
      processDiscoveryCompleteEvent(message, messageData);
    } else if (message.getAttributesMap().containsKey(GATEWAY_ID_FIELD)) {
      processRegistryFetchFromClearBlade(message, messageData);
    }
  }

  private void processDiscoveryCompleteEvent(PubsubMessage message, Map<String, Object> messageData)
      throws Exception {
    LOGGER.info(
        "Received event no. from message: {}", messageData.getOrDefault(EVENT_NUMBER_FIELD, 0));
    String mappingFamily = (String) messageData.getOrDefault(FAMILY_FIELD, "");
    String registryId = message.getAttributesOrDefault(REGISTRY_ID_FIELD, "");

    if (registryId.isEmpty()) {
      LOGGER.error("Registry Id not found for the message.");
      return;
    }

    Instant now = Instant.now();
    String currentTimestamp = DateTimeFormatter.ISO_INSTANT.format(now);
    String discoveryTimestamp =
        (String) messageData.getOrDefault(DISCOVERY_TIMESTAMP, currentTimestamp);
    String discoveryNodeDeviceId =
        message.getAttributesOrDefault(DISCOVERY_NODE_DEVICE_ID_FIELD, "");
    if (discoveryNodeDeviceId.isEmpty()) {
      LOGGER.error("Discovery Node device Id not found for the message received.");
      return;
    }
    LOGGER.info(
        "Starting Mapping process for registry: {}, family: {}, discoverNode deviceId:" + " {}",
        registryId, mappingFamily, discoveryNodeDeviceId);

    withRepository(registryId, TRIGGER_BRANCH,
        repository -> {
          String udmiModelPath = repository.getUdmiModelPath();
          (new Registrar())
              .processArgs(new ArrayList<>(List.of(udmiModelPath, projectSpec)))
              .execute();
          MappingAgent mappingAgent =
              new MappingAgent(new ArrayList<>(List.of(udmiModelPath, projectSpec)));
          mappingAgent.processMapping(
              new ArrayList<>(List.of(discoveryNodeDeviceId, discoveryTimestamp, mappingFamily)));
        });
  }

  private void processRegistryFetchFromClearBlade(PubsubMessage message, Map<String, Object> messageData)
      throws Exception {
    String registryId = message.getAttributesOrDefault(REGISTRY_ID_FIELD, "");

    if (registryId.isEmpty()) {
      LOGGER.error("Registry Id not found for the message.");
      return;
    }
    LOGGER.info("Starting Stitching process for registry: {}", registryId);

    withRepository(registryId, TRIGGER_BRANCH_IoT,
        repository -> {
          Object devicesObject = messageData.get("devices");
          if (!(devicesObject instanceof Map)) {
            LOGGER.warn("Skipping Processing : Received message without a valid 'devices' map.");
            return;
          }
          String udmiModelPath = repository.getUdmiModelPath();
          @SuppressWarnings("unchecked")
          Map<String, Map<String, Object>> devices =
              (Map<String, Map<String, Object>>) devicesObject;
          MappingAgent mappingAgent =
              new MappingAgent(new ArrayList<>(List.of(udmiModelPath, projectSpec)));

          mappingAgent.stitchProperties(devices);
        });
  }

  private void withRepository(String registryId, String branchPrefix, RepositoryConsumer work)
      throws Exception {
    SourceRepository repository = initRepository(registryId);
    if (repository.clone(DEFAULT_TARGET_BRANCH)) {
      try {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"));

        String exportBranch = String.format("%s/%s/%s", branchPrefix, SERVICE_NAME, timestamp);
        if (!repository.checkoutNewBranch(exportBranch)) {
          throw new RuntimeException("Unable to create and checkout export branch " + exportBranch);
        }

        work.accept(repository);

        LOGGER.info("Committing and pushing changes to branch {}", exportBranch);
        if (!repository.commitAndPush("Merge changes from source: MappingService")) {
          throw new RuntimeException("Unable to commit and push changes to branch " + exportBranch);
        }
        LOGGER.info("Export operation complete.");
      } finally {
        repository.delete();
      }
    } else {
      LOGGER.error("Could not clone repository! PR message was not published!");
    }
  }

  @FunctionalInterface
  private interface RepositoryConsumer {
    void accept(SourceRepository repository) throws Exception;
  }
}