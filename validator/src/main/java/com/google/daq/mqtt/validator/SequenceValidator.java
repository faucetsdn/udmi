package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.bos.iot.core.proxy.IotCoreClient;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ValidatorConfig;
import com.google.daq.mqtt.validator.validations.SkipTest;
import java.io.File;
import java.io.FileOutputStream;
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
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;
import udmi.schema.SystemState;

public abstract class SequenceValidator {

  private static final String EMPTY_MESSAGE = "{}";
  private static final String STATE_QUERY_TOPIC = "query/state";

  private static final String CLOUD_IOT_CONFIG_FILE = "cloud_iot_config.json";
  private static final String RESULT_LOG_FILE = "RESULT.log";
  private static final String DEVICE_METADATA_FORMAT = "%s/devices/%s/metadata.json";
  private static final String DEVICE_CONFIG_FORMAT = "%s/devices/%s/out/generated_config.json";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new CleanDateFormat())
      .setSerializationInclusion(Include.NON_NULL);
  public static final String RESULT_FAIL = "fail";
  public static final String RESULT_PASS = "pass";
  public static final String RESULT_SKIP = "skip";

  private static final String projectId;
  private static final String deviceId;
  private static final String siteModel;
  private static final String registryId;
  protected static final String serial_no;
  protected static final Metadata deviceMetadata;
  protected static final Config generatedConfig;
  private static final File deviceOutputDir;
  private static final File resultSummary;
  private static final IotCoreClient client;
  private static final String VALIDATOR_CONFIG = "VALIDATOR_CONFIG";
  private static final String CONFIG_PATH = System.getenv(VALIDATOR_CONFIG);
  public static final String RESULT_FORMAT = "RESULT %s %s %s";
  public static final Integer INITIAL_MIN_LOGLEVEL = 400;
  public static final String TESTS_OUT_DIR = "tests";
  public static final String SERIAL_NO_MISSING = "//";

  private static final Map<SubFolder, Class<?>> expectedEvents = ImmutableMap.of(
      SubFolder.SYSTEM, SystemEvent.class,
      SubFolder.POINTSET, PointsetEvent.class
  );

  public static final String UPDATE_SUBTYPE = "update";
  private static final Map<String, Class<?>> expectedUpdates = ImmutableMap.of(
      "configs", Config.class,
      "states", State.class
  );
  public static final String SEQUENCER_CATEGORY = "sequencer";

  // Because of the way tests are run and configured, these parameters need to be
  // a singleton to avoid runtime conflicts.
  static {
    final String key_file;
    if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
      throw new RuntimeException(VALIDATOR_CONFIG + " env not defined.");
    }
    final File CONFIG_FILE = new File(CONFIG_PATH);
    try {
      System.err.println("Reading config file " + CONFIG_FILE.getAbsolutePath());
      ValidatorConfig validatorConfig = ConfigUtil.readValidatorConfig(CONFIG_FILE);
      siteModel = checkNotNull(validatorConfig.site_model, "site_model not defined");
      deviceId = checkNotNull(validatorConfig.device_id, "device_id not defined");
      projectId = checkNotNull(validatorConfig.project_id, "project_id not defined");
      String serial = checkNotNull(validatorConfig.serial_no, "serial_no not defined");
      serial_no = serial.equals(SERIAL_NO_MISSING) ? null : serial;
      key_file = checkNotNull(validatorConfig.key_file, "key_file not defined");
    } catch (Exception e) {
      throw new RuntimeException("While loading " + CONFIG_FILE, e);
    }

    File cloudIoTConfigFile = new File(siteModel + "/" + CLOUD_IOT_CONFIG_FILE);
    final CloudIotConfig cloudIotConfig;
    try {
      cloudIotConfig = ConfigUtil.readCloudIotConfig(cloudIoTConfigFile);
      registryId = checkNotNull(cloudIotConfig.registry_id, "registry_id not defined");
    } catch (Exception e) {
      throw new RuntimeException("While loading " + cloudIoTConfigFile.getAbsolutePath(), e);
    }

    deviceMetadata = readDeviceMetadata();
    generatedConfig = readGeneratedConfig();

    deviceOutputDir = new File("out/devices/" + deviceId);
    try {
      deviceOutputDir.mkdirs();
      File testsOutputDir = new File(deviceOutputDir, TESTS_OUT_DIR);
      FileUtils.deleteDirectory(testsOutputDir);
    } catch (Exception e) {
      throw new RuntimeException("While preparing " + deviceOutputDir.getAbsolutePath(), e);
    }

    resultSummary = new File(deviceOutputDir, RESULT_LOG_FILE);
    resultSummary.delete();
    System.err.println("Writing results to " + resultSummary.getAbsolutePath());

    System.err.printf("Validating against device %s serial %s%n", deviceId, serial_no);
    client = new IotCoreClient(projectId, cloudIotConfig, key_file);
  }

  private static Metadata readDeviceMetadata() {
    File deviceMetadataFile = new File(String.format(DEVICE_METADATA_FORMAT, siteModel, deviceId));
    try {
      System.err.println("Reading device metadata file " + deviceMetadataFile.getPath());
      return OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  private static Config readGeneratedConfig() {
    File deviceConfigFile = new File(String.format(DEVICE_CONFIG_FORMAT, siteModel, deviceId));
    try {
      System.err.println("Reading generated config file " + deviceConfigFile.getPath());
      Config generatedConfig = OBJECT_MAPPER.readValue(deviceConfigFile, Config.class);
      Config config = Optional.ofNullable(generatedConfig).orElse(new Config());
      config.system = Optional.ofNullable(config.system).orElse(new SystemConfig());
      return config;
    } catch (Exception e) {
      throw new RuntimeException("While loading " + deviceConfigFile.getAbsolutePath(), e);
    }
  }

  protected String extraField;
  protected Config deviceConfig;
  protected State deviceState;
  private final Map<SubFolder, String> sentConfig = new HashMap<>();
  private final Map<SubFolder, String> receivedState = new HashMap<>();
  private final Map<SubFolder, List<Map<String, Object>>> receivedEvents = new HashMap<>();
  private final Map<String, Object> receivedUpdates = new HashMap<>();
  private Date lastLog;
  private String waitingCondition;
  private boolean confirm_serial;
  private String testName;
  private String last_serial_no;

  @Before
  public void setUp() {
    deviceState = null;
    sentConfig.clear();
    receivedState.clear();
    receivedEvents.clear();
    waitingCondition = null;
    confirm_serial = false;

    deviceConfig = readGeneratedConfig();
    deviceConfig.system.min_loglevel = 400;

    clearLogs();
    queryState();

    syncConfig();

    untilTrue(() -> deviceState != null, "device state update");
  }

  protected Date syncConfig() {
    updateConfig();
    untilTrue(this::configUpdateComplete, "device config update");
    info("config synced to " + getTimestamp(deviceConfig.timestamp));
    return deviceConfig.timestamp;
  }

  @Test
  public void valid_serial_no() {
    Preconditions.checkNotNull(serial_no, "no test serial_no provided");
  }

  @Rule
  public Timeout globalTimeout = new Timeout(60, TimeUnit.SECONDS);

  @Rule
  public TestWatcher testWatcher = new TestWatcher() {
    @Override
    protected void starting(Description description) {
      testName = description.getMethodName();
      info("starting test " + testName);
    }

    @Override
    protected void finished(Description description) {
      assert testName.equals(description.getMethodName());
      info("ending test " + testName);
      testName = null;
    }

    @Override
    protected void succeeded(Description description) {
      recordCompletion(RESULT_PASS, Level.INFO, description,"Sequence complete");
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

    private void recordCompletion(String result, Level level, Description description, String message) {
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

  private void recordResult(String result, String methodName, String message) {
    info(String.format(RESULT_FORMAT, result, methodName, message));
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.printf(RESULT_FORMAT, result, methodName, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing report summary " + resultSummary.getAbsolutePath(),
          e);
    }
  }

  private void recordRawMessage(Map<String, Object> message, Map<String, String> attributes) {
    String messageBase = String
        .format("%s_%s", attributes.get("subType"), attributes.get("subFolder"));
    info("received " + messageBase);
    String testOutDirName = TESTS_OUT_DIR + "/" + testName;
    File testOutDir = new File(deviceOutputDir, testOutDirName);
    testOutDir.mkdirs();
    File attributeFile = new File(testOutDir, messageBase + ".attr");
    try {
      OBJECT_MAPPER.writeValue(attributeFile, attributes);
    } catch (Exception e) {
      throw new RuntimeException("While writing attributes to " + attributeFile.getAbsolutePath(),
          e);
    }

    File messageFile = new File(testOutDir, messageBase + ".json");
    try {
      OBJECT_MAPPER.writeValue(messageFile, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing message to " + attributeFile.getAbsolutePath(), e);
    }
  }

  private void writeSystemLogs(SystemEvent message) {
    if (message.logentries == null) {
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
    String testOutDirName = TESTS_OUT_DIR + "/" + testName;
    File testOutDir = new File(deviceOutputDir, testOutDirName);
    testOutDir.mkdirs();

    File logFile = new File(testOutDir, filename);
    try (PrintWriter logAppend = new PrintWriter(new FileOutputStream(logFile, true))) {
      String messageStr = String.format("%s %s %s %s", getTimestamp(logEntry.timestamp),
          Level.fromValue(logEntry.level),
          logEntry.category,
          logEntry.message);
      logAppend.println(messageStr);
      if (logEntry.timestamp == null) {
        throw new RuntimeException("log entry timestamp is null");
      }
      return messageStr;
    } catch (Exception e) {
      throw new RuntimeException("While writing message to " + logFile.getAbsolutePath(), e);
    }
  }

  private void queryState() {
    client.publish(deviceId, STATE_QUERY_TOPIC, EMPTY_MESSAGE);
  }

  @After
  public void tearDown() {
    // Restore the config to a canonical state.
    deviceConfig = readGeneratedConfig();
    updateConfig();

    deviceConfig = null;
    deviceState = null;
  }

  private boolean updateConfig(SubFolder subBlock, Object data) {
    try {
      String messageData = OBJECT_MAPPER.writeValueAsString(data);
      boolean updated = !messageData.equals(sentConfig.get(subBlock));
      if (updated) {
        info(String.format("sending %s_%s", "config", subBlock));
        sentConfig.put(subBlock, messageData);
        String topic = "config/" + subBlock;
        client.publish(deviceId, topic, messageData);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While updating config block " + subBlock, e);
    }
  }

  protected void updateConfig() {
    updateConfig(SubFolder.SYSTEM, augmentConfig(deviceConfig.system));
    updateConfig(SubFolder.POINTSET, deviceConfig.pointset);
    updateConfig(SubFolder.GATEWAY, deviceConfig.gateway);
    updateConfig(SubFolder.LOCALNET, deviceConfig.localnet);
    updateConfig(SubFolder.BLOBSET, deviceConfig.blobset);
  }

  private AugmentedSystemConfig augmentConfig(SystemConfig system) {
    try {
      String conversionString = OBJECT_MAPPER.writeValueAsString(system);
      AugmentedSystemConfig augmentedConfig = OBJECT_MAPPER.readValue(conversionString,
          AugmentedSystemConfig.class);
      augmentedConfig.extraField = extraField;
      return augmentedConfig;
    } catch (Exception e) {
      throw new RuntimeException("While augmenting system config", e);
    }
  }

  private <T> T messageConvert(Class<T> target, Map<String, Object> message) {
    try {
      String timestamp = (String) message.remove("timestamp");
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      return OBJECT_MAPPER.readValue(messageString, target);
    } catch (Exception e) {
      throw new RuntimeException("While converting object type " + target.getName(), e);
    }
  }

  private <T> boolean updateState(SubFolder subFolder, SubFolder expected, Class<T> target,
      Map<String, Object> message, Consumer<T> handler) {
    try {
      if (!expected.equals(subFolder)) {
        return false;
      }
      String timestamp = (String) message.remove("timestamp");
      String version = (String) message.remove("version");
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      boolean updated = !messageString.equals(receivedState.get(subFolder));
      if (updated) {
        info(String.format("updating %s state", subFolder));
        T state = OBJECT_MAPPER.readValue(messageString, target);
        if (deviceState == null) {
          deviceState = new State();
        }
        handler.accept(state);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While converting state type " + subFolder, e);
    }
  }

  private void handleStateMessage(SubFolder subFolder, Map<String, Object> message) {
    updateState(subFolder, SubFolder.SYSTEM, SystemState.class, message, state -> deviceState.system = state);
    updateState(subFolder, SubFolder.POINTSET, PointsetState.class, message, state -> deviceState.pointset = state);
    validSerialNo();
  }

  protected boolean validSerialNo() {
    String device_serial = deviceState.system == null ? null : deviceState.system.serial_no;
    if (!Objects.equals(device_serial, last_serial_no)) {
      info(String.format("Received serial no %s", device_serial));
      last_serial_no = device_serial;
    }
    boolean serialValid = Objects.equals(serial_no, device_serial);
    if (!serialValid && confirm_serial) {
      throw new IllegalStateException("Unexpected serial_no " + device_serial);
    }
    confirm_serial = serialValid;
    return serialValid;
  }

  private boolean caughtAsFalse(Supplier<Boolean> evaluator) {
    try {
      return evaluator.get();
    } catch (Exception e) {
      info("Suppressing exception: " + e);
      e.printStackTrace();
      return false;
    }
  }

  protected List<Map<String, Object>> clearLogs() {
    lastLog = null;
    info("logs cleared");
    return receivedEvents.remove(SubFolder.SYSTEM);
  }

  protected void hasLogged(String category, Level level) {
    untilTrue(() -> {
      List<Map<String, Object>> messages = receivedEvents.get(SubFolder.SYSTEM);
      if (messages == null) {
        return false;
      }
      for (Map<String, Object> message : messages) {
        SystemEvent systemEvent = convertTo(message, SystemEvent.class);
        if (systemEvent.logentries == null) {
          continue;
        }
        for (Entry logEntry : systemEvent.logentries) {
          boolean validEntry = lastLog == null || !logEntry.timestamp.before(lastLog);
          if (validEntry && category.equals(logEntry.category) && level.value() == logEntry.level) {
            lastLog = logEntry.timestamp;
            info("Advancing log marker to " + getTimestamp(lastLog));
            return true;
          }
        }
      }
      return false;
    }, "waiting for log message " + category + " level " + level);
  }

  protected void hasNotLogged(String category, Level level) {
    info("WARNING HASNOTLOGGED IS NOT COMPLETE");
  }

  protected void untilTrue(Supplier<Boolean> evaluator, String description) {
    updateConfig();
    waitingCondition = "waiting for " + description;
    info("start " + waitingCondition);
    while (!caughtAsFalse(evaluator)) {
      receiveMessage();
    }
    info("finished " + waitingCondition);
    waitingCondition = null;
  }

  protected String getTimestamp(Date date) {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(date);
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  protected String getTimestamp() {
    return getTimestamp(CleanDateFormat.cleanDate());
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

      if (UPDATE_SUBTYPE.equals(subTypeRaw)) {
        handleReflectorMessage(subFolderRaw, message);
      } else {
        handleDeviceMessage(message, subFolderRaw, subTypeRaw);
      }
    });
  }

  private void handleDeviceMessage(Map<String, Object> message, String subFolderRaw, String subTypeRaw) {
    SubFolder subFolder = SubFolder.fromValue(subFolderRaw);
    SubType subType = SubType.fromValue(subTypeRaw);
    switch (subType) {
      case CONFIG:
        // These are echos of sent config messages, so do nothing.
        break;
      case STATE:
        handleStateMessage(subFolder, message);
        break;
      case EVENT:
        handleEventMessage(subFolder, message);
        break;
    }
  }

  private void handleReflectorMessage(String subFolderRaw, Map<String, Object> message) {
    info("updated " + subFolderRaw + " " + message.get("timestamp"));
    receivedUpdates.put(subFolderRaw, convertTo(message, expectedUpdates.get(subFolderRaw)));
  }

  private void handleEventMessage(SubFolder subFolder, Map<String, Object> message) {
    receivedEvents.computeIfAbsent(subFolder, key -> new ArrayList<>()).add(message);
    if (SubFolder.SYSTEM.equals(subFolder)) {
      writeSystemLogs(convertTo(message, SystemEvent.class));
    }
  }


  private <T> T convertTo(Object message, Class<T> entryClass) {
    try {
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      return OBJECT_MAPPER.readValue(messageString, entryClass);
    } catch (Exception e) {
      throw new RuntimeException("While converting message to " + entryClass.getName());
    }
  }

  private boolean configUpdateComplete() {
    Config receivedConfig = (Config) receivedUpdates.get("configs");
    if (receivedConfig != null) {
      deviceConfig.timestamp = receivedConfig.timestamp;
      deviceConfig.version = receivedConfig.version;
    }
    return deviceConfig.equals(receivedConfig);
  }

  protected void info(String message) {
    Entry logEntry = new Entry();
    logEntry.timestamp = CleanDateFormat.cleanDate();
    logEntry.level = Level.INFO.value();
    logEntry.message = message;
    logEntry.category = SEQUENCER_CATEGORY;
    writeSequencerLog(logEntry);
  }

  private void writeSequencerLog(Entry logEntry) {
    System.err.println(writeLogEntry(logEntry, "sequencer.log"));
  }
}
