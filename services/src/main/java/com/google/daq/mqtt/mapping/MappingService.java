package com.google.daq.mqtt.mapping;


import com.google.daq.mqtt.registrar.Registrar;
import com.google.udmi.util.SourceRepository;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapping Service: which currently runs from command line
 * fetches the site mode, internally calls registrar, then the mapping process
 * and then pushes the changes in the different proposal branch.
 */
public class MappingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MappingService.class);
  private static final String SERVICE_NAME = "MappingService";
  private SourceRepository sourceRepository;
  private final int requiredArgumentLength = 6;
  private final String registryId;
  private final String projectId;
  private final String baseCloningDirectory;
  private final String projectSpec;
  private final String mappingFamily;
  private final String discoveryNodeDeviceId;
  private static final String IMPORT_BRANCH = "main";

  /**
   * Mapping Service for mapping the discovery results
   * accepts commandlineArguments as
   * registryId projectId baseCloningDirectory projectSpec mappingFamily discoveryNodeDeviceId
   * registryId: e.g. ZZ-TRI-FECTA
   * projectId: GCP project id
   * baseCloningDirectory: local path for the repository
   * projectSpec: eg. //mqtt/localhost
   * mappingFamily:vendor/bacnet
   * discoveryNodeDeviceId: discoveryNode deviceId e.g. GAT-123
   */
  public static void main(String[] args) {
    MappingService mappingService = new MappingService(args);
    mappingService.initialize();

    mappingService.cloneRepo();
    mappingService.process();
  }

  /**
   * Initializes new Mapping Service.
   *
   * @param args commandline args as:site_path project_spec
   */
  public MappingService(String[] args) {
    if (args.length != requiredArgumentLength) {
      throw new IllegalArgumentException("Invalid arguments provided");
    }

    this.registryId = args[0];
    this.projectId = args[1];
    this.baseCloningDirectory = args[2];
    this.projectSpec = args[3];
    this.mappingFamily = args[4];
    this.discoveryNodeDeviceId = args[5];
  }

  private void cloneRepo() {
    if (!sourceRepository.clone(IMPORT_BRANCH)) {
      throw new RuntimeException("Could not clone repository " + registryId);
    }
  }

  private void initialize() {
    String repoCloneDir = Paths.get(baseCloningDirectory, registryId).toString();
    sourceRepository = new SourceRepository(registryId, repoCloneDir, null, projectId, null);

  }

  private void process() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"));

    String exportBranch = String.format("proposal/%s/%s", SERVICE_NAME, timestamp);
    if (!sourceRepository.checkoutNewBranch(exportBranch)) {
      throw new RuntimeException("Unable to create and checkout export branch " + exportBranch);
    }

    String udmiModelPath = sourceRepository.getUdmiModelPath();
    (new Registrar()).processArgs(new ArrayList<>(List.of(udmiModelPath, projectSpec))).execute();
    MappingAgent mappingAgent = new MappingAgent(new ArrayList<>(
        List.of(udmiModelPath, projectSpec)));
    mappingAgent.processMapping(new ArrayList<>(List.of(discoveryNodeDeviceId, mappingFamily)));

    LOGGER.info("Committing and pushing changes to branch {}", exportBranch);
    if (!sourceRepository.commitAndPush("Merge changes from source: MappingService")) {
      throw new RuntimeException("Unable to commit and push changes to branch " + exportBranch);
    }
    LOGGER.info("Export operation complete.");
  }

}