package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.util.JsonUtil.stringify;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.ConfigDiffEngine;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.JsonUtil;
import com.google.daq.mqtt.util.ValidatorConfig;
import com.google.daq.mqtt.validator.AugmentedState;
import com.google.daq.mqtt.validator.AugmentedSystemConfig;
import com.google.daq.mqtt.validator.CleanDateFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
public abstract class SequenceBase {

  public static final String RESULT_FAIL = "fail";
  public static final String RESULT_PASS = "pass";
  public static final String RESULT_SKIP = "skip";
  public static final String RESULT_FORMAT = "RESULT %s %s %s";
  public static final String TESTS_OUT_DIR = "tests";
  public static final String SERIAL_NO_MISSING = "//";
  public static final String SEQUENCER_CATEGORY = "sequencer";
  public static final String EVENT_PREFIX = "event_";
  public static final String SYSTEM_EVENT_MESSAGE_BASE = "event_system";
  public static final int CONFIG_UPDATE_DELAY_MS = 2000;
  public static final int NORM_TIMEOUT_MS = 120 * 1000;
  public static final String LOCAL_CONFIG = "local_config";
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
  private static final long LOG_CLEAR_TIME_MS = 1000;
  private static final String LOCAL_PREFIX = "local_";
  private static final String SEQUENCER_LOG = "sequencer.log";
  private static final String SYSTEM_LOG = "system.log";
  private static final String SEQUENCE_MD = "sequence.md";
  protected static Metadata deviceMetadata;
  static ValidatorConfig validatorConfig;
  private static String projectId;
  private static String deviceId;
  private static String udmiVersion;
  private static String siteModel;
  private static String serialNo;
  private static int logLevel;
  private static File deviceOutputDir;
  private static File resultSummary;
  private static IotReflectorClient client;
  private static Date stateTimestamp;

  // Because of the way tests are run and configured, these parameters need to be
  // a singleton to avoid runtime conflicts.
  static {
    ensureValidatorConfig();
    setupSequencer();
  }

  private final Map<SubFolder, String> sentConfig = new HashMap<>();
  private final Map<SubFolder, String> receivedState = new HashMap<>();
  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private final ConfigDiffEngine configDiffEngine = new ConfigDiffEngine();
  @Rule
  public Timeout globalTimeout = new Timeout(NORM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  protected String extraField;
  protected Config deviceConfig;
  protected State deviceState;
  protected boolean configAcked;
  private Date lastLog;
  private String waitingCondition;
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
  @Rule
  public TestWatcher testWatcher = new TestWatcher() {
    @Override
    protected void starting(org.junit.runner.Description description) {
      try {
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
        message = "timeout " + waitingCondition;
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
      withRecordSequence(true, () -> recordSequence("Test failed: " + message));
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
  };

  private static void ensureValidatorConfig() {
    if (SequenceRunner.validationConfig != null) {
      validatorConfig = SequenceRunner.validationConfig;
    } else {
      if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
        throw new RuntimeException(CONFIG_ENV + " env not defined.");
      }
      final File configFile = new File(CONFIG_PATH);
      try {
        System.err.println("Reading config file " + configFile.getAbsolutePath());
        validatorConfig = ConfigUtil.readValidatorConfig(configFile);
      } catch (Exception e) {
        throw new RuntimeException("While loading " + configFile, e);
      }
    }
  }

  private static void setupSequencer() {
    final String key_file;
    try {
      siteModel = checkNotNull(validatorConfig.site_model, "site_model not defined");
      deviceId = checkNotNull(validatorConfig.device_id, "device_id not defined");
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

    File cloudIotConfigFile = new File(new File(siteModel), CLOUD_IOT_CONFIG_FILE);
    final CloudIotConfig cloudIotConfig;
    try {
      cloudIotConfig = ConfigUtil.readCloudIotConfig(cloudIotConfigFile);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + cloudIotConfigFile.getAbsolutePath(), e);
    }

    deviceMetadata = readDeviceMetadata();

    deviceOutputDir = new File(new File(siteModel), "out/devices/" + deviceId);
    deviceOutputDir.mkdirs();

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Loading reflector key file from %s%n", new File(key_file).getAbsolutePath());
    System.err.printf("Validating against device %s serial %s%n", deviceId, serialNo);
    client = new IotReflectorClient(projectId, cloudIotConfig, key_file);
    setReflectorState();
  }

  private static void setReflectorState() {
    ReflectorState reflectorState = new ReflectorState();
    stateTimestamp = new Date();
    reflectorState.timestamp = stateTimestamp;
    reflectorState.version = udmiVersion;
    reflectorState.setup = new SetupReflectorState();
    reflectorState.setup.user = System.getenv("USER");
    try {
      System.err.printf("Setting state version %s timestamp %s%n",
          udmiVersion, JsonUtil.getTimestamp(stateTimestamp));
      client.setReflectorState(stringify(reflectorState));
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
  }

  private static Metadata readDeviceMetadata() {
    File deviceMetadataFile = new File(String.format(DEVICE_METADATA_FORMAT, siteModel, deviceId));
    try {
      System.err.println("Reading device metadata file " + deviceMetadataFile.getPath());
      return JsonUtil.OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceMetadataFile.getAbsolutePath(), e);
    }
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
    if (deviceConfig.system != null && !(deviceConfig.system.last_start instanceof SemanticDate)) {
      deviceConfig.system.last_start = SemanticDate.describe("device reported",
          deviceConfig.system.last_start);
    }
    return deviceConfig;
  }

  private Config readGeneratedConfig() {
    File deviceConfigFile = new File(String.format(DEVICE_CONFIG_FORMAT, siteModel, deviceId));
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
    deviceState = new State();
    configAcked = false;
    receivedState.clear();
    receivedEvents.clear();
    waitingCondition = "startup";
    enforceSerial = false;
    recordMessages = true;
    recordSequence = false;

    resetConfig();

    clearLogs();
    queryState();

    syncConfig();

    untilTrue("device state update", () -> deviceState != null);
    recordSequence = true;
  }

  protected void resetConfig() {
    recordSequence("Force reset config");
    withRecordSequence(false, () -> {
      recordSequence = false;
      debug("Starting reset_config");
      resetDeviceConfig(true);
      extraField = "reset_config";
      deviceConfig.system.testing.sequence_name = extraField;
      waitForConfigSync();
      clearLogs();
      extraField = null;
      resetDeviceConfig();
      waitForConfigSync();
      debug("Done with reset_config");
    });
  }

  private void waitForConfigSync() {
    untilTrue("device config sync", this::configUpdateComplete);
  }

  private Date syncConfig() {
    updateConfig();
    waitForConfigSync();
    debug("config synced to " + JsonUtil.getTimestamp(deviceConfig.timestamp));
    return CleanDateFormat.cleanDate(deviceConfig.timestamp);
  }

  @Test
  public void valid_serial_no() {
    if (serialNo == null) {
      throw new SkipTest("No test serial number provided");
    }
    checkThat("received serial no matches", () -> serialNo.equals(lastSerialNo));
  }

  private void recordResult(String result, String methodName, String message) {
    notice(String.format(RESULT_FORMAT, result, methodName, message));
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.printf(RESULT_FORMAT, result, methodName, message);
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
        message == null ? JsonUtil.getTimestamp() : (String) message.get("timestamp");
    String messageBase = String.format("%s_%s", subType, subFolder, JsonUtil.getTimestamp());
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
      messageBase = messageBase + "_" + JsonUtil.getTimestamp();
    }
    recordRawMessage(objectMap, messageBase);
  }

  private void recordRawMessage(Map<String, Object> message, String messageBase) {
    if (!recordMessages) {
      return;
    }

    String prefix = messageBase.startsWith(LOCAL_PREFIX) ? "local " : "received ";
    File messageFile = new File(testDir, messageBase + ".json");
    try {
      JsonUtil.OBJECT_MAPPER.writeValue(messageFile, message);
      boolean traceMessage =
          traceLogLevel() || (debugLogLevel() && messageBase.equals(LOCAL_CONFIG));
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
    }
  }

  private void logSystemEvent(String messageBase, Map<String, Object> message) {
    try {
      Preconditions.checkArgument(!messageBase.startsWith(LOCAL_PREFIX));
      SystemEvent event = JsonUtil.convertTo(SystemEvent.class, message);
      if (event.logentries == null || event.logentries.isEmpty()) {
        debug("received " + SYSTEM_EVENT_MESSAGE_BASE + " (no logs)");
      } else {
        for (Entry logEntry : event.logentries) {
          debug(String.format("%s%s %s %s %s: %s", "received ", messageBase,
              Level.fromValue(logEntry.level).name(), logEntry.category,
              JsonUtil.getTimestamp(logEntry.timestamp), logEntry.message));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While logging system event", e);
    }
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
    String messageStr = String.format("%s %s %s %s", JsonUtil.getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level),
        logEntry.category,
        logEntry.message);

    printWriter.println(messageStr);
    printWriter.flush();
    return messageStr;
  }

  protected void queryState() {
    client.publish(deviceId, Common.STATE_QUERY_TOPIC, EMPTY_MESSAGE);
  }

  /**
   * Reset everything (including the state of the DUT) when done.
   */
  @After
  public void tearDown() {
    recordMessages = false;
    recordSequence = false;
    if (debugLogLevel()) {
      warning("Not resetting config to enable post-execution debugging");
    } else {
      resetConfig();
    }
    deviceConfig = null;
    deviceState = null;
    configAcked = false;
  }

  protected void updateConfig() {
    updateConfig(null);
  }

  protected void updateConfig(String reason) {
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
    updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    recordDeviceConfig(reason);
  }

  private void updateConfig(SubFolder subBlock, Object data) {
    try {
      String messageData = stringify(data);
      String sentBlockConfig = sentConfig.computeIfAbsent(subBlock, key -> "null");
      boolean updated = !messageData.equals(sentBlockConfig);
      if (updated) {
        final Object tracedObject = augmentTrace(data);
        String augmentedMessage = stringify(tracedObject);
        String topic = subBlock + "/config";
        client.publish(deviceId, topic, augmentedMessage);
        debug(String.format("update %s_%s", "config", subBlock));
        recordRawMessage(tracedObject, LOCAL_PREFIX + subBlock.value());
        sentConfig.put(subBlock, messageData);
        // Delay so the backend can process the update before others arrive.
        Thread.sleep(CONFIG_UPDATE_DELAY_MS);
      }
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  private Object augmentTrace(Object data) {
    try {
      if (data == null) {
        return null;
      }
      if (traceLogLevel()) {
        String messageData = stringify(data);
        Map<String, Long> map = JsonUtil.OBJECT_MAPPER.readValue(messageData, Map.class);
        map.put("nonce", System.currentTimeMillis());
        return map;
      } else {
        return data;
      }
    } catch (Exception e) {
      throw new RuntimeException("While augmenting data message", e);
    }
  }

  private void recordDeviceConfig(String reason) {
    try {
      recordRawMessage(deviceConfig, LOCAL_PREFIX + "config");
      List<String> configUpdates = configDiffEngine.computeChanges(deviceConfig);
      if (configUpdates.isEmpty()) {
        return;
      }
      String suffix = reason == null ? "" : (" to " + reason);
      recordSequence(String.format("Update config%s:", suffix));
      configUpdates.forEach(this::recordBullet);
      sequenceMd.flush();
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

  private <T> boolean updateState(SubFolder subFolder, SubFolder expected, Class<T> target,
      Map<String, Object> message, Consumer<T> handler) {
    try {
      if (!expected.equals(subFolder)) {
        return false;
      }
      message.remove("timestamp");
      message.remove("version");
      String messageString = stringify(message);
      boolean updated = !messageString.equals(receivedState.get(subFolder));
      if (updated) {
        debug(String.format("updating %s state", subFolder));
        T state = JsonUtil.OBJECT_MAPPER.readValue(messageString, target);
        handler.accept(state);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While converting state type " + subFolder, e);
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
      throw new IllegalStateException("Failed check that " + description);
    }
    recordSequence("Check that " + description);
  }

  protected List<Map<String, Object>> clearLogs() {
    info("clearing system logs...");
    JsonUtil.safeSleep(LOG_CLEAR_TIME_MS);
    lastLog = null;
    return receivedEvents.remove(SubFolder.SYSTEM);
  }

  protected void hasLogged(String category, Level level) {
    untilTrue(String.format("log category `%s` level `%s`", category, level), () -> {
      List<Map<String, Object>> messages = receivedEvents.get(SubFolder.SYSTEM);
      if (messages == null) {
        return false;
      }
      for (Map<String, Object> message : messages) {
        SystemEvent systemEvent = JsonUtil.convertTo(SystemEvent.class, message);
        if (systemEvent.logentries == null) {
          continue;
        }
        for (Entry logEntry : systemEvent.logentries) {
          boolean validEntry = lastLog == null || !logEntry.timestamp.before(lastLog);
          if (validEntry && category.equals(logEntry.category) && level.value() == logEntry.level) {
            lastLog = logEntry.timestamp;
            debug("Advancing log marker to " + JsonUtil.getTimestamp(lastLog));
            return true;
          }
        }
      }
      return false;
    });
  }

  protected void hasNotLogged(String category, Level level) {
    warning("WARNING HASNOTLOGGED IS NOT COMPLETE");
    recordSequence(
        String.format("Check has not logged category `%s` level `%s` (**incomplete!**)", category,
            level));
    updateConfig();
  }

  private void untilLoop(Supplier<Boolean> evaluator, String description) {
    waitingCondition = "waiting for " + description;
    info(String.format("start %s after %s", waitingCondition, timeSinceStart()));
    updateConfig();
    recordSequence("Wait for " + description);
    while (evaluator.get()) {
      receiveMessage();
    }
    info(String.format("finished %s after %s", waitingCondition, timeSinceStart()));
    waitingCondition = "nothing";
  }

  private void recordSequence(String step) {
    if (recordSequence) {
      sequenceMd.println("1. " + step);
      sequenceMd.flush();
    }
  }

  private void recordBullet(String step) {
    if (recordSequence) {
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

  private void receiveMessage() {
    if (!client.isActive()) {
      throw new RuntimeException("Trying to receive message from inactive client");
    }
    client.processMessage((message, attributes) -> {
      String category = attributes.get("category");
      if ("commands".equals(category)) {
        processCommand(message, attributes);
      } else if ("config".equals(category)) {
        processConfig(message, attributes);
      }
    });
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
      info("Ignoring mismatch state/config timestamp " + JsonUtil.getTimestamp(lastState));
    }
  }

  private void processCommand(Map<String, Object> message, Map<String, String> attributes) {
    if (!deviceId.equals(attributes.get("deviceId"))) {
      return;
    }
    recordRawMessage(message, attributes);
    String subFolderRaw = attributes.get("subFolder");
    String subTypeRaw = attributes.get("subType");

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
      if (message.containsKey("exception")) {
        debug("Ignoring reflector exception:\n" + stringify(message));
        return;
      }
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
        info("Updated config with timestamp " + JsonUtil.getTimestamp(config.timestamp));
        debug(String.format("Updated config #%03d:\n%s", updateCount,
            stringify(converted)));
        recordDeviceConfig("received config message");
      } else if (converted instanceof AugmentedState) {
        debug(String.format("Updated state #%03d:\n%s", updateCount,
            stringify(converted)));
        deviceState = (State) converted;
        updateConfigAcked((AugmentedState) converted);
        validSerialNo();
        info("Updated state has last_config " + JsonUtil.getTimestamp(
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

  protected boolean configUpdateComplete() {
    Object receivedConfig = receivedUpdates.get("config");
    return receivedConfig instanceof Config
        && configDiffEngine.equals(deviceConfig, sanitizeConfig((Config) receivedConfig));
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

  protected <T> List<T> getReceivedEvents(Class<T> clazz) {
    SubFolder subFolder = CLASS_SUBFOLDER_MAP.get(clazz);
    List<Map<String, Object>> events = receivedEvents.remove(subFolder);
    if (events == null) {
      return ImmutableList.of();
    }
    return events.stream().map(message -> JsonUtil.convertTo(clazz, message))
        .collect(Collectors.toList());
  }

  /**
   * Add a description for a test that can be programatically extracted.
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
}
