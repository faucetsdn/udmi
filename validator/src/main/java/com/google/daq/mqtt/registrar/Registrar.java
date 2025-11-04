package com.google.daq.mqtt.registrar;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_ROOT;
import static com.google.udmi.util.Common.CLOUD_VERSION_KEY;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.DEVICE_NUM_KEY;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.PROJECT_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SEC_TO_MS;
import static com.google.udmi.util.Common.SITE_METADATA_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.UDMI_VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotEmptyThen;
import static com.google.udmi.util.GeneralUtils.ifNotEmptyThrow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.listUniqueSet;
import static com.google.udmi.util.GeneralUtils.setOrSize;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.JSON_SUFFIX;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.loadFile;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PREFIX;
import static com.google.udmi.util.SiteModel.DEVICES_DIR;
import static com.google.udmi.util.SiteModel.MOCK_CLEAN;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static java.lang.Math.ceil;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.external.ExternalProcessor;
import com.google.daq.mqtt.registrar.LocalDevice.DeviceKind;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.DeviceGatewayBoundException;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.util.PubSubPusher;
import com.google.udmi.util.CommandLineOption;
import com.google.udmi.util.CommandLineProcessor;
import com.google.udmi.util.Common;
import com.google.udmi.util.ExceptionList;
import com.google.udmi.util.ExceptionMap;
import com.google.udmi.util.ExceptionMap.ErrorTree;
import com.google.udmi.util.ExceptionMap.ExceptionCategory;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.ValidationError;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.VisibleForTesting;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Credential;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.GatewayModel;
import udmi.schema.LinkExternalsModel;
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
  private static final int UNBIND_SET_SIZE = 1000;
  public static final int BATCH_REPORT_SIZE = 100;
  private boolean autoAltRegistry;
  private final Map<String, JsonSchema> schemas = new HashMap<>();
  private final String generation = JsonUtil.isoConvert();
  private final Set<Summarizer> summarizers = new HashSet<>();
  private final List<String> usageForms = ImmutableList.of(
      "bin/registrar site_model project_spec [options] [devices...]",
      "bin/registrar site_spec [options] [devices...]");
  private final CommandLineProcessor commandLineProcessor = new CommandLineProcessor(this,
      usageForms);
  private final AtomicInteger updatedDevices = new AtomicInteger();
  private final Map<Credential, String> usedCredentials = new ConcurrentHashMap<>();
  private final Map<String, ExternalProcessor> processors = new ConcurrentHashMap<>();
  private CloudIotManager cloudIotManager;
  private File schemaBase;
  private PubSubPusher updatePusher;
  private PubSubPusher feedPusher;
  private Map<String, LocalDevice> allDevices;
  private Map<String, LocalDevice> workingDevices;
  private Set<String> changedDevices;
  private Set<String> extraDevices;
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
  private boolean instantiateExtras;
  private boolean metadataModelOut;
  private int createRegistries = -1;
  private int runnerThreads = 5;
  private ExecutorService executor;
  private List<Future<?>> executing = new ArrayList<>();
  private SiteModel siteModel;
  private boolean queryOnly;
  private boolean strictWarnings;
  private boolean doNotUpdate;
  private boolean expandDependencies;
  private boolean updateMetadata;
  private String currentRunTimestamp;
  private String lastRunTimestamp;
  private boolean optimizeRun;

  /**
   * Main entry point for registrar.
   */
  public static void main(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    try {
      new Registrar().processArgs(argList).execute();
    } catch (Exception e) {
      System.err.printf("Exception at %s in main: %s%n", getTimestamp(), friendlyStackTrace(e));
      e.printStackTrace();
      System.exit(Common.EXIT_CODE_ERROR);
    }

    Common.forcedDelayedShutdown();
  }

  private void maybeProcessAltRegistry() {
    if (autoAltRegistry) {
      ifNotNullThen(siteModel.getExecutionConfiguration().alt_registry, this::processAltRegistry);
    }
  }

  private void processAltRegistry(String altRegistry) {
    System.err.printf("%n%n%n%nDoing that whole thing again for the alternate registry %s%n%n%n%n",
        altRegistry);
    siteModel.resetRegistryId(altRegistry);
    execute();
  }

  @SuppressWarnings("unchecked")
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

  private static String defaultToolRoot() {
    return UDMI_ROOT.getAbsolutePath();
  }

  /**
   * process the arguments and create new SiteModel.
   *
   * <p/>argumentListRaw includes: site_path project_spec deviceList
   *
   * @param argListRaw raw list of arguments to process
   * @return Registrar Instance
   */
  public Registrar processArgs(List<String> argListRaw) {
    List<String> argList = new ArrayList<>(argListRaw);
    if (argList.size() == 1 && new File(argList.get(0)).isDirectory()) {
      // Add implicit NO_SITE site spec for local-only site model processing.
      argList.add(NO_SITE);
    }
    try {
      setSiteModel(new SiteModel(TOOL_NAME, argList));
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

  @CommandLineOption(short_form = "-o", arg_name = "optimize",
      description = "Only process devices updated after the last run")
  private void setOptimizeRun() {
    this.optimizeRun = true;
  }

  @CommandLineOption(short_form = "-u", description = "Update metadata.json")
  private void setUpdateMetadata() {
    this.updateMetadata = true;
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
    deleteDevices = true;
    updateCloudIoT = true;
  }

  @CommandLineOption(short_form = "-i", description = "Instantiate extra (unknown) devices")
  private void setInstantiateExtras() {
    instantiateExtras = true;
    updateCloudIoT = true;
  }

  @CommandLineOption(short_form = "-x", description = "Expunge (unknown) devices")
  private void setExpungeDevices() {
    checkNotNull(projectId, "expunge devices specified with no target project");
    expungeDevices = true;
    updateCloudIoT = true;
  }

  @CommandLineOption(short_form = "-z", description = "Do not update existing devices")
  private void setDoNotUpdate() {
    doNotUpdate = true;
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

  /**
   * runs the registrar.
   *
   * @return registrar instance
   */
  public Registrar execute() {
    execute(null);
    maybeProcessAltRegistry();
    return this;
  }

  @VisibleForTesting
  protected void execute(Runnable modelMunger) {
    try {
      if (projectId != null) {
        initializeCloudProject();
      }
      if (schemaBase == null) {
        // Use the proper (relative) tool root directory for unit tests.
        setToolRoot(isMockProject() ? ".." : defaultToolRoot());
      }
      loadSiteDefaults();
      if (createRegistries >= 0) {
        createRegistries();
      } else {
        processSiteMetadata();
        processAllDevices(modelMunger);
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

  private void loadSiteRegistrationTimestamps() {
    currentRunTimestamp = isoConvert(Instant.now());

    File registrationHistory = new File(siteDir, SiteModel.REGISTRATION_SUMMARY_BASE + ".json");
    lastRunTimestamp = catchToNull(() -> asMap(registrationHistory).get(TIMESTAMP_KEY).toString());
  }

  private void updateDeviceMetadata(String deviceId) {
    checkNotNull(projectId, "can't update metadata: cloud project not defined");

    CloudModel registeredDevice = cloudModels.get(deviceId);
    if (registeredDevice == null) {
      return;
    }
    Metadata localMetadata = workingDevices.get(deviceId).getMetadata();
    if (localMetadata.cloud == null) {
      localMetadata.cloud = new CloudModel();
    }
    String registeredId = registeredDevice.num_id;
    if (!Common.EMPTY_RETURN_RECEIPT.equals(registeredId)
        && !registeredId.equals(localMetadata.cloud.num_id)) {
      System.err.printf("Updating device %s num_id %s -> %s%n",
          deviceId, localMetadata.cloud.num_id, registeredId);
      localMetadata.cloud.num_id = registeredId;
      updatedDevices.incrementAndGet();
    }
  }

  private boolean isMockProject() {
    return MOCK_PROJECT.equals(projectId) || MOCK_CLEAN.equals(projectId);
  }

  @VisibleForTesting
  protected Map<String, LocalDevice> getWorkingDevices() {
    return workingDevices;
  }

  private void processSiteMetadata() {
    SiteMetadata siteMetadata = siteModel.loadSiteMetadata();
    siteMetadata.name = ofNullable(siteMetadata.name).orElse(siteModel.getSiteName());
    ifTrueThen(updateCloudIoT,
        () -> cloudIotManager.updateRegistry(getSiteMetadata(), ModelOperation.UPDATE));
  }

  private SiteMetadata getSiteMetadata() {
    return siteModel.loadSiteMetadata();
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
    if (workingDevices == null) {
      return;
    }
    ifNotTrueThen(doNotUpdate, () -> workingDevices.values().forEach(LocalDevice::writeErrors));
    workingDevices.values().forEach(device -> {
      getErrorKeyMap(errorSummary, ExceptionCategory.status.toString())
          .put(device.getDeviceId(), device.getStatus().toString());
      getDeviceErrorEntries(device).forEach(error -> getErrorKeyMap(errorSummary, error.getKey())
          .put(device.getDeviceId(), error.getValue().message));
    });
    if (extraDevices != null && !extraDevices.isEmpty()) {
      errorSummary.put(ExceptionCategory.extra.toString(),
          extraDevices.stream().collect(Collectors.toMap(Function.identity(),
              id -> cloudModels.get(id).operation.toString())));
    }
    System.err.println("\nSummary:");
    errorSummary.forEach((key, value) -> System.err.println(
        "  Device " + key + ": " + getErrorSummaryDetail(value)));
    System.err.println("Out of " + workingDevices.size() + " total.");
    // WARNING! entries inserted into `errorSummary` ABOVE this comment must have a map value ^^^^^^
    errorSummary.put(CLOUD_VERSION_KEY, getCloudVersionInfo());
    errorSummary.put(TIMESTAMP_KEY, currentRunTimestamp);
    lastErrorSummary = errorSummary;
    errorSummary.put(UDMI_VERSION_KEY, Common.getUdmiVersion());
    ifNotNullThen(siteModel.siteMetadataExceptionMap,
        exceptions -> ifNotTrueThen(exceptions.isEmpty(), () ->
            errorSummary.put(SITE_METADATA_KEY, exceptions.stream()
                .map(Entry::getValue)
                .map(Exception::getMessage)
                .collect(Collectors.toList()))));
    summarizers.forEach(summarizer -> {
      File outFile = summarizer.outFile;
      try {
        summarizer.summarize(workingDevices, errorSummary, cloudModels);
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

  private void setSiteModel(SiteModel siteModel) {
    checkState(this.siteModel == null, "site model already defined");
    siteModel.loadSiteMetadata();
    this.siteModel = siteModel;
    ifTrueThen(strictWarnings, siteModel::setStrictWarnings);
    initializeExternalProcessors(siteModel);
  }

  private synchronized void initializeExternalProcessors(SiteModel siteModel) {
    Map<String, ? extends ExternalProcessor> created = ExternalProcessor.PROCESSORS.stream()
        .map(p -> {
          try {
            return p.getConstructor(SiteModel.class).newInstance(siteModel);
          } catch (Exception e) {
            throw new RuntimeException("While initializing " + p, e);
          }
        }).collect(Collectors.toMap(ExternalProcessor::getName, Function.identity()));
    checkState(created.size() == ExternalProcessor.PROCESSORS.size(), "size mismatch");
    ifNotEmptyThrow(processors.keySet(), p -> "Processors already initialized!");
    processors.putAll(created);
  }

  @CommandLineOption(short_form = "-s", arg_name = "site_path", description = "Set site path")
  private void setSitePath(String sitePath) {
    checkNotNull(SCHEMA_NAME, "schemaName not set yet");
    siteDir = new File(sitePath);
    ifNullThen(siteModel, () -> setSiteModel(new SiteModel(sitePath)));
    File summaryBase = new File(siteDir, SiteModel.REGISTRATION_SUMMARY_BASE);
    File parentFile = summaryBase.getParentFile();
    if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
      throw new IllegalStateException("Could not create directory " + parentFile.getAbsolutePath());
    }
    loadSiteRegistrationTimestamps();
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

  private void processAllDevices(Runnable modelMunger) {
    allDevices = loadAllDevices();
    changedDevices = loadChangedDevices();
    Set<String> explicitDevices = getExplicitDevices();
    try {
      workingDevices = instantiateExtras
          ? loadExtraDevices(explicitDevices) : getLocalDevices(explicitDevices);
      ifNotNullThen(modelMunger, Runnable::run);
      initializeLocalDevices();
      updateExplicitDevices(explicitDevices, workingDevices);
      cloudModels = ifNotNullGet(fetchCloudModels(), devices -> new ConcurrentHashMap<>(devices));
      if (deleteDevices || expungeDevices) {
        deleteCloudDevices();
        return;
      }

      if (explicitDevices != null) {
        Set<String> unknownLocals = difference(explicitDevices, workingDevices.keySet());
        if (!unknownLocals.isEmpty()) {
          throw new RuntimeException(
              "Unknown specified devices: " + JOIN_CSV.join(unknownLocals));
        }
      }

      boolean isTargeted = explicitDevices != null;
      Set<String> targetDevices = isTargeted ? explicitDevices : workingDevices.keySet();
      Map<String, LocalDevice> targetLocals = targetDevices.stream()
          .collect(Collectors.toMap(key -> key, key -> workingDevices.get(key)));
      Set<String> oldDevices = targetDevices.stream().filter(this::alreadyRegistered)
          .collect(Collectors.toSet());
      Set<String> newDevices = difference(targetDevices, oldDevices);

      int total = 0;

      if (updateCloudIoT) {
        System.err.printf("Processing %d new devices...%n", newDevices.size());
        total += processDevices(newDevices);

        System.err.printf("Updating %d existing devices...%n", oldDevices.size());
        total += processDevices(oldDevices);
      } else {
        System.err.printf("Processing %d target devices...%n", targetDevices.size());
        total += processDevices(targetDevices);
      }

      System.err.printf("Updated %d device metadata files.%n", updatedDevices.get());
      System.err.printf("Finished processing %d/%d devices.%n", total, targetDevices.size());

      if (updateCloudIoT) {
        bindGatewayDevices(targetLocals);
      }

      finalizeLocalDevices();

      if (cloudModels != null && !isTargeted && !instantiateExtras) {
        extraDevices = processExtraDevices(difference(cloudModels.keySet(), targetDevices));
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing devices", e);
    }
  }

  private void updateExplicitDevices(Set<String> explicitDevices,
      Map<String, LocalDevice> workingDevices) {
    ifNotNullThen(explicitDevices, () -> explicitDevices.addAll(workingDevices.keySet()));
  }

  private Map<String, LocalDevice> loadAllDevices() {
    checkNotNull(siteDir, "missing site directory");
    File devicesDir = new File(siteDir, DEVICES_DIR);
    if (!devicesDir.isDirectory()) {
      throw new RuntimeException("Not a valid directory: " + devicesDir.getAbsolutePath());
    }
    return loadDevices(SiteModel.listDevices(devicesDir));
  }

  private Set<String> loadChangedDevices() {
    if (optimizeRun && lastRunTimestamp != null) {
      System.err.println("Collecting devices changed after " + lastRunTimestamp);
      return allDevices.entrySet().stream()
          .filter(entry -> {
            LocalDevice device = entry.getValue();
            Metadata metadata = device.getMetadata();
            return metadata == null || metadata.timestamp == null
                || metadata.timestamp.toInstant().isAfter(Instant.parse(lastRunTimestamp));
          })
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).keySet();
    }
    return allDevices.keySet();
  }

  private int processDevices(Set<String> deviceSet) {
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
      double perDevice = Math.floor(seconds / workingDevices.size() * 1000.0) / 1000.0;
      int finalCount = processedCount.get();
      System.err.printf("Processed %d (skipped %d) devices in %.03fs, %.03fs/d%n",
          finalCount, deviceCount - finalCount, seconds, perDevice);
      return finalCount;
    } catch (Exception e) {
      throw new RuntimeException("While processing local devices", e);
    }
  }

  private Map<String, LocalDevice> loadExtraDevices(Set<String> explicitDevices) {
    Set<String> expectedExtras = siteModel.getExtraDevices();
    Set<String> deviceIds = ofNullable(explicitDevices).orElse(expectedExtras);
    SetView<String> unknownExtras = difference(deviceIds, expectedExtras);
    ifNotEmptyThrow(unknownExtras, extras -> "Devices not found in extras dir: " + extras);
    return deviceIds.stream().collect(Collectors.toMap(id -> id, this::makeExtraDevice));
  }

  private LocalDevice makeLocalDevice(String id) {
    return makeLocalDevice(id, doValidate ? DeviceKind.LOCAL : DeviceKind.SIMPLE);
  }

  private LocalDevice makeLocalDevice(String id, DeviceKind kind) {
    return new LocalDevice(siteModel, id, schemas, generation, kind);
  }

  private LocalDevice makeExtraDevice(String id) {
    return makeLocalDevice(id, DeviceKind.EXTRA);
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
    Set<String> cloudDevices = cloudModels.keySet();

    final Set<String> toDelete;
    final String reason;
    boolean handleExplicitly = explicitDevices != null;

    if (handleExplicitly) {
      toDelete = intersection(cloudDevices, explicitDevices);
      reason = "explicit";
    } else if (deleteDevices && expungeDevices) {
      toDelete = cloudDevices;
      reason = "everything";
    } else if (deleteDevices) {
      toDelete = intersection(cloudDevices, workingDevices.keySet());
      reason = "registered";
    } else if (expungeDevices) {
      toDelete = difference(cloudDevices, workingDevices.keySet());
      reason = "extra";
    } else {
      throw new IllegalStateException("Neither delete nor expunge indicated");
    }

    executeDelete(toDelete, reason);

    if (expungeDevices) {
      Set<String> existingExtras = siteModel.getExtraDevices();
      Set<String> toExpunge = ofNullable(explicitDevices).orElse(existingExtras);
      Set<String> toReap = intersection(existingExtras, toExpunge);
      reapExtraDevices(toReap);
    }
  }

  private void executeDelete(Set<String> deviceSet, String kind) {
    try {
      System.err.printf("Preparing to delete %s %s devices...%n", deviceSet.size(), kind);
      if (deviceSet.isEmpty()) {
        return;
      }
      final Set<String> original = ImmutableSet.copyOf(deviceSet);
      Set<String> gateways = original.stream().filter(id -> ifNotNullGet(workingDevices.get(id),
          LocalDevice::isGateway, false)).collect(Collectors.toSet());
      final Set<String> others = difference(original, gateways);

      final Instant start = Instant.now();

      System.err.printf("Deleting %d gateways and %d devices%n", gateways.size(), others.size());

      // Delete gateways first so that they aren't holding the other devices hostage.
      synchronizedDelete(gateways);
      synchronizedDelete(others);

      // There is a hidden race-condition in the ClearBlade IoT API that will report a device
      // as deleted (not listed in the registry), but still complain with an "already exists"
      // error if it's attempted to be created. Just as a hack, add a sleep in here to make
      // sure the backend is cleared out.
      safeSleep(DELETE_FLUSH_DELAY_MS);

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Deleted %d devices in %.03fs%n", original.size(), seconds);

      Set<String> deviceIds = fetchCloudModels().keySet();
      Set<String> remaining = intersection(deviceIds, original);
      if (!remaining.isEmpty()) {
        throw new RuntimeException("Did not delete all devices! " + CSV_JOINER.join(remaining));
      }
    } catch (Exception e) {
      throw new RuntimeException("While deleting cloud devices", e);
    }
  }

  private void synchronizedDelete(Set<String> devices) throws InterruptedException {
    AtomicInteger accumulator = new AtomicInteger();
    int totalCount = devices.size();
    devices.forEach(id -> parallelExecute(() -> deleteSingleDevice(devices, accumulator, id)));
    System.err.println("Waiting for device deletion completion...");
    dynamicTerminate();
  }

  private void deleteSingleDevice(Set<String> allDevices, AtomicInteger count, String id) {
    int incremented = count.incrementAndGet();
    System.err.printf("Deleting device %s (%d/%d)%n", id, incremented, allDevices.size());
    try {
      deleteDevice(allDevices, id);
    } catch (Exception e) {
      System.err.println("Exception caught during execution: " + friendlyStackTrace(e));
    }
  }

  private void deleteDevice(Set<String> allDevices, String deviceId) {
    try {
      Set<String> unbindIds = catchToNull(
          () -> new HashSet<>(workingDevices.get(deviceId).getMetadata().gateway.proxy_ids));
      cloudIotManager.deleteDevice(deviceId, unbindIds);
    } catch (DeviceGatewayBoundException boundException) {
      CloudModel cloudModel = boundException.getCloudModel();
      if (cloudModel.resource_type == Resource_type.GATEWAY) {
        Set<String> proxyIds = new HashSet<>(cloudModel.gateway.proxy_ids);
        System.err.printf("Retrying delete %s with bound devices: %s%n", deviceId,
            setOrSize(proxyIds));
        cloudIotManager.deleteDevice(deviceId, proxyIds);
      } else if (cloudModel.resource_type == Resource_type.DIRECT) {
        Set<String> gatewayIds = ImmutableSet.of(cloudModel.gateway.gateway_id);
        System.err.printf("Unbinding %s from bound gateways: %s%n", deviceId, gatewayIds);
        unbindDevicesFromGateways(allDevices, gatewayIds);
        System.err.printf("Retrying delete %s%n", deviceId);
        cloudIotManager.deleteDevice(deviceId, null);
      } else {
        throw new RuntimeException("Unknown cloud model resource type", boundException);
      }
    } catch (Exception e) {
      throw new RuntimeException("While deleting device " + deviceId, e);
    }

    cloudModels.remove(deviceId);
  }

  /**
   * Unbind all devices for the list of gateways. This is synchronized to ensure that only one
   * instance at a time, since it's a global operation that doesn't need to be executed for each
   * device individually.
   */
  private void unbindDevicesFromGateways(Set<String> allDevices, Set<String> boundGateways) {
    boundGateways.forEach(gatewayId -> {
      synchronized (ModelOperation.UNBIND) {
        Map<String, CloudModel> boundDevices = cloudIotManager.fetchDevice(gatewayId).device_ids;
        Set<String> toUnbind = new HashSet<>(intersection(allDevices, boundDevices.keySet()));
        System.err.printf("Unbinding from gateway %s: %s%n", gatewayId, setOrSize(toUnbind));
        boolean multiple = toUnbind.size() > UNBIND_SET_SIZE;
        while (!toUnbind.isEmpty()) {
          Set<String> limitedSet = limitSetSize(toUnbind, UNBIND_SET_SIZE);
          ifTrueThen(multiple, () -> System.err.printf("Unbinding subset from %s: %s%n", gatewayId,
              setOrSize(limitedSet)));
          cloudIotManager.bindDevices(limitedSet, gatewayId, false);
          toUnbind.removeAll(limitedSet);
        }
      }
    });
  }

  private Set<String> limitSetSize(Set<String> toUnbind, int sizeLimit) {
    return toUnbind.stream().limit(sizeLimit).collect(Collectors.toSet());
  }

  private boolean processLocalDevice(String localName, AtomicInteger processedDeviceCount,
      int totalCount) {
    LocalDevice localDevice = workingDevices.get(localName);
    if (!localDevice.isValid()) {
      System.err.println("Skipping invalid device " + localName);
      return false;
    }
    if (shouldLimitDevice(localDevice)) {
      System.err.println("Skipping active device " + localDevice.getDeviceId());
      return false;
    }

    if (doNotUpdate && cloudModels.containsKey(localName)) {
      return false;
    }

    Instant start = Instant.now();
    int count = processedDeviceCount.incrementAndGet();
    boolean created = false;
    try {
      localDevice.writeConfigFile();
      if (cloudModels != null && updateCloudIoT) {
        created = pushToCloudIoT(localName, localDevice);
        sendUpdateMessages(localDevice);
        cloudModels.computeIfAbsent(localName, name -> fetchDevice(localName, true));
        sendSwarmMessage(localDevice);

        Duration between = Duration.between(start, Instant.now());
        double seconds = (between.getSeconds() + between.getNano() / 1e9) / runnerThreads;
        System.err.printf("Processed %s (%d/%d) in %.03fs (%s)%n", localName, count, totalCount,
            seconds, created ? "add" : "update");
      }
      ifTrueThen(updateMetadata, () -> updateDeviceMetadata(localName));
    } catch (Exception e) {
      System.err.printf("Error processing %s: %s%n", localDevice.getDeviceId(), e);
      localDevice.captureError(ExceptionCategory.registering, e);
    }
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

    if (!localDevice.isDirect()) {
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

  private boolean pushToCloudIoT(String localName, LocalDevice localDevice) {
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
      cloudIotManager.deleteDevice(localName, null);
    }
    return cloudIotManager.registerDevice(localName, localDeviceSettings);
  }

  private boolean preDeleteDevice(String localName) {
    if (!workingDevices.get(localName).isGateway()) {
      return false;
    }
    CloudModel registeredDevice = cloudIotManager.getRegisteredDevice(localName);
    return ifNotNullGet(registeredDevice, Registrar::isNotGateway, false);
  }

  private Set<String> getExplicitDevices() {
    if (deviceList == null && optimizeRun) {
      return new HashSet<>(changedDevices);
    }
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

  private Set<String> processExtraDevices(Set<String> extraDevices) {
    try {
      Map<String, CloudModel> extras = new ConcurrentHashMap<>();
      if (extraDevices.isEmpty()) {
        return extras.keySet();
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
      reapExtraDevices(difference(siteModel.getExtraDevices(), extraDevices));
      return extras.keySet();
    } catch (Exception e) {
      throw new RuntimeException(format("Processing %d extra devices", extraDevices.size()), e);
    }
  }

  private CloudModel processExtra(String extraName, AtomicInteger alreadyBlocked) {
    CloudModel cloudModel = cloudModels.get(extraName);
    boolean isBlocked = isTrue(cloudModel.blocked);
    ifTrueThen(isBlocked, alreadyBlocked::incrementAndGet);
    try {
      if (blockUnknown && !isBlocked) {
        System.err.println("Blocking device " + extraName);
        cloudIotManager.blockDevice(extraName, true);
        cloudModel.blocked = true;
      }
      cloudModel.operation = ifTrueGet(cloudModel.blocked, ModelOperation.BLOCK,
          ModelOperation.ALLOW);
    } catch (Exception e) {
      cloudModel.operation = ModelOperation.ERROR;
      cloudModel.detail = friendlyStackTrace(e);
      System.err.printf("Blocking device %s: %s%n", extraName, cloudModel.detail);
    }
    writeExtraDevice(extraName, cloudModel);
    return cloudModel;
  }

  private void reapExtraDevices(Set<String> toReap) {
    toReap.forEach(extraDeviceId -> {
      File file = siteModel.getExtraDir(extraDeviceId);
      try {
        FileUtils.deleteDirectory(file);
        System.err.println("Deleted extraneous extra device directory " + extraDeviceId);
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
      ifNotTrueThen(augmentedModel.equals(loadFile(CloudModel.class, modelFile)), () -> {
        System.err.println("Writing extra device model to " + devPath);
        writeFile(augmentedModel, modelFile);
        updateExtraMetadata(extraName, extraDir);
      });
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
      attributes.put(DEVICE_ID_KEY, localDevice.getDeviceId());
      attributes.put(DEVICE_NUM_KEY, localDevice.getDeviceNumId());
      attributes.put(REGISTRY_ID_PROPERTY_KEY, cloudIotManager.getRegistryId());
      attributes.put(PROJECT_ID_PROPERTY_KEY, cloudIotManager.getProjectId());
      attributes.put(SUBTYPE_PROPERTY_KEY, subType);
      attributes.put(SUBFOLDER_PROPERTY_KEY, subFolder.value());
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
      Map<String, Set<LocalDevice>> gatewayBindings = localDevices.values().stream()
          .filter(LocalDevice::isProxied)
          .collect(groupingBy(LocalDevice::getGatewayId, Collectors.toSet()));
      AtomicInteger bindingCount = new AtomicInteger();
      System.err.printf("Binding devices to gateways: %s%n", setOrSize(gatewayBindings.keySet()));
      gatewayBindings.forEach((gatewayId, proxiedDevices) -> {
        parallelExecute(() -> {
          try {
            Set<String> proxyIds = proxiedDevices.stream().map(LocalDevice::getDeviceId)
                .collect(Collectors.toSet());
            Set<String> boundDevices = ofNullable(cloudIotManager.fetchBoundDevices(gatewayId))
                .orElse(ImmutableSet.of());
            System.err.printf("Already bound to %s: %s%n", gatewayId, setOrSize(boundDevices));
            SetView<String> toBind = difference(proxyIds, boundDevices);
            int count = bindingCount.incrementAndGet();
            System.err.printf("Binding %s to %s (%d/%d)%n", setOrSize(toBind), gatewayId, count,
                gatewayBindings.size());
            cloudIotManager.bindDevices(toBind, gatewayId, true);
          } catch (Exception e) {
            proxiedDevices.forEach(localDevice ->
                localDevice.captureError(ExceptionCategory.binding, e));
          }
        });
      });

      System.err.printf("Waiting for device binding...%n");
      dynamicTerminate();

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Finished binding gateways in %.03f%n", seconds);
    } catch (Exception e) {
      throw new RuntimeException("While binding devices to gateways", e);
    }
  }

  private synchronized void dynamicTerminate() throws InterruptedException {
    try {
      if (executor == null) {
        return;
      }
      executor.shutdown();
      System.err.printf("Waiting for tasks to complete...%n");
      while (!executor.awaitTermination(QuerySpeed.SHORT.seconds(), TimeUnit.SECONDS)
          && cloudIotManager.stillActive()) {
        System.err.println("Still waiting...");
      }
      if (!executor.isTerminated()) {
        throw new RuntimeException("Incomplete executor termination.");
      }
    } finally {
      executor = null;
      executing = null;
    }
  }

  private synchronized void dynamicTerminate(int expected) {
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
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while waiting for termination", e);
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
    int total = cloudModels.size() != 0 ? cloudModels.size() : workingDevices.size();
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

  private Map<String, LocalDevice> getLocalDevices(Set<String> specifiedDevices) {
    return allDevices.entrySet().stream()
        .filter(entry -> specifiedDevices == null || specifiedDevices.contains(entry.getKey()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private void preprocessMetadata(Map<String, LocalDevice> workingDevices) {
    preprocessSiteMetadata();
    preprocessDeviceMetadata(workingDevices);
  }

  private void preprocessSiteMetadata() {
    allDevices.values().forEach(LocalDevice::configure);
    allDevices.values().stream().filter(LocalDevice::isGateway).forEach(this::preprocessGateway);
  }

  private void preprocessGateway(LocalDevice gateway) {
    try {
      Metadata metadata = gateway.getMetadata();
      ifNullThen(metadata.gateway, () -> metadata.gateway = new GatewayModel());
      GatewayModel gatewayMetadata = metadata.gateway;
      String gatewayId = gateway.getDeviceId();
      ifNotNullThen(gatewayMetadata.proxy_ids,
          list -> normalizeChildren(gatewayId, listUniqueSet(list)),
          () -> gatewayMetadata.proxy_ids = new ArrayList<>(proxiedChildren(gatewayId)));
    } catch (Exception e) {
      gateway.captureError(ExceptionCategory.preprocess, e);
    }
  }

  private void normalizeChildren(String gatewayId, Set<String> proxyIds) {
    List<Exception> exceptions = new ArrayList<>();
    allDevices.entrySet().stream().filter(entry -> proxyIds.contains(entry.getKey()))
        .filter(entry -> entry.getValue().getMetadata() != null)
        .forEach(entry -> {
          String deviceId = entry.getKey();
          LocalDevice local = entry.getValue();
          try {
            checkState(!local.isDirect(), "Should not proxy direct device " + deviceId);
            checkState(!local.isGateway(), "Should not proxy gateway device " + deviceId);
            Metadata deviceMetadata = local.getMetadata();
            ifNullThen(deviceMetadata.gateway, () -> deviceMetadata.gateway = new GatewayModel());
            GatewayModel gatewayMetadata = deviceMetadata.gateway;
            ifNullThen(gatewayMetadata.gateway_id, () -> gatewayMetadata.gateway_id = gatewayId);
            checkState(gatewayMetadata.gateway_id.equals(gatewayId),
                format("gateway_id mismatch for %s: %s != %s",
                    deviceId, gatewayMetadata.gateway_id, gatewayId));
            checkState(local.isProxied(), "Does not identify as proxied device " + deviceId);
          } catch (Exception e) {
            exceptions.add(e);
            ifNotNullThen(workingDevices.get(deviceId),
                device -> device.captureError(ExceptionCategory.proxy, e));
          }
        });
    ifNotEmptyThen(Sets.symmetricDifference(proxiedChildren(gatewayId), proxyIds), diff ->
        exceptions.add(new RuntimeException(format("%s proxy_id mismatch: %s", gatewayId, diff))));
    ifNotEmptyThen(exceptions, list -> ifNotNullThen(workingDevices.get(gatewayId),
        gateway -> gateway.captureError(ExceptionCategory.proxy, new ExceptionList(exceptions))));
  }

  private Set<String> proxiedChildren(String gatewayId) {
    return allDevices.entrySet().stream()
        .filter(entry ->
            gatewayId.equals(catchToNull(() -> entry.getValue().getMetadata().gateway.gateway_id)))
        .map(Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
  }

  private void preprocessDeviceMetadata(Map<String, LocalDevice> workingDevices) {
    workingDevices.values().forEach(localDevice -> {
      try {
        localDevice.preprocessMetadata();
      } catch (ValidationError error) {
        throw new RuntimeException("While preprocessing metadata", error);
      } catch (Exception e) {
        localDevice.captureError(ExceptionCategory.metadata, e);
      }
    });
  }

  private void validateKeys(LocalDevice localDevice) {
    if (!localDevice.isDirect()) {
      return;
    }
    CloudDeviceSettings settings = localDevice.getSettings();
    String deviceName = localDevice.getDeviceId();
    for (Credential credential : settings.credentials) {
      String previous = usedCredentials.put(credential, deviceName);
      if (previous != null) {
        throw new RuntimeException(format(
            "Duplicate credentials found for %s & %s", previous, deviceName));
      }
    }
  }

  private Map<String, LocalDevice> loadDevices(List<String> devices) {
    Set<String> actual = devices.stream()
        .filter(deviceName -> siteModel.deviceExists(deviceName)).collect(Collectors.toSet());
    return actual.stream().collect(Collectors.toMap(name -> name, this::makeLocalDevice));
  }

  private void initializeLocalDevices() {
    System.err.printf("Initializing %d local devices...%n", workingDevices.size());
    initializeDevices(workingDevices);
    preprocessMetadata(workingDevices);
    expandDependencies(workingDevices);
    allWorking(LocalDevice::initializeSettings, "initialize settings", ExceptionCategory.settings);
  }

  private void finalizeLocalDevices() {
    previewModelRegistry();

    allWorking(LocalDevice::writeNormalized, "writing normalized", ExceptionCategory.metadata);
    allWorking(this::previewModel, "previewing model", ExceptionCategory.updating);
    allWorking(LocalDevice::validateExpectedFiles, "validating expected", ExceptionCategory.files);
    allWorking(LocalDevice::validateSamples, "validate samples", ExceptionCategory.samples);
    allWorking(this::validateKeys, "validating keys", ExceptionCategory.credentials);
    allWorking(this::processExternals, "process externals", ExceptionCategory.externals);
  }

  private void processExternals(LocalDevice localDevice) {
    List<Exception> exceptionList = new ArrayList<>();
    Map<String, LinkExternalsModel> ext = catchToNull(() -> localDevice.getMetadata().externals);
    ifNotNullThen(ext, map -> map.forEach((key, value) -> {
      try {
        requireNonNull(processors.get(key), "Missing external processor " + key).process(
            localDevice);
      } catch (Exception e) {
        exceptionList.add(e);
      }
    }));
    ExceptionList.throwIfNotEmpty(exceptionList);
  }

  private void allWorking(Consumer<LocalDevice> action, String message,
      ExceptionCategory category) {
    AtomicInteger actionCount = new AtomicInteger();
    int setSize = workingDevices.size();
    System.err.printf("Starting %s for %d devices...%n", message, setSize);
    workingDevices.values().forEach(localDevice -> parallelExecute(() -> {
      int baseCount = actionCount.incrementAndGet();
      ifTrueThen(baseCount % BATCH_REPORT_SIZE == 0, () -> System.err.printf(
          "Execute %s for device %d/%d...%n", message, baseCount, setSize));
      try {
        action.accept(localDevice);
      } catch (Exception e) {
        localDevice.captureError(category, e);
      }
    }));

    dynamicTerminate(setSize);
    ifTrueThen(setSize > BATCH_REPORT_SIZE,
        () -> System.err.printf("Finished %s for %d devices.%n", message, setSize));
  }

  @CommandLineOption(short_form = "-T", description = "Expand transitive dependencies")
  private void setExpandDependencies() {
    expandDependencies = true;
  }

  private void expandDependencies(Map<String, LocalDevice> workingDevices) {
    if (!expandDependencies) {
      return;
    }

    Set<String> proxyIds = workingDevices.values().stream()
        .filter(LocalDevice::isGateway)
        .map(LocalDevice::getProxyIds)
        .flatMap(List::stream).collect(Collectors.toSet());
    SetView<String> newDevices = difference(proxyIds, workingDevices.keySet());
    Map<String, LocalDevice> newEntries = newDevices.stream()
        .collect(Collectors.toMap(Function.identity(), allDevices::get));
    workingDevices.putAll(newEntries);
    initializeDevices(newEntries);
    System.err.printf("Added %d transitive devices to working set.%n", newDevices.size());
  }

  private void previewModelRegistry() {
    ifTrueThen(metadataModelOut,
        () -> cloudIotManager.updateRegistry(getSiteMetadata(), ModelOperation.PREVIEW));
  }

  private void previewModel(LocalDevice device) {
    ifTrueThen(metadataModelOut,
        () -> cloudIotManager.updateDevice(device.getDeviceId(), device.getSettings(),
            ModelOperation.PREVIEW));
  }

  private void initializeDevices(Map<String, LocalDevice> localDevices) {
    localDevices.values().forEach(localDevice -> {
      try {
        localDevice.initialize();
      } catch (Exception e) {
        localDevice.captureError(ExceptionCategory.settings, e);
        return;
      }
      try {
        localDevice.loadCredentials();
      } catch (Exception e) {
        localDevice.captureError(ExceptionCategory.credentials, e);
        return;
      }

      if (cloudIotManager != null) {
        try {
          localDevice.validateEnvelope(
              cloudIotManager.getRegistryId(), cloudIotManager.getSiteName());
        } catch (Exception e) {
          localDevice.captureError(ExceptionCategory.envelope,
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

  @CommandLineOption(short_form = "-w", description = "Strict warning checking (pedantic mode)")
  private void setStrictWarnings() {
    strictWarnings = true;
    ifNotNullThen(siteModel, SiteModel::setStrictWarnings);
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

  @SuppressWarnings("unchecked")
  public List<MockAction> getMockActions() {
    return (List<MockAction>) (List<?>) cloudIotManager.getMockActions();
  }

  @CommandLineOption(short_form = "-a", arg_name = "alternate",
      description = "Set alternate registry")
  private void setTargetRegistry(String altRegistry) {
    siteModel.getExecutionConfiguration().registry_id = altRegistry;
    siteModel.getExecutionConfiguration().alt_registry = null;
  }

  @CommandLineOption(short_form = "-A", description = "Auto process alt_registry from config")
  private void setAutoAltRegistry() {
    autoAltRegistry = true;
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
