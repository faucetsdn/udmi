package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.bos.iot.core.proxy.IotCoreClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ValidatorConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import org.junit.runner.Description;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Config;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;

/**
 * Validate a device using a sequence of message exchanges.
 */
public abstract class SequenceValidator {

  public static final String RESULT_FAIL = "fail";
  public static final String RESULT_PASS = "pass";
  public static final String RESULT_SKIP = "skip";
  public static final String RESULT_FORMAT = "RESULT %s %s %s";
  public static final String TESTS_OUT_DIR = "tests";
  public static final String SERIAL_NO_MISSING = "//";
  public static final String SEQUENCER_CATEGORY = "sequencer";
  public static final String EVENT_PREFIX = "event_";
  public static final int CONFIG_UPDATE_DELAY_MS = 1000;
  public static final int UDMI_TEST_TIMEOUT_SEC = 60;
  protected static final Metadata deviceMetadata;
  private static final String EMPTY_MESSAGE = "{}";
  private static final String CLOUD_IOT_CONFIG_FILE = "cloud_iot_config.json";
  private static final String RESULT_LOG_FILE = "RESULT.log";
  private static final String DEVICE_METADATA_FORMAT = "%s/devices/%s/metadata.json";
  private static final String DEVICE_CONFIG_FORMAT = "%s/devices/%s/out/generated_config.json";
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new CleanDateFormat())
      .setSerializationInclusion(Include.NON_NULL);
  private static final String projectId;
  private static final String deviceId;
  private static final String siteModel;
  private static final String registryId;
  private static final String serialNo;
  private static final int logLevel;
  private static final File deviceOutputDir;
  private static final File resultSummary;
  private static final IotCoreClient client;
  private static final String VALIDATOR_CONFIG = "VALIDATOR_CONFIG";
  private static final String CONFIG_PATH = System.getenv(VALIDATOR_CONFIG);

  private static final Map<Class<?>, SubFolder> CLASS_SUBFOLDER_MAP = ImmutableMap.of(
      SystemEvent.class, SubFolder.SYSTEM,
      PointsetEvent.class, SubFolder.POINTSET,
      DiscoveryEvent.class, SubFolder.DISCOVERY
  );
  private static final Map<String, Class<?>> expectedUpdates = ImmutableMap.of(
      "config", Config.class,
      "state", State.class
  );

  // Because of the way tests are run and configured, these parameters need to be
  // a singleton to avoid runtime conflicts.
  static {
    final String key_file;
    if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
      throw new RuntimeException(VALIDATOR_CONFIG + " env not defined.");
    }
    final File configFile = new File(CONFIG_PATH);
    try {
      System.err.println("Reading config file " + configFile.getAbsolutePath());
      ValidatorConfig validatorConfig = ConfigUtil.readValidatorConfig(configFile);
      siteModel = checkNotNull(validatorConfig.site_model, "site_model not defined");
      deviceId = checkNotNull(validatorConfig.device_id, "device_id not defined");
      projectId = checkNotNull(validatorConfig.project_id, "project_id not defined");
      String serial = checkNotNull(validatorConfig.serial_no, "serial_no not defined");
      serialNo = serial.equals(SERIAL_NO_MISSING) ? null : serial;
      logLevel = Level.valueOf(checkNotNull(validatorConfig.log_level, "log_level not defined"))
          .value();
      key_file = checkNotNull(validatorConfig.key_file, "key_file not defined");
    } catch (Exception e) {
      throw new RuntimeException("While loading " + configFile, e);
    }

    File cloudIotConfigFile = new File(new File(siteModel), CLOUD_IOT_CONFIG_FILE);
    final CloudIotConfig cloudIotConfig;
    try {
      cloudIotConfig = ConfigUtil.readCloudIotConfig(cloudIotConfigFile);
      registryId = checkNotNull(cloudIotConfig.registry_id, "registry_id not defined");
    } catch (Exception e) {
      throw new RuntimeException("While loading " + cloudIotConfigFile.getAbsolutePath(), e);
    }

    deviceMetadata = readDeviceMetadata();

    deviceOutputDir = new File(new File(siteModel), "out/devices/" + deviceId);
    deviceOutputDir.mkdirs();

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Validating against device %s serial %s%n", deviceId, serialNo);
    client = new IotCoreClient(projectId, cloudIotConfig, key_file);
  }

  private final Map<SubFolder, String> sentConfig = new HashMap<>();
  private final Map<SubFolder, String> receivedState = new HashMap<>();
  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  @Rule
  public Timeout globalTimeout = new Timeout(UDMI_TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
  protected String extraField;
  protected Config deviceConfig;
  protected State deviceState;
  protected State previousState;
  private String sentDeviceConfig;
  private Date lastLog;
  private String waitingCondition;
  private boolean enforceSerial;
  private String testName;
  @Rule
  public TestWatcher testWatcher = new TestWatcher() {
    @Override
    protected void starting(Description description) {
      try {
        testName = description.getMethodName();
        File testsOutputDir = new File(new File(deviceOutputDir, TESTS_OUT_DIR), testName);
        FileUtils.deleteDirectory(testsOutputDir);
        testsOutputDir.mkdirs();
        notice("starting test " + testName);
      } catch (Exception e) {
        throw new RuntimeException("While preparing " + deviceOutputDir.getAbsolutePath(), e);
      }
    }

    @Override
    protected void finished(Description description) {
      assert testName.equals(description.getMethodName());
      notice("ending test " + testName);
      testName = null;
    }

    @Override
    protected void succeeded(Description description) {
      recordCompletion(RESULT_PASS, Level.INFO, description, "Sequence complete");
    }

    @Override
    protected void failed(Throwable e, Description description) {
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
    }

    private void recordCompletion(String result, Level level, Description description,
        String message) {
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
  private String lastSerialNo;
  private boolean recordMessages;

  private static Metadata readDeviceMetadata() {
    File deviceMetadataFile = new File(String.format(DEVICE_METADATA_FORMAT, siteModel, deviceId));
    try {
      System.err.println("Reading device metadata file " + deviceMetadataFile.getPath());
      return OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  protected static String getTimestamp(Date date) {
    try {
      if (date == null) {
        return "null";
      }
      String dateString = OBJECT_MAPPER.writeValueAsString(date);
      // Remove the encapsulating quotes included because it's a JSON string-in-a-string.
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  protected String getTimestamp() {
    return getTimestamp(CleanDateFormat.cleanDate());
  }

  private Config readGeneratedConfig() {
    File deviceConfigFile = new File(String.format(DEVICE_CONFIG_FORMAT, siteModel, deviceId));
    try {
      debug("Reading generated config file " + deviceConfigFile.getPath());
      Config generatedConfig = OBJECT_MAPPER.readValue(deviceConfigFile, Config.class);
      Config config = Optional.ofNullable(generatedConfig).orElse(new Config());
      config.system = Optional.ofNullable(config.system).orElse(new SystemConfig());
      return config;
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
    receivedState.clear();
    receivedEvents.clear();
    waitingCondition = "startup";
    enforceSerial = false;
    recordMessages = true;

    resetConfig();

    deviceConfig = readGeneratedConfig();
    deviceConfig.system.min_loglevel = 400;

    clearLogs();
    queryState();

    syncConfig();

    untilTrue("device state update", () -> deviceState != null);
  }

  protected void resetConfig() {
    deviceConfig = new Config();
    deviceConfig.system = new SystemConfig();
    sentConfig.clear();
    extraField = "reset_config";
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    untilTrue("device config reset", this::configUpdateComplete);
    extraField = null;
  }

  private Date syncConfig() {
    updateConfig();
    untilTrue("device config sync", this::configUpdateComplete);
    debug("config synced to " + getTimestamp(deviceConfig.timestamp));
    return CleanDateFormat.cleanDate(deviceConfig.timestamp);
  }

  @Test
  public void valid_serial_no() {
    if (serialNo == null) {
      throw new SkipTest("No test serial number provided");
    }
    assertEquals("received serial no", serialNo, lastSerialNo);
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
    String messageBase = String.format("%s_%s", subType, subFolder, getTimestamp());
    String timestamp = message == null ? getTimestamp() : (String) message.get("timestamp");
    if (traceLogLevel()) {
      messageBase = messageBase + "_" + timestamp;
    }

    recordRawMessage(message, messageBase);

    String testOutDirName = TESTS_OUT_DIR + "/" + checkNotNull(testName);
    File testOutDir = new File(deviceOutputDir, testOutDirName);
    File attributeFile = new File(testOutDir, messageBase + ".attr");
    try {
      OBJECT_MAPPER.writeValue(attributeFile, attributes);
    } catch (Exception e) {
      throw new RuntimeException("While writing attributes to " + attributeFile.getAbsolutePath(),
          e);
    }
  }

  private void recordRawMessage(Object message, String messageBase) {
    Map<String, Object> objectMap = OBJECT_MAPPER.convertValue(message, new TypeReference<>() {
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

    String testOutDirName = TESTS_OUT_DIR + "/" + checkNotNull(testName);
    File testOutDir = new File(deviceOutputDir, testOutDirName);

    File messageFile = new File(testOutDir, messageBase + ".json");
    try {
      OBJECT_MAPPER.writeValue(messageFile, message);
      if (traceLogLevel() && !messageBase.startsWith(EVENT_PREFIX)) {
        trace("received " + messageBase + ":\n" + OBJECT_MAPPER.writeValueAsString(message));
      } else {
        debug("received " + messageBase);
      }
    } catch (Exception e) {
      throw new RuntimeException("While writing message to " + messageFile.getAbsolutePath(), e);
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
    return writeLogEntry(logEntry, "system.log");
  }

  private String writeLogEntry(Entry logEntry, String filename) {
    if (logEntry.timestamp == null) {
      throw new RuntimeException("log entry timestamp is null");
    }
    String messageStr = String.format("%s %s %s %s", getTimestamp(logEntry.timestamp),
        Level.fromValue(logEntry.level),
        logEntry.category,
        logEntry.message);
    if (testName == null) {
      return messageStr;
    }

    String testOutDirName = TESTS_OUT_DIR + "/" + checkNotNull(testName);
    File testOutDir = new File(deviceOutputDir, testOutDirName);

    File logFile = new File(testOutDir, filename);
    try (PrintWriter logAppend = new PrintWriter(new FileOutputStream(logFile, true))) {
      logAppend.println(messageStr);
      return messageStr;
    } catch (Exception e) {
      throw new RuntimeException("While writing message to " + logFile.getAbsolutePath(), e);
    }
  }

  private void queryState() {
    client.publish(deviceId, Validator.STATE_QUERY_TOPIC, EMPTY_MESSAGE);
  }

  /**
   * Reset everything (including the state of the DUT) when done.
   */
  @After
  public void tearDown() {
    recordMessages = false;
    if (debugLogLevel()) {
      warning("Not resetting config to enable post-execution debugging");
    } else {
      // Restore the config to a canonical state.
      resetConfig();
      deviceConfig = readGeneratedConfig();
      updateConfig();
    }
    deviceConfig = null;
    deviceState = null;
  }

  protected void updateConfig() {
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
    updateConfig(SubFolder.DISCOVERY, deviceConfig.discovery);
    recordDeviceConfig();
  }

  private void updateConfig(SubFolder subBlock, Object data) {
    try {
      String messageData = OBJECT_MAPPER.writeValueAsString(data);
      boolean updated = !messageData.equals(sentConfig.get(subBlock));
      if (updated) {
        recordRawMessage(data, "local_" + subBlock.value());
        debug(String.format("update %s_%s", "config", subBlock));
        sentConfig.put(subBlock, messageData);
        String topic = subBlock + "/config";
        client.publish(deviceId, topic, messageData);
        Thread.sleep(CONFIG_UPDATE_DELAY_MS);
      }
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  private boolean recordDeviceConfig() {
    try {
      String messageData = OBJECT_MAPPER.writeValueAsString(deviceConfig);
      boolean updated = !messageData.equals(sentDeviceConfig);
      if (updated) {
        recordRawMessage(deviceConfig, "local_config");
        sentDeviceConfig = messageData;
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While recording device config", e);
    }
  }

  private AugmentedSystemConfig augmentConfig(SystemConfig system) {
    try {
      String conversionString = OBJECT_MAPPER.writeValueAsString(system);
      AugmentedSystemConfig augmentedConfig = OBJECT_MAPPER.readValue(conversionString,
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
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      boolean updated = !messageString.equals(receivedState.get(subFolder));
      if (updated) {
        debug(String.format("updating %s state", subFolder));
        T state = OBJECT_MAPPER.readValue(messageString, target);
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
    if (!serialValid && enforceSerial) {
      assertEquals("state serial no", serialNo, deviceSerial);
    }
    enforceSerial = serialValid;
    return serialValid;
  }

  protected boolean catchToFalse(Supplier<Boolean> evaluator) {
    Boolean value = catchToNull(evaluator);
    return value != null && value;
  }

  protected <T> T catchToNull(Supplier<T> evaluator) {
    try {
      return evaluator.get();
    } catch (Exception e) {
      debug("Suppressing exception: " + e);
      trace(stackTraceString(e));
      return null;
    }
  }

  private String stackTraceString(Exception e) {
    OutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(outputStream)) {
      e.printStackTrace(ps);
    }
    return outputStream.toString();
  }

  protected List<Map<String, Object>> clearLogs() {
    lastLog = null;
    debug("logs cleared");
    return receivedEvents.remove(SubFolder.SYSTEM);
  }

  protected void hasLogged(String category, Level level) {
    untilTrue("waiting for log message " + category + " level " + level, () -> {
      List<Map<String, Object>> messages = receivedEvents.get(SubFolder.SYSTEM);
      if (messages == null) {
        return false;
      }
      for (Map<String, Object> message : messages) {
        SystemEvent systemEvent = convertTo(SystemEvent.class, message);
        if (systemEvent.logentries == null) {
          continue;
        }
        for (Entry logEntry : systemEvent.logentries) {
          boolean validEntry = lastLog == null || !logEntry.timestamp.before(lastLog);
          if (validEntry && category.equals(logEntry.category) && level.value() == logEntry.level) {
            lastLog = logEntry.timestamp;
            debug("Advancing log marker to " + getTimestamp(lastLog));
            return true;
          }
        }
      }
      return false;
    });
  }

  protected void hasNotLogged(String category, Level level) {
    warning("WARNING HASNOTLOGGED IS NOT COMPLETE");
  }

  private void untilLoop(Supplier<Boolean> evaluator, String description) {
    updateConfig();
    waitingCondition = "waiting for " + description;
    info("start " + waitingCondition);
    while (evaluator.get()) {
      receiveMessage();
    }
    info("finished " + waitingCondition);
    waitingCondition = "nothing";
  }

  protected void untilTrue(String description, Supplier<Boolean> evaluator) {
    untilLoop(() -> !catchToFalse(evaluator), description);
  }

  protected void untilUntrue(String description, Supplier<Boolean> evaluator) {
    untilLoop(() -> catchToFalse(evaluator), description);
  }

  private void receiveMessage() {
    if (!client.isActive()) {
      throw new RuntimeException("Trying to receive message from inactive client");
    }
    client.processMessage((message, attributes) -> {
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
    });
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
      Object converted = convertTo(expectedUpdates.get(subFolderRaw), message);
      receivedUpdates.put(subFolderRaw, converted);
      if (converted instanceof Config) {
        String extraField = getExtraField(message);
        if ("reset_config".equals(extraField)) {
          debug("Update with config reset");
        } else if ("break_json".equals(extraField)) {
          notice("Ignoring broken json");
          return;
        }
        Config config = (Config) converted;
        deviceConfig.timestamp = config.timestamp;
        deviceConfig.version = config.version;
        info("Updated config with timestamp " + getTimestamp(config.timestamp));
        debug("Updated config:\n" + OBJECT_MAPPER.writeValueAsString(converted));
        recordDeviceConfig();
      } else if (converted instanceof State) {
        debug("Updated state:\n" + OBJECT_MAPPER.writeValueAsString(converted));
        deviceState = (State) converted;
        validSerialNo();
        info("Updated state has last_config " + getTimestamp(
            ((State) converted).system.last_config));
      } else {
        error("Unknown update type " + converted.getClass().getSimpleName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling reflector message", e);
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
      writeSystemLogs(convertTo(SystemEvent.class, message));
    }
  }

  protected String toJsonString(Object message) {
    try {
      return OBJECT_MAPPER.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException("While stringifying message", e);
    }
  }

  private <T> T convertTo(Class<T> targetClass, Object message) {
    if (message == null) {
      return null;
    }
    try {
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      return OBJECT_MAPPER.readValue(messageString, checkNotNull(targetClass, "target class"));
    } catch (Exception e) {
      throw new RuntimeException("While converting message to " + targetClass.getName(), e);
    }
  }

  private synchronized boolean configUpdateComplete() {
    return deviceConfig.equals(receivedUpdates.get("config"));
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
    String entry = writeLogEntry(logEntry, "sequencer.log");
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
    return events.stream().map(message -> convertTo(clazz, message)).collect(Collectors.toList());
  }
}
