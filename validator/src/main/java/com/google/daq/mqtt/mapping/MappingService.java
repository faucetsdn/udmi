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

public class MappingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MappingService.class);
  private static final String SERVICE_NAME = "MappingService";
  private SourceRepository sourceRepository;
  private final int REQUIRED_ARGUMENT_LENGTH = 5;

  private final String registryId;
  private final String projectId;
  private final String baseCloningDirectory;
  private final String projectSpec;
  private final String mappingFamily;
  private static final String IMPORT_BRANCH = "main";
  public static void main(String[] args) {
    MappingService mappingService = new MappingService(args);
    mappingService.initialize();

    mappingService.cloneRepo();
    mappingService.process();
  }

  public MappingService(String[] args) {
    if (args.length != REQUIRED_ARGUMENT_LENGTH) {
      System.err.printf("Invalid arguments provided");
    }

    this.registryId = args[0];
    this.projectId = args[1];
    this.baseCloningDirectory = args[2];
    this.projectSpec = args[3];
    this.mappingFamily = args[4];
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

    String udmiModelPath = Paths.get(sourceRepository.getDirectory(), "udmi").toString();
    (new Registrar()).processArgs(new ArrayList<>(List.of(udmiModelPath, projectSpec))).execute();
    MappingAgent mappingAgent = new MappingAgent(new ArrayList<>(List.of(udmiModelPath, projectSpec)));

    mappingAgent.process(new ArrayList<>(List.of("map", mappingFamily)));

    System.err.printf("Committing and pushing changes to branch {}", exportBranch);
    if (!sourceRepository.commitAndPush("Merge changes from MappingAgent source")) {
      throw new RuntimeException("Unable to commit and push changes to branch " + exportBranch);
    }
    System.err.printf("Export operation complete.");
  }

}
