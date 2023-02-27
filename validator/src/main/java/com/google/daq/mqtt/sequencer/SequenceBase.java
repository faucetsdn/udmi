package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.sequencer.Feature.Stage.STABLE;
import static com.google.daq.mqtt.sequencer.semantic.SemanticValue.actualize;
import static com.google.daq.mqtt.util.Common.EXCEPTION_KEY;
import static com.google.daq.mqtt.util.Common.TIMESTAMP_PROPERTY_KEY;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.nio.file.Files.newOutputStream;
import static java.util.Optional.ofNullable;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MockPublisher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.Feature.Stage;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.ConfigDiffEngine;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.AugmentedState;
import com.google.daq.mqtt.validator.AugmentedSystemConfig;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.CleanDateFormat;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Bucket;
import udmi.schema.Config;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointsetEvent;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;
import udmi.schema.TestingSystemConfig;

/**
 * Validate a device using a sequence of message exchanges.
 */
public class SequenceBase {

  public static final String RESULT_FAIL = "fail";
  public static final String RESULT_PASS = "pass";
  public static final String RESULT_SKIP = "skip";
  public static final String RESULT_FORMAT = "RESULT %s %s %s %s %s %s";
  public static final String TESTS_OUT_DIR = "tests";
  public static final String SERIAL_NO_MISSING = "//";
  public static final String SEQUENCER_CATEGORY = "sequencer";
  public static final String EVENT_PREFIX = "event_";
  public static final String SYSTEM_EVENT_MESSAGE_BASE = "event_system";
  public static final int CONFIG_UPDATE_DELAY_MS = 8 * 1000;
  public static final int NORM_TIMEOUT_MS = 300 * 1000;
  public static final String CONFIG_NONCE_KEY = "debug_config_nonce";
  private static final String EMPTY_MESSAGE = "{}";
  private static final String RESULT_LOG_FILE = "RESULT.log";
  private static final String DEVICE_METADATA_FORMAT = "%s/devices/%s/metadata.json";
  private static final String DEVICE_CONFIG_FORMAT = "%s/devices/%s/out/generated_config.json";
  private static final String CONFIG_ENV = "VALIDATOR_CONFIG";
  private static final String DEFAULT_CONFIG = "/tmp/validator_config.json";
  private static final String CONFIG_PATH =
      Objects.requireNonNullElse(System.getenv(CONFIG_ENV), DEFAULT_CONFIG);
  private static final Map<Class<?>, SubFolder> CLASS_SUBFOLDER_MAP = ImmutableMap.of(
      SystemEvent.class, SubFolder.SYSTEM,
      PointsetEvent.class, SubFolder.POINTSET,
      DiscoveryEvent.class, SubFolder.DISCOVERY
  );
  private static final Map<String, Class<?>> expectedUpdates = ImmutableMap.of(
      "config", Config.class,
      "state", AugmentedState.class
  );
  private static final Map<String, AtomicInteger> UPDATE_COUNTS = new HashMap<>();
  private static final String LOCAL_PREFIX = "local_";
  private static final String UPDATE_SUBFOLDER = SubFolder.UPDATE.value();
  private static final String CONFIG_SUBTYPE = SubType.CONFIG.value();
  private static final String LOCAL_CONFIG_UPDATE = LOCAL_PREFIX + UPDATE_SUBFOLDER;
  private static final String SEQUENCER_LOG = "sequencer.log";
  private static final String SYSTEM_LOG = "system.log";
  private static final String SEQUENCE_MD = "sequence.md";
  private static final int LOG_TIMEOUT_SEC = 10;
  private static final long ONE_SECOND_MS = 1000;
  private static final int EXIT_CODE_PRESERVE = -9;
  private static final String SYSTEM_TESTING_MARKER = " `system.testing";
  private static final Map<SubFolder, String> sentConfig = new HashMap<>();
  private static final ConfigDiffEngine configDiffEngine = new ConfigDiffEngine();
  private static final Set<String> configTransactions = new ConcurrentSkipListSet<>();
  private static boolean udmisInstallValid;
  protected static Metadata deviceMetadata;
  protected static String projectId;
  protected static String cloudRegion;
  protected static String registryId;
  protected static String altRegistry;
  protected static Config deviceConfig;
  static ExecutionConfiguration validatorConfig;
  private static String udmiVersion;
  private static String siteModel;
  private static String serialNo;
  private static int logLevel;
  private static File deviceOutputDir;
  private static File resultSummary;
  private static MessagePublisher client;
  private static MessagePublisher altClient;
  private static SequenceBase activeInstance;
  private static MessageBundle stashedBundle;
  private static boolean resetRequired = true;
  private final Map<SubFolder, String> receivedState = new HashMap<>();
  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private final Queue<Entry> logEntryQueue = new LinkedBlockingDeque<>();
  private final Stack<String> waitingCondition = new Stack<>();
  @Rule
  public Timeout globalTimeout = new Timeout(NORM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  @Rule
  public SequenceTestWatcher testWatcher = new SequenceTestWatcher();
  protected State deviceState;
  protected boolean configAcked;
  private String extraField;
  private Instant lastConfigUpdate;
  private boolean enforceSerial;
  private String testName;
  private String testDescription;
  private Stage testStage;
  private long testStartTimeMs;
  private File testDir;
  private PrintWriter sequencerLog;
  private PrintWriter sequenceMd;
  private PrintWriter systemLog;
  private String lastSerialNo;
  private boolean recordMessages;
  private boolean recordSequence;
  private int previousEventCount;
  private String configExceptionTimestamp;
  private boolean useAlternateClient;

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
        validatorConfig.cloud_region = Optional.ofNullable(validatorConfig.cloud_region)
            .orElse(model.getCloudRegion());
        validatorConfig.registry_id = Optional.ofNullable(validatorConfig.registry_id)
            .orElse(model.getRegistryId());
        validatorConfig.reflect_region = Optional.ofNullable(validatorConfig.reflect_region)
            .orElse(model.getReflectRegion());
      } catch (Exception e) {
        throw new RuntimeException("While loading " + configFile, e);
      }
    }
  }

  private static void setupSequencer() {
    ensureValidatorConfig();
    if (client != null) {
      return;
    }
    final String key_file;
    try {
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
    registryId = validatorConfig.registry_id;
    altRegistry = Strings.emptyToNull(validatorConfig.alt_registry);

    deviceMetadata = readDeviceMetadata();

    deviceOutputDir = new File(new File(SequenceBase.siteModel), "out/devices/" + getDeviceId());
    deviceOutputDir.mkdirs();

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Loading reflector key file from %s%n", new File(key_file).getAbsolutePath());
    System.err.printf("Validating against device %s serial %s%n", getDeviceId(), serialNo);
    client = getPublisherClient();
    altClient = getAlternateClient();
  }

  private static MessagePublisher getPublisherClient() {
    boolean isMockProject = validatorConfig.project_id.equals(SiteModel.MOCK_PROJECT);
    boolean failFast = SiteModel.MOCK_PROJECT.equals(validatorConfig.alt_project);
    return isMockProject ? getMockClient(failFast) : getReflectorClient();
  }

  static MockPublisher getMockClient(boolean failFast) {
    return Optional.ofNullable((MockPublisher) client).orElseGet(() -> new MockPublisher(failFast));
  }

  private static MessagePublisher getAlternateClient() {
    if (altRegistry == null) {
      return null;
    }
    ExecutionConfiguration altConfiguration = GeneralUtils.deepCopy(validatorConfig);
    altConfiguration.registry_id = altRegistry;
    altConfiguration.alt_registry = null;
    return new IotReflectorClient(altConfiguration);
  }

  private static MessagePublisher getReflectorClient() {
    return new IotReflectorClient(validatorConfig);
  }

  static void resetState() {
    validatorConfig = null;
    client = null;
  }

  private static Metadata readDeviceMetadata() {
    File deviceMetadataFile = new File(
        String.format(DEVICE_METADATA_FORMAT, siteModel, getDeviceId()));
    try {
      System.err.println("Reading device metadata file " + deviceMetadataFile.getPath());
      return JsonUtil.OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  protected static String getDeviceId() {
    return checkNotNull(validatorConfig.device_id, "device_id not defined");
  }

  protected static void setDeviceId(String deviceId) {
    if (validatorConfig != null) {
      validatorConfig.device_id = deviceId;
    }
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
   * Set the last_start field. Used for change tracking..
   *
   * @param lastStart last start value to use
   */
  public void setLastStart(Date lastStart) {
    boolean lastStartChanged = !stringify(deviceConfig.system.operation.last_start).equals(
        stringify(lastStart));
    debug("lastStartChanged " + lastStartChanged + ", last_start " + getTimestamp(lastStart));
    deviceConfig.system.operation.last_start = lastStart;
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
    String block = testDescription == null ? "" : String.format("%s\n\n", testDescription);
    sequenceMd.printf("\n## %s (%s)\n\n%s", testName, testStage.name(), block);
    sequenceMd.flush();
  }

  private String getTestDescription(org.junit.runner.Description description) {
    Description annotation = description.getAnnotation(Description.class);
    return annotation == null ? null : annotation.value();
  }

  private Stage getTestStage(org.junit.runner.Description description) {
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
    File deviceConfigFile = new File(String.format(DEVICE_CONFIG_FORMAT, siteModel, getDeviceId()));
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
    waitingCondition.clear();
    waitingCondition.push("starting test wrapper");
    assert reflector().isActive();

    // Old messages can sometimes take a while to clear out, so need some delay for stability.
    // TODO: Minimize time, or better yet find deterministic way to flush messages.
    safeSleep(CONFIG_UPDATE_DELAY_MS);

    configAcked = false;
    receivedState.clear();
    receivedEvents.clear();
    enforceSerial = false;
    recordMessages = true;
    recordSequence = false;

    queryState();

    resetConfig(resetRequired);

    updateConfig("setUp");

    untilTrue("device state update", () -> deviceState != null);
    recordSequence = true;
    waitingCondition.push("executing test");
    debug(String.format("stage begin %s at %s", waitingCondition.peek(), timeSinceStart()));
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
        setExtraField("reset_config");
        deviceConfig.system.testing.sequence_name = extraField;
        sentConfig.clear();
        configDiffEngine.computeChanges(deviceConfig);
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
      messageEvaluateLoop(this::configIsPending);
      Duration between = Duration.between(lastConfigUpdate, CleanDateFormat.clean(Instant.now()));
      debug(String.format("Configuration sync took %ss", between.getSeconds()));
    } finally {
      debug("wait for config sync result " + configIsPending(true));
    }
  }

  @Test
  @Feature(stage = STABLE)
  public void valid_serial_no() {
    if (serialNo == null) {
      throw new SkipTest("No test serial number provided");
    }
    untilTrue("received serial number matches", () -> serialNo.equals(lastSerialNo));
  }

  private void recordResult(String result, org.junit.runner.Description description,
      String message) {
    String methodName = description.getMethodName();
    Feature feature = description.getAnnotation(Feature.class);
    Bucket bucket = getBucket(feature);
    String stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    int score = (feature == null ? Feature.DEFAULT_SCORE : feature.score());
    String resultString = String.format(RESULT_FORMAT, result, bucket.value(), methodName, stage,
        score, message);
    notice(resultString);
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.print(resultString);
    } catch (Exception e) {
      throw new RuntimeException("While writing report summary " + resultSummary.getAbsolutePath(),
          e);
    }
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

  private void recordRawMessage(Map<String, Object> message, Map<String, String> attributes) {
    if (testName == null) {
      return;
    }
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    String timestamp = message == null ? getTimestamp() : (String) message.get("timestamp");
    String messageBase = String.format("%s_%s", subType, subFolder);
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

    String prefix = messageBase.startsWith(LOCAL_PREFIX) ? "local " : "received ";
    File messageFile = new File(testDir, messageBase + ".json");
    Object savedException = message == null ? null : message.get(EXCEPTION_KEY);
    try {
      // An actual exception here will cause the JSON seralizer to barf, so temporarily sanitize.
      if (savedException instanceof Exception) {
        message.put(EXCEPTION_KEY, ((Exception) savedException).getMessage());
      }
      JsonUtil.OBJECT_MAPPER.writeValue(messageFile, message);
      boolean traceMessage =
          traceLogLevel() || (debugLogLevel() && messageBase.equals(LOCAL_CONFIG_UPDATE));
      String postfix = traceMessage ? (message == null ? "(null)" : stringify(message)) : "";
      if (messageBase.equals(SYSTEM_EVENT_MESSAGE_BASE)) {
        logSystemEvent(messageBase, message);
      } else if (traceLogLevel() && !messageBase.startsWith(EVENT_PREFIX)) {
        trace(prefix + messageBase, postfix);
      } else {
        debug(prefix + messageBase, postfix);
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
    return String.format("%s %s %s: %s", getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level).name(), logEntry.category, logEntry.message);
  }

  private boolean debugLogLevel() {
    return logLevel <= Level.DEBUG.value();
  }

  private boolean traceLogLevel() {
    return logLevel <= Level.TRACE.value();
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
    String messageStr = String.format("%s %s %s %s", getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level), logEntry.category, logEntry.message);

    printWriter.println(messageStr);
    printWriter.flush();
    return messageStr;
  }

  protected void queryState() {
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
    debug(String.format("stage done %s at %s", waitingCondition.peek(), timeSinceStart()));
    recordMessages = false;
    recordSequence = false;
    configAcked = false;
  }

  private void assertConfigIsNotPending() {
    if (!configTransactions.isEmpty()) {
      String transactions = configTransactionsListString();
      configTransactions.clear();
      throw new RuntimeException("Unexpected config transactions: " + transactions);
    }
  }

  protected void updateConfig(String reason) {
    assertConfigIsNotPending();
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
    updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    if (configIsPending()) {
      lastConfigUpdate = CleanDateFormat.clean(Instant.now());
      String debugReason = reason == null ? "" : (", because " + reason);
      debug(String.format("Update lastConfigUpdate %s%s", lastConfigUpdate, debugReason));
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
        debug(String.format("update %s_%s, id %s", CONFIG_SUBTYPE, subBlock, transactionId));
        recordRawMessage(data, LOCAL_PREFIX + subBlock.value());
        sentConfig.put(subBlock, messageData);
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
      String header = String.format("Update config%s: ", suffix);
      debug(header + getTimestamp(deviceConfig.timestamp));
      recordRawMessage(deviceConfig, LOCAL_CONFIG_UPDATE);
      List<String> allDiffs = configDiffEngine.computeChanges(deviceConfig);
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
      notice(String.format("Received serial number %s", deviceSerial));
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
          "Aborting message loop while " + waitingCondition.peek() + " because " + e.getMessage());
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
    if (!catchToFalse(condition)) {
      warning("Failed check that " + description);
      throw new IllegalStateException("Failed check that " + description);
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
    untilTrue(String.format("log category `%s` level `%s` was logged", category, exactLevel),
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
    checkThat(String.format("log category `%s` level `%s` not logged", category, minLevel), () -> {
      if (!entries.isEmpty()) {
        warning(String.format("Filtered config between %s and %s", getTimestamp(lastConfigUpdate),
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
      int eventCount = Optional.ofNullable(systemEvent.event_count).orElse(previousEventCount + 1);
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

    trace(String.format("stage suspend %s at %s", waitingCondition.peek(), timeSinceStart()));
    waitingCondition.push("waiting for " + condition);
    info(String.format("stage start %s at %s", waitingCondition.peek(), timeSinceStart()));

    action.run();

    Duration between = Duration.between(startTime, Instant.now());
    debug(String.format("stage finished %s at %s after %ss", waitingCondition.peek(),
        timeSinceStart(), between.toSeconds()));
    waitingCondition.pop();
    trace(String.format("stage resume %s at %s", waitingCondition.peek(), timeSinceStart()));
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
      processMessage();
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
    return (System.currentTimeMillis() - testStartTimeMs) / 1000 + "s";
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

  private void processMessage() {
    MessageBundle bundle = nextMessageBundle();
    processCommand(bundle.message, bundle.attributes);
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
      MessageBundle bundle = reflector().takeNextMessage();
      if (activeInstance != this) {
        debug("stashing interrupted message bundle");
        assert stashedBundle == null;
        stashedBundle = bundle;
        throw new RuntimeException("Message loop no longer for active thread");
      }
      return bundle;
    }
  }

  private void processCommand(Map<String, Object> message, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    String subFolderRaw = attributes.get("subFolder");
    String subTypeRaw = attributes.get("subType");
    String transactionId = attributes.get("transactionId");
    if (CONFIG_SUBTYPE.equals(subTypeRaw)) {
      String attributeMark = String.format("%s/%s/%s", deviceId, subTypeRaw, subFolderRaw);
      Object configNonce = message == null ? null : message.get(CONFIG_NONCE_KEY);
      trace("received command " + attributeMark + " nonce " + configNonce);
    }
    if (!SequenceBase.getDeviceId().equals(deviceId)) {
      return;
    }
    recordRawMessage(message, attributes);

    if (SubFolder.UPDATE.value().equals(subFolderRaw)) {
      handleReflectorMessage(subTypeRaw, message, transactionId);
    } else {
      handleDeviceMessage(message, subFolderRaw, subTypeRaw, transactionId);
    }
  }

  private void handleDeviceMessage(Map<String, Object> message, String subFolderRaw,
      String subTypeRaw, String transactionId) {
    SubFolder subFolder = SubFolder.fromValue(subFolderRaw);
    SubType subType = SubType.fromValue(subTypeRaw);
    switch (subType) {
      case CONFIG:
        debug("Received confirmation of individual config id " + transactionId);
        // These are echos of sent config messages, so do nothing.
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
      Map<String, Object> message, String transactionId) {
    try {
      // Do this first to handle all cases of a Config payload, including exceptions.
      if (CONFIG_SUBTYPE.equals(subTypeRaw) && transactionId != null) {
        configTransactions.remove(transactionId);
      }
      if (message.containsKey(EXCEPTION_KEY)) {
        debug("Ignoring reflector exception:\n" + message.get(EXCEPTION_KEY).toString());
        configExceptionTimestamp = (String) message.get(TIMESTAMP_PROPERTY_KEY);
        return;
      }
      configExceptionTimestamp = null;
      Object converted = JsonUtil.convertTo(expectedUpdates.get(subTypeRaw), message);
      receivedUpdates.put(subTypeRaw, converted);
      int updateCount = UPDATE_COUNTS.computeIfAbsent(subTypeRaw, key -> new AtomicInteger())
          .incrementAndGet();
      if (converted instanceof Config) {
        String extraField = getExtraField(message);
        if ("reset_config".equals(extraField)) {
          debug("Update with config reset");
        } else if ("break_json".equals(extraField)) {
          error("Shouldn't be seeing this!");
          return;
        }
        Config config = (Config) converted;
        updateDeviceConfig(config);
        debug(String.format("Updated config %s, id %s", getTimestamp(config.timestamp),
            transactionId));
        info(String.format("Updated config #%03d", updateCount), stringify(converted));
      } else if (converted instanceof AugmentedState) {
        info(String.format("Updated state #%03d", updateCount), stringify(converted));
        deviceState = (State) converted;
        updateConfigAcked((AugmentedState) converted);
        validSerialNo();
        debug("Updated state has last_config " + getTimestamp(deviceState.system.last_config));
      } else {
        error("Unknown update type " + converted.getClass().getSimpleName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling reflector message", e);
    }
  }

  private void updateDeviceConfig(Config config) {
    if (deviceConfig == null) {
      return;
    }

    // These parameters are set by the cloud functions, so explicitly set to maintain parity.
    deviceConfig.timestamp = config.timestamp;
    deviceConfig.version = config.version;
    if (config.system != null && config.system.operation != null) {
      setLastStart(SemanticDate.describe("device reported", config.system.operation.last_start));
    }
    sanitizeConfig(deviceConfig);
  }

  /**
   * Check/remember the configAcked field of a state update. This field is only populated by the
   * supporting cloud functions in response to an explicit state query, and checks that the device
   * has acked (in an MQTT sense) a previously sent config.
   *
   * @param converted received message to pull the ack from
   */
  private void updateConfigAcked(AugmentedState converted) {
    if (converted.configAcked != null) {
      configAcked = "true".equals(converted.configAcked);
    }
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
    boolean lastStartSynchronized = stateLast == null || stateLast.equals(configLast);
    if (debugOut) {
      debug(String.format("lastStartSynchronized %s, pending transactions: %s",
          lastStartSynchronized, configTransactionsListString()));
    }
    return !(lastStartSynchronized && configTransactions.isEmpty());
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

  private void debug(String message, String parts) {
    log(message, Level.DEBUG, parts);
  }

  protected void debug(String message) {
    log(message, Level.DEBUG);
  }

  private void info(String message, String parts) {
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
    log(message, Level.ERROR);
  }

  private void log(String message, Level level, String parts) {
    Arrays.stream(parts.split("\\n")).forEach(part -> log(message + ": " + part, level));
  }

  private void log(String message, Level level) {
    Entry logEntry = new Entry();
    logEntry.timestamp = CleanDateFormat.cleanDate();
    logEntry.level = level.value();
    logEntry.message = message;
    logEntry.category = SEQUENCER_CATEGORY;
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

  protected void withAlternateClient(Runnable evaluator) {
    assert !useAlternateClient;
    assert deviceConfig.system.testing.endpoint_type == null;
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
    assert altClient != null;
    String topic = UPDATE_SUBFOLDER + "/" + CONFIG_SUBTYPE;
    reflector(!useAlternateClient).publish(getDeviceId(), topic, receivedConfig);
    // There's a race condition if the mirror command gets delayed, so chill for a bit.
    safeSleep(ONE_SECOND_MS);
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

  /**
   * Add a description for a test that can be programmatically extracted.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface Description {

    /**
     * Description of the test.
     *
     * @return test description
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
    protected void starting(@NotNull org.junit.runner.Description description) {
      try {
        setupSequencer();
        SequenceRunner.getAllTests().add(getDeviceId() + "/" + description.getMethodName());
        assert reflector().isActive();

        testName = description.getMethodName();
        testDescription = getTestDescription(description);
        testStage = getTestStage(description);

        testStartTimeMs = System.currentTimeMillis();

        testDir = new File(new File(deviceOutputDir, TESTS_OUT_DIR), testName);
        FileUtils.deleteDirectory(testDir);
        testDir.mkdirs();
        systemLog = new PrintWriter(newOutputStream(new File(testDir, SYSTEM_LOG).toPath()));
        sequencerLog = new PrintWriter(newOutputStream(new File(testDir, SEQUENCER_LOG).toPath()));
        sequenceMd = new PrintWriter(newOutputStream(new File(testDir, SEQUENCE_MD).toPath()));
        writeSequenceMdHeader();

        notice("starting test " + testName);
        activeInstance = SequenceBase.this;
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("While preparing " + deviceOutputDir.getAbsolutePath(), e);
      }
    }

    @Override
    protected void finished(org.junit.runner.Description description) {
      if (activeInstance == null) {
        return;
      }
      if (!testName.equals(description.getMethodName())) {
        throw new IllegalStateException("Unexpected test method name");
      }
      long stopTimeMs = System.currentTimeMillis();
      notice("ending test " + testName + " after " + (stopTimeMs - testStartTimeMs) / 1000 + "s");
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
    protected void succeeded(org.junit.runner.Description description) {
      recordCompletion(RESULT_PASS, Level.INFO, description, "Sequence complete");
    }

    @Override
    protected void failed(Throwable e, org.junit.runner.Description description) {
      if (activeInstance == null) {
        return;
      }
      final String message;
      final String type;
      final Level level;
      if (e instanceof TestTimedOutException) {
        waitingCondition.forEach(condition -> warning("while " + condition));
        error(String.format("stage timeout %s at %s", waitingCondition.peek(), timeSinceStart()));
        message = "timeout " + waitingCondition.peek();
        type = RESULT_FAIL;
        level = Level.ERROR;
      } else if (e instanceof SkipTest) {
        message = e.getMessage();
        type = RESULT_SKIP;
        level = Level.WARNING;
      } else {
        while (e.getCause() != null) {
          e = e.getCause();
        }
        message = e.getMessage();
        type = RESULT_FAIL;
        level = Level.ERROR;
      }
      debug("ending stack trace: " + GeneralUtils.stackTraceString(e));
      recordCompletion(type, level, description, message);
      String actioned = type.equals(RESULT_SKIP) ? "skipped" : "failed";
      withRecordSequence(true, () -> recordSequence("Test " + actioned + ": " + message));
      resetRequired = true;
      if (debugLogLevel()) {
        error("Reset required during debug, forcing exit to preserve failing config/state");
        System.exit(EXIT_CODE_PRESERVE);
      }
    }

    private void recordCompletion(String result, Level level,
        org.junit.runner.Description description, String message) {
      recordResult(result, description, message);
      Entry logEntry = new Entry();
      logEntry.category = SEQUENCER_CATEGORY;
      logEntry.message = message;
      logEntry.level = level.value();
      logEntry.timestamp = CleanDateFormat.cleanDate();
      writeSequencerLog(logEntry);
      writeSystemLog(logEntry);
    }
  }
}
