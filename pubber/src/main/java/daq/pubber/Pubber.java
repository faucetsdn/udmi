package daq.pubber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CloudIotConfig;
import daq.pubber.MqttPublisher.PublisherException;
import daq.pubber.PubSubClient.Bundle;
import daq.pubber.SwarmMessage.Attributes;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.Config;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.Entry;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.Firmware;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetMetadata;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.State;
import udmi.schema.SystemEvent;
import udmi.schema.SystemState;

/**
 * IoT Core UDMI Device Emulator.
 */
public class Pubber {

  private static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String HOSTNAME = System.getenv("HOSTNAME");

  private static final String CONFIG_TOPIC = "config";
  private static final String ERROR_TOPIC = "errors";

  private static final int MIN_REPORT_MS = 200;
  private static final int DEFAULT_REPORT_SEC = 10;
  private static final int CONFIG_WAIT_TIME_MS = 10000;
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s_private.pkcs8";
  private static final String OUT_DIR = "out";
  private static final String PUBSUB_SITE = "PubSub";
  private static final String SWARM_SUBFOLDER = "swarm";
  private static final Set<String> BOOLEAN_UNITS = ImmutableSet.of("No-units");
  private static final double DEFAULT_BASELINE_VALUE = 50;
  private static final String MESSAGE_CATEGORY_FORMAT = "system.%s.%s";
  private static final Map<Class<?>, String> MESSAGE_TOPIC_MAP = ImmutableMap.of(
      State.class, "state",
      SystemEvent.class, "events/system",
      PointsetEvent.class, "events/pointset",
      ExtraPointsetEvent.class, "events/pointset",
      DiscoveryEvent.class, "events/discovery"
  );
  private static final int MESSAGE_REPORT_INTERVAL = 100;
  private static final Map<Level, Consumer<String>> LOG_MAP = ImmutableMap.of(
      Level.DEBUG, LOG::debug,
      Level.INFO, LOG::info,
      Level.WARNING, LOG::warn,
      Level.ERROR, LOG::error
  );
  private static final Map<String, PointPointsetMetadata> DEFAULT_POINTS = ImmutableMap.of(
      "recalcitrant_angle", makePointPointsetMetadata(true, 50, 50, "Celsius"),
      "faulty_finding", makePointPointsetMetadata(true, 40, 0, "deg"),
      "superimposition_reading", makePointPointsetMetadata(false)
  );
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final Configuration configuration;
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_SEC * 1000);
  private final CountDownLatch configLatch = new CountDownLatch(1);
  private final State deviceState = new State();
  private final ExtraPointsetEvent devicePoints = new ExtraPointsetEvent();
  private final Set<AbstractPoint> allPoints = new HashSet<>();
  private final AtomicInteger logMessageCount = new AtomicInteger(0);
  private int deviceMessageCount = -1;
  private MqttPublisher mqttPublisher;
  private ScheduledFuture<?> scheduledFuture;
  private long lastStateTimeMs;
  private int sendCount;
  private boolean stateDirty;
  private PubSubClient pubSubClient;
  private Consumer<String> onDone;
  private boolean publishingLog;
  private Date lastDiscoveryGeneration = new Date();

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public Pubber(String configPath) {
    File configFile = new File(configPath);
    try {
      configuration = OBJECT_MAPPER.readValue(configFile, Configuration.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config " + configFile.getAbsolutePath(), e);
    }
  }

  /**
   * Start an instance from explicit args.
   *
   * @param projectId GCP project
   * @param sitePath  Path to site_model
   * @param deviceId  Device ID to emulate
   * @param serialNo  Serial number of the device
   */
  public Pubber(String projectId, String sitePath, String deviceId, String serialNo) {
    configuration = new Configuration();
    configuration.projectId = projectId;
    configuration.deviceId = deviceId;
    configuration.serialNo = serialNo;
    if (PUBSUB_SITE.equals(sitePath)) {
      pubSubClient = new PubSubClient(projectId, deviceId);
    } else {
      configuration.sitePath = sitePath;
    }
  }

  private static PointPointsetMetadata makePointPointsetMetadata(boolean writable, int value,
      double tolerance, String units) {
    PointPointsetMetadata pointMetadata = new PointPointsetMetadata();
    pointMetadata.writable = writable;
    pointMetadata.baseline_value = value;
    pointMetadata.baseline_tolerance = tolerance;
    pointMetadata.units = units;
    return pointMetadata;
  }

  private static PointPointsetMetadata makePointPointsetMetadata(boolean writable) {
    PointPointsetMetadata pointMetadata = new PointPointsetMetadata();
    return pointMetadata;
  }

  /**
   * Start a pubber instance with command line args.
   *
   * @param args The usual
   * @throws Exception When something is wrong...
   */
  public static void main(String[] args) throws Exception {
    boolean swarm = args.length > 1 && PUBSUB_SITE.equals(args[1]);
    if (swarm) {
      swarmPubber(args);
    } else {
      singularPubber(args);
    }
    LOG.info("Done with main");
  }

  private static void singularPubber(String[] args) throws InterruptedException {
    final Pubber pubber;
    if (args.length == 1) {
      pubber = new Pubber(args[0]);
    } else if (args.length == 4) {
      pubber = new Pubber(args[0], args[1], args[2], args[3]);
    } else {
      throw new IllegalArgumentException(
          "Usage: config_file or { project_id site_path/ device_id serial_no }");
    }
    pubber.initialize();
    pubber.startConnection(deviceId -> {
      LOG.info("Connection closed/finished " + deviceId);
      pubber.terminate();
    });
  }

  private static void swarmPubber(String[] args) throws InterruptedException {
    if (args.length != 4) {
      throw new IllegalArgumentException(
          "Usage: { project_id PubSub pubsub_subscription instance_count }");
    }
    String projectId = args[0];
    String siteName = args[1];
    String feedName = args[2];
    int instances = Integer.parseInt(args[3]);
    LOG.info(String.format("Starting %d pubber instances", instances));
    for (int instance = 0; instance < instances; instance++) {
      String serialNo = String.format("%s-%d", HOSTNAME, (instance + 1));
      startFeedListener(projectId, siteName, feedName, serialNo);
    }
    LOG.info(String.format("Started all %d pubber instances", instances));
  }

  private static void startFeedListener(String projectId, String siteName, String feedName,
      String serialNo) {
    try {
      LOG.info("Starting feed listener " + serialNo);
      Pubber pubber = new Pubber(projectId, siteName, feedName, serialNo);
      pubber.initialize();
      pubber.startConnection(deviceId -> {
        pubber.terminate();
        LOG.error("Connection terminated, restarting listener");
        startFeedListener(projectId, siteName, feedName, serialNo);
      });
    } catch (Exception e) {
      LOG.error("Exception starting instance " + serialNo, e);
      startFeedListener(projectId, siteName, feedName, serialNo);
    }
  }

  private void loadDeviceMetadata() {
    Preconditions.checkState(configuration.sitePath != null, "sitePath not defined");
    Preconditions.checkState(configuration.deviceId != null, "deviceId not defined");
    File devicesFile = new File(new File(configuration.sitePath), "devices");
    File deviceDir = new File(devicesFile, configuration.deviceId);
    File deviceMetadataFile = new File(deviceDir, "metadata.json");
    try {
      Metadata metadata = OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
      processDeviceMetadata(metadata);
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading metadata file " + deviceMetadataFile.getAbsolutePath(), e);
    }
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata.cloud != null) {
      configuration.algorithm = metadata.cloud.auth_type.value();
      info("Configuring with key type " + configuration.algorithm);
    }

    Map<String, PointPointsetMetadata> points =
        metadata.pointset == null ? DEFAULT_POINTS : metadata.pointset.points;
    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  private AbstractPoint makePoint(String name, PointPointsetMetadata point) {
    boolean writable = point.writable != null && point.writable;
    if (BOOLEAN_UNITS.contains(point.units)) {
      return new RandomBoolean(name, writable);
    } else {
      double baselineValue = convertValue(point.baseline_value, DEFAULT_BASELINE_VALUE);
      double baselineTolerance = convertValue(point.baseline_tolerance, baselineValue);
      double min = baselineValue - baselineTolerance;
      double max = baselineValue + baselineTolerance;
      return new RandomPoint(name, writable, min, max, point.units);
    }
  }

  private double convertValue(Object baselineValue, double defaultBaselineValue) {
    if (baselineValue == null) {
      return defaultBaselineValue;
    }
    if (baselineValue instanceof Double) {
      return (double) baselineValue;
    }
    if (baselineValue instanceof Integer) {
      return (double) (int) baselineValue;
    }
    throw new RuntimeException("Unknown value type " + baselineValue.getClass());
  }

  private void loadCloudConfig() {
    Preconditions.checkState(configuration.sitePath != null,
        "sitePath not defined in configuration");
    File cloudConfig = new File(new File(configuration.sitePath), "cloud_iot_config.json");
    try {
      processCloudConfig(OBJECT_MAPPER.readValue(cloudConfig, CloudIotConfig.class));
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  private void processCloudConfig(CloudIotConfig cloudIotConfig) {
    configuration.registryId = cloudIotConfig.registry_id;
    configuration.cloudRegion = cloudIotConfig.cloud_region;
  }

  private void initializeDevice() {
    deviceState.system = new SystemState();
    deviceState.pointset = new PointsetState();
    deviceState.pointset.points = new HashMap<>();
    devicePoints.points = new HashMap<>();

    if (configuration.sitePath != null) {
      loadCloudConfig();
      loadDeviceMetadata();
    } else if (pubSubClient != null) {
      pullDeviceMessage();
    }

    info(String.format("Starting pubber %s, serial %s, mac %s, extra %s, gateway %s",
        configuration.deviceId, configuration.serialNo, configuration.macAddr,
        configuration.extraField,
        configuration.gatewayId));

    deviceState.system.operational = true;
    deviceState.system.serial_no = configuration.serialNo;
    deviceState.system.make_model = "DAQ_pubber";
    deviceState.system.firmware = new Firmware();
    deviceState.system.firmware.version = "v1";
    devicePoints.extraField = configuration.extraField;

    stateDirty = true;
  }

  private void pullDeviceMessage() {
    while (true) {
      try {
        info("Waiting for swarm configuration");
        SwarmMessage.Attributes attributes = new Attributes();
        Bundle pull = pubSubClient.pull();
        attributes.subFolder = pull.attributes.get("subFolder");
        if (!SWARM_SUBFOLDER.equals(attributes.subFolder)) {
          error("Ignoring message with subFolder " + attributes.subFolder);
          continue;
        }
        attributes.deviceId = pull.attributes.get("deviceId");
        attributes.deviceRegistryId = pull.attributes.get("deviceRegistryId");
        attributes.deviceRegistryLocation = pull.attributes.get("deviceRegistryLocation");
        SwarmMessage swarm = OBJECT_MAPPER.readValue(pull.body, SwarmMessage.class);
        processSwarmConfig(swarm, attributes);
        return;
      } catch (Exception e) {
        error("Error pulling swarm message", e);
      }
    }
  }

  private void processSwarmConfig(SwarmMessage swarm, SwarmMessage.Attributes attributes) {
    configuration.deviceId = Preconditions.checkNotNull(attributes.deviceId, "deviceId");
    configuration.keyBytes = Base64.getDecoder()
        .decode(Preconditions.checkNotNull(swarm.key_base64, "key_base64"));
    processCloudConfig(makeCloudIotConfig(attributes));
    processDeviceMetadata(Preconditions.checkNotNull(swarm.device_metadata, "device_metadata"));
  }

  private CloudIotConfig makeCloudIotConfig(Attributes attributes) {
    CloudIotConfig cloudIotConfig = new CloudIotConfig();
    cloudIotConfig.registry_id = Preconditions.checkNotNull(attributes.deviceRegistryId,
        "deviceRegistryId");
    cloudIotConfig.cloud_region = Preconditions.checkNotNull(attributes.deviceRegistryLocation,
        "deviceRegistryLocation");
    return cloudIotConfig;
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
    info("Starting executor with send message delay " + delay);
    scheduledFuture = executor
        .scheduleAtFixedRate(this::sendMessages, delay, delay, TimeUnit.MILLISECONDS);
  }

  private synchronized void cancelExecutor() {
    if (scheduledFuture != null) {
      try {
        scheduledFuture.cancel(false);
        scheduledFuture.get();
      } catch (Exception e) {
        throw new RuntimeException("While cancelling executor", e);
      } finally {
        scheduledFuture = null;
      }
    }
  }

  private void sendMessages() {
    try {
      updatePoints();
      sendDeviceMessage();
      if (stateDirty) {
        publishStateMessage();
      }
      sendCount++;
    } catch (Exception e) {
      error("Fatal error during execution", e);
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
      executor.shutdown();
    } catch (Exception e) {
      info("Error terminating: " + e.getMessage());
    }
  }

  private void startConnection(Consumer<String> onDone) throws InterruptedException {
    this.onDone = onDone;
    connect();
    boolean result = configLatch.await(CONFIG_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    info("synchronized start config result " + result);
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

    File outDir = new File(OUT_DIR);
    try {
      outDir.mkdir();
    } catch (Exception e) {
      throw new RuntimeException("While creating out dir " + outDir.getPath(), e);
    }

    Preconditions.checkNotNull(configuration.deviceId, "configuration deviceId not defined");
    if (configuration.sitePath != null && configuration.keyFile != null) {
      configuration.keyFile = String.format(KEY_SITE_PATH_FORMAT, configuration.sitePath,
          configuration.deviceId, getDeviceKeyPrefix());
    }
    Preconditions.checkState(mqttPublisher == null, "mqttPublisher already defined");
    ensureKeyBytes();
    mqttPublisher = new MqttPublisher(configuration, this::publisherException);
    if (configuration.gatewayId != null) {
      mqttPublisher.registerHandler(configuration.gatewayId, CONFIG_TOPIC,
          this::gatewayHandler, Config.class);
      mqttPublisher.registerHandler(configuration.gatewayId, ERROR_TOPIC,
          this::errorHandler, GatewayError.class);
    }
    mqttPublisher.registerHandler(configuration.deviceId, CONFIG_TOPIC,
        this::configHandler, Config.class);
  }

  private void ensureKeyBytes() {
    if (configuration.keyBytes != null) {
      return;
    }
    Preconditions.checkNotNull(configuration.keyFile, "configuration keyFile not defined");
    info("Loading device key bytes from " + configuration.keyFile);
    configuration.keyBytes = getFileBytes(configuration.keyFile);
  }

  private String getDeviceKeyPrefix() {
    return configuration.algorithm.startsWith("RS") ? "rsa" : "ec";
  }

  private void connect() {
    try {
      mqttPublisher.connect(configuration.deviceId);
      info("Connection complete.");
    } catch (Exception e) {
      error("Connection error", e);
    }
  }

  private void publisherConfigLog(String phase, Exception e) {
    publisherHandler("config", phase, e);
  }

  private void publisherException(Exception toReport) {
    if (toReport instanceof PublisherException) {
      publisherHandler(((PublisherException) toReport).type, ((PublisherException) toReport).phase,
          toReport.getCause());
    } else if (toReport instanceof ConnectionClosedException) {
      if (onDone != null) {
        onDone.accept(configuration.deviceId);
      }
    } else {
      error("Unknown exception type " + toReport.getClass(), toReport);
    }
  }

  private void publisherHandler(String type, String phase, Throwable cause) {
    if (cause != null) {
      error("Error receiving message " + type, cause);
    }
    String category = String.format(MESSAGE_CATEGORY_FORMAT, type, phase);
    final Entry report;
    if (cause == null) {
      report = entryFromException(category, null);
    } else {
      report = entryFromException(category, cause);
    }
    if (Level.DEBUG.value() == report.level || Level.INFO.value() == report.level) {
      deviceState.system.status = null;
    } else {
      deviceState.system.status = report;
    }
    localLog(report);
    publishLogMessage(report);
    publishStateMessage();
    if (cause != null && configLatch.getCount() > 0) {
      configLatch.countDown();
      warn("Released startup latch because reported error");
    }
  }

  private Entry entryFromException(String category, Throwable e) {
    boolean success = e == null;
    Entry entry = new Entry();
    entry.category = category;
    entry.timestamp = new Date();
    entry.message = success ? "success" : e.getMessage();
    entry.detail = success ? null : e.toString();
    entry.level = success ? Level.INFO.value() : Level.ERROR.value();
    return entry;
  }

  private void gatewayHandler(Config config) {
    info(String.format("%s gateway config %s", getTimestamp(), isoConvert(config.timestamp)));
  }

  private void configHandler(Config config) {
    try {
      File configOut = new File(OUT_DIR, "config.json");
      try {
        OBJECT_MAPPER.writeValue(configOut, config);
      } catch (Exception e) {
        throw new RuntimeException("While writing config " + configOut.getPath(), e);
      }
      processConfigUpdate(config);
      configLatch.countDown();
      publisherConfigLog("apply", null);
    } catch (Exception e) {
      publisherConfigLog("apply", e);
    }
    publishStateMessage();
  }

  private void processConfigUpdate(Config config) {
    final int actualInterval;
    if (config != null) {
      info(String.format("%s received config %s", getTimestamp(), isoConvert(config.timestamp)));
      deviceState.system.last_config = config.timestamp;
      actualInterval = updateSystemConfig(config.pointset);
      updatePointsetConfig(config.pointset);
      updateDiscoveryConfig(config.discovery);
    } else {
      info(getTimestamp() + " defaulting empty config");
      actualInterval = DEFAULT_REPORT_SEC * 1000;
    }
    maybeRestartExecutor(actualInterval);
  }

  private void updateDiscoveryConfig(DiscoveryConfig discovery) {
    if (discovery == null || discovery.enumeration == null) {
      deviceState.discovery = null;
      return;
    }
    Date enumerationGeneration = discovery.enumeration.generation;
    if (!enumerationGeneration.after(lastDiscoveryGeneration)) {
      return;
    }
    lastDiscoveryGeneration = enumerationGeneration;
    deviceState.discovery = new DiscoveryState();
    deviceState.discovery.enumeration = new FamilyDiscoveryState();
    deviceState.discovery.enumeration.generation = enumerationGeneration;
    info("Discovery enumeration generation " + isoConvert(enumerationGeneration));
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.generation = enumerationGeneration;
    discoveryEvent.points = allPoints.stream().collect(Collectors.toMap(AbstractPoint::getName,
        AbstractPoint::enumerate));
    publishDeviceMessage(discoveryEvent);
  }

  private String getTimestamp() {
    return isoConvert(new Date());
  }

  private Date isoConvert(String timestamp) {
    try {
      String wrappedString = "\"" + timestamp + "\"";
      return OBJECT_MAPPER.readValue(wrappedString, Date.class);
    } catch (Exception e) {
      throw new RuntimeException("Creating date", e);
    }
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

  private void sendDeviceMessage() {
    devicePoints.version = 1;
    devicePoints.timestamp = new Date();
    if ((++deviceMessageCount) % MESSAGE_REPORT_INTERVAL == 0) {
      info(String.format("%s sending test message #%d", isoConvert(devicePoints.timestamp),
          deviceMessageCount));
    }
    publishDeviceMessage(devicePoints);
  }

  private void pubberLogMessage(String logMessage, Level level, String timestamp) {
    Entry logEntry = new Entry();
    logEntry.category = "pubber";
    logEntry.level = level.value();
    logEntry.timestamp = isoConvert(timestamp);
    logEntry.message = logMessage;
    publishLogMessage(logEntry);
  }

  private void publishLogMessage(Entry report) {
    SystemEvent systemEvent = new SystemEvent();
    systemEvent.version = 1;
    systemEvent.timestamp = new Date();
    systemEvent.logentries.add(report);
    publishDeviceMessage(systemEvent);
  }

  private void publishStateMessage() {
    long delay = lastStateTimeMs + STATE_THROTTLE_MS - System.currentTimeMillis();
    if (delay > 0) {
      warn(String.format("defer state update %d", delay));
      stateDirty = true;
      return;
    }
    deviceState.timestamp = new Date();
    info(String.format("update state %s", isoConvert(deviceState.timestamp)));
    stateDirty = false;
    publishDeviceMessage(deviceState);
    lastStateTimeMs = System.currentTimeMillis();
  }

  private void publishDeviceMessage(Object message) {
    if (mqttPublisher == null) {
      warn("Ignoring publish message b/c connection is shutdown");
      return;
    }
    String topic = MESSAGE_TOPIC_MAP.get(message.getClass());
    if (topic == null) {
      error("Unknown message class " + message.getClass());
      return;
    }
    mqttPublisher.publish(configuration.deviceId, topic, message);

    String fileName = topic.replace("/", "_") + ".json";
    File stateOut = new File(OUT_DIR, fileName);
    try {
      OBJECT_MAPPER.writeValue(stateOut, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + stateOut.getAbsolutePath(), e);
    }
  }

  private void cloudLog(String message, Level level) {
    String timestamp = getTimestamp();
    localLog(message, level, timestamp);

    if (publishingLog || mqttPublisher == null) {
      return;
    }

    try {
      publishingLog = true;
      pubberLogMessage(message, level, timestamp);
    } finally {
      publishingLog = false;
    }
  }

  private void localLog(Entry entry) {
    String message = entry.category + " " + entry.message;
    localLog(message, Level.fromValue(entry.level), isoConvert(entry.timestamp));
  }

  private void localLog(String message, Level level, String timestamp) {
    String logMessage = String.format("%s %s", timestamp, message);
    LOG_MAP.get(level).accept(logMessage);
  }

  private void info(String message) {
    cloudLog(message, Level.INFO);
  }

  private void warn(String message) {
    cloudLog(message, Level.WARNING);
  }

  private void error(String message) {
    cloudLog(message, Level.ERROR);
  }

  private void error(String message, Throwable e) {
    LOG.error(message, e);
    String longMessage = message + ": " + e.getMessage();
    cloudLog(longMessage, Level.ERROR);
  }

  static class ExtraPointsetEvent extends PointsetEvent {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }
}
