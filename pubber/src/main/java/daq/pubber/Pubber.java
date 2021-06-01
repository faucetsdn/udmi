package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.CloudIotConfig;
import java.util.HashMap;
import udmi.schema.Entry;
import udmi.schema.Config;
import udmi.schema.Firmware;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.SystemState;

public class Pubber {

  private static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String POINTSET_TOPIC = "events/pointset";
  private static final String SYSTEM_TOPIC = "events/system";
  private static final String STATE_TOPIC = "state";
  private static final String CONFIG_TOPIC = "config";
  private static final String ERROR_TOPIC = "errors";

  private static final int MIN_REPORT_MS = 200;
  private static final int DEFAULT_REPORT_SEC = 10;
  private static final int CONFIG_WAIT_TIME_MS = 10000;
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String CONFIG_ERROR_STATUS_KEY = "config_error";
  private static final int LOGGING_MOD_COUNT = 10;
  public static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final Configuration configuration;
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_SEC * 1000);
  private final CountDownLatch configLatch = new CountDownLatch(1);

  private final State deviceState = new State();
  private final PointsetEvent devicePoints = new PointsetEvent();
  private final Set<AbstractPoint> allPoints = new HashSet<>();

  private MqttPublisher mqttPublisher;
  private ScheduledFuture<?> scheduledFuture;
  private long lastStateTimeMs;
  private int sendCount;
  private boolean stateDirty;

  public static void main(String[] args) throws Exception {
    final Pubber pubber;
    if (args.length == 1) {
      pubber = new Pubber(args[0]);
    } else if (args.length == 4) {
      pubber = new Pubber(args[0], args[1], args[2], args[3]);
    } else {
      throw new IllegalArgumentException("Usage: config_file or { project_id site_path/ device_id serial_no }");
    }
    pubber.initialize();
    pubber.startConnection();
    LOG.info("Done with main");
  }

  public Pubber(String configPath) {
    File configFile = new File(configPath);
    try {
      configuration = OBJECT_MAPPER.readValue(configFile, Configuration.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config " + configFile.getAbsolutePath(), e);
    }
  }

  public Pubber(String projectId, String sitePath, String deviceId, String serialNo) {
    configuration = new Configuration();
    configuration.projectId = projectId;
    configuration.sitePath = sitePath;
    configuration.deviceId = deviceId;
    configuration.serialNo = serialNo;
  }

  private void loadDeviceMetadata() {
    Preconditions.checkState(configuration.sitePath != null, "sitePath not defined");
    Preconditions.checkState(configuration.deviceId != null, "deviceId not defined");
    File devicesFile = new File(new File(configuration.sitePath), "devices");
    File deviceDir = new File(devicesFile, configuration.deviceId);
    File deviceMetadataFile = new File(deviceDir, "metadata.json");
    try {
      Metadata metadata = OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
      if (metadata.cloud != null) {
        configuration.algorithm = metadata.cloud.auth_type.value();
        LOG.info("Configuring with metadata key type " + configuration.algorithm);
      }
    } catch (Exception e) {
      throw new RuntimeException("While reading metadata file " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  private void loadCloudConfig() {
    Preconditions.checkState(configuration.sitePath != null, "sitePath not defined in configuration");
    File cloudConfig = new File(new File(configuration.sitePath), "cloud_iot_config.json");
    try {
      CloudIotConfig cloudIotConfig = OBJECT_MAPPER.readValue(cloudConfig, CloudIotConfig.class);
      configuration.registryId = cloudIotConfig.registry_id;
      configuration.cloudRegion = cloudIotConfig.cloud_region;
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  private void initializeDevice() {
    if (configuration.sitePath != null) {
      loadCloudConfig();
      loadDeviceMetadata();
    }
    LOG.info(String.format("Starting pubber %s, serial %s, mac %s, extra %s, gateway %s",
        configuration.deviceId, configuration.serialNo, configuration.macAddr, configuration.extraField,
        configuration.gatewayId));
    deviceState.system = new SystemState();
    deviceState.system.serial_no = configuration.serialNo;
    deviceState.system.make_model = "DAQ_pubber";
    deviceState.system.firmware = new Firmware();
    deviceState.system.firmware.version = "v1";
    deviceState.system.statuses = new HashMap<>();
    deviceState.pointset = new PointsetState();
    deviceState.pointset.points = new HashMap<>();
    devicePoints.points = new HashMap<>();
    // devicePoints.extraField = configuration.extraField;
    addPoint(new RandomPoint("superimposition_reading", true,0, 100, "Celsius"));
    addPoint(new RandomPoint("recalcitrant_angle", true,40, 40, "deg" ));
    addPoint(new RandomBoolean("faulty_finding", false));
    stateDirty = true;
  }

  private synchronized void maybeRestartExecutor(int intervalMs) {
    if (scheduledFuture == null || intervalMs != messageDelayMs.get()) {
      cancelExecutor();
      messageDelayMs.set(intervalMs);
      startExecutor();
    }
  }

  private synchronized void startExecutor() {
    Preconditions.checkState(scheduledFuture == null);
    int delay = messageDelayMs.get();
    LOG.info("Starting executor with send message delay " + delay);
    scheduledFuture = executor
        .scheduleAtFixedRate(this::sendMessages, delay, delay, TimeUnit.MILLISECONDS);
  }

  private synchronized void cancelExecutor() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
  }

  private void sendMessages() {
    try {
      updatePoints();
      sendDeviceMessage(configuration.deviceId);
      if (sendCount % LOGGING_MOD_COUNT == 0) {
        publishLogMessage(configuration.deviceId,"Sent " + sendCount + " messages");
      }
      if (stateDirty) {
        publishStateMessage();
      }
      sendCount++;
    } catch (Exception e) {
      LOG.error("Fatal error during execution", e);
      terminate();
    }
  }

  private void updatePoints() {
    allPoints.forEach(point -> {
      point.updateData();
        updateState(point);
    });
  }

  private void updateState(AbstractPoint point) {
    if (point.isDirty()) {
      deviceState.pointset.points.put(point.getName(), point.getState());
      stateDirty = true;
    }
  }

  private void terminate() {
    try {
      info("Terminating");
      mqttPublisher.close();
      cancelExecutor();
    } catch (Exception e) {
      info("Error terminating: " + e.getMessage());
    }
  }

  private void startConnection() throws InterruptedException {
    connect();
    boolean result = configLatch.await(CONFIG_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    LOG.info("synchronized start config result " + result);
    if (!result) {
      mqttPublisher.close();
    }
  }

  private void addPoint(AbstractPoint point) {
    String pointName = point.getName();
    if (devicePoints.points.put(pointName, point.getData()) != null) {
      throw new IllegalStateException("Duplicate pointName " + pointName);
    }
    updateState(point);
    allPoints.add(point);
  }

  private void initialize() {
    initializeDevice();

    Preconditions.checkNotNull(configuration.deviceId, "configuration deviceId not defined");
    if (configuration.sitePath != null && configuration.keyFile != null) {
      configuration.keyFile = String.format(KEY_SITE_PATH_FORMAT, configuration.sitePath,
          configuration.deviceId, getDeviceKeyPrefix());
    }
    Preconditions.checkState(mqttPublisher == null, "mqttPublisher already defined");
    Preconditions.checkNotNull(configuration.keyFile, "configuration keyFile not defined");
    LOG.info("Loading device key file from " + configuration.keyFile);
    configuration.keyBytes = getFileBytes(configuration.keyFile);
    mqttPublisher = new MqttPublisher(configuration, this::reportError);
    if (configuration.gatewayId != null) {
      mqttPublisher.registerHandler(configuration.gatewayId, CONFIG_TOPIC,
          this::gatewayHandler, Config.class);
      mqttPublisher.registerHandler(configuration.gatewayId, ERROR_TOPIC,
          this::errorHandler, GatewayError.class);
    }
    mqttPublisher.registerHandler(configuration.deviceId, CONFIG_TOPIC,
        this::configHandler, Config.class);
  }

  private String getDeviceKeyPrefix() {
    return configuration.algorithm.startsWith("RS") ? "rsa" : "ec";
  }

  private void connect() {
    try {
      mqttPublisher.connect(configuration.deviceId);
      LOG.info("Connection complete.");
    } catch (Exception e) {
      LOG.error("Connection error", e);
      LOG.error("Forcing termination");
      System.exit(-1);
    }
  }

  private void reportError(Exception toReport) {
    if (toReport != null) {
      LOG.error("Error receiving message: " + toReport);
      Entry report = entryFromException(toReport);
      deviceState.system.statuses.put(CONFIG_ERROR_STATUS_KEY, report);
      publishStateMessage();
      if (configLatch.getCount() > 0) {
        LOG.warn("Releasing startup latch because reported error");
        configHandler(null);
      }
    } else {
      Entry previous = deviceState.system.statuses.remove(CONFIG_ERROR_STATUS_KEY);
      if (previous != null) {
        publishStateMessage();
      }
    }
  }

  private Entry entryFromException(Exception e) {
    Entry entry = new Entry();
    entry.message = e.toString();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    e.printStackTrace(new PrintStream(outputStream));
    entry.detail = outputStream.toString();
    entry.category = e.getStackTrace()[0].getClassName();
    entry.level = 800;
    return entry;
  }

  private void info(String msg) {
    LOG.info(msg);
  }

  private void gatewayHandler(Config config) {
    info(String.format("%s gateway config %s", getTimestamp(), isoConvert(config.timestamp)));
  }

  private void configHandler(Config config) {
    try {
      final int actualInterval;
      if (config != null) {
        String state_etag = deviceState.pointset == null ? null : deviceState.pointset.state_etag;
        info(String.format("%s received new config %s %s",
            getTimestamp(), state_etag, isoConvert(config.timestamp)));
        deviceState.system.last_config = config.timestamp;
        actualInterval = updateSystemConfig(config.pointset);
        updatePointsetConfig(config.pointset);
      } else {
        info(getTimestamp() + " defaulting empty config");
        actualInterval = DEFAULT_REPORT_SEC * 1000;
      }
      maybeRestartExecutor(actualInterval);
      configLatch.countDown();
      publishStateMessage();
      reportError(null);
    } catch (Exception e) {
      reportError(e);
    }
  }

  private String getTimestamp() {
    return isoConvert(new Date());
  }

  private String isoConvert(Date timestamp) {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(timestamp);
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  private void updatePointsetConfig(PointsetConfig pointsetConfig) {
    PointsetConfig useConfig = pointsetConfig != null ? pointsetConfig : new PointsetConfig();
    allPoints.forEach(point ->
        updatePointConfig(point, useConfig.points.get(point.getName())));
    deviceState.pointset.state_etag = useConfig.state_etag;
  }

  private void updatePointConfig(AbstractPoint point, PointPointsetConfig pointConfig) {
    point.setConfig(pointConfig);
    updateState(point);
  }

  private int updateSystemConfig(PointsetConfig pointsetConfig) {
    final int actualInterval;
    boolean hasSampleRate = pointsetConfig != null && pointsetConfig.sample_rate_sec != null;
    int reportInterval = hasSampleRate ? pointsetConfig.sample_rate_sec : DEFAULT_REPORT_SEC;
    actualInterval = Integer.max(MIN_REPORT_MS, reportInterval * 1000);
    return actualInterval;
  }

  private void errorHandler(GatewayError error) {
    info(String.format("%s for %s: %s", error.error_type, error.device_id, error.description));
  }

  private byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  private void sendDeviceMessage(String deviceId) {
    if (mqttPublisher.clientCount() == 0) {
      LOG.error("No connected clients, exiting.");
      System.exit(-2);
    }
    devicePoints.timestamp = new Date();
    info(String.format("%s sending test message", isoConvert(devicePoints.timestamp)));
    mqttPublisher.publish(deviceId, POINTSET_TOPIC, devicePoints);
  }

  private void publishLogMessage(String deviceId, String logMessage) {
    SystemEvent systemEvent = new SystemEvent();
    systemEvent.timestamp = new Date();
    info(String.format("%s sending log message", isoConvert(systemEvent.timestamp)));
    Entry logEntry = new Entry();
    logEntry.message = logMessage;
    systemEvent.logentries.add(logEntry);
    mqttPublisher.publish(deviceId, SYSTEM_TOPIC, systemEvent);
  }

  private void publishStateMessage() {
    lastStateTimeMs = sleepUntil(lastStateTimeMs + STATE_THROTTLE_MS);
    deviceState.timestamp = new Date();
    String deviceId = configuration.deviceId;
    info(String.format("%s sending state message", isoConvert(deviceState.timestamp)));
    mqttPublisher.publish(deviceId, STATE_TOPIC, deviceState);
    stateDirty = false;
  }

  private long sleepUntil(long targetTimeMs) {
    long currentTime = System.currentTimeMillis();
    long delay = targetTimeMs - currentTime;
    try {
      if (delay > 0) {
        Thread.sleep(delay);
      }
      return System.currentTimeMillis();
    } catch (Exception e) {
      throw new RuntimeException("While sleeping for " + delay, e);
    }
  }
}
