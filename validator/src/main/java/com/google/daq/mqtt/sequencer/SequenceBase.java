package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.sequencer.semantic.SemanticValue.actualize;
import static com.google.daq.mqtt.util.Common.EXCEPTION_KEY;
import static com.google.daq.mqtt.util.Common.TIMESTAMP_PROPERTY_KEY;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MockPublisher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.ConfigDiffEngine;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.TimePeriodConstants;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Config;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.ReflectorConfig;
import udmi.schema.ReflectorState;
import udmi.schema.SetupReflectorState;
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
  public static final String RESULT_FORMAT = "RESULT %s %s %s";
  public static final String TESTS_OUT_DIR = "tests";
  public static final String SERIAL_NO_MISSING = "//";
  public static final String SEQUENCER_CATEGORY = "sequencer";
  public static final String EVENT_PREFIX = "event_";
  public static final String SYSTEM_EVENT_MESSAGE_BASE = "event_system";
  public static final int CONFIG_UPDATE_DELAY_MS = 8 * 1000;
  public static final int NORM_TIMEOUT_MS = 300 * 1000;
  public static final String CONFIG_NONCE_KEY = "debug_config_nonce";
  private static final String EMPTY_MESSAGE = "{}";
  private static final String CLOUD_IOT_CONFIG_FILE = "cloud_iot_config.json";
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
  protected static Metadata deviceMetadata;
  protected static String projectId;
  protected static String cloudRegion;
  protected static String registryId;
  protected static String altRegistry;
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
  private static Date stateTimestamp;

  private final Map<SubFolder, String> sentConfig = new HashMap<>();
  private final Map<SubFolder, String> receivedState = new HashMap<>();
  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private final ConfigDiffEngine configDiffEngine = new ConfigDiffEngine();
  private final Queue<Entry> logEntryQueue = new LinkedBlockingDeque<>();
  private final Stack<String> waitingCondition = new Stack<>();
  @Rule
  public Timeout globalTimeout = new Timeout(NORM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  @Rule
  public SequenceTestWatcher testWatcher = new SequenceTestWatcher();
  protected Config deviceConfig;
  protected State deviceState;
  protected boolean configAcked;
  private String extraField;
  private boolean extraFieldChanged;
  private Instant lastConfigUpdate;
  private boolean enforceSerial;
  private String testName;
  private String testDescription;
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
  private String cachedMessageData;
  private String cachedSentBlock;
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
    IotReflectorClient client = new IotReflectorClient(altConfiguration);
    initializeReflectorState(client);
    return client;
  }

  private static MessagePublisher getReflectorClient() {
    IotReflectorClient client = new IotReflectorClient(validatorConfig);
    initializeReflectorState(client);
    return client;
  }

  private static void initializeReflectorState(IotReflectorClient client) {
    ReflectorState reflectorState = new ReflectorState();
    stateTimestamp = new Date();
    reflectorState.timestamp = stateTimestamp;
    reflectorState.version = udmiVersion;
    reflectorState.setup = new SetupReflectorState();
    reflectorState.setup.user = System.getenv("USER");
    try {
      System.err.printf("Setting state version %s timestamp %s%n",
          udmiVersion, getTimestamp(stateTimestamp));
      client.setReflectorState(stringify(reflectorState));
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
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
   * Set the extra field test capability for device config.
   *
   * @param extraField value for the extra field
   */
  public void setExtraField(String extraField) {
    debug("Setting extra_field to " + extraField);
    this.extraField = extraField;
    extraFieldChanged = true;
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
    sequenceMd.printf("\n## %s\n\n%s", testName, block);
    sequenceMd.flush();
  }

  private String getTestDescription(org.junit.runner.Description description) {
    Description annotation = description.getAnnotation(Description.class);
    return annotation == null ? null : annotation.value();
  }

  private void resetDeviceConfig() {
    resetDeviceConfig(false);
  }

  private void resetDeviceConfig(boolean clean) {
    deviceConfig = clean ? new Config() : readGeneratedConfig();
    sentConfig.clear();
    sanitizeConfig(deviceConfig);
    deviceConfig.system = ofNullable(deviceConfig.system).orElse(new SystemConfig());
    deviceConfig.system.min_loglevel = Level.INFO.value();
    deviceConfig.system.testing = new TestingSystemConfig();
    deviceConfig.system.testing.sequence_name = testName;
  }

  private Config sanitizeConfig(Config deviceConfig) {
    if (!(deviceConfig.timestamp instanceof SemanticDate)) {
      deviceConfig.timestamp = SemanticDate.describe("generated timestamp", deviceConfig.timestamp);
    }
    if (!SemanticValue.isSemanticString(deviceConfig.version)) {
      deviceConfig.version = SemanticValue.describe("cloud udmi version", deviceConfig.version);
    }
    if (deviceConfig.system == null) {
      deviceConfig.system = new SystemConfig();
    }
    if (!(deviceConfig.system.last_start instanceof SemanticDate)) {
      deviceConfig.system.last_start = SemanticDate.describe("device reported",
          deviceConfig.system.last_start);
    }
    return deviceConfig;
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
    waitingCondition.clear();
    waitingCondition.push("starting test wrapper");
    assert reflector().isActive();

    // Old messages can sometimes take a while to clear out, so need some delay for stability.
    // TODO: Minimize time, or better yet find deterministic way to flush messages.
    safeSleep(CONFIG_UPDATE_DELAY_MS);

    deviceState = new State();
    configAcked = false;
    receivedState.clear();
    receivedEvents.clear();
    enforceSerial = false;
    recordMessages = true;
    recordSequence = false;

    resetConfig();

    queryState();

    updateConfig();

    untilTrue("device state update", () -> deviceState != null);
    recordSequence = true;
    waitingCondition.push("executing test");
    debug(String.format("stage begin %s at %s", waitingCondition.peek(), timeSinceStart()));
  }

  protected void resetConfig() {
    recordSequence("Force reset config");
    withRecordSequence(false, () -> {
      debug("Starting reset_config");
      resetDeviceConfig(true);
      setExtraField("reset_config");
      deviceConfig.system.testing.sequence_name = extraField;
      updateConfig();
      setExtraField(null);
      resetDeviceConfig();
      updateConfig();
      debug("Done with reset_config");
    });
  }

  private void waitForConfigSync(Instant configUpdateStart) {
    try {
      lastConfigUpdate = configUpdateStart;
      debug("lastConfigUpdate is " + lastConfigUpdate);
      withRecordSequence(false, () -> untilTrue("device config sync", this::configReady));
    } finally {
      configReady(true);
    }
  }

  @Test
  public void valid_serial_no() {
    if (serialNo == null) {
      throw new SkipTest("No test serial number provided");
    }
    untilTrue("received serial no matches", () -> serialNo.equals(lastSerialNo));
  }

  private void recordResult(String result, String methodName, String message) {
    String resultString = String.format(RESULT_FORMAT, result, methodName, message);
    notice(resultString);
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.print(resultString);
    } catch (Exception e) {
      throw new RuntimeException("While writing report summary " + resultSummary.getAbsolutePath(),
          e);
    }
  }

  private void recordRawMessage(Map<String, Object> message, Map<String, String> attributes) {
    if (testName == null) {
      return;
    }
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    String timestamp =
        message == null ? getTimestamp() : (String) message.get("timestamp");
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
    Object savedException = message.get(EXCEPTION_KEY);
    try {
      // An actual exception here will cause the JSON seralizer to barf, so temporarily sanitize.
      if (savedException instanceof Exception) {
        message.put(EXCEPTION_KEY, ((Exception) savedException).getMessage());
      }
      JsonUtil.OBJECT_MAPPER.writeValue(messageFile, message);
      boolean traceMessage =
          traceLogLevel() || (debugLogLevel() && messageBase.equals(LOCAL_CONFIG_UPDATE));
      String postfix =
          traceMessage ? (message == null ? ": (null)" : ":\n" + stringify(message)) : "";
      if (messageBase.equals(SYSTEM_EVENT_MESSAGE_BASE)) {
        logSystemEvent(messageBase, message);
      } else if (traceLogLevel() && !messageBase.startsWith(EVENT_PREFIX)) {
        trace(prefix + messageBase + postfix);
      } else {
        debug(prefix + messageBase + postfix);
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
        Level.fromValue(logEntry.level),
        logEntry.category,
        logEntry.message);

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
    debug(String.format("stage done %s at %s", waitingCondition.peek(), timeSinceStart()));
    recordMessages = false;
    recordSequence = false;
    if (debugLogLevel()) {
      warning("Not resetting config to enable post-execution debugging");
    } else {
      whileDoing("tear down", this::resetConfig);
    }
    deviceConfig = null;
    deviceState = null;
    configAcked = false;
  }

  protected void updateConfig() {
    updateConfig(null);
  }

  protected void updateConfig(String reason) {
    // Timestamps are quantized to one second, so make sure at least that much time passes.
    safeSleep(ONE_SECOND_MS);
    Instant configStart = CleanDateFormat.clean(Instant.now());

    cachedMessageData = null;
    cachedSentBlock = null;
    boolean updated = updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updated |= updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updated |= updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updated |= updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updated |= updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
    updated |= updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    boolean computedConfigChange = localConfigChange(reason);
    if (computedConfigChange != updated) {
      notice("cachedMessageData " + cachedMessageData);
      notice("cachedSentBlock " + cachedSentBlock);
      throw new AbortMessageLoop("Unexpected config change!");
    }
    if (computedConfigChange) {
      safeSleep(ONE_SECOND_MS);
      waitForConfigSync(configStart);
    }
  }

  private boolean updateConfig(SubFolder subBlock, Object data) {
    try {
      String messageData = stringify(data);
      String sentBlockConfig = sentConfig.computeIfAbsent(subBlock, key -> "null");
      boolean updated = !messageData.equals(sentBlockConfig);
      if (updated) {
        cachedMessageData = messageData;
        cachedSentBlock = sentBlockConfig;
        final Object tracedObject = augmentConfigTrace(data);
        String augmentedMessage = actualize(stringify(tracedObject));
        String topic = subBlock + "/config";
        reflector().publish(getDeviceId(), topic, augmentedMessage);
        debug(String.format("update %s_%s", CONFIG_SUBTYPE, subBlock));
        recordRawMessage(tracedObject, LOCAL_PREFIX + subBlock.value());
        sentConfig.put(subBlock, messageData);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Object augmentConfigTrace(Object data) {
    try {
      if (data == null || !traceLogLevel()) {
        return data;
      }
      Map<String, Object> map = JsonUtil.convertTo(Map.class, data);
      map.put(CONFIG_NONCE_KEY, System.currentTimeMillis());
      return map;
    } catch (Exception e) {
      throw new RuntimeException("While augmenting data message", e);
    }
  }

  private boolean localConfigChange(String reason) {
    try {
      String suffix = reason == null ? "" : (" " + reason);
      String header = String.format("Update config%s:", suffix);
      debug(header + " " + getTimestamp(deviceConfig.timestamp));
      recordRawMessage(deviceConfig, LOCAL_CONFIG_UPDATE);
      List<String> configUpdates = configDiffEngine.computeChanges(deviceConfig);
      if (configUpdates.isEmpty() && !extraFieldChanged) {
        return false;
      }
      if (!configUpdates.isEmpty()) {
        recordSequence(header);
        configUpdates.forEach(this::recordBullet);
        sequenceMd.flush();
      }
      if (extraFieldChanged) {
        debug("Device config extra_field changed: " + extraField);
        extraFieldChanged = false;
      }
      return true;
    } catch (Exception e) {
      throw new RuntimeException("While recording device config", e);
    }
  }

  private AugmentedSystemConfig augmentConfig(SystemConfig system) {
    try {
      String conversionString = stringify(system);
      AugmentedSystemConfig augmentedConfig = JsonUtil.OBJECT_MAPPER.readValue(conversionString,
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
      notice(String.format("Received serial no %s", deviceSerial));
      lastSerialNo = deviceSerial;
    }
    boolean serialValid = deviceSerial != null && Objects.equals(serialNo, deviceSerial);
    if (!serialValid && enforceSerial && Objects.equals(serialNo, deviceSerial)) {
      throw new IllegalStateException("Serial no mismatch " + serialNo + " != " + deviceSerial);
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
      debug("Suppressing exception: " + e);
      trace("Suppressed from line " + getTraceString(e));
      return null;
    }
  }

  private String getTraceString(Exception e) {
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
    logEntryQueue.removeIf(entry -> entry.timestamp.toInstant().isBefore(lastConfigUpdate));
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
      sequenceMd.println("1. " + step);
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
    String category = bundle.attributes.get("category");
    if ("commands".equals(category)) {
      processCommand(bundle.message, bundle.attributes);
    } else if (CONFIG_SUBTYPE.equals(category)) {
      processConfig(bundle.message, bundle.attributes);
    }
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

  private void processConfig(Map<String, Object> message, Map<String, String> attributes) {
    ReflectorConfig reflectorConfig = JsonUtil.convertTo(ReflectorConfig.class, message);
    Date lastState = reflectorConfig.setup.last_state;
    if (CleanDateFormat.dateEquals(lastState, stateTimestamp)) {
      info("Cloud UDMI version " + reflectorConfig.version);
      if (!udmiVersion.equals(reflectorConfig.version)) {
        warning("Local/cloud UDMI version mismatch!");
      }
    } else {
      info("Ignoring mismatch state/config timestamp " + getTimestamp(lastState));
    }
  }

  private void processCommand(Map<String, Object> message, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    String subFolderRaw = attributes.get("subFolder");
    String subTypeRaw = attributes.get("subType");
    if (CONFIG_SUBTYPE.equals(subTypeRaw)) {
      String attributeMark = String.format("%s/%s/%s", deviceId, subTypeRaw, subFolderRaw);
      trace("received command " + attributeMark + " nonce " + message.get(CONFIG_NONCE_KEY));
    }
    if (!SequenceBase.getDeviceId().equals(deviceId)) {
      return;
    }
    recordRawMessage(message, attributes);

    if (SubFolder.UPDATE.value().equals(subFolderRaw)) {
      handleReflectorMessage(subTypeRaw, message);
    } else {
      handleDeviceMessage(message, subFolderRaw, subTypeRaw);
    }
  }

  private void handleDeviceMessage(Map<String, Object> message, String subFolderRaw,
      String subTypeRaw) {
    SubFolder subFolder = SubFolder.fromValue(subFolderRaw);
    SubType subType = SubType.fromValue(subTypeRaw);
    switch (subType) {
      case CONFIG:
        // These are echos of sent config messages, so do nothing.
        break;
      case STATE:
        // State updates are handled as a monolithic block with a state reflector update.
        break;
      case EVENT:
        handleEventMessage(subFolder, message);
        break;
      default:
        info("Encountered unexpected subType " + subTypeRaw);
    }
  }

  private synchronized void handleReflectorMessage(String subFolderRaw,
      Map<String, Object> message) {
    try {
      if (message.containsKey(EXCEPTION_KEY)) {
        debug("Ignoring reflector exception:\n" + message.get(EXCEPTION_KEY).toString());
        configExceptionTimestamp = (String) message.get(TIMESTAMP_PROPERTY_KEY);
        return;
      }
      configExceptionTimestamp = null;
      Object converted = JsonUtil.convertTo(expectedUpdates.get(subFolderRaw), message);
      receivedUpdates.put(subFolderRaw, converted);
      int updateCount = UPDATE_COUNTS.computeIfAbsent(subFolderRaw, key -> new AtomicInteger())
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
        debug("Updated config with timestamp " + getTimestamp(config.timestamp));
        info(String.format("Updated config #%03d:\n%s", updateCount,
            stringify(converted)));
      } else if (converted instanceof AugmentedState) {
        info(String.format("Updated state #%03d:\n%s", updateCount,
            stringify(converted)));
        deviceState = (State) converted;
        updateConfigAcked((AugmentedState) converted);
        validSerialNo();
        debug("Updated state has last_config " + getTimestamp(
            deviceState.system.last_config));
      } else {
        error("Unknown update type " + converted.getClass().getSimpleName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling reflector message", e);
    }
  }

  private void updateDeviceConfig(Config config) {
    // These parameters are set by the cloud functions, so explicitly set to maintain parity.
    deviceConfig.timestamp = config.timestamp;
    deviceConfig.version = config.version;
    if (config.system != null) {
      deviceConfig.system.last_start = config.system.last_start;
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

  private boolean configReady() {
    return configReady(false);
  }

  private boolean configReady(boolean debugOut) {
    Object receivedConfig = receivedUpdates.get(CONFIG_SUBTYPE);
    if (!(receivedConfig instanceof Config)) {
      trace("no valid received config");
      return false;
    }
    if (configExceptionTimestamp != null) {
      debug("Received config exception at " + configExceptionTimestamp);
      return true;
    }
    // Config isn't properly sync'd until this is filled in, else there are startup race-conditions.
    if (deviceConfig.system.last_start == null) {
      return false;
    }
    List<String> differences = configDiffEngine.diff(
        sanitizeConfig((Config) receivedConfig), deviceConfig);
    boolean configReady = differences.isEmpty();
    Consumer<String> output = debugOut ? this::debug : this::trace;
    output.accept("testing valid received config " + configReady);
    if (!configReady) {
      output.accept("\n+- " + Joiner.on("\n+- ").join(differences));
      trace("final deviceConfig: " + JsonUtil.stringify(deviceConfig));
      trace("final receivedConfig: " + JsonUtil.stringify(receivedUpdates.get(CONFIG_SUBTYPE)));
    }
    return configReady;
  }

  protected void trace(String message) {
    log(message, Level.TRACE);
  }

  protected void debug(String message) {
    log(message, Level.DEBUG);
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
      deviceConfig.system.testing.endpoint_type = null;
    }
  }

  protected void mirrorDeviceConfig() {
    String receivedConfig = actualize(stringify(receivedUpdates.get(CONFIG_SUBTYPE)));
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
    protected void starting(org.junit.runner.Description description) {
      try {
        setupSequencer();
        SequenceRunner.getAllTests().add(getDeviceId() + "/" + description.getMethodName());
        assert reflector().isActive();

        testName = description.getMethodName();
        testDescription = getTestDescription(description);
        if (deviceConfig != null) {
          deviceConfig.system.testing.sequence_name = testName;
        }

        testStartTimeMs = System.currentTimeMillis();

        testDir = new File(new File(deviceOutputDir, TESTS_OUT_DIR), testName);
        FileUtils.deleteDirectory(testDir);
        testDir.mkdirs();
        systemLog = new PrintWriter(new FileOutputStream(new File(testDir, SYSTEM_LOG)));
        sequencerLog = new PrintWriter(new FileOutputStream(new File(testDir, SEQUENCER_LOG)));
        sequenceMd = new PrintWriter(new FileOutputStream(new File(testDir, SEQUENCE_MD)));
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
      recordCompletion(type, level, description, message);
      String actioned = type.equals(RESULT_SKIP) ? "skipped" : "failed";
      withRecordSequence(true, () -> recordSequence("Test " + actioned + ": " + message));
    }

    private void recordCompletion(String result, Level level,
        org.junit.runner.Description description, String message) {
      String category = description.getMethodName();
      recordResult(result, category, message);
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
