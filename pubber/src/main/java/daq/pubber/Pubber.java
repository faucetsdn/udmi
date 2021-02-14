package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.CloudIotConfig;
import daq.udmi.Entry;
import daq.udmi.Message;
import daq.udmi.Message.PointConfig;
import daq.udmi.Message.Pointset;
import daq.udmi.Message.PointsetConfig;
import daq.udmi.Message.PointsetState;
import daq.udmi.Message.State;
import java.io.File;
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
  private static final int DEFAULT_REPORT_MS = 10000;
  private static final int CONFIG_WAIT_TIME_MS = 10000;
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String CONFIG_ERROR_STATUS_KEY = "config_error";
  private static final int LOGGING_MOD_COUNT = 10;
  public static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/rsa_private.pkcs8";

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final Configuration configuration;
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_MS);
  private final CountDownLatch configLatch = new CountDownLatch(1);

  private final State deviceState = new State();
  private final Pointset devicePoints = new Pointset();
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
    loadCloudConfig();
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
    LOG.info(String.format("Starting pubber %s serial %s extra %s",
        configuration.deviceId, configuration.serialNo, configuration.extraField));
    deviceState.system.serial_no = configuration.serialNo;
    deviceState.system.make_model = "DAQ_pubber";
    deviceState.system.firmware.version = "v1";
    deviceState.pointset = new PointsetState();
    devicePoints.extraField = configuration.extraField;
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
          configuration.deviceId);
    }
    Preconditions.checkState(mqttPublisher == null, "mqttPublisher already defined");
    Preconditions.checkNotNull(configuration.keyFile, "configuration keyFile not defined");
    System.err.println("Loading device key file from " + configuration.keyFile);
    configuration.keyBytes = getFileBytes(configuration.keyFile);
    mqttPublisher = new MqttPublisher(configuration, this::reportError);
    if (configuration.gatewayId != null) {
      mqttPublisher.registerHandler(configuration.gatewayId, CONFIG_TOPIC,
          this::configHandler, Message.Config.class);
      mqttPublisher.registerHandler(configuration.gatewayId, ERROR_TOPIC,
          this::errorHandler, GatewayError.class);
    }
    mqttPublisher.registerHandler(configuration.deviceId, CONFIG_TOPIC,
        this::configHandler, Message.Config.class);
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
      Entry report = new Entry(toReport);
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

  private void info(String msg) {
    LOG.info(msg);
  }

  private void configHandler(Message.Config config) {
    try {
      final int actualInterval;
      if (config != null) {
        info("Received new config at " + getTimestamp());
        deviceState.system.last_config = config.timestamp;
        actualInterval = updateSystemConfig(config.system);
        updatePointsetConfig(config.pointset);
      } else {
        info("Defaulting empty config at " + getTimestamp());
        actualInterval = DEFAULT_REPORT_MS;
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
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(new Date());
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  private void updatePointsetConfig(PointsetConfig pointsetConfig) {
    PointsetConfig useConfig = pointsetConfig != null ? pointsetConfig : new PointsetConfig();
    allPoints.forEach(point ->
        updatePointConfig(point, useConfig.points.get(point.getName())));
    deviceState.pointset.etag = useConfig.etag;
    devicePoints.etag = useConfig.etag;
  }

  private void updatePointConfig(AbstractPoint point, PointConfig pointConfig) {
    point.setConfig(pointConfig);
    updateState(point);
  }

  private int updateSystemConfig(Message.SystemConfig configSystem) {
    final int actualInterval;
    Integer reportInterval = configSystem == null ? null : configSystem.report_interval_ms;
    actualInterval = Integer.max(MIN_REPORT_MS,
        reportInterval == null ? DEFAULT_REPORT_MS : reportInterval);
    return actualInterval;
  }

  private void errorHandler(GatewayError error) {
    // TODO: Handle error and give up on device.
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
    info(String.format("Sending test message for %s/%s at %s",
        configuration.registryId, deviceId, getTimestamp()));
    devicePoints.timestamp = new Date();
    mqttPublisher.publish(deviceId, POINTSET_TOPIC, devicePoints);
  }

  private void publishLogMessage(String deviceId, String logMessage) {
    info(String.format("Sending log message for %s/%s at %s",
        configuration.registryId, deviceId, getTimestamp()));
    Message.SystemEvent systemEvent = new Message.SystemEvent();
    systemEvent.logentries.add(new Entry(logMessage));
    mqttPublisher.publish(deviceId, SYSTEM_TOPIC, systemEvent);
  }

  private void publishStateMessage() {
    lastStateTimeMs = sleepUntil(lastStateTimeMs + STATE_THROTTLE_MS);
    deviceState.timestamp = new Date();
    String deviceId = configuration.deviceId;
    info("Sending state message for device " + deviceId + " at " + getTimestamp());
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
