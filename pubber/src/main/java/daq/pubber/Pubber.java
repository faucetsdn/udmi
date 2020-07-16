package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.CloudIotConfig;
import daq.udmi.Entry;
import daq.udmi.Message;
import daq.udmi.Message.Config;
import daq.udmi.Message.Pointset;
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
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String POINTSET_TOPIC = "events/pointset";
  private static final String SYSTEM_TOPIC = "events/system";
  private static final String STATE_TOPIC = "state";
  private static final String CONFIG_TOPIC = "config";
  private static final String ERROR_TOPIC = "errors";

  private static final int MIN_REPORT_MS = 200;
  private static final int DEFAULT_REPORT_MS = 5000;
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

  public static void main(String[] args) throws Exception {
    final Pubber pubber;
    if (args.length == 1) {
      pubber = new Pubber(args[0]);
    } else if (args.length == 3) {
      pubber = new Pubber(args[0], args[1], args[2]);
    } else {
      throw new IllegalArgumentException("Usage: config_file or { project_id site_path/ device_id }");
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
      throw new RuntimeException("While reading config " + configFile.getAbsolutePath());
    }
  }

  public Pubber(String projectId, String sitePath, String deviceId) {
    configuration = new Configuration();
    configuration.projectId = projectId;
    configuration.sitePath = sitePath;
    configuration.deviceId = deviceId;
  }

  private void loadCloudConfig() {
    Preconditions.checkState(configuration.sitePath != null, 'sitePath not defined in configuration);
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
    deviceState.system.make_model = "DAQ_pubber";
    deviceState.system.firmware.version = "v1";
    deviceState.pointset = new PointsetState();
    devicePoints.extraField = configuration.extraField;
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
      sendDeviceMessage(configuration.deviceId);
      updatePoints();
      if (sendCount % LOGGING_MOD_COUNT == 0) {
        publishLogMessage(configuration.deviceId,"Sent " + sendCount + " messages");
      }
      sendCount++;
    } catch (Exception e) {
      LOG.error("Fatal error during execution", e);
      terminate();
    }
  }

  private void updatePoints() {
    allPoints.forEach(AbstractPoint::updateData);
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
    deviceState.pointset.points.put(pointName, point.getState());
    allPoints.add(point);
  }

  private void initialize() {
    loadCloudConfig();
    initializeDevice();
    addPoint(new RandomPoint("superimposition_reading", 0, 100, "Celsius"));
    addPoint(new RandomPoint("recalcitrant_angle", 0, 360, "deg" ));
    addPoint(new RandomPoint("faulty_finding", 1, 1, "truth"));

    Preconditions.checkNotNull(configuration.deviceId, "configuration deviceId not defined");
    if (configuration.sitePath != null) {
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
      publishStateMessage(configuration.deviceId);
    } else {
      Entry previous = deviceState.system.statuses.remove(CONFIG_ERROR_STATUS_KEY);
      if (previous != null) {
        publishStateMessage(configuration.deviceId);
      }
    }
  }

  private void info(String msg) {
    LOG.info(msg);
  }

  private void configHandler(Message.Config config) {
    try {
      info("Received new config " + config);
      final int actualInterval;
      if (config != null) {
        Integer reportInterval = config.system == null ? null : config.system.report_interval_ms;
        actualInterval = Integer.max(MIN_REPORT_MS,
            reportInterval == null ? DEFAULT_REPORT_MS : reportInterval);
        deviceState.system.last_config = config.timestamp;
      } else {
        actualInterval = DEFAULT_REPORT_MS;
      }
      maybeRestartExecutor(actualInterval);
      configLatch.countDown();
      publishStateMessage(configuration.deviceId);
      reportError(null);
    } catch (Exception e) {
      reportError(e);
    }
  }

  private void errorHandler(GatewayError error) {
    // TODO: Handle error and give up on device.
    info(String.format("%s for %s: %s",
        error.error_type, error.device_id, error.description));
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
    info(String.format("Sending test message for %s/%s", configuration.registryId, deviceId));
    devicePoints.timestamp = new Date();
    mqttPublisher.publish(deviceId, POINTSET_TOPIC, devicePoints);
  }

  private void publishLogMessage(String deviceId, String logMessage) {
    info(String.format("Sending log message for %s/%s", configuration.registryId, deviceId));
    Message.SystemEvent systemEvent = new Message.SystemEvent();
    systemEvent.logentries.add(new Entry(logMessage));
    mqttPublisher.publish(deviceId, SYSTEM_TOPIC, systemEvent);
  }

  private void publishStateMessage(String deviceId) {
    lastStateTimeMs = sleepUntil(lastStateTimeMs + STATE_THROTTLE_MS);
    deviceState.timestamp = new Date();
    info("Sending state message for device " + deviceId + " at " + deviceState.timestamp);
    mqttPublisher.publish(deviceId, STATE_TOPIC, deviceState);
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
