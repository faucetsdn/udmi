package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.daq.mqtt.sequencer.semantic.SemanticValue.actualize;
import static com.google.daq.mqtt.validator.Validator.CONFIG_PREFIX;
import static com.google.daq.mqtt.validator.Validator.STATE_PREFIX;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.GeneralUtils.changedLines;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static java.util.Optional.ofNullable;
import static org.junit.Assume.assumeTrue;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;
import static udmi.schema.Category.VALIDATION_FEATURE_SCHEMA;
import static udmi.schema.Category.VALIDATION_FEATURE_SEQUENCE;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;
import static udmi.schema.FeatureEnumeration.FeatureStage.PREVIEW;
import static udmi.schema.FeatureEnumeration.FeatureStage.STABLE;
import static udmi.schema.Level.ERROR;
import static udmi.schema.Level.NOTICE;
import static udmi.schema.Level.WARNING;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MockPublisher;
import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.ObjectDiffEngine;
import com.google.daq.mqtt.validator.AugmentedSystemConfig;
import com.google.daq.mqtt.validator.ReportingDevice;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.CleanDateFormat;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.Instant;
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
import java.util.Stack;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Bucket;
import udmi.schema.Config;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FeatureEnumeration;
import udmi.schema.FeatureEnumeration.FeatureStage;
import udmi.schema.FeatureValidationState;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointsetEvent;
import udmi.schema.SchemaValidationState;
import udmi.schema.SequenceValidationState;
import udmi.schema.SequenceValidationState.SequenceResult;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;
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
  public static final String FINALIZE_TEST = "finalize_test";
  static final FeatureStage DEFAULT_MIN_STAGE = BETA;
  private static final int FUNCTIONS_VERSION_BETA = Validator.REQUIRED_FUNCTION_VER;
  private static final int FUNCTIONS_VERSION_ALPHA = 9; // Version required for alpha execution.
  private static final long CONFIG_BARRIER_MS = 1000;
  private static final String START_END_MARKER = "################################";
  private static final String RESULT_FORMAT = "RESULT %s %s %s %s %s %s";
  private static final String SCHEMA_FORMAT = "SCHEMA %s %s %s %s %s %s";
  private static final String TESTS_OUT_DIR = "tests";
  private static final String EVENT_PREFIX = "event_";
  private static final String SYSTEM_EVENT_MESSAGE_BASE = "event_system";
  private static final int CONFIG_UPDATE_DELAY_MS = 8 * 1000;
  private static final int NORM_TIMEOUT_MS = 300 * 1000;
  private static final String RESULT_LOG_FILE = "RESULT.log";
  private static final String DEVICE_MODDATA = "%s/out/devices/%s/metadata_mod.json";
  private static final String DEVICE_METADATA = "%s/devices/%s/metadata.json";
  private static final String DEVICE_CONFIG_FORMAT = "%s/devices/%s/out/generated_config.json";
  private static final String SUMMARY_OUTPUT_FORMAT = "%s/out/sequencer_%s.json";
  private static final String CONFIG_ENV = "VALIDATOR_CONFIG";
  private static final String DEFAULT_CONFIG = "/tmp/validator_config.json";
  private static final String CONFIG_PATH =
      Objects.requireNonNullElse(System.getenv(CONFIG_ENV), DEFAULT_CONFIG);
  private static final Map<Class<?>, SubFolder> CLASS_SUBFOLDER_MAP = ImmutableMap.of(
      SystemEvent.class, SubFolder.SYSTEM,
      PointsetEvent.class, SubFolder.POINTSET,
      DiscoveryEvent.class, SubFolder.DISCOVERY
  );
  private static final Map<String, Class<?>> EXPECTED_UPDATES = ImmutableMap.of(
      SubType.CONFIG.value(), Config.class,
      SubType.STATE.value(), State.class
  );
  private static final Map<String, AtomicInteger> UPDATE_COUNTS = new HashMap<>();
  private static final String LOCAL_PREFIX = "local_";
  private static final String UPDATE_SUBFOLDER = SubFolder.UPDATE.value();
  private static final String STATE_SUBTYPE = SubType.STATE.value();
  private static final String CONFIG_SUBTYPE = SubType.CONFIG.value();
  private static final String REPLY_SUBTYPE = SubType.REPLY.value();
  private static final String LOCAL_CONFIG_UPDATE = LOCAL_PREFIX + UPDATE_SUBFOLDER;
  private static final String SEQUENCER_LOG = "sequencer.log";
  private static final String SYSTEM_LOG = "system.log";
  private static final String SEQUENCE_MD = "sequence.md";
  private static final int LOG_TIMEOUT_SEC = 10;
  private static final long ONE_SECOND_MS = 1000;
  private static final int EXIT_CODE_PRESERVE = -9;
  private static final String SYSTEM_TESTING_MARKER = " `system.testing";
  private static final BiMap<SequenceResult, Level> RESULT_LEVEL_MAP = ImmutableBiMap.of(
      SequenceResult.START, Level.INFO,
      SequenceResult.SKIP, Level.WARNING,
      SequenceResult.PASS, Level.NOTICE,
      SequenceResult.FAIL, ERROR
  );
  private static final Map<SubFolder, String> sentConfig = new HashMap<>();
  private static final ObjectDiffEngine SENT_CONFIG_DIFFERNATOR = new ObjectDiffEngine();
  private static final ObjectDiffEngine RECV_CONFIG_DIFFERNATOR = new ObjectDiffEngine();
  private static final ObjectDiffEngine RECV_STATE_DIFFERNATOR = new ObjectDiffEngine();
  private static final Set<String> configTransactions = new ConcurrentSkipListSet<>();
  private static final int MINIMUM_TEST_SEC = 30;
  protected static Metadata deviceMetadata;
  protected static String projectId;
  protected static String cloudRegion;
  protected static String registryId;
  protected static String altRegistry;
  protected static Config deviceConfig;
  protected static MessagePublisher altClient;
  protected static String serialNo;
  static ExecutionConfiguration validatorConfig;
  private static Validator messageValidator;
  private static ValidationState validationState;
  private static String udmiVersion;
  private static String siteModel;
  private static int logLevel;
  private static File deviceOutputDir;
  private static File resultSummary;
  private static MessagePublisher client;
  private static SequenceBase activeInstance;
  private static MessageBundle stashedBundle;
  private static boolean resetRequired = true;
  private static boolean enableAllTargets;

  static {
    // Sanity check to make sure ALPHA version is increased if forced by increased BETA.
    checkState(FUNCTIONS_VERSION_ALPHA >= FUNCTIONS_VERSION_BETA,
        "ALPHA functions version should not be > BETA");
  }

  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private final Queue<Entry> logEntryQueue = new LinkedBlockingDeque<>();
  private final Stack<String> waitingCondition = new Stack<>();
  private final Map<String, List<Entry>> validationResults = new HashMap<>();
  @Rule
  public Timeout globalTimeout = new Timeout(NORM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  @Rule
  public SequenceTestWatcher testWatcher = new SequenceTestWatcher();
  protected State deviceState;
  protected boolean configAcked;
  protected String lastSerialNo;
  private String extraField;
  private Instant lastConfigUpdate;
  private boolean enforceSerial;
  private String testName;
  private String testDescription;
  private Bucket testBucket;
  private FeatureStage testStage;
  private long startCaptureTime;
  private File testDir;
  private PrintWriter sequencerLog;
  private PrintWriter sequenceMd;
  private PrintWriter systemLog;
  private boolean recordMessages;
  private boolean recordSequence;
  private int previousEventCount;
  private String configExceptionTimestamp;
  private boolean useAlternateClient;
  private SequenceResult testResult;
  private int startStateCount;

  static void ensureValidatorConfig() {
    if (validatorConfig != null) {
      return;
    }
    if (SequenceRunner.executionConfiguration != null) {
      validatorConfig = SequenceRunner.executionConfiguration;
    } else {
      if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
        throw new RuntimeException(CONFIG_ENV + " env not defined.");
      }
      final File configFile = new File(CONFIG_PATH);
      try {
        System.err.println("Reading config file " + configFile.getAbsolutePath());
        validatorConfig = ConfigUtil.readValidatorConfig(configFile);
        SiteModel model = new SiteModel(validatorConfig.site_model);
        model.initialize();
        reportLoadingErrors(model);
        validatorConfig.cloud_region = ofNullable(validatorConfig.cloud_region)
            .orElse(model.getCloudRegion());
        validatorConfig.registry_id = ofNullable(validatorConfig.registry_id)
            .orElse(model.getRegistryId());
        validatorConfig.reflect_region = ofNullable(validatorConfig.reflect_region)
            .orElse(model.getReflectRegion());
      } catch (Exception e) {
        throw new RuntimeException("While loading " + configFile, e);
      }
    }
  }

  private static void reportLoadingErrors(SiteModel model) {
    String deviceId = validatorConfig.device_id;
    checkState(model.allDeviceIds().contains(deviceId),
        format("device_id %s not found in site model", deviceId));
    Exception exception = model.getMetadata(deviceId).exception;
    if (exception != null) {
      System.err.println("Device loading error: " + exception.getMessage());
    }
  }

  private static void setupSequencer() {
    ensureValidatorConfig();
    if (client != null) {
      return;
    }
    final String key_file;
    try {
      messageValidator = new Validator(validatorConfig);
      siteModel = checkNotNull(validatorConfig.site_model, "site_model not defined");
      projectId = checkNotNull(validatorConfig.project_id, "project_id not defined");
      udmiVersion = checkNotNull(validatorConfig.udmi_version, "udmi_version not defined");
      String serial = checkNotNull(validatorConfig.serial_no, "serial_no not defined");
      serialNo = serial.equals(SERIAL_NO_MISSING) ? null : serial;
      logLevel = Level.valueOf(checkNotNull(validatorConfig.log_level, "log_level not defined"))
          .value();
      key_file = checkNotNull(validatorConfig.key_file, "key_file not defined");
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("While processing validator config", e);
    }

    cloudRegion = validatorConfig.cloud_region;
    registryId = SiteModel.getRegistryActual(validatorConfig);
    altRegistry = SiteModel.getRegistryActual(validatorConfig.alt_registry,
        validatorConfig.registry_suffix);

    deviceMetadata = readDeviceMetadata();

    File baseOutputDir = new File(SequenceBase.siteModel, "out");
    deviceOutputDir = new File(baseOutputDir, "devices/" + getDeviceId());
    deviceOutputDir.mkdirs();

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Loading reflector key file from %s%n", new File(key_file).getAbsolutePath());
    System.err.printf("Validating against device %s serial %s%n", getDeviceId(), serialNo);
    client = getPublisherClient();
    altClient = getAlternateClient();
    initializeValidationState();
  }

  private static void initializeValidationState() {
    validationState = new ValidationState();
    validationState.features = new HashMap<>();
    validationState.schemas = new HashMap<>();
    validationState.start_time = new Date();
    validationState.udmi_version = Common.getUdmiVersion();
    validationState.cloud_version = client.getVersionInformation();
    Entry statusEntry = new Entry();
    statusEntry.category = VALIDATION_FEATURE_SEQUENCE;
    statusEntry.message = "Starting sequence run for device " + getDeviceId();
    statusEntry.level = Level.NOTICE.value();
    statusEntry.timestamp = new Date();
    validationState.status = statusEntry;
    updateValidationState();
  }

  private static MessagePublisher getPublisherClient() {
    boolean isMockProject = validatorConfig.project_id.equals(SiteModel.MOCK_PROJECT);
    boolean failFast = SiteModel.MOCK_PROJECT.equals(validatorConfig.alt_project);
    return isMockProject ? getMockClient(failFast) : getReflectorClient();
  }

  static MockPublisher getMockClient(boolean failFast) {
    return ofNullable((MockPublisher) client).orElseGet(() -> new MockPublisher(failFast));
  }

  private static MessagePublisher getAlternateClient() {
    if (altRegistry == null) {
      return null;
    }
    ExecutionConfiguration altConfiguration = GeneralUtils.deepCopy(validatorConfig);
    altConfiguration.registry_id = altRegistry;
    altConfiguration.registry_suffix = null;  // Don't double-dip adding suffix to the registry.
    altConfiguration.alt_registry = null;
    try {
      return new IotReflectorClient(altConfiguration, getRequiredFunctionsVersion());
    } catch (Exception e) {
      System.err.println("Could not connect to alternate registry, disabling: " + e.getMessage());
      if (traceLogLevel()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  private static int getRequiredFunctionsVersion() {
    FeatureStage minStage = isNullOrEmpty(validatorConfig.min_stage) ? DEFAULT_MIN_STAGE
        : FeatureStage.valueOf(validatorConfig.min_stage);
    return SequenceRunner.processGiven(ALPHA, minStage) ? FUNCTIONS_VERSION_ALPHA
        : FUNCTIONS_VERSION_BETA;
  }

  private static MessagePublisher getReflectorClient() {
    return new IotReflectorClient(validatorConfig, getRequiredFunctionsVersion());
  }

  static void resetState() {
    validatorConfig = null;
    client = null;
  }

  private static Metadata readDeviceMetadata() {
    File moddataFile = new File(format(DEVICE_MODDATA, siteModel, getDeviceId()));
    File metadataFile = new File(format(DEVICE_METADATA, siteModel, getDeviceId()));
    System.err.println("Checking for modified metadata file " + moddataFile.getAbsolutePath());
    File useFile = moddataFile.exists() ? moddataFile : metadataFile;
    System.err.println("Reading device metadata file " + useFile.getPath());
    return loadFileRequired(Metadata.class, useFile);
  }

  protected static String getDeviceId() {
    return checkNotNull(validatorConfig.device_id, "device_id not defined");
  }

  protected static void setDeviceId(String deviceId) {
    if (validatorConfig != null) {
      validatorConfig.device_id = deviceId;
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
    client.publish(getDeviceId(), VALIDATION_STATE_TOPIC, validationString);
  }

  static File getSequencerStateFile() {
    return new File(format(SUMMARY_OUTPUT_FORMAT, siteModel, getDeviceId()));
  }

  static void processComplete(Exception e) {
    boolean wasError = e != null;
    Entry statusEntry = new Entry();
    statusEntry.level = wasError ? ERROR.value() : Level.NOTICE.value();
    statusEntry.timestamp = cleanDate();
    statusEntry.message = wasError ? Common.getExceptionMessage(e) : "Run completed";
    statusEntry.category = VALIDATION_FEATURE_SEQUENCE;
    if (validationState != null) {
      validationState.status = statusEntry;
      summarizeSchemaStages();
      updateValidationState();
    }
  }

  static void enableAllBuckets(boolean enabled) {
    enableAllTargets = enabled;
  }

  private static String makeMessageBase(Map<String, String> attributes) {
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    String messageBase = format("%s_%s", subType, subFolder);
    return messageBase;
  }

  @NotNull
  private static Predicate<Map.Entry<String, List<Entry>>> isInterestingValidation() {
    return entry -> {
      String schemaName = entry.getKey();
      return !schemaName.startsWith(CONFIG_PREFIX)
          && (!schemaName.startsWith(STATE_PREFIX) || schemaName.equals(STATE_UPDATE_MESSAGE_TYPE));
    };
  }

  private static void emitSequenceResult(SequenceResult result, String bucket, String methodName,
      String stage,
      int score, String message) {
    emitSequencerOut(format(RESULT_FORMAT, result, bucket, methodName, stage, score, message));
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
    validationState.schemas.forEach((schema, schemaResult) -> {
      Map<FeatureStage, List<SequenceValidationState>> schemaStages = new HashMap<>();
      schemaResult.sequences.forEach((sequence, sequenceResult) -> {
        schemaStages.computeIfAbsent(sequenceResult.stage, stage -> new ArrayList<>())
            .add(sequenceResult);
      });
      schemaResult.stages = schemaStages.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey, SequenceBase::summarizeSchemaResults));
      schemaResult.stages.forEach((stage, entry) -> {
        SequenceResult result = RESULT_LEVEL_MAP.inverse().get(Level.fromValue(entry.level));
        String stageValue = stage.value();
        String schemaStage = schema + "_" + stageValue;
        emitSequenceResult(result, "schemas", schemaStage, stageValue.toUpperCase(), 5,
            entry.message);
      });
    });
  }

  private static Entry summarizeSchemaResults(
      Map.Entry<FeatureStage, List<SequenceValidationState>> entry) {
    List<SequenceValidationState> values = entry.getValue();
    Set<SequenceValidationState> failures = values.stream()
        .filter(state -> state.result != SequenceResult.PASS).collect(Collectors.toSet());

    Entry logEntry = new Entry();
    logEntry.category = VALIDATION_FEATURE_SCHEMA;
    logEntry.timestamp = cleanDate();
    if (values.isEmpty()) {
      logEntry.message = "No messages validated";
      logEntry.level = WARNING.value();
    } else if (failures.isEmpty()) {
      logEntry.message = "All schema validations passed";
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
    debug("last_start changed " + changed + ", last_start " + getTimestamp(use));
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
    String block = testDescription == null ? "" : format("%s\n\n", testDescription);
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
    deviceConfig = clean ? new Config() : readGeneratedConfig();
    sanitizeConfig(deviceConfig);
    deviceConfig.system.min_loglevel = Level.INFO.value();
    setExtraField(null);
    setLastStart(SemanticDate.describe("device reported", new Date(1)));
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

  private Config readGeneratedConfig() {
    File deviceConfigFile = new File(format(DEVICE_CONFIG_FORMAT, siteModel, getDeviceId()));
    try {
      debug("Reading generated config file " + deviceConfigFile.getPath());
      Config generatedConfig = JsonUtil.OBJECT_MAPPER.readValue(deviceConfigFile, Config.class);
      return ofNullable(generatedConfig).orElse(new Config());
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceConfigFile.getAbsolutePath(), e);
    }
  }

  /**
   * Prepare everything for an initial test run.
   */
  @Before
  public void setUp() {
    if (activeInstance == null) {
      throw new RuntimeException("Active sequencer instance not setup, aborting");
    }

    assumeTrue("Feature bucket not enabled", isBucketEnabled(testBucket));

    waitingConditionPush("starting test wrapper");
    checkState(reflector().isActive(), "Reflector is not currently active");

    // Old messages can sometimes take a while to clear out, so need some delay for stability.
    // TODO: Minimize time, or better yet find deterministic way to flush messages.
    safeSleep(CONFIG_UPDATE_DELAY_MS);

    testResult = SequenceResult.START;
    configAcked = false;
    enforceSerial = false;
    recordMessages = true;
    recordSequence = false;

    queryState();

    resetConfig(resetRequired);

    updateConfig("initial setup");

    untilTrue("device state update", () -> deviceState != null);

    // Do this late in the sequence to make sure any state is cleared out from previous test.
    startStateCount = getUpdateCount(SubType.STATE.value()).get();
    startCaptureTime = System.currentTimeMillis();
    receivedEvents.clear();
    validationResults.clear();

    recordSequence = true;
    waitingConditionPush("executing test");

    debug(format("stage begin %s at %s", waitingConditionPeek(), timeSinceStart()));
  }

  protected boolean isBucketEnabled(Bucket bucket) {
    if (bucket == SYSTEM || enableAllTargets || deviceMetadata.features == null) {
      return true;
    }
    FeatureEnumeration metadata = deviceMetadata.features.get(bucket.value());
    if (metadata == null) {
      return false;
    }
    return ofNullable(metadata.stage).orElse(STABLE).compareTo(PREVIEW) >= 0;
  }

  protected void resetConfig() {
    resetConfig(true);
  }

  protected void resetConfig(boolean fullReset) {
    recordSequence("Force reset config");
    withRecordSequence(false, () -> {
      debug("Starting reset_config full reset " + fullReset);
      if (fullReset) {
        resetDeviceConfig(true);
        setExtraField(RESET_CONFIG_MARKER);
        deviceConfig.system.testing.sequence_name = RESET_CONFIG_MARKER;
        sentConfig.clear();
        debug("configTransactions clear");
        configTransactions.clear();
        SENT_CONFIG_DIFFERNATOR.resetState(deviceConfig);
        updateConfig("full reset");
      }
      resetDeviceConfig(false);
      updateConfig("soft reset");
      debug("Done with reset_config");
      resetRequired = false;
    });
  }

  private void waitForConfigSync() {
    try {
      whileDoing("config sync", () -> messageEvaluateLoop(this::configIsPending));
      Duration between = Duration.between(lastConfigUpdate, CleanDateFormat.clean(Instant.now()));
      debug(format("Configuration sync took %ss", between.getSeconds()));
    } finally {
      debug("wait for config sync pending " + configIsPending(true));
    }
  }

  private void recordResult(SequenceResult result, Description description, String message) {
    putSequencerResult(description, result);
    String methodName = description.getMethodName();
    Feature feature = description.getAnnotation(Feature.class);
    Bucket bucket = getBucket(feature);
    String stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    int score = (feature == null ? Feature.DEFAULT_SCORE : feature.score());
    emitSequenceResult(result, bucket.value(), methodName, stage, score, message);
  }

  private void recordSchemaValidations(Description description) {
    validationResults.entrySet().stream()
        .filter(isInterestingValidation())
        .forEach(entry -> {
          String schemaName = entry.getKey();
          List<Entry> values = entry.getValue();
          if (values.isEmpty()) {
            collectSchemaResult(description, schemaName, SequenceResult.PASS, SCHEMA_PASS_DETAIL);
          } else {
            Set<String> duplicates = new HashSet<>();
            values.stream().filter(item -> duplicates.add(uniqueKey(item))).forEach(result ->
                collectSchemaResult(description, schemaName, SequenceResult.FAIL, result.detail));
          }
        });
  }

  private String uniqueKey(Entry entry) {
    return format("%s_%s_%s", entry.category, entry.message, entry.detail);
  }

  private void collectSchemaResult(Description description, String schemaName,
      SequenceResult result,
      String detail) {
    String sequence = description.getMethodName();
    Feature feature = description.getAnnotation(Feature.class);
    String bucket = getBucket(feature).value();
    String stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    emitSchemaResult(schemaName, result, detail, sequence, bucket, stage);

    SchemaValidationState schemaValidationState = validationState.schemas.computeIfAbsent(
        schemaName, this::newSchemaValidationState);

    SequenceValidationState state = schemaValidationState.sequences.computeIfAbsent(
        sequence, key -> new SequenceValidationState());

    state.result = result;
    state.stage = getTestStage(description);

    if (!result.equals(SequenceResult.PASS)) {
      Entry logEntry = new Entry();
      logEntry.category = VALIDATION_FEATURE_SEQUENCE;
      logEntry.message = detail;
      logEntry.level = RESULT_LEVEL_MAP.get(result).value();
      logEntry.timestamp = cleanDate();
      state.status = logEntry;
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

  private void recordRawMessage(Map<String, String> attributes, Map<String, Object> message) {
    if (testName == null) {
      return;
    }
    String messageBase = makeMessageBase(attributes);
    String timestamp = message == null ? getTimestamp() : (String) message.get("timestamp");
    if (traceLogLevel()) {
      messageBase = messageBase + "_" + timestamp;
    }

    recordRawMessage(message, messageBase);

    File attributeFile = new File(testDir, messageBase + ".attr");
    try {
      JsonUtil.OBJECT_MAPPER.writeValue(attributeFile, attributes);
    } catch (Exception e) {
      throw new RuntimeException("While writing attributes to " + attributeFile.getAbsolutePath(),
          e);
    }
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

    boolean systemEvent = messageBase.equals(SYSTEM_EVENT_MESSAGE_BASE);
    boolean anyEvent = messageBase.startsWith(EVENT_PREFIX);
    boolean localUpdate = messageBase.startsWith(LOCAL_PREFIX);
    boolean updateMessage = messageBase.endsWith(UPDATE_SUBFOLDER);
    boolean configMessage = messageBase.startsWith(CONFIG_SUBTYPE);
    boolean stateMessage = messageBase.startsWith(STATE_SUBTYPE);
    boolean syntheticMessage = (configMessage || stateMessage) && !updateMessage;

    String prefix = localUpdate ? "local " : "received ";
    File messageFile = new File(testDir, messageBase + ".json");
    Object savedException = message == null ? null : message.get(EXCEPTION_KEY);
    try {
      // An actual exception here will cause the JSON seralizer to barf, so temporarily sanitize.
      if (savedException instanceof Exception) {
        message.put(EXCEPTION_KEY, ((Exception) savedException).getMessage());
      }
      JsonUtil.OBJECT_MAPPER.writeValue(messageFile, message);
      if (systemEvent) {
        logSystemEvent(messageBase, message);
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

  private void logSystemEvent(String messageBase, Map<String, Object> message) {
    try {
      checkArgument(!messageBase.startsWith(LOCAL_PREFIX));
      SystemEvent event = JsonUtil.convertTo(SystemEvent.class, message);
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
    return format("%s %s %s: %s", getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level).name(), logEntry.category, logEntry.message);
  }

  private void writeSystemLogs(SystemEvent message) {
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
    String messageStr = format("%s %s %s %s", getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level), logEntry.category, logEntry.message);

    printWriter.println(messageStr);
    printWriter.flush();
    return messageStr;
  }

  protected void queryState() {
    debug("Sending device state query");
    reflector().publish(getDeviceId(), Common.STATE_QUERY_TOPIC, EMPTY_MESSAGE);
  }

  /**
   * Reset everything (including the state of the DUT) when done.
   */
  @After
  public void tearDown() {
    if (activeInstance == null) {
      return;
    }
    String condition = waitingCondition.isEmpty() ? "initialize" : waitingConditionPeek();
    debug(format("stage done %s at %s", condition, timeSinceStart()));
    recordSequence = false;

    recordMessages = false;
    configAcked = false;
  }

  private void assertConfigIsNotPending() {
    if (!configTransactions.isEmpty()) {
      throw new RuntimeException(
          "Unexpected config transactions: " + configTransactionsListString());
    }
  }

  protected void updateConfig(String reason) {
    assertConfigIsNotPending();
    // Add a forced sleep to make sure second-quantized timestamps are unique.
    safeSleep(CONFIG_BARRIER_MS);
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
    updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    if (configIsPending()) {
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
      String sentBlockConfig = sentConfig.computeIfAbsent(subBlock, key -> "null");
      boolean updated = !messageData.equals(sentBlockConfig);
      trace("updated check config_" + subBlock, sentBlockConfig);
      if (updated) {
        String augmentedMessage = actualize(stringify(data));
        String topic = subBlock + "/config";
        final String transactionId = reflector().publish(getDeviceId(), topic, augmentedMessage);
        debug(format("update %s_%s, id %s", CONFIG_SUBTYPE, subBlock, transactionId));
        recordRawMessage(data, LOCAL_PREFIX + subBlock.value());
        sentConfig.put(subBlock, messageData);
        debug(format("configTransactions add " + transactionId));
        configTransactions.add(transactionId);
      } else {
        trace("unchanged config_" + subBlock + ": " + messageData);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  private void captureConfigChange(String reason) {
    try {
      String suffix = reason == null ? "" : (" " + reason);
      String header = format("Update config%s: ", suffix);
      debug(header + getTimestamp(deviceConfig.timestamp));
      recordRawMessage(deviceConfig, LOCAL_CONFIG_UPDATE);
      List<String> allDiffs = SENT_CONFIG_DIFFERNATOR.computeChanges(deviceConfig);
      List<String> filteredDiffs = filterTesting(allDiffs);
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
          "Aborting message loop while " + waitingConditionPeek() + " because " + e.getMessage());
      throw e;
    } catch (Exception e) {
      if (traceLogLevel()) {
        trace("Suppressed " + e + " from " + getExceptionLine(e));
      } else {
        debug("Suppressing exception: " + e);
      }
      return null;
    }
  }

  private String getExceptionLine(Exception e) {
    return Common.getExceptionLine(e, SequenceBase.class);
  }

  protected void checkThat(String description, Supplier<Boolean> condition) {
    checkThat(description, condition, null);
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
    String notDescription = "no " + description;
    if (catchToTrue(condition)) {
      warning("Failed check that " + notDescription);
      throw new IllegalStateException("Failed check that " + notDescription);
    }
    recordSequence("Check that " + notDescription);
  }

  protected void untilLogged(String category, Level exactLevel) {
    final List<Entry> entries = new ArrayList<>();
    untilTrue(format("log category `%s` level `%s` was logged", category, exactLevel),
        () -> {
          processLogMessages();
          entries.addAll(matchingLogQueueEntries(
              entry -> category.equals(entry.category) && entry.level == exactLevel.value()));
          return !entries.isEmpty();
        });
    entries.forEach(entry -> debug("matching " + entryMessage(entry)));
  }

  protected void checkNotLogged(String category, Level minLevel) {
    withRecordSequence(false, () -> {
      untilTrue("last_config synchronized",
          () -> dateEquals(deviceConfig.timestamp, deviceState.system.last_config));
      processLogMessages();
    });
    final Instant endTime = lastConfigUpdate.plusSeconds(LOG_TIMEOUT_SEC);
    List<Entry> entries = matchingLogQueueEntries(
        entry -> category.equals(entry.category) && entry.level >= minLevel.value());
    checkThat(format("log category `%s` level `%s` not logged", category, minLevel), () -> {
      if (!entries.isEmpty()) {
        warning(format("Filtered config between %s and %s", getTimestamp(lastConfigUpdate),
            getTimestamp(endTime)));
        entries.forEach(entry -> error("undesirable " + entryMessage(entry)));
      }
      return entries.isEmpty();
    });
  }

  private List<Entry> matchingLogQueueEntries(Function<Entry, Boolean> predicate) {
    return logEntryQueue.stream().filter(predicate::apply).collect(Collectors.toList());
  }

  private void processLogMessages() {
    List<SystemEvent> receivedEvents = popReceivedEvents(SystemEvent.class);
    receivedEvents.forEach(systemEvent -> {
      int eventCount = ofNullable(systemEvent.event_count).orElse(previousEventCount + 1);
      if (eventCount != previousEventCount + 1) {
        debug("Missing system events " + previousEventCount + " -> " + eventCount);
      }
      previousEventCount = eventCount;
      logEntryQueue.addAll(ofNullable(systemEvent.logentries).orElse(ImmutableList.of()));
    });
    List<Entry> toRemove = logEntryQueue.stream()
        .filter(entry -> entry.timestamp.toInstant().isBefore(lastConfigUpdate))
        .collect(Collectors.toList());
    if (!toRemove.isEmpty()) {
      debug("ignoring log entries before lastConfigUpdate " + lastConfigUpdate);
    }
    toRemove.forEach(entry -> debug(" x " + entryMessage(entry)));
    logEntryQueue.removeAll(toRemove);
  }

  protected void whileDoing(String condition, Runnable action) {
    final Instant startTime = Instant.now();

    waitingConditionPush(condition);

    action.run();

    waitingConditionPop(startTime);
  }

  private void waitingConditionPop(Instant startTime) {
    Duration between = Duration.between(startTime, Instant.now());
    debug(format("stage finished %s at %s after %ss", waitingConditionPeek(),
        timeSinceStart(), between.toSeconds()));
    waitingCondition.pop();
    ifTrueThen(!waitingCondition.isEmpty(),
        () -> trace(format("stage resume %s at %s", waitingConditionPeek(), timeSinceStart())));
  }

  private void waitingConditionPush(String condition) {
    ifTrueThen(!waitingCondition.isEmpty(),
        () -> trace(format("stage suspend %s at %s", waitingConditionPeek(), timeSinceStart())));
    waitingCondition.push("waiting for " + condition);
    info(format("stage start %s at %s", waitingConditionPeek(), timeSinceStart()));
  }

  private String waitingConditionPeek() {
    return waitingCondition.peek();
  }

  private void untilLoop(Supplier<Boolean> evaluator, String description) {
    whileDoing(description, () -> {
      updateConfig("before " + description);
      recordSequence("Wait for " + description);
      messageEvaluateLoop(evaluator);
    });
  }

  private void messageEvaluateLoop(Supplier<Boolean> evaluator) {
    while (evaluator.get()) {
      processNextMessage();
    }
  }

  private void recordSequence(String step) {
    if (recordSequence) {
      sequenceMd.println("1. " + step.trim());
      sequenceMd.flush();
    }
  }

  private void recordBullet(String step) {
    if (recordSequence) {
      info("Device config " + step);
      sequenceMd.println("    * " + step);
    }
  }

  private String timeSinceStart() {
    return secSinceStart() + "s";
  }

  private long secSinceStart() {
    return (System.currentTimeMillis() - startCaptureTime) / 1000;
  }

  protected void untilTrue(String description, Supplier<Boolean> evaluator) {
    untilLoop(() -> !catchToFalse(evaluator), description);
  }

  protected void untilFalse(String description, Supplier<Boolean> evaluator) {
    untilLoop(() -> catchToTrue(evaluator), description);
  }

  protected void untilUntrue(String description, Supplier<Boolean> evaluator) {
    untilLoop(() -> catchToFalse(evaluator), description);
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
      if (!reflector().isActive()) {
        throw new RuntimeException("Trying to receive message from inactive client");
      }
      MessageBundle bundle = reflector().takeNextMessage(true);
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
    String deviceId = attributes.get("deviceId");
    String subFolderRaw = attributes.get("subFolder");
    String subTypeRaw = attributes.get("subType");
    String transactionId = attributes.get("transactionId");

    String commandSignature = format("%s/%s/%s", deviceId, subTypeRaw, subFolderRaw);
    trace("received command " + commandSignature);

    if (SubType.REPLY.value().equals(subTypeRaw)) {
      return;
    }

    if (SubFolder.ERROR.value().equals(subFolderRaw)) {
      handlePipelineError(subTypeRaw, message);
      return;
    }

    if (!SequenceBase.getDeviceId().equals(deviceId)) {
      return;
    }

    recordRawMessage(attributes, message);

    preprocessMessage(attributes, message);

    validateMessage(attributes, message);

    if (SubFolder.UPDATE.value().equals(subFolderRaw)) {
      handleReflectorMessage(subTypeRaw, message, transactionId);
    } else {
      handleDeviceMessage(message, subFolderRaw, subTypeRaw, transactionId);
    }
  }

  private void validateMessage(Map<String, String> attributes, Map<String, Object> message) {
    if (SubType.CONFIG.value().equals(attributes.get("subType"))) {
      return;
    }

    String deviceId = attributes.get("deviceId");
    ReportingDevice reportingDevice = new ReportingDevice(deviceId);
    messageValidator.validateDeviceMessage(reportingDevice, message, attributes);
    String messageBase = makeMessageBase(attributes);
    validationResults.computeIfAbsent(messageBase, key -> new ArrayList<>())
        .addAll(reportingDevice.getMessageEntries());
  }

  private void handlePipelineError(String subTypeRaw, Map<String, Object> message) {
    throw new RuntimeException(
        format("Pipeline type %s error: %s", subTypeRaw, message.get("error")));
  }

  private void handleDeviceMessage(Map<String, Object> message, String subFolderRaw,
      String subTypeRaw, String transactionId) {
    debug(format("Handling device message %s/%s", subTypeRaw, subFolderRaw));
    SubFolder subFolder = SubFolder.fromValue(subFolderRaw);
    SubType subType = SubType.fromValue(subTypeRaw);
    switch (subType) {
      case CONFIG:
        // These are echos of sent partial config messages, so do nothing.
        debug("Ignoring echo configTransaction " + transactionId);
        break;
      case STATE:
        // State updates are handled as a monolithic block with a state reflector update.
        trace("Ignoring partial state update");
        break;
      case EVENT:
        handleEventMessage(subFolder, message);
        break;
      default:
        info("Encountered unexpected subType " + subTypeRaw);
    }
  }

  private synchronized void handleReflectorMessage(String subTypeRaw,
      Map<String, Object> message, String txnId) {
    try {
      // Do this first to handle all cases of a Config payload, including exceptions.
      if (CONFIG_SUBTYPE.equals(subTypeRaw) && txnId != null) {
        debug("Removing configTransaction " + txnId);
        configTransactions.remove(txnId);
      }
      if (message.containsKey(EXCEPTION_KEY)) {
        debug("Ignoring reflector exception:\n" + message.get(EXCEPTION_KEY).toString());
        configExceptionTimestamp = (String) message.get(TIMESTAMP_KEY);
        return;
      }
      configExceptionTimestamp = null;
      if (!EXPECTED_UPDATES.containsKey(subTypeRaw)) {
        debug("Ignoring unexpected update type " + subTypeRaw);
        return;
      }
      Object converted = JsonUtil.convertTo(EXPECTED_UPDATES.get(subTypeRaw), message);
      receivedUpdates.put(subTypeRaw, converted);
      int updateCount = getUpdateCount(subTypeRaw).incrementAndGet();
      if (converted instanceof Config config) {
        String extraField = getExtraField(message);
        if (RESET_CONFIG_MARKER.equals(extraField)) {
          debug("Update with config reset");
        } else if ("break_json".equals(extraField)) {
          error("Shouldn't be seeing this!");
          return;
        }
        debug(format("Updated config %s, id %s", getTimestamp(config.timestamp), txnId));
        List<String> changes = updateDeviceConfig(config);
        if (updateCount == 1) {
          info(format("Initial config #%03d", updateCount), stringify(deviceConfig));
        } else {
          info(format("Updated config #%03d", updateCount), changedLines(changes));
        }
      } else if (converted instanceof State convertedState) {
        if (deviceState != null && convertedState.timestamp != null
            && convertedState.timestamp.before(deviceState.timestamp)) {
          warning("Ignoring out-of-order state update " + convertedState);
          return;
        }
        if (updateCount == 1) {
          info(format("Initial state #%03d", updateCount), stringify(converted));
        } else {
          List<String> stateChanges = RECV_STATE_DIFFERNATOR.computeChanges(converted);
          info(format("Updated state #%03d", updateCount), changedLines(stateChanges));
        }
        deviceState = convertedState;
        validSerialNo();
        debug(format("Updated state has last_config %s (expecting %s)",
            getTimestamp(deviceState.system.last_config),
            getTimestamp(deviceConfig.timestamp)));
      } else {
        error("Unknown update type " + converted.getClass().getSimpleName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling reflector message", e);
    }
  }

  private List<String> updateDeviceConfig(Config config) {
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

  private void preprocessMessage(Map<String, String> attributes, Map<String, Object> message) {
    if (SubType.STATE.value().equals(attributes.get(Common.SUBTYPE_PROPERTY_KEY))) {
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
    ifNotTrueThen(wasAcked, () -> configAcked = true);
  }

  private String getExtraField(Map<String, Object> message) {
    Object system = message.get("system");
    if (system instanceof Map) {
      return (String) ((Map<?, ?>) system).get("extra_field");
    }
    return null;
  }

  private void handleEventMessage(SubFolder subFolder, Map<String, Object> message) {
    receivedEvents.computeIfAbsent(subFolder, key -> new ArrayList<>()).add(message);
    if (SubFolder.SYSTEM.equals(subFolder)) {
      writeSystemLogs(JsonUtil.convertTo(SystemEvent.class, message));
    }
  }

  private boolean configIsPending() {
    return configIsPending(false);
  }

  private boolean configIsPending(boolean debugOut) {
    Date stateLast = catchToNull(() -> deviceState.system.operation.last_start);
    Date configLast = catchToNull(() -> deviceConfig.system.operation.last_start);
    boolean synced = stateLast == null || stateLast.equals(configLast);
    boolean pending = !(synced && configTransactions.isEmpty());
    if (debugOut && pending) {
      notice(format("last_start synchronized %s: state/%s =? config/%s", synced,
          getTimestamp(stateLast), getTimestamp(configLast)));
      notice(format("pending configTransactions: %s", configTransactionsListString()));
    }
    return pending;
  }

  @NotNull
  private String configTransactionsListString() {
    return Joiner.on(' ').join(configTransactions);
  }

  /**
   * Filter out any testing-oriented messages, since they should not impact behavior.
   */
  private List<String> filterTesting(List<String> allDiffs) {
    return allDiffs.stream().filter(message -> !message.contains(SYSTEM_TESTING_MARKER))
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
  protected int countReceivedEvents(Class clazz) {
    SubFolder subFolder = CLASS_SUBFOLDER_MAP.get(clazz);
    List<Map<String, Object>> events = receivedEvents.get(subFolder);
    if (events == null) {
      return 0;
    }
    return events.size();
  }

  protected <T> List<T> popReceivedEvents(Class<T> clazz) {
    SubFolder subFolder = CLASS_SUBFOLDER_MAP.get(clazz);
    List<Map<String, Object>> events = receivedEvents.remove(subFolder);
    if (events == null) {
      return ImmutableList.of();
    }
    return events.stream().map(message -> JsonUtil.convertTo(clazz, message))
        .collect(Collectors.toList());
  }

  protected boolean isAlternateClient() {
    return useAlternateClient;
  }

  protected void withAlternateClient(Runnable evaluator) {
    checkNotNull(altClient, "Alternate client used but test not skipped");
    checkState(!useAlternateClient, "Alternate client already in use");
    checkState(deviceConfig.system.testing.endpoint_type == null, "endpoint type not null");
    try {
      useAlternateClient = true;
      deviceConfig.system.testing.endpoint_type = "alternate";
      whileDoing("using alternate client", evaluator);
    } finally {
      useAlternateClient = false;
      catchToNull(() -> deviceConfig.system.testing.endpoint_type = null);
    }
  }

  /**
   * Mirrors the current config to the "other" config, where the current and other configs are
   * defined by the useAlternateClient flag. This call is used to warm-up the new config before a
   * switch, so that when the client is switched, it is ready with the right (up to date) config
   * contents.
   */
  protected void mirrorToOtherConfig() {
    // First make sure the current config is up-to-date with any local changes.
    updateConfig("mirroring config " + useAlternateClient);

    // Grab the as-reported current config, to get a copy of the actual values uesd.
    Config target = (Config) receivedUpdates.get(CONFIG_SUBTYPE);

    // Modify the config with the alternate endpoint_type, prefetching the actual change.
    target.system.testing.endpoint_type = useAlternateClient ? null : "alternate";

    // Now update the other config with the tweaked version, in prep for the actual switch.
    updateMirrorConfig(actualize(stringify(target)));
  }

  /**
   * Clears out the "other" (not current) config, so that it can't be inadvertantly used for
   * something. This is the simple version of the endpoint going down (actually turning down the
   * endpoint would be a lot more work).
   */
  protected void clearOtherConfig() {
    // No need to be fancy here, just clear out the other config with an empty blob.
    updateMirrorConfig("{}");
  }

  private void updateMirrorConfig(String receivedConfig) {
    if (altClient != null) {
      String topic = UPDATE_SUBFOLDER + "/" + CONFIG_SUBTYPE;
      reflector(!useAlternateClient).publish(getDeviceId(), topic, receivedConfig);
      // There's a race condition if the mirror command gets delayed, so chill for a bit.
      safeSleep(ONE_SECOND_MS);
    }
  }

  private MessagePublisher reflector() {
    return reflector(useAlternateClient);
  }

  private MessagePublisher reflector(boolean useAlternateClient) {
    return useAlternateClient ? altClient : client;
  }

  protected boolean stateMatchesConfigTimestamp() {
    Date expectedConfig = deviceConfig.timestamp;
    Date lastConfig = deviceState.system.last_config;
    return dateEquals(expectedConfig, lastConfig);
  }

  protected boolean hasInterestingSystemStatus() {
    if (deviceState.system.status != null) {
      debug("Status level: " + deviceState.system.status.level);
    }
    return deviceState.system.status != null
        && deviceState.system.status.level >= Level.WARNING.value();
  }

  protected void checkThatHasInterestingSystemStatus(boolean isInteresting) {
    BiConsumer<String, Supplier<Boolean>> check =
        isInteresting ? this::checkThat : this::checkNotThat;
    check.accept("interesting system status", this::hasInterestingSystemStatus);
  }

  protected void untilHasInterestingSystemStatus(boolean isInteresting) {
    BiConsumer<String, Supplier<Boolean>> until =
        isInteresting ? this::untilTrue : this::untilFalse;
    until.accept("interesting system status", this::hasInterestingSystemStatus);
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
    setSequenceStatus(description, startResult, entry);
  }

  private void setSequenceStatus(Description description, SequenceResult result, Entry logEntry) {
    String bucket = getBucket(description).value();
    String sequence = description.getMethodName();
    SequenceValidationState sequenceValidationState = validationState.features.computeIfAbsent(
        bucket, this::newFeatureValidationState).sequences.computeIfAbsent(
        sequence, key -> new SequenceValidationState());
    sequenceValidationState.status = logEntry;
    sequenceValidationState.result = result;
    sequenceValidationState.summary = getTestSummary(description);
    sequenceValidationState.stage = getTestStage(description);
    updateValidationState();
  }

  private FeatureValidationState newFeatureValidationState(String key) {
    FeatureValidationState featureValidationState = new FeatureValidationState();
    featureValidationState.sequences = new HashMap<>();
    return featureValidationState;
  }

  private boolean receivedAtLeastOneState() {
    return getUpdateCount(SubType.STATE.value()).get() > startStateCount;
  }

  protected void ensureTestCapture() {
    // Add some additional checks to make sure enough stuff is ready for schema validation.
    deviceConfig.system.testing.sequence_name = FINALIZE_TEST;
    updateConfig("test finalization");

    withRecordSequence(false,
        () -> untilTrue("received at least one state update", this::receivedAtLeastOneState));
  }

  /**
   * Add a summary of a test, with a simple description of what it's testing.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface Summary {

    /**
     * Summary description of the test.
     *
     * @return test summary description
     */
    String value();
  }

  /**
   * Special exception to indicate that catching-loops should be terminated.
   */
  protected static class AbortMessageLoop extends RuntimeException {

    public AbortMessageLoop(String message) {
      super(message);
    }
  }

  class SequenceTestWatcher extends TestWatcher {

    @Override
    protected void starting(@NotNull Description description) {
      testName = description.getMethodName();
      try {
        setupSequencer();
        putSequencerResult(description, SequenceResult.START);
        checkState(reflector().isActive(), "Reflector is not currently active");

        testDescription = getTestSummary(description);
        testStage = getTestStage(description);
        testBucket = getBucket(description);

        testDir = new File(new File(deviceOutputDir, TESTS_OUT_DIR), testName);
        FileUtils.deleteDirectory(testDir);
        testDir.mkdirs();
        systemLog = new PrintWriter(newOutputStream(new File(testDir, SYSTEM_LOG).toPath()));
        sequencerLog = new PrintWriter(newOutputStream(new File(testDir, SEQUENCER_LOG).toPath()));
        sequenceMd = new PrintWriter(newOutputStream(new File(testDir, SEQUENCE_MD).toPath()));
        writeSequenceMdHeader();

        startSequenceStatus(description);

        notice("starting test " + testName + " " + START_END_MARKER);

        activeInstance = SequenceBase.this;
      } catch (Exception e) {
        e.printStackTrace();
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

    private boolean beenLongEnough() {
      return secSinceStart() >= MINIMUM_TEST_SEC;
    }

    @Override
    protected void succeeded(Description description) {
      // Ensure that enough time has passed to capture event messages for schema validation.
      untilTrue(format("minimum test duration of %ss", MINIMUM_TEST_SEC), this::beenLongEnough);

      recordCompletion(SequenceResult.PASS, description, "Sequence complete");
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
      final String message;
      final SequenceResult failureType;
      if (e instanceof TestTimedOutException) {
        waitingCondition.forEach(condition -> warning("while " + condition));
        error(format("stage timeout %s at %s", waitingConditionPeek(), timeSinceStart()));
        message = "timeout " + waitingConditionPeek();
        failureType = SequenceResult.FAIL;
      } else if (e instanceof AssumptionViolatedException) {
        message = e.getMessage();
        failureType = SequenceResult.SKIP;
      } else {
        while (e.getCause() != null) {
          e = e.getCause();
        }
        message = friendlyStackTrace(e);
        failureType = SequenceResult.FAIL;
      }
      debug("exception message: " + Common.getExceptionMessage(e));
      trace("ending stack trace", stackTraceString(e));
      recordCompletion(failureType, description, message);
      String action = failureType == SequenceResult.SKIP ? "skipped" : "failed";
      withRecordSequence(true, () -> recordSequence("Test " + action + ": " + message));
      if (failureType != SequenceResult.SKIP) {
        resetRequired = true;
        if (debugLogLevel()) {
          error("Forcing exit to preserve failing config/state " + START_END_MARKER);
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

        recordSchemaValidations(description);
      } catch (Exception e) {
        error("Error while recording completion: " + friendlyStackTrace(e));
        e.printStackTrace();
      }
    }
  }
}
