package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.daq.mqtt.sequencer.semantic.SemanticValue.actualize;
import static com.google.daq.mqtt.util.CloudIotManager.EMPTY_CONFIG;
import static com.google.daq.mqtt.util.ConfigManager.configFrom;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.daq.mqtt.validator.Validator.ATTRIBUTE_FILE_FORMAT;
import static com.google.daq.mqtt.validator.Validator.MESSAGE_FILE_FORMAT;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.MESSAGE_KEY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.changedLines;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static com.google.udmi.util.SiteModel.METADATA_JSON;
import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;
import static udmi.schema.Category.VALIDATION_FEATURE_CAPABILITY;
import static udmi.schema.Category.VALIDATION_FEATURE_SCHEMA;
import static udmi.schema.Category.VALIDATION_FEATURE_SEQUENCE;
import static udmi.schema.Envelope.SubFolder.UPDATE;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;
import static udmi.schema.FeatureDiscovery.FeatureStage.STABLE;
import static udmi.schema.Level.ERROR;
import static udmi.schema.Level.NOTICE;
import static udmi.schema.Level.WARNING;
import static udmi.schema.SequenceValidationState.SequenceResult.ERRR;
import static udmi.schema.SequenceValidationState.SequenceResult.FAIL;
import static udmi.schema.SequenceValidationState.SequenceResult.PASS;
import static udmi.schema.SequenceValidationState.SequenceResult.SKIP;
import static udmi.schema.SequenceValidationState.SequenceResult.START;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MockPublisher;
import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.util.ObjectDiffEngine;
import com.google.daq.mqtt.validator.AugmentedSystemConfig;
import com.google.daq.mqtt.validator.ReportingDevice;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.CleanDateFormat;
import com.google.udmi.util.Common;
import com.google.udmi.util.DiffEntry;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Bucket;
import udmi.schema.CapabilityValidationState;
import udmi.schema.CapabilityValidationState.CapabilityResult;
import udmi.schema.Config;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Entry;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FeatureDiscovery;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.FeatureValidationState;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointsetEvents;
import udmi.schema.SchemaValidationState;
import udmi.schema.Scoring;
import udmi.schema.SequenceValidationState;
import udmi.schema.SequenceValidationState.SequenceResult;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvents;
import udmi.schema.TestingSystemConfig;
import udmi.schema.ValidationState;

/**
 * Validate a device using a sequence of message exchanges.
 */
public class SequenceBase {

  public static final String EMPTY_MESSAGE = "{}";
  public static final String SERIAL_NO_MISSING = "//";
  public static final String VALIDATION_STATE_TOPIC = "validation/state";
  public static final String SCHEMA_PASS_DETAIL = "No schema violations found";
  public static final String STATE_UPDATE_MESSAGE_TYPE = "state_update";
  public static final String RESET_CONFIG_MARKER = "reset_config";
  public static final String SYSTEM_STATUS_MESSAGE = "significant system status";
  public static final String HAS_STATUS_PREFIX = "has ";
  public static final String NOT_STATUS_PREFIX = "no ";
  public static final String STATUS_CHECK_SUFFIX = " exists";
  public static final String SCHEMA_BUCKET = "schemas";
  public static final int SCHEMA_SCORE_TOTAL = 10;
  public static final int CAPABILITY_SCORE = 1;
  public static final String STATUS_LEVEL_VIOLATION = "STATUS_LEVEL";
  public static final String DEVICE_STATE_SCHEMA = "device_state";
  private static final String ALL_CHANGES = "";
  private static final int SEQUENCER_FUNCTIONS_VERSION = Validator.TOOLS_FUNCTIONS_VERSION;
  private static final int SEQUENCER_FUNCTIONS_ALPHA = SEQUENCER_FUNCTIONS_VERSION;
  private static final long CONFIG_BARRIER_MS = 1000;
  private static final String START_END_MARKER = "################################";
  private static final Date LONG_TIME_AGO = new Date(9217321);
  private static final String RESULT_FORMAT = "RESULT %s %s %s %s %s/%s %s";
  private static final String CAPABILITY_FORMAT = "CPBLTY %s %s %s %s %s/%s %s";
  private static final String SCHEMA_FORMAT = "SCHEMA %s %s %s %s %s %s";
  private static final String TESTS_OUT_DIR = "tests";
  private static final String EVENTS_PREFIX = SubType.EVENTS + "_";
  private static final String SYSTEM_EVENTS_MESSAGE_BASE = EVENTS_PREFIX + "system";
  private static final int CONFIG_UPDATE_DELAY_MS = 8 * 1000;
  private static final int NORM_TIMEOUT_MS = 300 * 1000;
  private static final String RESULT_LOG_FILE = "RESULT.log";
  private static final String OUT_DEVICE_FORMAT = "out/devices/%s/metadata_mod.json";
  private static final String SUMMARY_OUTPUT_FORMAT = "out/sequencer_%s.json";
  private static final Map<Class<?>, SubFolder> CLASS_SUBFOLDER_MAP = ImmutableMap.of(
      SystemEvents.class, SubFolder.SYSTEM,
      PointsetEvents.class, SubFolder.POINTSET,
      DiscoveryEvents.class, SubFolder.DISCOVERY
  );
  private static final Map<String, Class<?>> EXPECTED_UPDATES = ImmutableMap.of(
      SubType.CONFIG.value(), Config.class,
      SubType.STATE.value(), State.class
  );
  private static final Map<String, AtomicInteger> UPDATE_COUNTS = new HashMap<>();
  private static final String LOCAL_PREFIX = "local_";
  private static final String UPDATE_SUBFOLDER = UPDATE.value();
  private static final String STATE_SUBTYPE = SubType.STATE.value();
  private static final String CONFIG_SUBTYPE = SubType.CONFIG.value();
  private static final String LOCAL_CONFIG_UPDATE = LOCAL_PREFIX + UPDATE_SUBFOLDER;
  private static final String SEQUENCER_LOG = "sequencer.log";
  private static final String SYSTEM_LOG = "system.log";
  private static final String SEQUENCE_MD = "sequence.md";
  private static final int LOG_TIMEOUT_SEC = 10;
  private static final long ONE_SECOND_MS = 1000;
  private static final int EXIT_CODE_PRESERVE = -9;
  private static final String SYSTEM_TESTING_MARKER = "system.testing";
  private static final BiMap<SequenceResult, Level> RESULT_LEVEL_MAP = ImmutableBiMap.of(
      START, Level.INFO,
      PASS, Level.NOTICE,
      SKIP, Level.WARNING,
      FAIL, Level.ERROR,
      ERRR, Level.CRITIAL
  );
  private static final BiMap<CapabilityResult, Level> CAPABILITY_RESULT_MAP = ImmutableBiMap.of(
      CapabilityResult.PASS, Level.NOTICE,
      CapabilityResult.FAIL, Level.ERROR
  );
  private static final Map<SubFolder, String> sentConfig = new HashMap<>();
  private static final ObjectDiffEngine SENT_CONFIG_DIFFERNATOR = new ObjectDiffEngine();
  private static final ObjectDiffEngine RECV_CONFIG_DIFFERNATOR = new ObjectDiffEngine();
  private static final ObjectDiffEngine RECV_STATE_DIFFERNATOR = new ObjectDiffEngine();
  private static final Set<String> configTransactions = new ConcurrentSkipListSet<>();
  private static final AtomicReference<String> stateTransaction = new AtomicReference<>();
  private static final int MINIMUM_TEST_SEC = 15;
  private static final Date RESET_LAST_START = new Date(73642);
  private static final Date stateCutoffThreshold = Date.from(Instant.now());
  private static final String FAKE_DEVICE_ID = "TAP-1";
  private static final String NO_EXTRA_DETAIL = "no logs";
  private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(10);
  private static final Duration CONFIG_WAIT_TIME = Duration.ofSeconds(30);
  private static final Duration LOG_WAIT_TIME = Duration.ofSeconds(30);
  private static final Duration DEFAULT_LOOP_TIMEOUT = Duration.ofHours(30);
  private static final Set<String> SYSTEM_STATE_CHANGES = ImmutableSet.of(
      "timestamp", "system.last_config", "system.status");
  private static final long EVENT_WAIT_DELAY_MS = 1000;
  private static final Duration STATE_TIMESTAMP_ERROR_THRESHOLD = Duration.ofMinutes(20);
  private static final Set<IotAccess.IotProvider> SEQUENCER_PROVIDERS = ImmutableSet.of(
      IotProvider.GBOS, IotProvider.MQTT, IotProvider.GREF);
  public static final String SEQUENCER_TOOL_NAME = "sequencer";
  protected static Metadata deviceMetadata;
  protected static String projectId;
  protected static String cloudRegion;
  protected static String registryId;
  protected static String altRegistry;
  protected static IotReflectorClient altClient;
  protected static String serialNo;
  protected static SiteModel siteModel;
  public static ExecutionConfiguration exeConfig;
  private static Validator messageValidator;
  private static ValidationState validationState;
  private static int logLevel;
  private static File deviceOutputDir;
  private static File resultSummary;
  private static MessagePublisher client;
  private static SequenceBase activeInstance;
  private static MessageBundle stashedBundle;
  private static boolean enableAllTargets = true;
  private static boolean useAlternateClient;

  static {
    // Sanity check to make sure ALPHA version is increased if forced by increased BETA.
    checkState(SEQUENCER_FUNCTIONS_ALPHA >= SEQUENCER_FUNCTIONS_VERSION,
        "ALPHA functions version should not be > BETA");
  }

  private final Map<String, CaptureMap> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private final Queue<Entry> logEntryQueue = new LinkedBlockingDeque<>();
  private final Stack<String> waitingCondition = new Stack<>();
  private final SortedMap<String, List<Entry>> validationResults = new TreeMap<>();
  private final Map<String, String> deviceStateViolations = new ConcurrentHashMap<>();
  private final Map<Class<? extends Capability>, Exception> capExcept = new ConcurrentHashMap<>();
  private final Set<String> allowedDeviceStateChanges = new HashSet<>();
  @Rule
  public Timeout globalTimeout = new Timeout(NORM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  @Rule
  public SequenceTestWatcher testWatcher = new SequenceTestWatcher();
  protected Config deviceConfig;
  protected State deviceState;
  protected boolean configAcked;
  protected String lastSerialNo;
  private boolean doPartialUpdates;
  private boolean resetRequired = true;
  private int maxAllowedStatusLevel;
  private String extraField;
  private Instant lastConfigUpdate;
  private boolean enforceSerial;
  private String testName;
  private String testSummary;
  private Bucket testBucket;
  private FeatureStage testStage;
  private long startTestTimeMs;
  private long startCaptureTime;
  private File testDir;
  private PrintWriter sequencerLog;
  private PrintWriter sequenceMd;
  private PrintWriter systemLog;
  private boolean recordMessages;
  private boolean recordSequence;
  private int previousEventCount;
  private SequenceResult testResult;
  private int startStateCount;
  private Boolean expectedSystemStatus;
  private Description testDescription;
  private SubFolder testSchema;
  private int lastStatusLevel;
  private final CaptureMap otherEvents = new CaptureMap();
  private final AtomicBoolean waitingForConfigSync = new AtomicBoolean();
  private static String sessionPrefix;
  private static Scoring scoringResult;
  private Date configStateStart;

  private static void setupSequencer() {
    exeConfig = ofNullable(exeConfig).orElseGet(SequenceRunner::ensureExecutionConfig);
    if (client != null) {
      return;
    }
    final String key_file;
    try {
      messageValidator = new Validator(exeConfig, SequenceBase::validatorLogger);
      siteModel = new SiteModel(checkNotNull(exeConfig.site_model, "site_model not defined"));
      projectId = checkNotNull(exeConfig.project_id, "project_id not defined");
      checkNotNull(exeConfig.udmi_version, "udmi_version not defined");
      logLevel = Level.valueOf(checkNotNull(exeConfig.log_level, "log_level not defined"))
          .value();
      key_file = checkNotNull(exeConfig.key_file, "key_file not defined");
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("While processing validator config", e);
    }

    cloudRegion = exeConfig.cloud_region;
    registryId = SiteModel.getRegistryActual(exeConfig);

    deviceMetadata = readDeviceMetadata();

    serialNo = ofNullable(exeConfig.serial_no)
        .orElseGet(() -> GeneralUtils.catchToNull(() -> deviceMetadata.system.serial_no));

    File baseOutputDir = siteModel.getSubdirectory("out");
    deviceOutputDir = new File(baseOutputDir, "devices/" + getDeviceId());
    deviceOutputDir.mkdirs();

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Loading reflector key file from %s%n", new File(key_file).getAbsolutePath());
    System.err.printf("Validating against device %s serial %s%n", getDeviceId(), serialNo);
    client = checkNotNull(getPublisherClient(), "primary client not created");
    client.activate();
    sessionPrefix = client.getSessionPrefix();

    String udmiNamespace = exeConfig.udmi_namespace;
    String altRegistryId = exeConfig.alt_registry;
    String registrySuffix = exeConfig.registry_suffix;
    altRegistry = SiteModel.getRegistryActual(udmiNamespace, altRegistryId, registrySuffix);
    altClient = getAlternateClient();
  }

  private static void validatorLogger(Level level, String message) {
    activeInstance.log(message, level);
  }

  private static MessagePublisher getPublisherClient() {
    boolean isMockProject = exeConfig.project_id.equals(SiteModel.MOCK_PROJECT);
    boolean failFast = SiteModel.MOCK_PROJECT.equals(exeConfig.alt_project);
    return isMockProject ? getMockClient(failFast) : getReflectorClient();
  }

  static MockPublisher getMockClient(boolean failFast) {
    return ofNullable((MockPublisher) client).orElseGet(() -> new MockPublisher(failFast));
  }

  private static IotReflectorClient getAlternateClient() {
    if (altRegistry == null) {
      System.err.println("No alternate registry configured, disabling");
      return null;
    }

    ExecutionConfiguration altConfiguration = GeneralUtils.deepCopy(exeConfig);
    altConfiguration.registry_id = exeConfig.alt_registry;
    altConfiguration.registry_suffix = exeConfig.registry_suffix;
    altConfiguration.udmi_namespace = exeConfig.udmi_namespace;
    altConfiguration.alt_registry = null;

    return getReflectorClient(altConfiguration);
  }

  private static boolean messageFilter(Envelope envelope) {
    return true;
  }

  private static int getRequiredFunctionsVersion() {
    return SequenceRunner.processStage(ALPHA) ? SEQUENCER_FUNCTIONS_ALPHA
        : SEQUENCER_FUNCTIONS_VERSION;
  }

  @VisibleForTesting
  static void resetState() {
    System.err.println("Resetting SequenceBase state for testing");
    exeConfig = null;
    client = null;
    validationState = null;
  }

  private static Metadata readDeviceMetadata() {
    File moddataFile = siteModel.getSubdirectory(format(OUT_DEVICE_FORMAT, getDeviceId()));
    File metadataFile = siteModel.getDeviceFile(getDeviceId(), METADATA_JSON);
    System.err.println("Checking for modified metadata file " + moddataFile.getAbsolutePath());
    File useFile = moddataFile.exists() ? moddataFile : metadataFile;
    System.err.println("Reading device metadata file " + useFile.getPath());
    return loadFileRequired(Metadata.class, useFile);
  }

  protected static String getDeviceId() {
    return checkNotNull(exeConfig.device_id, "device_id not defined");
  }

  protected static void setDeviceId(String deviceId) {
    if (exeConfig != null) {
      exeConfig.device_id = deviceId;
    }
  }

  private static boolean debugLogLevel() {
    return logLevel <= Level.DEBUG.value();
  }

  private static boolean traceLogLevel() {
    return logLevel <= Level.TRACE.value();
  }

  private static void updateValidationState() {
    validationState.timestamp = cleanDate();
    JsonUtil.writeFile(validationState, getSequencerStateFile());
    String validationString = stringify(validationState);
    ifNotNullThen(client,
        () -> client.publish(getDeviceId(), VALIDATION_STATE_TOPIC, validationString));
  }

  static File getSequencerStateFile() {
    return siteModel.getSubdirectory(format(SUMMARY_OUTPUT_FORMAT, getDeviceId()));
  }

  static void processComplete(Throwable e) {
    boolean wasError = e != null;
    Entry statusEntry = new Entry();
    statusEntry.level = wasError ? ERROR.value() : Level.NOTICE.value();
    statusEntry.timestamp = cleanDate();
    statusEntry.message = wasError ? ("Error: " + Common.getExceptionMessage(e)) : "Run completed";
    statusEntry.category = VALIDATION_FEATURE_SEQUENCE;
    getValidationState().status = statusEntry;
    summarizeSchemaStages();
    ifTrueThen(exeConfig != null, SequenceBase::updateValidationState);
  }

  static void enableAllBuckets(boolean enabled) {
    enableAllTargets = enabled;
  }

  private static String makeMessageBase(Envelope attributes) {
    SubType subType = attributes.subType;
    SubFolder subFolder = attributes.subFolder;
    String gatewayId = attributes.gatewayId;
    String deviceSuffix = ofNullable(gatewayId).map(x -> "_" + attributes.deviceId).orElse("");
    return format("%s_%s%s", subType, subFolder, deviceSuffix);
  }

  private static void emitSequenceResult(SequenceResult result, String bucket, String name,
      String stage, int score, int total, String message) {
    // TODO: Clean up this hack of using a class-wide static variable to store this information.
    scoringResult = new Scoring();
    scoringResult.value = score;
    scoringResult.total = total;
    emitSequencerOut(format(RESULT_FORMAT, result, bucket, name, stage, score, total, message));
  }

  private static void emitSequencerOut(String resultString) {
    if (activeInstance != null) {
      activeInstance.notice(resultString);
    } else {
      System.err.println(resultString);
    }
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.println(resultString);
    } catch (Exception e) {
      throw new RuntimeException("While writing report summary " + resultSummary.getAbsolutePath(),
          e);
    }
  }

  private static void summarizeSchemaStages() {
    Map<String, SchemaValidationState> sortedSchemas = new TreeMap<>(validationState.schemas);
    sortedSchemas.forEach((schema, schemaResult) -> {
      Map<FeatureStage, List<SequenceValidationState>> schemaStages = new HashMap<>();
      schemaResult.sequences.forEach((sequence, sequenceResult) -> {
        schemaStages.computeIfAbsent(sequenceResult.stage, stage -> new ArrayList<>())
            .add(sequenceResult);
      });
      schemaResult.stages = schemaStages.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey, SequenceBase::summarizeSchemaResults));

      schemaResult.stages.forEach((stage, entry) -> {
        SequenceResult result = RESULT_LEVEL_MAP.inverse().get(levelFromValue(entry.level));
        String stageValue = stage.value();
        String schemaStage = schema + "_" + stageValue;
        emitSequenceResult(result, SCHEMA_BUCKET, schemaStage, stageValue.toUpperCase(),
            SCHEMA_SCORE_TOTAL, SCHEMA_SCORE_TOTAL, entry.message);
      });
    });
  }

  private static Entry summarizeSchemaResults(
      Map.Entry<FeatureStage, List<SequenceValidationState>> entry) {
    List<SequenceValidationState> values = entry.getValue();
    Set<SequenceValidationState> failures = values.stream()
        .filter(state -> state.result != PASS).collect(toSet());

    Entry logEntry = new Entry();
    logEntry.category = VALIDATION_FEATURE_SCHEMA;
    logEntry.timestamp = cleanDate();
    if (values.isEmpty()) {
      logEntry.message = "No messages validated";
      logEntry.level = WARNING.value();
    } else if (failures.isEmpty()) {
      logEntry.message = "Schema validation passed";
      logEntry.level = NOTICE.value();
    } else {
      logEntry.message = "Schema violations found";
      logEntry.level = ERROR.value();
    }
    return logEntry;
  }

  @NotNull
  private static AtomicInteger getUpdateCount(String subTypeRaw) {
    return UPDATE_COUNTS.computeIfAbsent(subTypeRaw, key -> new AtomicInteger());
  }

  private static int getStateUpdateCount() {
    return getUpdateCount(SubType.STATE.value()).get();
  }

  private static MessagePublisher getReflectorClient() {
    if (!SEQUENCER_PROVIDERS.contains(exeConfig.iot_provider)) {
      throw new IllegalArgumentException(
          format("IoT Provider '%s' not supported, should be one of: %s", exeConfig.iot_provider,
              CSV_JOINER.join(SEQUENCER_PROVIDERS)));
    }
    return getReflectorClient(exeConfig);
  }

  private static IotReflectorClient getReflectorClient(ExecutionConfiguration config) {
    try {
      return new IotReflectorClient(config, getRequiredFunctionsVersion(),
          SEQUENCER_TOOL_NAME, SequenceBase::messageFilter);
    } catch (Exception e) {
      System.err.println(
          "Could not connect to alternate registry, disabling: " + friendlyStackTrace(e));
      if (traceLogLevel()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  private static MessagePublisher altReflector() {
    return reflector(!useAlternateClient);
  }

  private static MessagePublisher reflector() {
    return reflector(useAlternateClient);
  }

  private static MessagePublisher reflector(boolean useAlternateClient) {
    return useAlternateClient ? altClient : client;
  }

  private static boolean isMatchingEntry(String category, Level exactLevel, Entry entry) {
    return category.equals(entry.category) && entry.level == exactLevel.value();
  }

  private static Map<Class<? extends Capability>, WithCapability> getCapabilities(
      Description desc) {
    try {
      AllCapabilities all = desc.getAnnotation(AllCapabilities.class);
      List<WithCapability> list = ofNullable(all).map(array -> Arrays.asList(all.value()))
          .orElseGet(ArrayList::new);
      ifNotNullThen(desc.getAnnotation(WithCapability.class), list::add);
      return list.stream()
          .collect(Collectors.toMap(WithCapability::value, cap -> cap));
    } catch (Exception e) {
      throw new RuntimeException("While extracting capabilities for " + desc.getMethodName(), e);
    }
  }

  private static ValidationState getValidationState() {
    ifNullThen(validationState, SequenceBase::initializeValidationState);
    return validationState;
  }

  static void initializeValidationState() {
    validationState = new ValidationState();
    validationState.features = new HashMap<>();
    validationState.schemas = new HashMap<>();
    validationState.start_time = new Date();
    validationState.udmi_version = Common.getUdmiVersion();
    validationState.cloud_version = ifNotNullGet(client, MessagePublisher::getVersionInformation);
    Entry statusEntry = new Entry();
    boolean startupError = exeConfig == null || siteModel == null;
    statusEntry.level = (startupError ? ERROR : NOTICE).value();
    statusEntry.message = startupError ? "Error starting sequence run"
        : "Starting sequence run for device " + getDeviceId();
    statusEntry.category = VALIDATION_FEATURE_SEQUENCE;
    statusEntry.timestamp = new Date();
    validationState.status = statusEntry;
    ifNotTrueThen(startupError, SequenceBase::updateValidationState);
  }

  public static void initialize() {
    setupSequencer();
    initializeValidationState();
  }

  @NotNull
  private Predicate<Map.Entry<String, List<Entry>>> isInterestingValidation() {
    return entry -> isInterestingValidation(entry.getKey());
  }

  private boolean isInterestingValidation(String schemaName) {
    String eventsSchema = format("%s%s", EVENTS_PREFIX, ifNotNullGet(testSchema, SubFolder::value));
    return schemaName.equals(eventsSchema) || schemaName.equals(STATE_UPDATE_MESSAGE_TYPE);
  }

  private Map.Entry<Integer, Integer> emitCapabilityResult(Class<? extends Capability> capability,
      Exception state,
      WithCapability cap, Bucket bucket, String methodName) {
    boolean pass = state instanceof CapabilitySuccess;
    SequenceResult result = state == null ? SKIP : (pass ? PASS : FAIL);
    String message = ifNotNullGet(state, Throwable::getMessage, "Never executed");
    String capabilityName = methodName + "." + capability.getSimpleName();
    int total = result != SKIP ? CAPABILITY_SCORE : 0;
    int score = result == PASS ? total : 0;
    emitSequencerOut(format(CAPABILITY_FORMAT,
        result, bucket.value(), capabilityName, cap.stage().name(), score, total, message));
    return new SimpleEntry<>(score, total);
  }

  protected String getAlternateEndpointHostname() {
    ifNullSkipTest(altClient, "No functional alternate registry defined");
    return altClient.getBridgeHost();
  }

  /**
   * Set the extra field test capability for device config. Used for change tracking.
   *
   * @param extraField value for the extra field
   */
  public void setExtraField(String extraField) {
    boolean extraFieldChanged = !Objects.equals(this.extraField, extraField);
    debug("extraFieldChanged " + extraFieldChanged + " because extra_field " + extraField);
    this.extraField = extraField;
  }

  /**
   * Set the last_start field. Used for change tracking.
   *
   * @param use last start value to use
   */
  public void setLastStart(Date use) {
    boolean changed = !stringify(deviceConfig.system.operation.last_start).equals(stringify(use));
    debug("last_start changed " + changed + ", last_start " + isoConvert(use));
    deviceConfig.system.operation.last_start = use;
  }

  private void withRecordSequence(boolean value, Runnable operation) {
    boolean saved = recordSequence;
    recordSequence = value;
    try {
      operation.run();
    } finally {
      recordSequence = saved;
    }
  }

  private void writeSequenceMdHeader() {
    String block = testSummary == null ? "" : format("%s\n\n", testSummary);
    sequenceMd.printf("\n## %s (%s)\n\n%s", testName, testStage.name(), block);
    sequenceMd.flush();
  }

  private String getTestSummary(Description summary) {
    Summary annotation = summary.getAnnotation(Summary.class);
    return annotation == null ? null : annotation.value();
  }

  private FeatureStage getTestStage(Description description) {
    Feature annotation = description.getAnnotation(Feature.class);
    return annotation == null ? Feature.DEFAULT_STAGE : annotation.stage();
  }

  private void resetDeviceConfig(boolean clean) {
    debug("Clear configTransactions and reset device config");
    configTransactions.clear();
    sentConfig.clear();
    deviceConfig = clean ? new Config() : configFrom(deviceMetadata).deviceConfig();
    deviceConfig.timestamp = null;
    sanitizeConfig(deviceConfig);
    deviceConfig.system.min_loglevel = Level.INFO.value();
    Date resetDate = ofNullable(catchToNull(() -> deviceState.system.operation.last_start))
        .orElse(RESET_LAST_START);
    debug("Configuring device last_start to be " + isoConvert(resetDate));
    setLastStart(SemanticDate.describe("device reported", resetDate));
    setExtraField(null);
  }

  private Config sanitizeConfig(Config config) {
    if (!(config.timestamp instanceof SemanticDate)) {
      config.timestamp = SemanticDate.describe("generated timestamp", config.timestamp);
    }
    if (!SemanticValue.isSemanticString(config.version)) {
      config.version = SemanticValue.describe("cloud udmi version", config.version);
    }
    if (config.system == null) {
      config.system = new SystemConfig();
    }
    if (config.system.operation == null) {
      config.system.operation = new Operation();
    }
    if (config.system.testing == null) {
      config.system.testing = new TestingSystemConfig();
    }
    config.system.testing.sequence_name = testName;
    return config;
  }

  /**
   * Prepare everything for an initial test run.
   */
  @Before
  public void setUp() {
    checkNotNull(activeInstance, "Active sequencer instance not setup, aborting");

    assumeTrue(format("Feature bucket %s not enabled", testBucket.key()),
        isBucketEnabled(testBucket));

    assertTrue("exceptions map should be empty", capExcept.isEmpty());
    assertTrue("allowed changes map should be empty", allowedDeviceStateChanges.isEmpty());

    if (!deviceSupportsState()) {
      boolean featureDisabled = ifNotNullGet(testDescription.getAnnotation(Feature.class),
          feature -> !feature.nostate(), true);
      ifTrueSkipTest(featureDisabled, "State testing disabled");
      notice("Running test with state checks disabled");
    }

    waitingConditionStart("starting test wrapper");
    checkState(reflector().isActive(), "Reflector is not currently active");

    // Old messages can sometimes take a while to clear out, so need some delay for stability.
    // TODO: Minimize time, or better yet find deterministic way to flush messages.
    safeSleep(CONFIG_UPDATE_DELAY_MS);

    doPartialUpdates = false;
    configAcked = false;
    enforceSerial = false;
    recordMessages = true;
    recordSequence = false;
    maxAllowedStatusLevel = NOTICE.value();

    resetConfig(resetRequired);

    updateConfig("initial setup");

    debug(format("Stale state cutoff threshold is %s", isoConvert(stateCutoffThreshold)));
    queryState();

    ifTrueThen(deviceSupportsState(),
        () -> untilTrue("initial device state", () -> deviceState != null));
    checkThatHasNoInterestingSystemStatus();

    // Do this late in the sequence to make sure any state is cleared out from previous test.
    startStateCount = getStateUpdateCount();
    startCaptureTime = System.currentTimeMillis();
    clearReceivedEvents();
    validationResults.clear();

    waitForConfigSync();

    doPartialUpdates = true;
    recordSequence = true;
    waitingConditionStart("executing test");

    debug(format("stage begin %s at %s", currentWaitingCondition(), timeSinceStart()));
  }

  private boolean deviceSupportsState() {
    return !isTrue(catchToNull(() -> deviceMetadata.testing.nostate));
  }

  protected boolean isBucketEnabled(Bucket bucket) {
    if (bucket == SYSTEM || enableAllTargets || deviceMetadata.features == null) {
      return true;
    }
    FeatureDiscovery metadata = deviceMetadata.features.get(bucket.value());
    if (metadata == null) {
      return false;
    }
    return ofNullable(metadata.stage).orElse(STABLE).compareTo(PREVIEW) >= 0;
  }

  protected void resetConfig() {
    resetConfig(true);
  }

  protected void resetConfig(boolean fullReset) {
    allowDeviceStateChange(ALL_CHANGES);

    recordSequence("Force reset config");
    withRecordSequence(false, () -> {
      debug("Starting reset_config full reset " + fullReset);
      if (fullReset) {
        expectedSystemStatus = null;
        resetDeviceConfig(true);
        setExtraField(RESET_CONFIG_MARKER);
        deviceConfig.system.testing.sequence_name = RESET_CONFIG_MARKER;
        SENT_CONFIG_DIFFERNATOR.resetState(deviceConfig);
        if (doPartialUpdates) {
          updateConfig("full reset");
          untilHasNoInterestingSystemStatus();
        }
      }
      resetDeviceConfig(false);
      updateConfig("soft reset");
      debug("Done with reset_config");
      resetRequired = false;
    });

    waitForConfigSync();

    // If last config isn't reported by the device, then add in a fixed delay to
    // give it time to handle to the last sent config before proceeding. Otherwise, it would
    // falsely detect the eventually-updated-state as an unexpected state change.
    ifNullThen(catchToNull(() -> deviceState.system.last_config),
        () -> delayAndProcess(Duration.ofMillis(CONFIG_UPDATE_DELAY_MS)));

    disallowDeviceStateChange(ALL_CHANGES);
  }

  private void waitForConfigSync() {
    checkState(!waitingForConfigSync.getAndSet(true), "Config is already updating...");
    try {
      waitFor("config sync", CONFIG_WAIT_TIME, () -> {
        processNextMessage();
        return configIsPending(false);
      });
      Duration between = Duration.between(lastConfigUpdate, CleanDateFormat.clean(Instant.now()));
      debug(format("Configuration sync took %ss", between.getSeconds()));
    } finally {
      waitingForConfigSync.set(false);
      debug("wait for config sync pending " + configIsPending(true));
    }
  }

  private void recordResult(SequenceResult result, Description description, String message) {
    putSequencerResult(description, result);

    Feature feature = description.getAnnotation(Feature.class);
    Map<Class<? extends Capability>, WithCapability> capabilities = getCapabilities(description);
    Bucket bucket = getBucket(feature);
    final String stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    final int base = (feature == null ? Feature.DEFAULT_SCORE : feature.score());

    boolean isSkip = result == SKIP;
    boolean isPass = result == PASS;

    AtomicInteger total = new AtomicInteger(isSkip ? 0 : base);
    AtomicInteger score = new AtomicInteger(isPass ? base : 0);

    if (!capabilities.containsKey(LastConfig.class)) {
      debug("Removing implicit system capability LAST_CONFIG");
      capExcept.remove(LastConfig.class);
    }

    ifTrueThen(isPass, () -> assertEquals("executed test capabilities",
        capabilities.keySet(), capExcept.keySet()));

    String method = description.getMethodName();
    capabilities.keySet().stream()
        .map(key -> emitCapabilityResult(key, capExcept.get(key),
            capabilities.get(key), bucket, method))
        .forEach(scoreAndTotal -> {
          ifTrueThen(isPass, () -> score.addAndGet(scoreAndTotal.getKey()));
          total.addAndGet(scoreAndTotal.getValue());
        });

    emitSequenceResult(result, bucket.value(), method, stage, score.get(), total.get(), message);
  }

  private void recordSchemaValidations(Description description) {
    // Ensure that enough time has passed to capture event messages for schema validation.
    info(format("waiting %ds for more messages...", waitTimeRemainingSec()));
    whileDoing("minimum test time", () -> messageEvaluateLoop(() -> waitTimeRemainingSec() > 0));

    validationResults.entrySet().stream()
        .filter(isInterestingValidation())
        .forEach(entry -> {
          String schemaName = entry.getKey();
          List<Entry> values = entry.getValue();
          if (values.isEmpty()) {
            collectSchemaResult(description, schemaName, PASS, SCHEMA_PASS_DETAIL);
          } else {
            Set<String> duplicates = new HashSet<>();
            values.stream().filter(item -> duplicates.add(uniqueKey(item))).forEach(result ->
                collectSchemaResult(description, schemaName, FAIL, result.detail));
          }
        });

    SequenceResult result = deviceStateViolations.isEmpty() ? PASS : FAIL;
    String message = result == PASS
        ? "Only expected device state changes observed"
        : "Unexpected device state changes: " + CSV_JOINER.join(deviceStateViolations.keySet());
    collectSchemaResult(description, DEVICE_STATE_SCHEMA, result, message);
  }

  private String uniqueKey(Entry entry) {
    return format("%s_%s_%s", entry.category, entry.message, entry.detail);
  }

  private CapabilityValidationState collectCapabilityResult(Class<? extends Capability> key) {
    CapabilityValidationState capabilityValidationState = new CapabilityValidationState();
    Exception exception = capExcept.get(key);
    boolean isPass = exception instanceof CapabilitySuccess;
    capabilityValidationState.result = isPass ? CapabilityResult.PASS : CapabilityResult.FAIL;
    if (!isPass) {
      Entry entry = new Entry();
      entry.category = VALIDATION_FEATURE_CAPABILITY;
      entry.message = exception.getMessage();
      entry.level = CAPABILITY_RESULT_MAP.get(capabilityValidationState.result).value();
      entry.timestamp = cleanDate();
      capabilityValidationState.status = entry;
    }

    return capabilityValidationState;
  }

  private void collectSchemaResult(Description description, String schemaName,
      SequenceResult result, String detail) {
    String name = description.getMethodName();
    Feature feature = description.getAnnotation(Feature.class);
    String bucket = getBucket(feature).value();
    String stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    emitSchemaResult(schemaName, result, detail, name, bucket, stage);

    SchemaValidationState schema = validationState.schemas.computeIfAbsent(
        schemaName, this::newSchemaValidationState);

    SequenceValidationState sequence = schema.sequences.computeIfAbsent(
        name, key -> new SequenceValidationState());

    sequence.result = result;
    sequence.stage = getTestStage(description);

    if (!result.equals(PASS)) {
      Entry logEntry = new Entry();
      logEntry.category = VALIDATION_FEATURE_SEQUENCE;
      logEntry.message = detail;
      logEntry.level = RESULT_LEVEL_MAP.get(result).value();
      logEntry.timestamp = cleanDate();
      sequence.status = logEntry;
    }

    updateValidationState();
  }

  private void emitSchemaResult(String schemaName, SequenceResult result, String detail,
      String sequence, String bucket, String stage) {
    emitSequencerOut(format(SCHEMA_FORMAT, result, bucket, sequence, stage, schemaName, detail));
  }

  private SchemaValidationState newSchemaValidationState(String key) {
    SchemaValidationState schemaValidationState = new SchemaValidationState();
    schemaValidationState.sequences = new HashMap<>();
    return schemaValidationState;
  }

  private Bucket getBucket(Description description) {
    return getBucket(description.getAnnotation(Feature.class));
  }

  private Bucket getBucket(Feature feature) {
    if (feature == null) {
      return UNKNOWN_DEFAULT;
    }
    Bucket implicit = feature.value();
    Bucket explicit = feature.bucket();
    if (implicit != UNKNOWN_DEFAULT && explicit != UNKNOWN_DEFAULT) {
      throw new RuntimeException("Both implicit and explicit buckets defined for feature");
    }
    if (implicit == UNKNOWN_DEFAULT && explicit == UNKNOWN_DEFAULT) {
      return UNKNOWN_DEFAULT;
    }
    return implicit == UNKNOWN_DEFAULT ? explicit : implicit;
  }

  private void recordMessageAttributes(Envelope attributes, String messageBase) {
    File file = new File(testDir, format(ATTRIBUTE_FILE_FORMAT, messageBase));
    try {
      JsonUtil.OBJECT_MAPPER.writeValue(file, attributes);
    } catch (Exception e) {
      throw new RuntimeException("While writing attributes to " + file.getAbsolutePath(), e);
    }
  }

  private void unwrapException(Map<String, Object> message, Envelope attributes) {
    Object ex = message.get(EXCEPTION_KEY);
    attributes.payload = ifTrueGet(ex instanceof Exception,
        (Supplier<String>) () -> ((Exception) ex).getMessage(),
        (Supplier<String>) () -> (String) ex);
  }

  private void recordRawMessage(Envelope attributes, Map<String, Object> message) {
    if (testName == null || !recordMessages) {
      return;
    }

    String messageBase = makeMessageBase(attributes);
    String timestamp = message == null ? getTimestamp() : (String) message.get("timestamp");
    if (traceLogLevel()) {
      messageBase = messageBase + "_" + timestamp;
    }

    ifTrueThen(message.containsKey(EXCEPTION_KEY), () -> unwrapException(message, attributes));
    recordRawMessage(message, messageBase);
    recordMessageAttributes(attributes, messageBase);
  }

  private void recordRawMessage(Object message, String messageBase) {
    Map<String, Object> objectMap = JsonUtil.OBJECT_MAPPER.convertValue(message,
        new TypeReference<>() {
        });
    if (traceLogLevel()) {
      messageBase = messageBase + "_" + getTimestamp();
    }
    recordRawMessage(objectMap, messageBase);
  }

  private void recordRawMessage(Map<String, Object> message, String messageBase) {
    if (!recordMessages) {
      return;
    }

    boolean systemEvents = messageBase.equals(SYSTEM_EVENTS_MESSAGE_BASE);
    boolean anyEvent = messageBase.startsWith(EVENTS_PREFIX);
    boolean localUpdate = messageBase.startsWith(LOCAL_PREFIX);
    boolean updateMessage = messageBase.endsWith(UPDATE_SUBFOLDER);
    boolean configMessage = messageBase.startsWith(CONFIG_SUBTYPE);
    boolean stateMessage = messageBase.startsWith(STATE_SUBTYPE);
    boolean syntheticMessage = (configMessage || stateMessage) && !updateMessage;

    String prefix = localUpdate ? "local " : "received ";
    File messageFile = new File(testDir, format(MESSAGE_FILE_FORMAT, messageBase));

    if (message.containsKey(EXCEPTION_KEY)) {
      String exceptionMessage = (String) message.get(MESSAGE_KEY);
      Envelope exceptionWrapper = JsonUtil.fromString(Envelope.class, exceptionMessage);
      writeString(messageFile, decodeBase64(exceptionWrapper.payload));
      return;
    }

    Object savedException = message == null ? null : message.get(EXCEPTION_KEY);
    try {
      // An actual exception here will cause the JSON serializer to barf, so temporarily sanitize.
      if (savedException instanceof Exception) {
        message.put(EXCEPTION_KEY, ((Exception) savedException).getMessage());
      }
      JsonUtil.OBJECT_MAPPER.writeValue(messageFile, message);
      if (systemEvents) {
        logSystemEvents(messageBase, message);
      } else {
        if (localUpdate || syntheticMessage || anyEvent) {
          trace(prefix + messageBase, message == null ? "(null)" : stringify(message));
        } else {
          debug(prefix + messageBase);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While writing message to " + messageFile.getAbsolutePath(), e);
    } finally {
      if (savedException != null) {
        message.put(EXCEPTION_KEY, savedException);
      }
    }
  }

  private void logSystemEvents(String messageBase, Map<String, Object> message) {
    try {
      checkArgument(!messageBase.startsWith(LOCAL_PREFIX));
      SystemEvents event = convertTo(SystemEvents.class, message);
      String prefix = "received " + messageBase + " ";
      if (event.logentries == null || event.logentries.isEmpty()) {
        debug(prefix + "(no logs)");
      } else {
        for (Entry logEntry : event.logentries) {
          debug(prefix + entryMessage(logEntry));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While logging system event", e);
    }
  }

  private String entryMessage(Entry logEntry) {
    return format("%s %s %s: %s", isoConvert(logEntry.timestamp),
        levelFromValue(logEntry.level).name(), logEntry.category, logEntry.message);
  }

  private void writeSystemLogs(SystemEvents message) {
    if (message == null || message.logentries == null) {
      return;
    }
    for (Entry logEntry : message.logentries) {
      writeSystemLog(logEntry);
    }
  }

  private String writeSystemLog(Entry logEntry) {
    return writeLogEntry(logEntry, systemLog);
  }

  private String writeLogEntry(Entry logEntry, PrintWriter printWriter) {
    if (logEntry.timestamp == null) {
      throw new RuntimeException("log entry timestamp is null");
    }
    String messageStr = format("%s %s %s", isoConvert(logEntry.timestamp),
        levelFromValue(logEntry.level).name(), logEntry.message);

    PrintWriter output = ofNullable(printWriter).orElse(new PrintWriter(System.err));
    output.println(messageStr);
    output.flush();
    return messageStr;
  }

  private static Level levelFromValue(Integer level) {
    return catchToElse(() -> Level.fromValue(level), Level.INVALID);
  }

  private boolean stateTransactionPending() {
    return stateTransaction.get() != null;
  }

  protected void queryState() {
    if (!deviceSupportsState()) {
      return;
    }
    assertConfigIsNotPending();
    String txnId = reflector().publish(getDeviceId(), Common.UPDATE_QUERY_TOPIC, EMPTY_MESSAGE);
    String previous = stateTransaction.getAndSet(txnId);
    debug(format("Waiting for device stateTransaction %s (was %s)", txnId, previous));
    whileDoing("state query", () -> messageEvaluateLoop(this::stateTransactionPending),
        e -> debug(
            format("While waiting for stateTransaction %s: %s", txnId, friendlyStackTrace(e))));
  }

  /**
   * Reset everything (including the state of the DUT) when done.
   */
  @After
  public void tearDown() {
    if (activeInstance == null) {
      return;
    }
    String condition = waitingCondition.isEmpty() ? "initialize" : currentWaitingCondition();
    debug(format("stage done %s at %s", condition, timeSinceStart()));
    recordSequence = false;

    if (testResult == PASS) {
      checkThatHasNoInterestingSystemStatus();
    }

    recordMessages = false;
    configAcked = false;
  }

  private void assertConfigIsNotPending() {
    if (!configTransactions.isEmpty()) {
      throw new RuntimeException(
          "Unexpected pending config transactions: " + configTransactionsListString());
    }
  }

  protected void updateConfig(String reason) {
    updateConfig(reason, false);
  }

  private void updateConfig(String reason, boolean force) {
    assertConfigIsNotPending();

    // Add a forced sleep to make sure second-quantized timestamps are unique.
    safeSleep(CONFIG_BARRIER_MS);

    if (doPartialUpdates && !force) {
      updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
      updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
      updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
      updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
      updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
      updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    } else {
      if (force) {
        debug("Forcing config update");
        sentConfig.remove(UPDATE);
      }
      updateConfig(UPDATE, deviceConfig);
    }

    if (configIsPending()) {
      configStateStart = catchToNull(() -> deviceState.timestamp);
      debug(format("Saving pre-config state timestamp " + isoConvert(configStateStart)));
      lastConfigUpdate = CleanDateFormat.clean(Instant.now());
      String debugReason = reason == null ? "" : (", because " + reason);
      debug(format("Update lastConfigUpdate %s%s", lastConfigUpdate, debugReason));
      waitForConfigSync();
    }
    assertConfigIsNotPending();
    captureConfigChange(reason);
  }

  private boolean updateConfig(SubFolder subBlock, Object data) {
    try {
      String messageData = stringify(data);
      String sentBlockConfig = String.valueOf(
          sentConfig.get(requireNonNull(subBlock, "subBlock not defined")));
      boolean updated = !messageData.equals(sentBlockConfig);
      trace(format("updated check %s_%s: %s", CONFIG_SUBTYPE, subBlock, updated));
      if (updated) {
        String augmentedMessage = actualize(stringify(data));
        String topic = subBlock + "/config";
        final String transactionId =
            requireNonNull(reflector().publish(getDeviceId(), topic, augmentedMessage),
                "no transactionId returned for publish");
        debug(format("update %s_%s, adding configTransaction %s",
            CONFIG_SUBTYPE, subBlock, transactionId));
        recordRawMessage(data, LOCAL_PREFIX + subBlock.value());
        sentConfig.put(subBlock, messageData);
        configTransactions.add(transactionId);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  protected void updateProxyConfig(String proxyId, Config proxyConfig) {
    String configMessage = stringify(proxyConfig);
    client.publish(proxyId, Common.UPDATE_CONFIG_TOPIC, configMessage);
  }

  private void captureConfigChange(String reason) {
    try {
      String suffix = reason == null ? "" : (" " + reason);
      String header = format("Update config%s: ", suffix);
      debug(header + isoConvert(deviceConfig.timestamp));
      recordRawMessage(deviceConfig, LOCAL_CONFIG_UPDATE);
      List<DiffEntry> allDiffs = SENT_CONFIG_DIFFERNATOR.computeChanges(deviceConfig);
      List<DiffEntry> filteredDiffs = filterTesting(allDiffs);
      if (!filteredDiffs.isEmpty()) {
        recordSequence(header);
        filteredDiffs.forEach(this::recordBullet);
        filteredDiffs.forEach(change -> trace(header + change));
        sequenceMd.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException("While recording device config", e);
    }
  }

  /**
   * Special tweak to generate a custom system config block that has a field that's not in the
   * official schema (to explicitly check that condition).
   *
   * @param system input system config block
   * @return augmented config block with special "extraField" included.
   */
  private AugmentedSystemConfig augmentConfig(SystemConfig system) {
    try {
      AugmentedSystemConfig augmentedConfig = JsonUtil.OBJECT_MAPPER.readValue(stringify(system),
          AugmentedSystemConfig.class);
      debug("system config extra field " + extraField);
      augmentedConfig.extraField = extraField;
      return augmentedConfig;
    } catch (Exception e) {
      throw new RuntimeException("While augmenting system config", e);
    }
  }

  protected boolean validSerialNo() {
    String deviceSerial = deviceState == null ? null
        : deviceState.system == null ? null : deviceState.system.serial_no;
    if (!Objects.equals(deviceSerial, lastSerialNo)) {
      notice(format("Received serial number %s", deviceSerial));
      lastSerialNo = deviceSerial;
    }
    boolean serialValid = deviceSerial != null && Objects.equals(serialNo, deviceSerial);
    if (!serialValid && enforceSerial && Objects.equals(serialNo, deviceSerial)) {
      throw new IllegalStateException("Serial number mismatch " + serialNo + " != " + deviceSerial);
    }
    enforceSerial = serialValid;
    return serialValid;
  }

  protected boolean catchToFalse(Supplier<Boolean> evaluator) {
    Boolean value = catchToNull(evaluator);
    return value != null && value;
  }

  protected boolean catchToTrue(Supplier<Boolean> evaluator) {
    Boolean value = catchToNull(evaluator);
    return value == null || value;
  }

  protected <T> T catchToNull(Supplier<T> evaluator) {
    try {
      return evaluator.get();
    } catch (AbortMessageLoop e) {
      error(
          "Aborting message loop while " + currentWaitingCondition() + " because "
              + e.getMessage());
      throw e;
    } catch (Exception e) {
      trace("Suppressing exception: " + friendlyStackTrace(e));
      return null;
    }
  }

  private String getExceptionLine(Exception e) {
    return Common.getExceptionLine(e, SequenceBase.class);
  }

  protected void checkThat(String description, String detail) {
    checkThat(description, detail == null, detail);
  }

  protected void checkThat(String description, Boolean condition) {
    checkThat(description, condition, null);
  }

  protected void checkThat(String description, Supplier<Boolean> condition) {
    checkThat(description, condition, null);
  }

  protected void checkThat(String description, Boolean condition, String details) {
    checkThat(description, () -> condition, details);
  }

  protected void checkThat(String description, Supplier<Boolean> condition, String details) {
    if (!catchToFalse(condition)) {
      warning("Failed check that " + description);
      String suffix = ifNotNullGet(details, base -> "; " + base, "");
      throw new IllegalStateException("Failed check that " + description + suffix);
    }
    recordSequence("Check that " + description);
  }

  protected void checkNotThat(String description, Supplier<Boolean> condition) {
    String notDescription = NOT_STATUS_PREFIX + description;
    if (catchToTrue(condition)) {
      warning("Failed check that " + notDescription);
      throw new IllegalStateException("Failed check that " + notDescription);
    }
    recordSequence("Check that " + notDescription);
  }

  protected void waitFor(String description, Supplier<String> evaluator) {
    waitFor(description, DEFAULT_WAIT_TIME, evaluator);
  }

  protected void waitFor(String description, Duration maxWait, Supplier<String> evaluator) {
    AtomicReference<String> detail = new AtomicReference<>();
    whileDoing(description, () -> {
      ifNotTrueThen(waitingForConfigSync.get(), () -> updateConfig("Before " + description));
      recordSequence("Wait for " + description);
      messageEvaluateLoop(maxWait, () -> {
        String result = evaluator.get();
        String previous = detail.getAndSet(emptyToNull(result));
        ifTrueThen(!Objects.equals(previous, result),
            () -> debug(format("Detail %s is now: %s", description, result)));
        return result != null;
      });
    }, detail::get);
  }

  protected void sleepFor(String delayReason, Duration sleepTime) {
    String message = format("sleeping %ss for %s", sleepTime.getSeconds(), delayReason);
    whileDoing(message, () -> safeSleep(sleepTime.getSeconds() * ONE_SECOND_MS));
  }

  protected void checkFor(String description, Supplier<String> detailer) {
    String result = detailer.get();
    checkThat(description, () -> result == null, result);
  }

  protected void waitForLog(String category, Level exactLevel) {
    waitFor(format("log category `%s` level `%s` to be logged", category, exactLevel.name()),
        LOG_WAIT_TIME, () -> checkLogged(category, exactLevel));
  }

  protected void untilLogged(String category, Level exactLevel) {
    waitForLog(category, exactLevel);
  }

  private String checkLogged(String category, Level exactLevel) {
    processLogMessages();
    List<Entry> entries = matchingLogQueue(entry -> isMatchingEntry(category, exactLevel, entry));
    entries.forEach(entry -> debug("Matched entry " + entryMessage(entry)));
    return ifTrueGet(entries.isEmpty(), NO_EXTRA_DETAIL);
  }

  protected void checkNotLogged(String category, Level minLevel) {
    withRecordSequence(false, () -> {
      ifTrueThen(deviceSupportsState(), () ->
          waitFor("last_config synchronized", this::lastConfigUpdated));
      processLogMessages();
    });
    final Instant endTime = lastConfigUpdate.plusSeconds(LOG_TIMEOUT_SEC);
    List<Entry> entries = matchingLogQueue(
        entry -> category.equals(entry.category) && entry.level >= minLevel.value());
    checkThat(format("log category `%s` level `%s` not logged", category, minLevel), () -> {
      if (!entries.isEmpty()) {
        warning(format("Filtered config between %s and %s", isoConvert(lastConfigUpdate),
            isoConvert(endTime)));
        entries.forEach(entry -> error("undesirable " + entryMessage(entry)));
      }
      return entries.isEmpty();
    });
  }

  private List<Entry> matchingLogQueue(Function<Entry, Boolean> predicate) {
    return logEntryQueue.stream().filter(predicate::apply).collect(Collectors.toList());
  }

  private void processLogMessages() {
    List<SystemEvents> receivedEvents = popReceivedEvents(SystemEvents.class);
    receivedEvents.forEach(systemEvent -> {
      int eventCount = ofNullable(systemEvent.event_no).orElse(previousEventCount + 1);
      if (eventCount != previousEventCount + 1) {
        debug("Missing system events " + previousEventCount + " -> " + eventCount);
      }
      previousEventCount = eventCount;
      logEntryQueue.addAll(ofNullable(systemEvent.logentries).orElse(ImmutableList.of()));
    });
    List<Entry> toRemove = logEntryQueue.stream()
        .filter(entry -> entry.timestamp.toInstant().isBefore(lastConfigUpdate)).toList();
    if (!toRemove.isEmpty()) {
      debug("ignoring log entries before lastConfigUpdate " + lastConfigUpdate);
    }
    toRemove.forEach(entry -> debug(" x " + entryMessage(entry)));
    logEntryQueue.removeAll(toRemove);
  }

  protected void whileDoing(String description, Runnable action) {
    whileDoing(description, action, e -> debug("Caught " + friendlyStackTrace(e)));
  }

  private void whileDoing(String description, Runnable action, Supplier<String> detail) {
    whileDoing(description, action, e -> debug("Caught " + friendlyStackTrace(e)), detail);
  }

  protected void whileDoing(String description, Runnable action, Consumer<Exception> catcher) {
    whileDoing(description, action, catcher, null);
  }

  private void whileDoing(String description, Runnable action, Consumer<Exception> catcher,
      Supplier<String> detailer) {
    final Instant startTime = Instant.now();

    waitingConditionStart(description);

    try {
      try {
        action.run();
      } catch (AbortMessageLoop e) {
        // This is some fundamental problem, so just pass it along without the waiting detail.
        catcher.accept(e);
        throw e;
      } catch (Exception e) {
        catcher.accept(e);
        String detail = ifNotNullGet(detailer, Supplier::get);
        ifNotNullThen(detail, this::waitingConditionDetail);
        // Don't include the caught exception in order to preserve the detail as base cause.
        throw ifNotNullGet(detail, message -> new RuntimeException("Because " + message), e);
      }
    } catch (Exception e) {
      throw new RuntimeException("While " + description, e);
    }

    waitingConditionPop(startTime);
  }

  private void waitingConditionPop(Instant startTime) {
    Duration between = Duration.between(startTime, Instant.now());
    debug(format("Stage finished %s at %s after %ss", currentWaitingCondition(),
        timeSinceStart(), between.toSeconds()));
    waitingCondition.pop();
    ifTrueThen(!waitingCondition.isEmpty(),
        () -> trace(format("Stage resume %s at %s", currentWaitingCondition(), timeSinceStart())));
  }

  private void waitingConditionStart(String condition) {
    ifTrueThen(!waitingCondition.isEmpty(),
        () -> trace(format("stage suspend %s at %s", currentWaitingCondition(), timeSinceStart())));
    waitingConditionPush(condition);
    info(format("Stage start %s at %s", currentWaitingCondition(), timeSinceStart()));
  }

  private void waitingConditionDetail(String detail) {
    notice("Adding waiting condition detail: " + detail);
    waitingConditionPush(detail);
  }

  private void waitingConditionPush(String condition) {
    waitingCondition.push("waiting for " + condition);
  }

  private String currentWaitingCondition() {
    return waitingCondition.peek();
  }

  private void untilLoop(String description, Supplier<Boolean> evaluator) {
    untilLoop(description, evaluator, null);
  }

  private void untilLoop(String description, Supplier<Boolean> evaluator, Supplier<String> detail) {
    whileDoing(description, () -> {
      updateConfig("before " + description);
      recordSequence("Wait for " + description);
      messageEvaluateLoop(evaluator);
    }, detail);
  }

  private void delayAndProcess(Duration duration) {
    debug(format("Delaying sequence progression for %ss", duration.getSeconds()));
    Instant end = Instant.now().plus(duration);
    messageEvaluateLoop(duration.plus(DEFAULT_LOOP_TIMEOUT), () -> Instant.now().isBefore(end));
  }

  private void messageEvaluateLoop(Supplier<Boolean> whileTrue) {
    messageEvaluateLoop(DEFAULT_LOOP_TIMEOUT, whileTrue);
  }

  private void messageEvaluateLoop(Duration maxWait, Supplier<Boolean> whileTrue) {
    Instant end = Instant.now().plus(maxWait);
    while (whileTrue.get()) {
      if (Instant.now().isAfter(end)) {
        throw new RuntimeException(
            format("Timeout after %ss %s", maxWait.getSeconds(), currentWaitingCondition()));
      }
      processNextMessage();
    }
    if (expectedSystemStatus != null) {
      withRecordSequence(false, () -> checkThatHasInterestingSystemStatus(expectedSystemStatus));
    }
  }

  private void recordSequence(String step) {
    if (recordSequence) {
      sequenceMd.println("1. " + step.trim());
      sequenceMd.flush();
    }
  }

  private void recordBullet(DiffEntry step) {
    recordBullet(step.toString());
  }

  private void recordBullet(String step) {
    if (recordSequence) {
      info("Device config " + step);
      sequenceMd.println("    * " + step);
    }
  }

  private long waitTimeRemainingSec() {
    return Math.max(0, MINIMUM_TEST_SEC - (System.currentTimeMillis() - startCaptureTime) / 1000);
  }

  private String timeSinceStart() {
    return (System.currentTimeMillis() - startTestTimeMs) / 1000 + "s";
  }

  protected void untilTrue(String description, Supplier<Boolean> evaluator) {
    untilTrue(description, evaluator, null);
  }

  protected void untilTrue(String description, Supplier<Boolean> evaluator,
      Supplier<String> detail) {
    untilLoop(description, () -> !catchToFalse(evaluator), detail);
  }

  protected void untilFalse(String description, Supplier<Boolean> evaluator) {
    untilLoop(description, () -> catchToTrue(evaluator));
  }

  protected void untilUntrue(String description, Supplier<Boolean> evaluator) {
    untilLoop(description, () -> catchToFalse(evaluator));
  }

  /**
   * Thread-safe way to get a message. Tests are run in different threads, and if one blocks it
   * might end up trying to take a message while another thread is still looping. This prevents that
   * by checking that the calling test is still active, and then if not, saves the message for the
   * next round and interrupts the current thread.
   *
   * @return message bundle
   */
  MessageBundle nextMessageBundle() {
    synchronized (SequenceBase.class) {
      if (stashedBundle != null) {
        debug("using stashed message bundle");
        MessageBundle bundle = stashedBundle;
        stashedBundle = null;
        return bundle;
      }
      MessagePublisher reflector = reflector();
      if (!reflector.isActive()) {
        throw new RuntimeException(
            "Trying to receive message from inactive client " + reflector.getSubscriptionId());
      }
      final MessageBundle bundle;
      try {
        bundle = reflector.takeNextMessage(QuerySpeed.SHORT);
      } catch (Exception e) {
        throw new AbortMessageLoop("Exception receiving message", e);
      }
      if (activeInstance != this) {
        debug("stashing interrupted message bundle");
        checkState(stashedBundle == null, "stashed bundle is not null");
        stashedBundle = bundle;
        throw new RuntimeException("Message loop no longer for active thread");
      }
      return bundle;
    }
  }

  private void processNextMessage() {
    MessageBundle bundle = nextMessageBundle();
    if (bundle != null) {
      processMessage(bundle.attributes, bundle.message);
    }
  }

  private void processMessage(Map<String, String> attributes, Map<String, Object> message) {
    String deviceId = attributes.get(DEVICE_ID_KEY);
    String subFolderRaw = attributes.get("subFolder");
    String subTypeRaw = attributes.get("subType");
    String transactionId = attributes.get("transactionId");

    String commandSignature = format("%s/%s/%s", deviceId, subTypeRaw, subFolderRaw);
    trace("received command " + commandSignature);

    boolean targetDevice = getDeviceId().equals(deviceId);
    boolean proxiedDevice = !targetDevice && receivedEvents.containsKey(deviceId);

    if (!targetDevice && !proxiedDevice) {
      return;
    }

    if (SubFolder.ERROR.value().equals(subFolderRaw)) {
      handlePipelineError(subTypeRaw, message);
      return;
    }

    if (SubType.REPLY.value().equals(subTypeRaw)) {
      return;
    }

    Envelope envelope = convertTo(Envelope.class, attributes);

    try {
      recordRawMessage(envelope, message);

      preprocessMessage(envelope, message);

      validateMessage(envelope, message);

      if (proxiedDevice) {
        handleProxyMessage(deviceId, envelope, message);
      } else if (UPDATE.value().equals(subFolderRaw)) {
        handleUpdateMessage(subTypeRaw, message, transactionId);
      } else {
        handleDeviceMessage(message, subTypeRaw, subFolderRaw, transactionId);
      }
    } catch (Exception e) {
      throw new AbortMessageLoop(
          format("While processing message %s_%s %s", subTypeRaw, subFolderRaw, transactionId), e);
    }
  }

  private void handleProxyMessage(String deviceId, Envelope envelope, Map<String, Object> message) {
    info(format("Handling proxy %s message %s", deviceId, envelope.subFolder.value()));
    getReceivedEvents(deviceId, envelope.subFolder).add(message);
  }

  private void validateMessage(Envelope attributes, Map<String, Object> message) {
    String schemaName = format("%s_%s", attributes.subType, attributes.subFolder);
    if (!isInterestingValidation(schemaName)) {
      return;
    }

    String deviceId = attributes.deviceId;
    ReportingDevice reportingDevice = new ReportingDevice(deviceId);

    Envelope modified = GeneralUtils.deepCopy(attributes);
    modified.deviceId = FAKE_DEVICE_ID; // Allow for non-standard device IDs.

    messageValidator.validateDeviceMessage(reportingDevice, message, toStringMap(modified));
    validationResults.computeIfAbsent(makeMessageBase(attributes), key -> new ArrayList<>())
        .addAll(reportingDevice.getMessageEntries());
  }

  private void handlePipelineError(String subTypeRaw, Map<String, Object> message) {
    String detail = format("Pipeline type %s error: %s", subTypeRaw, message.get("error"));
    if (deviceSupportsState()) {
      throw new RuntimeException(detail);
    } else {
      error(detail);
    }
  }

  private void handleDeviceMessage(Map<String, Object> message, String subTypeRaw,
      String subFolderRaw, String transactionId) {
    debug(format("Handling device message %s_%s %s", subTypeRaw, subFolderRaw, transactionId));
    SubType subType = SubType.fromValue(requireNonNull(subTypeRaw, "missing subType"));
    SubFolder subFolder = SubFolder.fromValue(requireNonNull(subFolderRaw, "missing subFolder"));
    switch (subType) {
      // These are echos of sent partial config messages, so do nothing.
      case CONFIG -> trace("Ignoring echo configTransaction " + transactionId);
      // State updates are handled as a monolithic block with a state reflector update.
      case STATE -> trace("Ignoring partial state update");
      case EVENTS -> handleEventMessage(subFolder, message);
      default -> info("Encountered unexpected subType " + subTypeRaw);
    }
  }

  private synchronized void handleUpdateMessage(String subTypeRaw,
      Map<String, Object> message, String txnId) {
    try {
      debug(format("Handling update message %s_update %s", subTypeRaw, txnId));

      // Do this first to handle all cases of update payloads, including exceptions.
      if (txnId != null) {
        if (CONFIG_SUBTYPE.equals(subTypeRaw)) {
          ifTrueThen(configTransactions.remove(txnId),
              () -> debug("Removed configTransaction " + txnId));
        } else if (STATE_SUBTYPE.equals(subTypeRaw) && txnId.startsWith(sessionPrefix)) {
          String expected = stateTransaction.getAndSet(null);
          if (txnId.equals(expected)) {
            debug("Removed stateTransaction " + txnId);
          } else {
            debug(format("Received unexpected stateTransaction %s, dropping %s", txnId, expected));
          }
        }
      }

      if (message.containsKey(EXCEPTION_KEY)) {
        debug("Ignoring reflector exception:\n" + message.get(EXCEPTION_KEY).toString());
        return;
      }

      if (!EXPECTED_UPDATES.containsKey(subTypeRaw)) {
        debug("Ignoring unexpected update type " + subTypeRaw);
        return;
      }
      Object converted = convertTo(EXPECTED_UPDATES.get(subTypeRaw), message);
      getReceivedUpdates().put(subTypeRaw, converted);
      int updateCount = getUpdateCount(subTypeRaw).incrementAndGet();
      if (converted instanceof Config config) {
        String extraField = getExtraField(message);
        if (RESET_CONFIG_MARKER.equals(extraField)) {
          debug("Update with config reset");
        } else if ("break_json".equals(extraField)) {
          error("Shouldn't be seeing this!");
          return;
        }
        List<DiffEntry> changes = updateDeviceConfig(config);
        debug(format("Updated config %s %s", isoConvert(config.timestamp), txnId));
        String changeUpdate = updateCount == 1 ? stringify(deviceConfig) : changedLines(changes);
        info(format("Updated config #%03d", updateCount), changeUpdate);
      } else if (converted instanceof State convertedState) {
        String timestamp = isoConvert(convertedState.timestamp);
        if (convertedState.timestamp == null) {
          warning("No timestamp in state message, rejecting.");
          return;
        }
        if (deviceState != null && convertedState.timestamp.before(deviceState.timestamp)) {
          warning(format("Ignoring out-of-order state update %s %s", timestamp, txnId));
          return;
        }
        if (deviceState == null && convertedState.timestamp.before(stateCutoffThreshold)) {
          String lastStart = isoConvert(
              catchToNull(() -> convertedState.system.operation.last_start));
          warning(format("Ignoring stale state update %s %s %s %s", timestamp,
              isoConvert(stateCutoffThreshold), lastStart, txnId));
          return;
        }
        checkState(deviceSupportsState(), "Received state update with no-state device");
        boolean deltaState = RECV_STATE_DIFFERNATOR.isInitialized();
        List<DiffEntry> stateChanges = RECV_STATE_DIFFERNATOR.computeChanges(converted);
        Instant start = ofNullable(convertedState.timestamp).orElseGet(Date::new).toInstant();
        long delta = Duration.between(start, Instant.now()).getSeconds();
        debug(format("Updated state after %ds %s %s", delta, timestamp, txnId));
        if (deltaState) {
          info(format("Updated state #%03d", updateCount), changedLines(stateChanges));
          validateIntermediateState(convertedState, stateChanges);
        } else {
          info(format("Initial state #%03d", updateCount), stringify(converted));
        }

        Date cutoff = Date.from(getNowInstant().minus(STATE_TIMESTAMP_ERROR_THRESHOLD));
        checkState(convertedState.timestamp.after(cutoff),
            format("State timestamp %s before error cutoff threshold %s", timestamp,
                isoConvert(cutoff)));

        deviceState = convertedState;
        validSerialNo();

        debug(format("Updated state has last_config %s (expecting %s)",
            isoConvert((Date) ifNotNullGet(deviceState.system, x -> x.last_config)),
            isoConvert((Date) ifNotNullGet(deviceConfig, config -> config.timestamp))));
      } else {
        error("Unknown update type " + converted.getClass().getSimpleName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling reflector message", e);
    }
  }

  protected void expectedStatusLevel(Level level) {
    maxAllowedStatusLevel = level.value();
  }

  private void validateIntermediateState(State convertedState, List<DiffEntry> stateChanges) {
    if (!recordSequence || !shouldValidateSchema(SubFolder.VALIDATION)) {
      return;
    }

    int statusLevel = catchToElse(() -> convertedState.system.status.level, Level.TRACE.value());
    if (statusLevel > maxAllowedStatusLevel) {
      String message = format("System status level %d exceeded allowed threshold %d", statusLevel,
          maxAllowedStatusLevel);
      deviceStateViolations.put(STATUS_LEVEL_VIOLATION, message);
      warning(message);
    }
    Map<String, String> badChanges = stateChanges.stream().filter(not(this::changeAllowed))
        .collect(Collectors.toMap(DiffEntry::key, DiffEntry::toString));
    badChanges.values().stream().map(x -> "Unexpected device state change: " + x)
        .forEach(this::warning);
    deviceStateViolations.putAll(badChanges);
  }

  private boolean changeAllowed(DiffEntry change) {
    String key = change.key();
    return SYSTEM_STATE_CHANGES.stream().anyMatch(key::startsWith)
        || allowedDeviceStateChanges.stream().anyMatch(key::startsWith);
  }

  protected void allowDeviceStateChange(String changePrefix) {
    String changeMessage = changePrefix.equals(ALL_CHANGES) ? "(everything)" : changePrefix;
    debug("Allowing device state change " + changeMessage);
    if (!allowedDeviceStateChanges.add(changePrefix)) {
      throw new AbortMessageLoop("Device state change prefix already allowed: " + changeMessage);
    }
  }

  protected void disallowDeviceStateChange(String changePrefix) {
    String changeMessage = changePrefix.equals(ALL_CHANGES) ? "(everything)" : changePrefix;
    debug("Disallowing device state change " + changeMessage);
    if (!allowedDeviceStateChanges.remove(changePrefix)) {
      throw new AbortMessageLoop("Unexpected device state change removal: " + changeMessage);
    }
  }

  private List<DiffEntry> updateDeviceConfig(Config config) {
    if (deviceConfig == null) {
      return null;
    }

    // These parameters are set by the cloud functions, so explicitly set to maintain parity.
    deviceConfig.timestamp = config.timestamp;
    deviceConfig.version = config.version;
    if (config.system != null && config.system.operation != null) {
      setLastStart(SemanticDate.describe("device reported", config.system.operation.last_start));
    }
    sanitizeConfig(deviceConfig);
    return RECV_CONFIG_DIFFERNATOR.computeChanges(deviceConfig);
  }

  private void preprocessMessage(Envelope attributes, Map<String, Object> message) {
    if (getDeviceId().equals(attributes.deviceId) && SubType.STATE == attributes.subType) {
      updateConfigAcked(message);
    }
  }

  /**
   * Check/remember the configAcked field of a state update. This field is only populated by the
   * supporting cloud functions in response to an explicit state query, and checks that the device
   * has acked (in an MQTT sense) a previously sent config.
   */
  private void updateConfigAcked(Map<String, Object> converted) {
    Object ackedResult = converted.remove("configAcked");
    boolean wasAcked = "true".equals(ackedResult) || Boolean.TRUE.equals(ackedResult);
    if (wasAcked && !configAcked) {
      info("Received device configAcked");
    }
    ifTrueThen(wasAcked, () -> configAcked = true);
  }

  private String getExtraField(Map<String, Object> message) {
    Object system = message.get("system");
    if (system instanceof Map) {
      return (String) ((Map<?, ?>) system).get("extra_field");
    }
    return null;
  }

  private void handleEventMessage(SubFolder subFolder, Map<String, Object> message) {
    getReceivedEvents(subFolder).add(message);
    if (SubFolder.SYSTEM.equals(subFolder)) {
      writeSystemLogs(convertTo(SystemEvents.class, message));
    }
  }

  private boolean configIsPending() {
    return configIsPending(false) != null;
  }

  private String configIsPending(boolean debugOut) {
    Date stateLastStart = catchToNull(() -> deviceState.system.operation.last_start);
    Date configLastStart = catchToNull(() -> deviceConfig.system.operation.last_start);
    boolean lastStartSynced = stateLastStart == null || stateLastStart.equals(configLastStart);

    Date currentState = catchToNull(() -> deviceState.timestamp);
    final boolean stateUpdated =
        !deviceSupportsState() || !Objects.equals(configStateStart, currentState);

    Date stateLastConfig = catchToNull(() -> deviceState.system.last_config);

    Date lastConfig = catchToNull(() -> deviceConfig.timestamp);
    final boolean lastConfigSynced = stateLastConfig == null || stateLastConfig.equals(lastConfig);
    final boolean transactionsClean = configTransactions.isEmpty();

    List<String> failures = new ArrayList<>();
    ifNotTrueThen(stateUpdated, () -> failures.add("device state not updated since test start"));
    ifNotTrueThen(lastStartSynced, () -> failures.add("last_start not synced in config"));
    ifNotTrueThen(transactionsClean, () -> failures.add("config transactions not cleared"));
    ifNotTrueThen(lastConfigSynced, () -> failures.add("last_config not synced in state"));

    if (debugOut) {
      if (!failures.isEmpty()) {
        notice(format("state updated at %s then %s", isoConvert(configStateStart),
            isoConvert(currentState)));
        notice(format("last_start synchronized %s: state/%s =? config/%s", lastStartSynced,
            isoConvert(stateLastStart), isoConvert(configLastStart)));
        notice(format("configTransactions flushed %s: %s", transactionsClean,
            configTransactionsListString()));
        notice(format("last_config synchronized %s: state/%s =? config/%s", lastConfigSynced,
            isoConvert(stateLastConfig), isoConvert(lastConfig)));
      } else if (stateLastConfig == null) {
        debug("last_config synchronized check disabled due to missing state.system.last_config");
      }
    }
    return ifNotTrueGet(failures.isEmpty(), () -> CSV_JOINER.join(failures));
  }

  @NotNull
  private String configTransactionsListString() {
    return Joiner.on(' ').join(configTransactions);
  }

  /**
   * Filter out any testing-oriented messages, since they should not impact behavior.
   */
  private List<DiffEntry> filterTesting(List<DiffEntry> allDiffs) {
    return allDiffs.stream().filter(message -> !message.key().startsWith(SYSTEM_TESTING_MARKER))
        .collect(Collectors.toList());
  }

  private void trace(String message, String parts) {
    log(message, Level.TRACE, parts);
  }

  protected void trace(String message) {
    log(message, Level.TRACE);
  }

  protected void debug(String message, String parts) {
    log(message, Level.DEBUG, parts);
  }

  protected void debug(String message) {
    log(message, Level.DEBUG);
  }

  protected void info(String message, String parts) {
    log(message, Level.INFO, parts);
  }

  protected void info(String message) {
    log(message, Level.INFO);
  }

  protected void notice(String message) {
    log(message, Level.NOTICE);
  }

  protected void warning(String message) {
    log(message, Level.WARNING);
  }

  protected void error(String message) {
    log(message, ERROR);
  }

  private void log(String message, Level level, String parts) {
    Arrays.stream(parts.split("\\n")).forEach(part -> log(message + ": " + part, level));
  }

  private void log(String message, Level level) {
    Entry logEntry = new Entry();
    logEntry.timestamp = cleanDate();
    logEntry.level = level.value();
    logEntry.message = message;
    logEntry.category = VALIDATION_FEATURE_SEQUENCE;
    writeSequencerLog(logEntry);
  }

  private void writeSequencerLog(Entry logEntry) {
    String entry = writeLogEntry(logEntry, sequencerLog);
    if (logEntry.level >= logLevel) {
      System.err.println(entry);
    }
  }

  /**
   * Returns the number of events of given type in the message buffer.
   *
   * @param clazz Event type
   * @return Number of messages
   */
  protected int countReceivedEvents(Class<?> clazz) {
    SubFolder subFolder = CLASS_SUBFOLDER_MAP.get(clazz);
    List<Map<String, Object>> events = getReceivedEvents(subFolder);
    if (events == null) {
      return 0;
    }
    return events.size();
  }

  protected <T> List<T> popReceivedEvents(Class<T> clazz) {
    SubFolder subFolder = CLASS_SUBFOLDER_MAP.get(clazz);
    safeSleep(EVENT_WAIT_DELAY_MS);
    List<Map<String, Object>> events = getReceivedEvents().remove(subFolder);
    if (events == null) {
      return ImmutableList.of();
    }
    return events.stream().map(message -> convertTo(clazz, message))
        .collect(Collectors.toList());
  }

  protected void withAlternateClient(Runnable evaluator) {
    checkNotNull(altClient, "Alternate client used but test not skipped");
    checkState(!useAlternateClient, "Alternate client already in use");
    checkState(deviceConfig.system.testing.endpoint_type == null, "endpoint type not null");
    try {
      useAlternateClient = true;
      warning("Now using alternate connection client!");
      deviceConfig.system.testing.endpoint_type = "alternate";
      whileDoing("using alternate client", evaluator);
    } finally {
      useAlternateClient = false;
      warning("Done with alternate connection client!");
      catchToNull(() -> deviceConfig.system.testing.endpoint_type = null);
    }
  }

  /**
   * Mirrors the current config to the "other" config, where the current and other configs are
   * defined by the useAlternateClient flag. This call is used to warmup the new config before a
   * switch, so that when the client is switched, it is ready with the right (up to date) config
   * contents.
   */
  protected void mirrorToOtherConfig() {
    // First make sure the current config is up-to-date with any local changes.
    updateConfig("mirroring config " + useAlternateClient);

    // Grab the as-reported current config, to get a copy of the actual values used.
    Config target = (Config) getReceivedUpdates().get(CONFIG_SUBTYPE);

    // Modify the config with the alternate endpoint_type, prefetching the actual change.
    target.system.testing.endpoint_type = useAlternateClient ? null : "alternate";

    // Now update the other config with the tweaked version, in prep for the actual switch.
    updateMirrorConfig(actualize(stringify(target)));
  }

  /**
   * Clears out the "other" (not current) config, so that it can't be inadvertently used for
   * something. This is the simple version of the endpoint going down (actually turning down the
   * endpoint would be a lot more work).
   */
  protected void clearOtherConfig() {
    // No need to be fancy here, just clear out the other config with an empty blob.
    updateMirrorConfig(EMPTY_CONFIG);
  }

  private void updateMirrorConfig(String receivedConfig) {
    if (altClient != null) {
      String topic = UPDATE_SUBFOLDER + "/" + CONFIG_SUBTYPE;
      altReflector().publish(getDeviceId(), topic, receivedConfig);
      // There's a race condition if the mirror command gets delayed, so chill for a bit.
      safeSleep(ONE_SECOND_MS);
    }
  }

  protected String stateMatchesConfig() {
    List<String> parity = new ArrayList<>();
    if (deviceConfig == null) {
      return "device config is null";
    }
    if (deviceState == null) {
      return "device state is null";
    }
    addToParity(parity, "system", deviceConfig.system, deviceState.system);
    addToParity(parity, "pointset", deviceConfig.pointset, deviceState.pointset);
    addToParity(parity, "gateway", deviceConfig.gateway, deviceState.gateway);
    addToParity(parity, "localnet", deviceConfig.localnet, deviceState.localnet);
    addToParity(parity, "discovery", deviceConfig.discovery, deviceState.discovery);
    addToParity(parity, "blobset", deviceConfig.blobset, deviceState.blobset);
    return ifTrueGet(parity.isEmpty(), (String) null,
        () -> "config/state subblock mismatch: " + CSV_JOINER.join(parity));
  }

  private void addToParity(List<String> parity, String block, Object config, Object state) {
    boolean hasConfig = config != null;
    boolean hasState = state != null;
    if (hasConfig != hasState) {
      parity.add(block);
    }
  }

  protected String lastConfigUpdated() {
    Date lastConfig = deviceState.system.last_config;
    Date expectedConfig = deviceConfig.timestamp;
    return dateEquals(expectedConfig, lastConfig) ? null :
        format("state.system.last_config %s does not match expected config.timestamp %s",
            isoConvert(lastConfig), isoConvert(expectedConfig));
  }

  private String notSignificantStatusDetail() {
    return significantStatusDetail() == null ? "no significant device system status" : null;
  }

  private String significantStatusDetail() {
    int statusLevel = catchToElse(() -> deviceState.system.status.level, 0);

    if (statusLevel != lastStatusLevel) {
      debug("Device state system status level is now " + statusLevel);
      lastStatusLevel = statusLevel;
    }

    return statusLevel >= Level.WARNING.value() ? ("system status is level " + statusLevel) : null;
  }

  protected void checkThatHasNoInterestingSystemStatus() {
    checkThatHasInterestingSystemStatus(false);
  }

  protected void checkThatHasInterestingSystemStatus(boolean isInteresting) {
    if (!deviceSupportsState()) {
      return;
    }
    String systemStatusMessage = SYSTEM_STATUS_MESSAGE + STATUS_CHECK_SUFFIX;
    if (isInteresting) {
      checkThat(systemStatusMessage, notSignificantStatusDetail());
    } else {
      checkThat(NOT_STATUS_PREFIX + systemStatusMessage, significantStatusDetail());
    }
  }

  protected void untilHasInterestingSystemStatus() {
    untilHasInterestingSystemStatus(true);
  }

  protected void untilHasNoInterestingSystemStatus() {
    untilHasInterestingSystemStatus(false);
  }

  protected void untilHasInterestingSystemStatus(boolean interesting) {
    if (!deviceSupportsState()) {
      return;
    }
    expectedSystemStatus = null;
    String message = (interesting ? HAS_STATUS_PREFIX : NOT_STATUS_PREFIX) + SYSTEM_STATUS_MESSAGE;
    Supplier<String> detailer =
        interesting ? this::notSignificantStatusDetail : this::significantStatusDetail;
    waitFor(message, detailer);
    expectedSystemStatus = interesting;
    checkThatHasInterestingSystemStatus(interesting);
  }

  private void putSequencerResult(Description description, SequenceResult result) {
    String resultId = getDeviceId() + "/" + description.getMethodName();
    SequenceRunner.getAllTests().put(resultId, result);
  }

  private void startSequenceStatus(Description description) {
    Entry entry = new Entry();
    entry.message = "Starting test";
    entry.category = VALIDATION_FEATURE_SEQUENCE;
    SequenceResult startResult = SequenceResult.START;
    entry.level = RESULT_LEVEL_MAP.get(startResult).value();
    entry.timestamp = new Date();
    scoringResult = null;
    setSequenceStatus(description, startResult, entry);
  }

  private void setSequenceStatus(Description description, SequenceResult result, Entry logEntry) {
    String bucket = getBucket(description).value();
    String sequence = description.getMethodName();
    SequenceValidationState sequenceValidationState = getValidationState().features.computeIfAbsent(
        bucket, this::newFeatureValidationState).sequences.computeIfAbsent(
        sequence, key -> new SequenceValidationState());
    sequenceValidationState.status = logEntry;
    sequenceValidationState.result = result;
    sequenceValidationState.summary = getTestSummary(description);
    sequenceValidationState.stage = getTestStage(description);
    sequenceValidationState.capabilities = capExcept.keySet().stream()
        .collect(Collectors.toMap(Class::getSimpleName, this::collectCapabilityResult));
    sequenceValidationState.scoring = scoringResult;
    updateValidationState();
  }

  private FeatureValidationState newFeatureValidationState(String key) {
    FeatureValidationState featureValidationState = new FeatureValidationState();
    featureValidationState.sequences = new HashMap<>();
    return featureValidationState;
  }

  private boolean receivedAtLeastOneState() {
    return getStateUpdateCount() > startStateCount;
  }

  protected int getNumStateUpdates() {
    return getStateUpdateCount() - startStateCount;
  }

  protected void ensureStateUpdate() {
    updateConfig("ensure state update", true);
    withRecordSequence(false,
        () -> untilTrue("received at least one state update", this::receivedAtLeastOneState));
  }

  protected void forceConfigUpdate(String reason) {
    updateConfig(reason, true);
    recordSequence("Force config update to " + reason);
  }

  protected void skipTest(String reason) {
    throw new AssumptionViolatedException(reason);
  }

  protected void ifTrueSkipTest(Boolean condition, String reason) {
    ifTrueThen(condition, () -> skipTest(reason));
  }

  protected <T> T ifNullSkipTest(T testable, String reason) {
    ifNullThen(testable, () -> skipTest(reason));
    return testable;
  }

  protected <T> T ifCatchNullSkipTest(Supplier<T> evaluator, String reason) {
    T evaluatorResult = catchToNull(evaluator);
    return ifNullSkipTest(evaluatorResult, reason);
  }

  protected void mapSemanticKey(String keyPath, String keyName, String description,
      String describedValue) {
    SENT_CONFIG_DIFFERNATOR.mapSemanticKey(keyPath, keyName, description, describedValue);
  }

  public Set<String> getReceivedDevices() {
    return receivedEvents.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
        .map(Map.Entry::getKey).collect(toSet());
  }

  protected void captureReceivedEventsFor(Set<String> proxyDevices) {
    proxyDevices.forEach(proxyId -> receivedEvents.put(proxyId, new CaptureMap()));
  }

  public List<Map<String, Object>> getReceivedEvents(String deviceId, SubFolder subFolder) {
    return getReceivedEvents(deviceId).computeIfAbsent(subFolder, key -> new ArrayList<>());
  }

  public List<Map<String, Object>> getReceivedEvents(SubFolder subFolder) {
    return getReceivedEvents(getDeviceId(), subFolder);
  }

  public CaptureMap getReceivedEvents() {
    return getReceivedEvents(getDeviceId());
  }

  public CaptureMap getReceivedEvents(String deviceId) {
    return ofNullable(receivedEvents.get(deviceId)).orElse(otherEvents);
  }

  private void clearReceivedEvents() {
    receivedEvents.clear();
    receivedEvents.put(getDeviceId(), new CaptureMap());
    otherEvents.clear();
  }

  public Map<String, Object> getReceivedUpdates() {
    return receivedUpdates;
  }

  protected void waitForCapability(Class<? extends Capability> cap, String description,
      Supplier<String> action) {
    forCapability(cap, () -> waitFor(description, action));
  }

  protected void forCapability(Class<? extends Capability> capability, Runnable action) {
    try {
      ifTrueThen(isCapableOf(capability), action);
    } catch (Exception e) {
      info("Failed capability check " + capability.getSimpleName() + " because "
          + friendlyStackTrace(e));
      capExcept.put(capability, e);
    }
  }

  private boolean isCapableOf(Class<? extends Capability> capability) {
    Exception previous = capExcept.computeIfAbsent(capability, CapabilitySuccess::new);
    return previous instanceof CapabilitySuccess;
  }

  private boolean shouldValidateSchema(SubFolder folder) {
    return testSchema != null && (testSchema == folder || folder == SubFolder.VALIDATION);
  }

  private void validateTestSpecification(Description description) {
    try {
      Test annotation = requireNonNull(description.getAnnotation(Test.class), "missing annotation");
      long timeout = annotation.timeout();
      if (timeout > 0 && timeout < TWO_MINUTES_MS) {
        // The Junit test runner will default to ~5min for anything <2ms. Sigh.
        throw new RuntimeException(
            format("Test timeout less than minimum allowed %ss", TWO_MINUTES_MS / 1000));

      }
    } catch (Exception e) {
      throw new RuntimeException("While validating test specification", e);
    }
  }

  /**
   * Capability indicating if the target implements last_config state reporting.
   */
  class LastConfig implements Capability {

  }

  /**
   * Map of captured messages for a device, grouped by SubFolder.
   */
  protected static class CaptureMap extends HashMap<SubFolder, List<Map<String, Object>>> {

  }

  /**
   * Special exception to indicate that catching-loops should be terminated.
   */
  protected static class AbortMessageLoop extends RuntimeException {

    public AbortMessageLoop(String message) {
      super(message);
    }

    public AbortMessageLoop(String message, Exception e) {
      super(message, e);
    }
  }

  private static class CapabilitySuccess extends RuntimeException {

    public CapabilitySuccess(Class<? extends Capability> capability) {
      super("Capability supported");
    }
  }

  class SequenceTestWatcher extends TestWatcher {

    @Override
    protected void starting(@NotNull Description description) {
      testName = description.getMethodName();
      try {
        testDescription = description;
        testSummary = getTestSummary(description);
        testStage = getTestStage(description);
        testBucket = getBucket(description);
        testResult = SequenceResult.START;
        testSchema = ifNotNullGet(description.getAnnotation(ValidateSchema.class),
            ValidateSchema::value);

        setupSequencer();

        requireNonNull(deviceOutputDir, "deviceOutputDir not defined");
        testDir = new File(new File(deviceOutputDir, TESTS_OUT_DIR), testName);
        info("Cleaning test output dir " + testDir.getAbsolutePath());
        FileUtils.deleteDirectory(testDir);
        testDir.mkdirs();
        systemLog = new PrintWriter(newOutputStream(new File(testDir, SYSTEM_LOG).toPath()));
        sequencerLog = new PrintWriter(newOutputStream(new File(testDir, SEQUENCER_LOG).toPath()));
        sequenceMd = new PrintWriter(newOutputStream(new File(testDir, SEQUENCE_MD).toPath()));

        putSequencerResult(description, SequenceResult.START);

        ifNotNullThen(validationState,
            state -> state.cloud_version = client.getVersionInformation());

        ifNotNullThen(altClient, IotReflectorClient::activate);
        checkState(reflector().isActive(), "Reflector is not currently active");

        activeInstance = SequenceBase.this;

        validateTestSpecification(description);

        writeSequenceMdHeader();

        lastStatusLevel = 0;
        startSequenceStatus(description);

        startCaptureTime = 0;
        startTestTimeMs = System.currentTimeMillis();
        notice("starting test " + testName + " " + START_END_MARKER);

      } catch (IllegalArgumentException e) {
        putSequencerResult(description, ERRR);
        recordCompletion(ERRR, description, friendlyStackTrace(e));
        throw e;
      } catch (Exception e) {
        trace("Exception stack:", stackTraceString(e));
        putSequencerResult(description, ERRR);
        recordCompletion(ERRR, description, friendlyStackTrace(e));
        throw new RuntimeException("While starting " + testName, e);
      }
    }

    @Override
    protected void finished(Description description) {
      if (activeInstance == null) {
        return;
      }

      if (!testName.equals(description.getMethodName())) {
        throw new IllegalStateException("Unexpected test method name");
      }

      ifTrueThen(testResult == PASS && shouldValidateSchema(SubFolder.VALIDATION),
          () -> recordSchemaValidations(description));

      notice("ending test " + testName + " after " + timeSinceStart() + " " + START_END_MARKER);
      testName = null;
      if (deviceConfig != null) {
        deviceConfig.system.testing = null;
      }
      systemLog.close();
      sequencerLog.close();
      sequenceMd.close();

      activeInstance = null;
    }

    @Override
    protected void succeeded(Description description) {
      recordCompletion(PASS, description, "Sequence complete");
    }

    @Override
    protected void skipped(AssumptionViolatedException e, Description description) {
      failed(e, description);
    }

    @Override
    protected void failed(Throwable e, Description description) {
      if (activeInstance == null) {
        return;
      }

      recordMessages = false;

      final String message;
      final SequenceResult failureType;
      if (e instanceof TestTimedOutException) {
        waitingCondition.forEach(condition -> warning("While " + condition));
        error(format("stage timeout %s at %s", currentWaitingCondition(), timeSinceStart()));
        message = "Timeout " + currentWaitingCondition();
        failureType = FAIL;
      } else if (e instanceof AssumptionViolatedException) {
        message = e.getMessage();
        failureType = SKIP;
      } else {
        Throwable cause = e;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        message = friendlyStackTrace(cause);
        failureType = FAIL;
      }
      debug("exception message: " + friendlyStackTrace(e));
      trace("ending stack trace", stackTraceString(e));
      recordCompletion(failureType, description, message);
      String action = failureType == SKIP ? "skipped" : "failed";
      withRecordSequence(true, () -> recordSequence("Test " + action + ": " + message));
      if (failureType != SKIP) {
        resetRequired = true;
        if (debugLogLevel()) {
          processComplete(e);
          trace("Stack trace:", stackTraceString(e));
          error("terminating test " + testName + " after " + timeSinceStart() + " "
              + START_END_MARKER);
          System.exit(EXIT_CODE_PRESERVE);
        }
      }
    }

    private void recordCompletion(SequenceResult result, Description description, String message) {
      try {
        testResult = result;
        recordResult(result, description, message);
        Entry logEntry = new Entry();
        logEntry.category = VALIDATION_FEATURE_SEQUENCE;
        logEntry.message = message;
        logEntry.level = RESULT_LEVEL_MAP.get(result).value();
        logEntry.timestamp = cleanDate();
        writeSequencerLog(logEntry);
        writeSystemLog(logEntry);
        setSequenceStatus(description, result, logEntry);
      } catch (Exception e) {
        error("Error while recording completion: " + friendlyStackTrace(e));
        e.printStackTrace();
      }
    }
  }
}
