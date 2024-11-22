package com.google.daq.mqtt.registrar;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_ROOT;
import static com.google.udmi.util.Common.CLOUD_VERSION_KEY;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.SEC_TO_MS;
import static com.google.udmi.util.Common.SITE_METADATA_KEY;
import static com.google.udmi.util.Common.UDMI_VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.JSON_SUFFIX;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static com.google.udmi.util.JsonUtil.loadFile;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PREFIX;
import static com.google.udmi.util.SiteModel.DEVICES_DIR;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static java.lang.Math.ceil;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.PubSubPusher;
import com.google.daq.mqtt.util.ValidationError;
import com.google.udmi.util.CommandLineOption;
import com.google.udmi.util.CommandLineProcessor;
import com.google.udmi.util.Common;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Metadata;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.SiteMetadata;

/**
 * Validate devices' static metadata and register them in the cloud.
 */
public class Registrar {

  public static final String SCHEMA_BASE_PATH = "schema";
  public static final Joiner JOIN_CSV = Joiner.on(", ");
  public static final File BASE_DIR = new File(".");
  static final String ENVELOPE_SCHEMA_JSON = "envelope.json";
  static final String METADATA_SCHEMA_JSON = "metadata.json";
  static final String DEVICE_ERRORS_MAP = "errors.map";
  private static final String SCHEMA_SUFFIX = ".json";
  private static final String SCHEMA_NAME = "UDMI";
  private static final String SWARM_SUBFOLDER = "swarm";
  private static final String CONFIG_SUB_TYPE = "config";
  private static final String MODEL_SUB_TYPE = "model";
  private static final boolean DEFAULT_BLOCK_UNKNOWN = false;
  private static final int EACH_ITEM_TIMEOUT_SEC = 60;
  private static final Map<String, Class<? extends Summarizer>> SUMMARIZERS = ImmutableMap.of(
      ".json", Summarizer.JsonSummarizer.class,
      ".csv", Summarizer.CsvSummarizer.class);
  private static final String TOOL_NAME = "registrar";
  private static final long DELETE_FLUSH_DELAY_MS = 10 * SEC_TO_MS;
  public static final String REGISTRAR_TOOL_NAME = "registrar";
  private final Map<String, JsonSchema> schemas = new HashMap<>();
  private final String generation = getGenerationString();
  private final Set<Summarizer> summarizers = new HashSet<>();
  private final CommandLineProcessor commandLineProcessor = new CommandLineProcessor(this);
  private CloudIotManager cloudIotManager;
  private File schemaBase;
  private PubSubPusher updatePusher;
  private PubSubPusher feedPusher;
  private Map<String, LocalDevice> localDevices;
  private Map<String, CloudModel> extraDevices;
  private String projectId;
  private boolean updateCloudIoT;
  private Duration idleLimit;
  private Metadata siteDefaults;
  private Map<String, CloudModel> cloudModels;
  private Map<String, Object> lastErrorSummary;
  private boolean doValidate = true;
  private List<String> deviceList;
  private Boolean blockUnknown;
  private File siteDir;
  private boolean deleteDevices;
  private boolean expungeDevices;
  private boolean metadataModelOut;
  private int createRegistries = -1;
  private int runnerThreads = 5;
  private ExecutorService executor;
  private List<Future<?>> executing = new ArrayList<>();
  private SiteModel siteModel;
  private boolean queryOnly;

  /**
   * Main entry point for registrar.
   */
  public static void main(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    try {
      new Registrar().processArgs(argList).execute();
    } catch (Exception e) {
      System.err.println("Exception in main: " + friendlyStackTrace(e));
      e.printStackTrace();
      System.exit(Common.EXIT_CODE_ERROR);
    }

    // Force exist because PubSub Subscriber in PubSubReflector does not shut down properly.
    safeSleep(2000);
    System.exit(0);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static Map<String, String> getErrorKeyMap(Map<String, Object> resultMap,
      String errorKey) {
    return (Map<String, String>) resultMap.computeIfAbsent(errorKey, cat -> new TreeMap<>());
  }

  @SuppressWarnings("unchecked")
  private static int getErrorSummaryDetail(Object value) {
    return ((Map<String, Object>) value).size();
  }

  private static boolean isNotGateway(CloudModel device) {
    return device.resource_type != Resource_type.GATEWAY;
  }

  private static CloudModel augmentModel(CloudModel cloudModel) {
    cloudModel.operation = ifTrueGet(cloudModel.blocked, Operation.BLOCK, Operation.ALLOW);
    return cloudModel;
  }

  private static String defaultToolRoot() {
    return UDMI_ROOT.getAbsolutePath();
  }

  Registrar processArgs(List<String> argListRaw) {
    List<String> argList = new ArrayList<>(argListRaw);
    if (argList.size() == 1 && new File(argList.get(0)).isDirectory()) {
      // Add implicit NO_SITE site spec for local-only site model processing.
      argList.add(NO_SITE);
    }
    try {
      siteModel = new SiteModel(TOOL_NAME, argList);
    } catch (IllegalArgumentException e) {
      commandLineProcessor.showUsage(e.getMessage());
    }
    processProfile(siteModel.getExecutionConfiguration());
    return postProcessArgs(argList);
  }

  Registrar postProcessArgs(List<String> argList) {
    List<String> remainingArgs = commandLineProcessor.processArgs(argList);
    ifNotNullThen(remainingArgs, this::setDeviceList);
    requireNonNull(siteModel, "siteModel not defined");
    return this;
  }

  @CommandLineOption(short_form = "-q", description = "Query only")
  private void setQueryOnly() {
    this.queryOnly = true;
    this.updateCloudIoT = false;
  }

  @CommandLineOption(short_form = "-b", description = "Block unknown devices")
  private void setBlockUnknown() {
    blockUnknown = true;
  }

  @CommandLineOption(short_form = "-c", arg_name = "count", description = "Create registries")
  private void setCreateRegistries(String registrySpec) {
    try {
      createRegistries = Integer.parseInt(registrySpec);
      if (createRegistries < 0) {
        throw new IllegalArgumentException("Negative value not allowed");
      }
    } catch (Exception e) {
      throw new RuntimeException("Create registries spec should be >= 0: " + registrySpec, e);
    }
  }

  @CommandLineOption(short_form = "-n", arg_name = "threads",
      description = "Set number of runner threads")
  private void setRunnerThreads(String argValue) {
    runnerThreads = Integer.parseInt(argValue);
  }

  @CommandLineOption(short_form = "-d", description = "Delete (known) devices")
  private void setDeleteDevices() {
    checkNotNull(projectId, "delete devices specified with no target project");
    this.deleteDevices = true;
    this.updateCloudIoT = true;
  }

  @CommandLineOption(short_form = "-x", description = "Expunge (unknown) devices")
  private void setExpungeDevices() {
    checkNotNull(projectId, "expunge devices specified with no target project");
    this.expungeDevices = true;
    this.updateCloudIoT = true;
  }

  @CommandLineOption(short_form = "-e", arg_name = "suffix", description = "Set registry suffix")
  private void setRegistrySuffix(String suffix) {
    siteModel.getExecutionConfiguration().registry_suffix = suffix;
  }

  private void processProfile(ExecutionConfiguration config) {
    config.site_model = new File(siteModel.getSitePath()).getAbsolutePath();
    setSitePath(config.site_model);
    setProjectId(config.project_id);
    if (config.project_id != null) {
      updateCloudIoT = true;
    }
  }

  void execute() {
    execute(null);
  }

  @VisibleForTesting
  protected void execute(Runnable modelMunger) {
    try {
      if (projectId != null) {
        initializeCloudProject();
      }
      if (schemaBase == null) {
        // Use the proper (relative) tool root directory for unit tests.
        setToolRoot(MOCK_PROJECT.equals(projectId) ? ".." : defaultToolRoot());
      }
      loadSiteDefaults();
      if (createRegistries >= 0) {
        createRegistries();
      } else {
        processSiteMetadata();
        processDevices(modelMunger);
      }
      writeErrors();
    } catch (ExceptionMap em) {
      ExceptionMap.format(em).write(System.err);
      throw new RuntimeException("mapped exceptions", em);
    } catch (Exception ex) {
      throw new RuntimeException("main exception", ex);
    } finally {
      shutdown();
    }
  }

  @VisibleForTesting
  protected Map<String, LocalDevice> getLocalDevices() {
    return localDevices;
  }

  private void processSiteMetadata() {
    ifTrueThen(updateCloudIoT,
        () -> cloudIotManager.updateRegistry(getSiteMetadata(), Operation.UPDATE));
  }

  private SiteMetadata getSiteMetadata() {
    SiteMetadata siteMetadata = ofNullable(siteModel.loadSiteMetadata()).orElseGet(
        SiteMetadata::new);
    siteMetadata.name = ofNullable(siteMetadata.name).orElse(siteModel.getSiteName());
    return siteMetadata;
  }

  private void createRegistries() {
    if (createRegistries == 0) {
      System.err.printf("Creating base registry...%n");
      createRegistrySuffix("");
    } else {
      System.err.printf("Creating %d registries...%n", createRegistries);
      String createFormat = "_%0" + format("%d", createRegistries - 1).length() + "d";
      for (int i = 0; i < createRegistries; i++) {
        createRegistrySuffix(format(createFormat, i));
      }
    }
  }

  private void createRegistrySuffix(String suffix) {
    String registry = cloudIotManager.createRegistry(suffix);
    System.err.println("Created registry " + registry);
  }

  @CommandLineOption(short_form = "-m", description = "Initial metadata model out")
  private void setMetadataModelOut() {
    metadataModelOut = true;
  }

  @CommandLineOption(short_form = "-l", arg_name = "timeout", description = "Set idle limit")
  private void setIdleLimit(String option) {
    idleLimit = Duration.parse("PT" + option);
    System.err.println("Limiting devices to duration " + idleLimit.toSeconds() + "s");
  }

  @CommandLineOption(short_form = "-t", description = "Do not validate metadata")
  private void setValidateMetadata() {
    this.doValidate = false;
  }

  private void setDeviceList(List<String> deviceList) {
    this.deviceList = deviceList;
    blockUnknown = false;
  }

  @CommandLineOption(short_form = "-f", arg_name = "topic", description = "Set PubSub feed topic")
  private void setFeedTopic(String feedTopic) {
    System.err.println("Sending device feed to topic " + feedTopic);
    feedPusher = new PubSubPusher(projectId, feedTopic);
    System.err.println("Checking subscription for existing messages...");
    if (!feedPusher.isEmpty()) {
      throw new RuntimeException("Feed is not empty, do you have enough pullers?");
    }
  }

  protected Map<String, Object> getLastErrorSummary() {
    return lastErrorSummary;
  }

  private void writeErrors() throws RuntimeException {
    Map<String, Object> errorSummary = new TreeMap<>();
    if (localDevices == null) {
      return;
    }
    localDevices.values().forEach(LocalDevice::writeErrors);
    localDevices.values().forEach(device -> {
      Set<Entry<String, ErrorTree>> entries = getDeviceErrorEntries(device);
      if (entries.isEmpty()) {
        getErrorKeyMap(errorSummary, "Clean")
            .put(device.getDeviceId(), device.getNormalizedTimestamp());
      } else {
        entries
            .forEach(
                error -> getErrorKeyMap(errorSummary, error.getKey())
                    .put(device.getDeviceId(), error.getValue().message));
      }
    });
    if (extraDevices != null && !extraDevices.isEmpty()) {
      errorSummary.put("Extra", extraDevices.entrySet().stream()
          .collect(
              Collectors.toMap(Entry::getKey, entry -> entry.getValue().operation.toString())));
    }
    System.err.println("\nSummary:");
    errorSummary.forEach((key, value) -> System.err.println(
        "  Device " + key + ": " + getErrorSummaryDetail(value)));
    System.err.println("Out of " + localDevices.size() + " total.");
    // WARNING! entries inserted into `errorSummary` ABOVE this comment must have a map value ^^^^^^
    errorSummary.put(CLOUD_VERSION_KEY, getCloudVersionInfo());
    lastErrorSummary = errorSummary;
    errorSummary.put(UDMI_VERSION_KEY, Common.getUdmiVersion());
    ifNotNullThen(siteModel.siteMetadataExceptionMap,
        exceptions -> errorSummary.put(SITE_METADATA_KEY, exceptions.stream()
            .map(Entry::getValue)
            .map(Exception::getMessage)
            .collect(Collectors.toList())));
    summarizers.forEach(summarizer -> {
      File outFile = summarizer.outFile;
      try {
        summarizer.summarize(localDevices, errorSummary, extraDevices);
        System.err.println("Registration summary available in " + outFile.getAbsolutePath());
      } catch (Exception e) {
        throw new RuntimeException("While summarizing output to " + outFile.getAbsolutePath(), e);
      }
    });
  }

  private Set<Entry<String, ErrorTree>> getDeviceErrorEntries(LocalDevice device) {
    return device.getTreeChildren();
  }

  private SetupUdmiConfig getCloudVersionInfo() {
    return ifNotNullGet(cloudIotManager, CloudIotManager::getVersionInformation);
  }

  @CommandLineOption(short_form = "-s", arg_name = "site_path", description = "Set site path")
  private void setSitePath(String sitePath) {
    checkNotNull(SCHEMA_NAME, "schemaName not set yet");
    siteDir = new File(sitePath);
    siteModel = ofNullable(siteModel).orElseGet(() -> new SiteModel(sitePath));
    File summaryBase = new File(siteDir, SiteModel.REGISTRATION_SUMMARY_BASE);
    File parentFile = summaryBase.getParentFile();
    if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
      throw new IllegalStateException("Could not create directory " + parentFile.getAbsolutePath());
    }

    summarizers.addAll(SUMMARIZERS.entrySet().stream().map(factory -> {
      try {
        Summarizer summarizer = factory.getValue().getDeclaredConstructor().newInstance();
        summarizer.outFile = new File(siteDir,
            SiteModel.REGISTRATION_SUMMARY_BASE + factory.getKey());
        summarizer.outFile.delete();
        return summarizer;
      } catch (Exception e) {
        throw new RuntimeException("While creating summarizer " + factory.getValue().getName());
      }
    }).collect(Collectors.toSet()));
  }

  private void initializeCloudProject() {
    cloudIotManager = new CloudIotManager(siteModel.getExecutionConfiguration(),
        REGISTRAR_TOOL_NAME);
    System.err.printf(
        "Working with project %s registry %s/%s%n",
        cloudIotManager.getProjectId(),
        cloudIotManager.getCloudRegion(),
        cloudIotManager.getRegistryId());

    checkState(cloudIotManager.canUpdateCloud(),
        "iot provider not properly initialized, can not update cloud");

    if (cloudIotManager.getUpdateTopic() != null) {
      updatePusher = new PubSubPusher(projectId, cloudIotManager.getUpdateTopic());
    }
    blockUnknown = ofNullable(blockUnknown)
        .orElse(Objects.requireNonNullElse(cloudIotManager.executionConfiguration.block_unknown,
            DEFAULT_BLOCK_UNKNOWN));
  }

  private <T> void ifNullUpdate(T old, T next, Consumer<T> update) {
    if (old == null && next != null) {
      update.accept(next);
    } else if (old != null && !old.equals(next)) {
      throw new RuntimeException(format("Trying to replace old %s with new %s", old, next));
    }
  }

  private String getGenerationString() {
    try {
      Date generationDate = new Date();
      String quotedString = OBJECT_MAPPER.writeValueAsString(generationDate);
      return quotedString.substring(1, quotedString.length() - 1);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While forming generation timestamp", e);
    }
  }

  private void processDevices(Runnable modelMunger) {
    Set<String> explicitDevices = getExplicitDevices();
    try {
      localDevices = loadLocalDevices(explicitDevices);
      ifNotNullThen(modelMunger, Runnable::run);
      initializeLocalDevices();
      cloudModels = ifNotNullGet(fetchCloudModels(), devices -> new HashMap<>(devices));
      if (deleteDevices || expungeDevices) {
        deleteCloudDevices();
        return;
      }

      if (explicitDevices != null) {
        Set<String> unknownLocals = difference(explicitDevices, localDevices.keySet());
        if (!unknownLocals.isEmpty()) {
          throw new RuntimeException(
              "Unknown specified devices: " + JOIN_CSV.join(unknownLocals));
        }
      }

      boolean isTargeted = explicitDevices != null;
      Set<String> targetDevices = isTargeted ? explicitDevices : localDevices.keySet();
      Map<String, LocalDevice> targetLocals = targetDevices.stream()
          .collect(Collectors.toMap(key -> key, key -> localDevices.get(key)));
      Set<String> oldDevices = targetDevices.stream().filter(this::alreadyRegistered)
          .collect(Collectors.toSet());
      Set<String> newDevices = difference(targetDevices, oldDevices);

      int total = 0;

      System.err.printf("Processing %d new devices...%n", newDevices.size());
      total += processLocalDevices(newDevices);

      System.err.printf("Updating %d existing devices...%n", oldDevices.size());
      total += processLocalDevices(oldDevices);
      System.err.printf("Finished processing %d/%d devices.%n", total, targetDevices.size());

      if (updateCloudIoT) {
        bindGatewayDevices(targetLocals);
      }

      if (cloudModels != null && !isTargeted) {
        extraDevices = processExtraDevices(difference(cloudModels.keySet(), targetDevices));
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing devices", e);
    }
  }

  private boolean notAlreadyRegistered(String device) {
    return !alreadyRegistered(device);
  }

  private boolean alreadyRegistered(String device) {
    return ifNotNullGet(cloudModels, devices -> devices.containsKey(device), false);
  }

  private void deleteCloudDevices() {
    if (cloudModels.isEmpty()) {
      System.err.println("No devices to delete, our work here is done!");
      return;
    }
    Set<String> explicitDevices = getExplicitDevices();
    if (explicitDevices != null) {
      executeDelete(explicitDevices, "explicit");
      return;
    }
    Set<String> cloudDevices = cloudModels.keySet();
    if (deleteDevices) {
      SetView<String> devices = Sets.intersection(cloudDevices, localDevices.keySet());
      executeDelete(devices, "registered");
    }
    if (expungeDevices) {
      SetView<String> extras = difference(cloudDevices, localDevices.keySet());
      executeDelete(extras, "unknown");
      reapExtraDevices(ImmutableSet.of());
    }
  }

  private void executeDelete(Set<String> deviceSet, String kind) {
    try {
      System.err.printf("Preparing to delete %s %s devices...%n", deviceSet.size(), kind);
      if (deviceSet.isEmpty()) {
        return;
      }
      Set<String> gateways = deviceSet.stream().filter(id -> ifNotNullGet(localDevices.get(id),
          LocalDevice::isGateway, false)).collect(Collectors.toSet());
      final Set<String> others = difference(deviceSet, gateways);

      final Instant start = Instant.now();

      // Delete gateways first so that they aren't holding the other devices hostage.
      synchronizedDelete(gateways);
      synchronizedDelete(others);

      // There is a hidden race-condition in the Clearblade IoT API that will report a device
      // as deleted (not listed in the registry), but still complain with an "already exists"
      // error if it's attempted to be created. Just as a hack, add a sleep in here to make
      // sure the backend is cleared out.
      safeSleep(DELETE_FLUSH_DELAY_MS);

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Deleted %d devices in %.03fs%n", deviceSet.size(), seconds);

      Set<String> deviceIds = fetchCloudModels().keySet();
      Set<String> remaining = intersection(deviceIds, deviceSet);
      if (!remaining.isEmpty()) {
        throw new RuntimeException("Did not delete all devices! " + CSV_JOINER.join(remaining));
      }
    } catch (Exception e) {
      throw new RuntimeException("While deleting cloud devices", e);
    }
  }

  private void synchronizedDelete(Set<String> devices) throws InterruptedException {
    AtomicInteger count = new AtomicInteger();
    devices.forEach(id -> parallelExecute(() -> {
      int incremented = count.incrementAndGet();
      System.err.printf("Deleting device %s (%d/%d)%n", id, incremented, devices.size());
      deleteDevice(id);
    }));
    System.err.println("Waiting for device deletion...");
    dynamicTerminate(devices.size());
  }

  private void deleteDevice(String deviceId) {
    try {
      cloudIotManager.deleteDevice(deviceId);
    } catch (Exception e) {
      throw new RuntimeException("While deleting device " + deviceId, e);
    }
  }

  private int processLocalDevices(Set<String> deviceSet) {
    try {
      AtomicInteger queued = new AtomicInteger();
      final Instant start = Instant.now();
      int deviceCount = deviceSet.size();
      AtomicInteger processedCount = new AtomicInteger();
      deviceSet.forEach(localName -> {
        queued.incrementAndGet();
        parallelExecute(() -> {
          processLocalDevice(localName, processedCount, deviceCount);
          queued.decrementAndGet();
        });
      });
      System.err.println("Waiting for device processing...");
      dynamicTerminate(queued.get());

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      double perDevice = Math.floor(seconds / localDevices.size() * 1000.0) / 1000.0;
      int finalCount = processedCount.get();
      System.err.printf("Processed %d (skipped %d) devices in %.03fs, %.03fs/d%n",
          finalCount, deviceCount - finalCount, seconds, perDevice);
      return finalCount;
    } catch (Exception e) {
      throw new RuntimeException("While processing local devices", e);
    }
  }

  private boolean processLocalDevice(String localName, AtomicInteger processedDeviceCount,
      int totalCount) {
    LocalDevice localDevice = localDevices.get(localName);
    if (!localDevice.isValid()) {
      System.err.println("Skipping invalid device " + localName);
      return false;
    }
    if (shouldLimitDevice(localDevice)) {
      System.err.println("Skipping active device " + localDevice.getDeviceId());
      return false;
    }
    Instant start = Instant.now();
    int count = processedDeviceCount.incrementAndGet();
    boolean created = false;
    boolean error = false;
    try {
      localDevice.writeConfigFile();
      if (cloudModels != null && updateCloudIoT) {
        created = syncCloudIoT(localName, localDevice);
        sendUpdateMessages(localDevice);
        cloudModels.computeIfAbsent(localName, name -> fetchDevice(localName, true));
        sendSwarmMessage(localDevice);
      }
    } catch (Exception e) {
      System.err.printf("Deferring exception for %s: %s%n", localDevice.getDeviceId(), e);
      localDevice.captureError(LocalDevice.EXCEPTION_REGISTERING, e);
      error = true;
    }
    Duration between = Duration.between(start, Instant.now());
    double seconds = (between.getSeconds() + between.getNano() / 1e9) / runnerThreads;
    String code = error ? "error" : (created ? "add" : "update");
    System.err.printf("Processed %s (%d/%d) in %.03fs (%s)%n", localName, count, totalCount,
        seconds, code);
    return created;
  }

  private boolean shouldLimitDevice(LocalDevice localDevice) {
    try {
      if (idleLimit == null) {
        return false;
      }
      CloudModel device = cloudIotManager.fetchDevice(localDevice.getDeviceId());
      if (device == null || device.last_event_time == null || idleLimit == null) {
        return false;
      }
      return Instant.now().minus(idleLimit).isBefore(device.last_event_time.toInstant());
    } catch (Exception e) {
      throw new RuntimeException("While checking device limit " + localDevice.getDeviceId(), e);
    }
  }

  private boolean sendSwarmMessage(LocalDevice localDevice) {
    if (feedPusher == null) {
      return false;
    }

    if (!localDevice.isDirectConnect()) {
      System.err.println("Skipping feed message for proxy device " + localDevice.getDeviceId());
      return false;
    }

    System.err.println("Sending feed message for " + localDevice.getDeviceId());

    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("subFolder", SWARM_SUBFOLDER);
      attributes.put("deviceId", localDevice.getDeviceId());
      attributes.put("deviceRegistryId", cloudIotManager.executionConfiguration.registry_id);
      attributes.put("deviceRegistryLocation", cloudIotManager.executionConfiguration.cloud_region);
      SwarmMessage swarmMessage = new SwarmMessage();
      swarmMessage.key_base64 = Base64.getEncoder().encodeToString(localDevice.getKeyBytes());
      swarmMessage.device_metadata = localDevice.getMetadata();
      String swarmString = OBJECT_MAPPER.writeValueAsString(swarmMessage);
      feedPusher.sendMessage(attributes, swarmString);
    } catch (Exception e) {
      throw new RuntimeException("While sending swarm feed message", e);
    }
    return true;
  }

  private boolean syncCloudIoT(String localName, LocalDevice localDevice) {
    boolean created = updateCloudIoT && updateCloudIoT(localDevice);
    CloudModel device =
        checkNotNull(fetchDevice(localName, created), "missing device " + localName);
    localDevice.updateModel(device);
    return created;
  }

  private boolean updateCloudIoT(LocalDevice localDevice) {
    String localName = localDevice.getDeviceId();
    fetchDevice(localName, false);
    CloudDeviceSettings localDeviceSettings = localDevice.getSettings();
    if (preDeleteDevice(localName)) {
      System.err.println("Deleting to incite recreation " + localName);
      cloudIotManager.deleteDevice(localName);
    }
    return cloudIotManager.registerDevice(localName, localDeviceSettings);
  }

  private boolean preDeleteDevice(String localName) {
    if (!localDevices.get(localName).isGateway()) {
      return false;
    }
    CloudModel registeredDevice = cloudIotManager.getRegisteredDevice(localName);
    return ifNotNullGet(registeredDevice, Registrar::isNotGateway, false);
  }

  private Set<String> getExplicitDevices() {
    if (deviceList == null) {
      return null;
    }
    return deviceList.stream().map(this::deviceNameFromPath).collect(Collectors.toSet());
  }

  private String deviceNameFromPath(String device) {
    while (device.endsWith("/")) {
      device = device.substring(0, device.length() - 1);
    }
    int slashPos = device.lastIndexOf('/');
    if (slashPos >= 0) {
      device = device.substring(slashPos + 1);
    }
    return device;
  }

  private Map<String, CloudModel> processExtraDevices(Set<String> extraDevices) {
    try {
      Map<String, CloudModel> extras = new ConcurrentHashMap<>();
      if (extraDevices.isEmpty()) {
        return extras;
      }
      if (blockUnknown) {
        System.err.printf("Blocking %d extra devices...", extraDevices.size());
      }
      AtomicInteger alreadyBlocked = new AtomicInteger();
      extraDevices.forEach(extraName -> parallelExecute(
          () -> extras.put(extraName, processExtra(extraName, alreadyBlocked))));
      dynamicTerminate(extraDevices.size());
      System.err.printf("There were %d/%d already blocked devices.%n", alreadyBlocked.get(),
          extraDevices.size());
      reapExtraDevices(extras.keySet());
      return extras;
    } catch (Exception e) {
      throw new RuntimeException(format("Processing %d extra devices", extraDevices.size()), e);
    }
  }

  private CloudModel processExtra(String extraName, AtomicInteger alreadyBlocked) {
    try {
      boolean isBlocked = isTrue(cloudModels.get(extraName).blocked);
      final CloudModel augmentedModel;
      if (blockUnknown && !isBlocked) {
        System.err.println("Blocking extra device: " + extraName);
        cloudIotManager.blockDevice(extraName, true);
        augmentedModel = augmentModel(cloudIotManager.fetchDevice(extraName));
      } else {
        ifTrueThen(isBlocked, alreadyBlocked::incrementAndGet);
        augmentedModel = augmentModel(cloudModels.get(extraName));
      }
      writeExtraDevice(extraName, augmentedModel);
      return augmentedModel;
    } catch (Exception e) {
      CloudModel errorModel = new CloudModel();
      errorModel.detail = e.toString();
      errorModel.operation = Operation.ERROR;
      writeExtraDevice(extraName, errorModel);
      return errorModel;
    }
  }

  private void reapExtraDevices(Set<String> current) {
    File extrasDir = new File(siteDir, SiteModel.EXTRAS_DIR);
    String[] existing = ofNullable(extrasDir.list()).orElse(new String[0]);
    Set<String> previous = Arrays.stream(existing).collect(Collectors.toSet());
    difference(previous, current).forEach(expired -> {
      File file = new File(extrasDir, expired);
      try {
        FileUtils.deleteDirectory(file);
        System.err.println("Deleted extraneous extra device directory " + expired);
      } catch (Exception e) {
        throw new RuntimeException("Error deleting extraneous directory " + file.getAbsolutePath(),
            e);
      }
    });
  }

  private void writeExtraDevice(String extraName, CloudModel augmentedModel) {
    String devPath = format(SiteModel.EXTRA_DEVICES_FORMAT, extraName);
    File extraDir = new File(siteDir, devPath);
    try {
      extraDir.mkdirs();
      File modelFile = new File(extraDir, SiteModel.CLOUD_MODEL_FILE);
      Date previous = ifTrueGet(modelFile.exists(),
          () -> loadFile(CloudModel.class, modelFile).updated_time);
      if (previous == null || !previous.equals(augmentedModel.updated_time)) {
        System.err.println("Writing extra device model to " + devPath);
        writeFile(augmentedModel, modelFile);
        updateExtraMetadata(extraName, extraDir);
      }
    } catch (Exception e) {
      throw new RuntimeException("Writing extra device data " + extraDir.getAbsolutePath(), e);
    }
  }

  private void updateExtraMetadata(String extraName, File extraDir) {
    File metadataDir = new File(extraDir, SiteModel.METADATA_DIR);
    try {
      CloudModel cloudModel = cloudIotManager.fetchDevice(extraName);
      FileUtils.deleteDirectory(metadataDir);
      if (cloudModel.metadata != null && !cloudModel.metadata.isEmpty()) {
        metadataDir.mkdirs();
        cloudModel.metadata.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(UDMI_PREFIX))
            .forEach(entry -> {
              File metadataFile = new File(metadataDir, entry.getKey() + JSON_SUFFIX);
              writeString(metadataFile, entry.getValue());
            });
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "While extracting cloud metadata to " + metadataDir.getAbsolutePath(), e);
    }
  }

  private CloudModel fetchDevice(String localName, boolean newDevice) {
    try {
      boolean shouldFetch = newDevice || cloudModels.containsKey(localName);
      return shouldFetch ? cloudIotManager.fetchDevice(localName) : null;
    } catch (Exception e) {
      throw new RuntimeException("Fetching device " + localName, e);
    }
  }

  private void sendUpdateMessages(LocalDevice localDevice) {
    if (updatePusher != null) {
      System.err.println("Sending model/config update for " + localDevice.getDeviceId());
      sendUpdateMessage(localDevice, SubFolder.SYSTEM);
      sendUpdateMessage(localDevice, SubFolder.POINTSET);
      sendUpdateMessage(localDevice, SubFolder.GATEWAY);
      sendUpdateMessage(localDevice, SubFolder.LOCALNET);
    }
  }

  // TODO: this is now broken if device uses a file rather than automatically generated config
  private void sendUpdateMessage(LocalDevice localDevice, SubFolder subFolder) {
    sendUpdateMessage(localDevice, MODEL_SUB_TYPE, subFolder, localDevice.getMetadata());
    sendUpdateMessage(localDevice, CONFIG_SUB_TYPE, subFolder, localDevice.deviceConfigObject());
  }

  private void sendUpdateMessage(LocalDevice localDevice, String subType, SubFolder subfolder,
      Object target) {
    String fieldName = subfolder.toString().toLowerCase();
    try {
      Field declaredField = target.getClass().getDeclaredField(fieldName);
      sendSubMessage(localDevice, subType, subfolder, declaredField.get(target));
    } catch (Exception e) {
      throw new RuntimeException(format("Getting field %s from target %s", fieldName,
          target.getClass().getSimpleName()));
    }
  }

  private void sendSubMessage(LocalDevice localDevice, String subType, SubFolder subFolder,
      Object subConfig) {
    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("deviceId", localDevice.getDeviceId());
      attributes.put("deviceNumId", localDevice.getDeviceNumId());
      attributes.put("deviceRegistryId", cloudIotManager.getRegistryId());
      attributes.put("projectId", cloudIotManager.getProjectId());
      attributes.put("subType", subType);
      attributes.put("subFolder", subFolder.value());
      String messageString = OBJECT_MAPPER.writeValueAsString(subConfig);
      updatePusher.sendMessage(attributes, messageString);
    } catch (Exception e) {
      throw new RuntimeException(
          format("Sending %s/%s messages for %s%n", subType, subFolder,
              localDevice.getDeviceId()), e);
    }
  }

  private void bindGatewayDevices(Map<String, LocalDevice> localDevices) {
    try {
      final Instant start = Instant.now();
      Set<LocalDevice> gateways = localDevices.values().stream().filter(LocalDevice::isGateway)
          .collect(Collectors.toSet());
      Set<Entry<String, String>> bindings = gateways.stream()
          .map(gateway -> getBindings(localDevices.keySet(), gateway)).flatMap(Collection::stream)
          .collect(Collectors.toSet());
      AtomicInteger bindingCount = new AtomicInteger();
      System.err.printf("Binding %d unbound devices to %d gateways...%n", bindings.size(),
          gateways.size());
      bindings.forEach(binding -> {
        parallelExecute(() -> {
          try {
            String proxyDeviceId = binding.getKey();
            String gatewayId = binding.getValue();
            int count = bindingCount.incrementAndGet();
            System.err.printf("Binding %s to %s (%d/%d)%n", proxyDeviceId, gatewayId, count,
                bindings.size());
            cloudIotManager.bindDevice(proxyDeviceId, gatewayId);
          } catch (Exception e) {
            localDevices.get(binding.getKey()).captureError(LocalDevice.EXCEPTION_BINDING, e);
          }
        });
      });

      System.err.println("Waiting for device binding...");
      dynamicTerminate(bindings.size());

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Finished binding gateways in %.03f%n", seconds);
    } catch (Exception e) {
      throw new RuntimeException("While binding gateways", e);
    }
  }

  private synchronized void dynamicTerminate(int expected) throws InterruptedException {
    try {
      if (executor == null) {
        return;
      }
      executor.shutdown();
      int timeout = (int) (ceil(expected / (double) runnerThreads) * EACH_ITEM_TIMEOUT_SEC) + 1;
      System.err.printf("Waiting %ds for %d tasks to complete...%n", timeout, expected);
      if (!executor.awaitTermination(timeout, TimeUnit.SECONDS)) {
        throw new RuntimeException("Incomplete executor termination after " + timeout + "s");
      }
    } finally {
      executor = null;
      executing = null;
    }
  }

  private synchronized void parallelExecute(Runnable runnable) {
    ifNullThen(executor, () -> executor = Executors.newFixedThreadPool(runnerThreads));
    ifNullThen(executing, () -> executing = new ArrayList<>());
    executing.add(executor.submit(runnable));
  }

  private Set<Entry<String, String>> getBindings(Set<String> deviceSet, LocalDevice localDevice) {
    String gatewayId = localDevice.getDeviceId();
    Set<String> boundDevices = ofNullable(cloudIotManager.fetchBoundDevices(gatewayId)).orElse(
        ImmutableSet.of());
    System.err.printf("Binding devices to %s, already bound: %s%n",
        gatewayId, JOIN_CSV.join(boundDevices));
    int total = cloudModels.size() != 0 ? cloudModels.size() : localDevices.size();
    checkState(boundDevices.size() != total,
        "all devices including the gateway can't be bound to one gateway!");
    return localDevice.getSettings().proxyDevices.stream()
        .filter(proxyDevice -> deviceSet == null || deviceSet.contains(proxyDevice))
        .filter(proxyDeviceId -> !boundDevices.contains(proxyDeviceId))
        .map(proxyDeviceId -> new SimpleEntry<>(proxyDeviceId, gatewayId))
        .collect(Collectors.toSet());
  }

  private void shutdown() {
    if (updatePusher != null) {
      updatePusher.shutdown();
    }
    if (feedPusher != null) {
      feedPusher.shutdown();
    }
    if (cloudIotManager != null) {
      cloudIotManager.shutdown();
    }
  }

  private Map<String, CloudModel> fetchCloudModels() {
    try {
      if (cloudIotManager != null) {
        System.err.printf("Fetching devices from registry %s...%n",
            cloudIotManager.getRegistryId());
        Map<String, CloudModel> devices = cloudIotManager.fetchCloudModels();
        System.err.printf("Fetched %d device models from cloud registry%n", devices.size());
        return devices;
      } else {
        System.err.println("Skipping remote registry fetch b/c no cloud project");
        return null;
      }
    } catch (Exception e) {
      throw new RuntimeException("While fetching cloud devices", e);
    }
  }

  private Map<String, LocalDevice> loadLocalDevices(Set<String> specifiedDevices) {
    checkNotNull(siteDir, "site directory");
    File devicesDir = new File(siteDir, DEVICES_DIR);
    if (!devicesDir.isDirectory()) {
      throw new RuntimeException("Not a valid directory: " + devicesDir.getAbsolutePath());
    }

    List<String> deviceList = getDeviceList(specifiedDevices, devicesDir);
    return loadDevices(deviceList);
  }

  private List<String> getDeviceList(Set<String> specifiedDevices, File devicesDir) {
    return SiteModel.listDevices(devicesDir).stream()
        .filter(name -> specifiedDevices == null || specifiedDevices.contains(name))
        .collect(Collectors.toList());
  }

  private void initializeSettings(Map<String, LocalDevice> localDevices) {
    localDevices.values().forEach(LocalDevice::initializeSettings);
  }

  private void validateSamples(Map<String, LocalDevice> localDevices) {
    for (LocalDevice device : localDevices.values()) {
      try {
        device.validateSamples();
      } catch (Exception e) {
        device.captureError(LocalDevice.EXCEPTION_SAMPLES, e);
      }
    }
  }

  private void validateExpected(Map<String, LocalDevice> localDevices) {
    for (LocalDevice device : localDevices.values()) {
      try {
        device.validateExpectedFiles();
      } catch (Exception e) {
        device.captureError(LocalDevice.EXCEPTION_FILES, e);
      }
    }
  }

  private void writeNormalized(Map<String, LocalDevice> localDevices) {
    for (String deviceName : localDevices.keySet()) {
      try {
        localDevices.get(deviceName).writeNormalized();
      } catch (Exception e) {
        throw new RuntimeException("While writing normalized " + deviceName, e);
      }
    }
  }

  private void validateKeys(Map<String, LocalDevice> localDevices) {
    Map<Credential, String> usedCredentials = new HashMap<>();
    localDevices.values().stream()
        .filter(LocalDevice::isDirectConnect)
        .forEach(
            localDevice -> {
              CloudDeviceSettings settings = localDevice.getSettings();
              String deviceName = localDevice.getDeviceId();
              for (Credential credential : settings.credentials) {
                String previous = usedCredentials.put(credential, deviceName);
                if (previous != null) {
                  RuntimeException exception =
                      new RuntimeException(
                          format(
                              "Duplicate credentials found for %s & %s", previous, deviceName));
                  localDevice.captureError(LocalDevice.EXCEPTION_CREDENTIALS, exception);
                }
              }
            });
  }

  private Map<String, LocalDevice> loadDevices(List<String> devices) {
    Set<String> actual = devices.stream()
        .filter(deviceName -> siteModel.deviceExists(deviceName)).collect(Collectors.toSet());
    return actual.stream().collect(Collectors.toMap(name -> name, name ->
        new LocalDevice(siteModel, name, schemas, generation, doValidate)));
  }

  private void initializeLocalDevices() {
    System.err.printf("Initializing %d local devices...%n", localDevices.size());
    initializeDevices(localDevices);
    initializeSettings(localDevices);
    writeNormalized(localDevices);
    outputMetadataModels(localDevices);
    validateExpected(localDevices);
    validateSamples(localDevices);
    validateKeys(localDevices);
  }

  private void outputMetadataModels(Map<String, LocalDevice> localDevices) {
    if (!metadataModelOut) {
      return;
    }

    cloudIotManager.updateRegistry(getSiteMetadata(), Operation.PREVIEW);

    try {
      AtomicInteger previewCount = new AtomicInteger();
      localDevices.forEach((id, device) -> parallelExecute(() -> {
        int baseCount = previewCount.getAndIncrement();
        ifTrueThen(baseCount % 100 == 0,
            () -> System.err.printf("Sending preview for device %d/%d...%n", baseCount + 1,
                localDevices.size()));
        cloudIotManager.updateDevice(id, device.getSettings(), Operation.PREVIEW);
      }));
      dynamicTerminate(localDevices.size());
      System.err.printf("Finished sending device preview for %d devices.%n", localDevices.size());
    } catch (Exception e) {
      throw new RuntimeException("While previewing local devices", e);
    }
  }

  private void initializeDevices(Map<String, LocalDevice> localDevices) {
    localDevices.values().forEach(localDevice -> {
      try {
        localDevice.initialize();
        localDevice.loadCredentials();
      } catch (ValidationError error) {
        throw new RuntimeException("While initializing device", error);
      } catch (Exception e) {
        localDevice.captureError(LocalDevice.EXCEPTION_CREDENTIALS, e);
      }

      if (cloudIotManager != null) {
        try {
          localDevice.validateEnvelope(
              cloudIotManager.getRegistryId(), cloudIotManager.getSiteName());
        } catch (Exception e) {
          localDevice.captureError(LocalDevice.EXCEPTION_ENVELOPE,
              new RuntimeException("While validating envelope", e));
        }
      }
    });
  }

  @CommandLineOption(short_form = "-p", arg_name = "project_id", description = "Set project id")
  private void setProjectId(String projectId) {
    if (NO_SITE.equals(projectId) || projectId == null) {
      this.projectId = null;
      return;
    }
    if (projectId.startsWith("-")) {
      throw new IllegalArgumentException("Project id starts with dash options flag " + projectId);
    }
    this.projectId = projectId;
  }

  @CommandLineOption(short_form = "-r", arg_name = "root_path", description = "Set tool root")
  private void setToolRoot(String toolRoot) {
    try {
      schemaBase = new File(toolRoot, SCHEMA_BASE_PATH);
      if (!schemaBase.isDirectory()) {
        throw new RuntimeException("Missing schema directory " + schemaBase.getAbsolutePath());
      }
      File[] schemaFiles = schemaBase.listFiles(file -> file.getName().endsWith(SCHEMA_SUFFIX));
      for (File schemaFile : requireNonNull(schemaFiles, "schema files")) {
        loadSchema(schemaFile.getName());
      }
      if (schemas.isEmpty()) {
        throw new RuntimeException(
            "No schemas successfully loaded from " + schemaBase.getAbsolutePath());
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing tool root " + toolRoot, e);
    }
  }

  private void loadSchema(String key) {
    File schemaFile = new File(schemaBase, key);
    try (InputStream schemaStream = Files.newInputStream(schemaFile.toPath())) {
      JsonNode schemaTree = OBJECT_MAPPER.readTree(schemaStream);
      if (schemaTree instanceof ObjectNode schemaObject) {
        Set<String> toRemove = new HashSet<>();
        schemaObject.fields().forEachRemaining(entry -> {
          ifTrueThen(entry.getKey().startsWith("$"), () -> toRemove.add(entry.getKey()));
        });
        toRemove.forEach(schemaObject::remove);
      }
      JsonSchema schema =
          JsonSchemaFactory.newBuilder()
              .setLoadingConfiguration(
                  LoadingConfiguration.newBuilder()
                      .addScheme("file", new RelativeDownloader())
                      .freeze()).freeze().getJsonSchema(schemaTree);
      schemas.put(key, schema);
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  private void loadSiteDefaults() {
    this.siteDefaults = null;

    if (!schemas.containsKey(SiteModel.METADATA_JSON)) {
      return;
    }

    File legacyMetadataFile = new File(siteDir, SiteModel.LEGACY_METADATA_FILE);
    if (legacyMetadataFile.exists()) {
      Metadata metadata = loadFileRequired(Metadata.class, legacyMetadataFile);
      ifNotNullThrow(metadata.system, format("Legacy %s detected, please rename to %s",
          SiteModel.LEGACY_METADATA_FILE, SiteModel.SITE_DEFAULTS_FILE));
    }

    File siteDefaultsFile = new File(siteDir, SiteModel.SITE_DEFAULTS_FILE);
    try (InputStream targetStream = new FileInputStream(siteDefaultsFile)) {
      // At this time, do not validate the site defaults schema because, by its nature of being
      // a partial overlay on each device Metadata, this Metadata will likely be incomplete
      // and fail validation.
      schemas.get(METADATA_SCHEMA_JSON).validate(OBJECT_MAPPER.readTree(targetStream));
    } catch (FileNotFoundException e) {
      return;
    } catch (Exception e) {
      throw new RuntimeException("While validating " + SiteModel.SITE_DEFAULTS_FILE, e);
    }

    try {
      System.err.printf("Loading " + SiteModel.SITE_DEFAULTS_FILE + "\n");
      this.siteDefaults = OBJECT_MAPPER.readValue(siteDefaultsFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + SiteModel.SITE_DEFAULTS_FILE, e);
    }
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }

  @CommandLineOption(short_form = "-a", arg_name = "alternate",
      description = "Set alternate registry")
  private void setTargetRegistry(String altRegistry) {
    siteModel.getExecutionConfiguration().registry_id = altRegistry;
    siteModel.getExecutionConfiguration().alt_registry = null;
  }

  class RelativeDownloader implements URIDownloader {

    @Override
    public InputStream fetch(URI source) {
      try {
        return Files.newInputStream(new File(schemaBase, source.getSchemeSpecificPart()).toPath());
      } catch (Exception e) {
        throw new RuntimeException("While loading sub-schema " + source, e);
      }
    }
  }
}
