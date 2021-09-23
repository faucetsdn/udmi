package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.IotCoreClient;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ValidatorConfig;
import com.google.daq.mqtt.validator.validations.SkipTest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
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
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.TestTimedOutException;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.PointsetState;
import udmi.schema.State;
import udmi.schema.SystemConfig;
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
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(Include.NON_NULL);
  public static final String RESULT_FAIL = "fail";
  public static final String RESULT_PASS = "pass";
  public static final String RESULT_SKIP = "skip";

  private static final String projectId;
  private static final String deviceId;
  private static final String siteModel;
  private static final String registryId;
  private static final String serial_no;
  protected static final Metadata deviceMetadata;
  protected static final Config generatedConfig;
  private static final File deviceOutputDir;
  private static final File resultSummary;
  private static final IotCoreClient client;
  private static final String VALIDATOR_CONFIG = "VALIDATOR_CONFIG";
  private static final String CONFIG_PATH = System.getenv(VALIDATOR_CONFIG);
  public static final String RESULT_FORMAT = "RESULT %s %s %s%n";
  public static final int INITIAL_MIN_LOGLEVEL = 400;

  protected Config deviceConfig;
  protected State deviceState;

  public static final String TESTS_OUT_DIR = "tests";

  // Because of the way tests are run and configured, these parameters need to be
  // a singleton to avoid runtime conflicts.
  static {
    final String key_file;
    if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
      throw new RuntimeException(VALIDATOR_CONFIG + " env not defined.");
    }
    final File CONFIG_FILE = new File(CONFIG_PATH);
    try {
      ValidatorConfig validatorConfig = ConfigUtil.readValidatorConfig(CONFIG_FILE);
      siteModel = checkNotNull(validatorConfig.site_model, "site_model not defined");
      deviceId = checkNotNull(validatorConfig.device_id, "device_id not defined");
      projectId = checkNotNull(validatorConfig.project_id, "project_id not defined");
      serial_no = checkNotNull(validatorConfig.serial_no, "serial_no not defined");
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

    System.err.printf("Validating device %s serial %s%n", deviceId, serial_no);
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

  private final Map<String, String> sentConfig = new HashMap<>();
  private final Map<String, String> receivedState = new HashMap<>();
  private String waitingCondition;
  private boolean check_serial;
  private String testName;
  private String last_serial_no;

  @Before
  public void setUp() {
    deviceConfig = readGeneratedConfig();
    deviceState = new State();
    sentConfig.clear();
    receivedState.clear();
    waitingCondition = null;
    check_serial = false;

    if (deviceConfig.system.min_loglevel != null &&
        deviceConfig.system.min_loglevel == INITIAL_MIN_LOGLEVEL) {
      // If necessary, force a config update by making sure at least one field changes!
      deviceConfig.system.min_loglevel = null;
      updateConfig();
    }
    deviceConfig.system.min_loglevel = INITIAL_MIN_LOGLEVEL;
    queryState();
  }

  @Rule
  public Timeout globalTimeout = new Timeout(60, TimeUnit.SECONDS);

  @Rule
  public TestWatcher testWatcher = new TestWatcher() {
    @Override
    protected void starting(Description description) {
      testName = description.getMethodName();
      System.err.println(getTimestamp() + " starting test " + testName);
    }

    @Override
    protected void succeeded(Description description) {
      System.err.println(getTimestamp() + " passed test " + testName);
      recordResult(RESULT_PASS, description.getMethodName(), "Sequence completed");
    }

    @Override
    protected void failed(Throwable e, Description description) {
      final String message;
      final String type;
      if (e instanceof TestTimedOutException) {
        message = "timeout " + waitingCondition;
        type = RESULT_FAIL;
      } else if (e instanceof SkipTest) {
        message = e.getMessage();
        type = RESULT_SKIP;
      } else {
        message = e.getMessage();
        type = RESULT_FAIL;
      }
      System.err.println(getTimestamp() + " failed " + message);
      recordResult(type, description.getMethodName(), message);
    }
  };

  private void recordResult(String result, String methodName, String message) {
    System.err.printf(RESULT_FORMAT, result, methodName, message);
    try (PrintWriter log = new PrintWriter(new FileOutputStream(resultSummary, true))) {
      log.printf(RESULT_FORMAT, result, methodName, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing report summary " + resultSummary.getAbsolutePath(),
          e);
    }
  }

  private void recordMessage(Map<String, Object> message, Map<String, String> attributes) {
    String messageBase = String
        .format("%s_%s", attributes.get("subFolder"), attributes.get("subType"));
    System.err.println(getTimestamp() + " received " + messageBase);
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

  private boolean updateConfig(String subBlock, Object data) {
    try {
      String messageData = OBJECT_MAPPER.writeValueAsString(data);
      boolean updated = !messageData.equals(sentConfig.get(subBlock));
      if (updated) {
        System.err.printf("%s sending %s_%s%n", getTimestamp(), subBlock, "config");
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
    if (updateConfig("system", deviceConfig.system) && deviceConfig.system != null) {
      System.err.println(getTimestamp() + " updated system loglevel " + deviceConfig.system.min_loglevel);
    }
    if (updateConfig("pointset", deviceConfig.pointset) && deviceConfig.pointset != null) {
      System.err.println(getTimestamp() + " updated pointset config");
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

  private <T> boolean updateState(String subFolder, String expected, Class<T> target,
      Map<String, Object> message, Consumer<T> handler) {
    try {
      if (!expected.equals(subFolder)) {
        return false;
      }
      String timestamp = (String) message.remove("timestamp");
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      boolean updated = !messageString.equals(receivedState.get(subFolder));
      if (updated) {
        System.err.printf("%s updating %s state%n", getTimestamp(), subFolder);
        T state = OBJECT_MAPPER.readValue(messageString, target);
        handler.accept(state);
      }
      return updated;
    } catch (Exception e) {
      throw new RuntimeException("While converting state type " + subFolder, e);
    }
  }

  private void updateState(String subFolder, Map<String, Object> message) {
    if (updateState(subFolder, "system", SystemState.class, message,
        state -> deviceState.system = state)) {
      Date last_config = deviceState.system == null ? null : deviceState.system.last_config;
      System.err.printf("%s received state last_config %s%n", getTimestamp(), last_config);
    }
    if (updateState(subFolder, "pointset", PointsetState.class, message,
        state -> deviceState.pointset = state)) {
      System.err.printf("%s received state pointset%n", getTimestamp());
    }
    validSerialNo();
  }

  private void dumpConfigUpdate(Map<String, Object> message) {
    Config config = messageConvert(Config.class, message);
    System.err.println(getTimestamp() + " update config");
  }

  private void dumpStateUpdate(Map<String, Object> message) {
    State state = messageConvert(State.class, message);
    System.err.println(getTimestamp() + " update state");
  }

  protected boolean validSerialNo() {
    String device_serial = deviceState.system == null ? null : deviceState.system.serial_no;
    if (!Objects.equals(device_serial, last_serial_no)) {
      System.err.printf("%s Received serial no %s%n", getTimestamp(), device_serial);
      last_serial_no = device_serial;
    }
    boolean serialValid = Objects.equals(serial_no, device_serial);
    if (!serialValid && check_serial) {
      throw new IllegalStateException("Unexpected serial_no " + device_serial);
    }
    check_serial = serialValid;
    return serialValid;
  }

  protected void untilTrue(Supplier<Boolean> evaluator, String description) {
    updateConfig();
    waitingCondition = "waiting for " + description;
    System.err.println(getTimestamp() + " start " + waitingCondition);
    while (!evaluator.get()) {
      receiveMessage();
    }
    System.err.println(getTimestamp() + " finished " + waitingCondition);
    waitingCondition = null;
  }

  protected String getTimestamp() {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(new Date());
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  private void receiveMessage() {
    if (!client.isActive()) {
      throw new RuntimeException("Trying to receive message from inactive client");
    }
    client.processMessage((message, attributes) -> {
      if (!deviceId.equals(attributes.get("deviceId"))) {
        return;
      }
      recordMessage(message, attributes);
      String subType = attributes.get("subType");
      if ("states".equals(subType)) {
        updateState(attributes.get("subFolder"), message);
      } else if ("config".equals(subType)) {
        dumpConfigUpdate(message);
      } else if ("state".equals(subType)) {
        dumpStateUpdate(message);
      }
    });
  }

}
