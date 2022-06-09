package daq.pubber;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import com.google.daq.mqtt.util.CloudIotConfig;
import daq.pubber.MqttPublisher.PublisherException;
import daq.pubber.PubSubClient.Bundle;
import daq.pubber.SwarmMessage.Attributes;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Config;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.Entry;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryEvent;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.Hardware;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointEnumerationEvent;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
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

  public static final int SCAN_DURATION_SEC = 10;
  public static final String DISCOVERY_ID = "RANDOM_ID";
  private static final String UDMI_VERSION = "1.3.14";
  private static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String HOSTNAME = System.getenv("HOSTNAME");
  private static final String POINTSET_TOPIC = "events/pointset";
  private static final String SYSTEM_TOPIC = "events/system";
  private static final String STATE_TOPIC = "state";
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
  private static final int MESSAGE_REPORT_INTERVAL = 10;
  private static final Map<Level, Consumer<String>> LOG_MAP = ImmutableMap.of(
      Level.TRACE, LOG::info,  // TODO: Make debug/trace programmatically visible.
      Level.DEBUG, LOG::info,
      Level.INFO, LOG::info,
      Level.WARNING, LOG::warn,
      Level.ERROR, LOG::error
  );
  private static final Map<String, PointPointsetModel> DEFAULT_POINTS = ImmutableMap.of(
      "recalcitrant_angle", makePointPointsetModel(true, 50, 50, "Celsius"),
      "faulty_finding", makePointPointsetModel(true, 40, 0, "deg"),
      "superimposition_reading", makePointPointsetModel(false)
  );
  private static final long VERY_LONG_TIME_SEC = 1234567890;
  private static final Date NEVER_FUTURE = Date.from(Instant.now().plusSeconds(VERY_LONG_TIME_SEC));
  private static final Date DEVICE_START_TIME = new Date();
  private final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  private final Configuration configuration;
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_SEC * 1000);
  private final CountDownLatch configLatch = new CountDownLatch(1);
  private final State deviceState = new State();
  private final ExtraPointsetEvent devicePoints = new ExtraPointsetEvent();
  private final Set<AbstractPoint> allPoints = new HashSet<>();
  private final AtomicInteger logMessageCount = new AtomicInteger(0);
  private final AtomicBoolean stateDirty = new AtomicBoolean();
  private Config deviceConfig = new Config();
  private int deviceMessageCount = -1;
  private MqttPublisher mqttPublisher;
  private ScheduledFuture<?> scheduledFuture;
  private long lastStateTimeMs;
  private PubSubClient pubSubClient;
  private Consumer<String> onDone;
  private boolean publishingLog;
  private Map<String, Metadata> allMetadata;

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public Pubber(String configPath) {
    File configFile = new File(configPath);
    try {
      configuration = OBJECT_MAPPER.readValue(configFile, Configuration.class);
    } catch (UnrecognizedPropertyException e) {
      throw new RuntimeException("Invalid arguments or options: " + e.getMessage());
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

  private static PointPointsetModel makePointPointsetModel(boolean writable, int value,
      double tolerance, String units) {
    PointPointsetModel pointMetadata = new PointPointsetModel();
    pointMetadata.writable = writable;
    pointMetadata.baseline_value = value;
    pointMetadata.baseline_tolerance = tolerance;
    pointMetadata.units = units;
    return pointMetadata;
  }

  private static PointPointsetModel makePointPointsetModel(boolean writable) {
    PointPointsetModel pointMetadata = new PointPointsetModel();
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

  private Set<String> getAllDevices() {
    Preconditions.checkState(configuration.sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(configuration.sitePath), "devices");
    File[] files = Preconditions.checkNotNull(devicesFile.listFiles(), "no files in site devices/");
    return Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
  }

  private void loadDeviceMetadata() {
    Preconditions.checkState(configuration.deviceId != null, "deviceId not defined");
    allMetadata = getAllDevices().stream()
        .collect(toMap(deviceId -> deviceId, deviceId -> getDeviceMetadata(deviceId)));
    processDeviceMetadata(allMetadata.get(configuration.deviceId));
  }

  private Metadata getDeviceMetadata(String deviceId) {
    Preconditions.checkState(configuration.sitePath != null, "sitePath not defined");
    File devicesFile = new File(new File(configuration.sitePath), "devices");
    File deviceDir = new File(devicesFile, deviceId);
    File deviceMetadataFile = new File(deviceDir, "metadata.json");
    try {
      return OBJECT_MAPPER.readValue(deviceMetadataFile, Metadata.class);
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

    if (metadata.gateway != null) {
      configuration.gatewayId = metadata.gateway.gateway_id;
      if (configuration.gatewayId != null) {
        Auth_type authType = allMetadata.get(configuration.gatewayId).cloud.auth_type;
        if (authType != null) {
          configuration.algorithm = authType.value();
        }
      }
    }

    Map<String, PointPointsetModel> points =
        metadata.pointset == null ? DEFAULT_POINTS : metadata.pointset.points;

    if (!configuration.options.missingPoint.isEmpty()) {
      points.remove(configuration.options.missingPoint);
    } 

    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  private AbstractPoint makePoint(String name, PointPointsetModel point) {
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
    deviceState.system.hardware = new Hardware();
    deviceState.pointset.points = new HashMap<>();
    devicePoints.points = new HashMap<>();

    if (configuration.sitePath != null) {
      loadCloudConfig();
      loadDeviceMetadata();
    } else if (pubSubClient != null) {
      pullDeviceMessage();
    }

    info(String.format("Starting pubber %s, serial %s, mac %, gateway %s",
        configuration.deviceId, configuration.serialNo, configuration.macAddr,
        configuration.gatewayId));

    deviceState.system.operational = true;
    deviceState.system.serial_no = configuration.serialNo;
    deviceState.system.hardware.make = "BOS";
    deviceState.system.hardware.model = "pubber";
    deviceState.system.software = new HashMap<>();
    deviceState.system.software.put("firmware", "v1");
    deviceState.system.last_config = new Date(0);

    // Pubber runtime options
    if (!configuration.options.extraField.isEmpty()) {
      devicePoints.extraField = configuration.options.extraField;
    }

    if (!configuration.options.extraPoint.isEmpty()) {
      addPoint(makePoint(configuration.options.extraPoint,
          makePointPointsetModel(true, 50, 50, "Celsius")));
    }

    if (configuration.options.noHardware) {
      deviceState.system.hardware = null;
    }

    markStateDirty(0);
  }

  private void markStateDirty(long delayMs) {
    stateDirty.set(true);
    if (delayMs >= 0) {
      executor.schedule(this::flushDirtyState, delayMs, TimeUnit.MILLISECONDS);
    }
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
        safeSleep(10000);
      }
    }
  }

  private void safeSleep(int duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      throw new RuntimeException("Error sleeping", e);
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
      cancelPeriodicSend();
      messageDelayMs.set(intervalMs);
      startPeriodicSend();
    }
  }

  private synchronized void startPeriodicSend() {
    Preconditions.checkState(scheduledFuture == null);
    int delay = messageDelayMs.get();
    info("Starting executor with send message delay " + delay);
    scheduledFuture = executor
        .scheduleAtFixedRate(this::sendMessages, delay, delay, TimeUnit.MILLISECONDS);
  }

  private synchronized void cancelPeriodicSend() {
    if (scheduledFuture != null) {
      try {
        scheduledFuture.cancel(false);
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
      flushDirtyState();
    } catch (Exception e) {
      error("Fatal error during execution", e);
      terminate();
    }
  }

  private void flushDirtyState() {
    if (stateDirty.get()) {
      publishStateMessage();
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
      markStateDirty(-1);
    }
  }

  private void terminate() {
    try {
      info("Terminating");
      if (mqttPublisher != null) {
        mqttPublisher.close();
        mqttPublisher = null;
      }
      cancelPeriodicSend();
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
    if (!result && mqttPublisher != null) {
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
      String keyDevice =
          configuration.gatewayId != null ? configuration.gatewayId : configuration.deviceId;
      configuration.keyFile = String.format(KEY_SITE_PATH_FORMAT, configuration.sitePath,
          keyDevice, getDeviceKeyPrefix());
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
    entry.message = success ? "success"
        : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    entry.detail = success ? null : exceptionDetail(e);
    entry.level = success ? Level.INFO.value() : Level.ERROR.value();
    return entry;
  }

  private String exceptionDetail(Throwable e) {
    StringBuilder buffer = new StringBuilder();
    while (e != null) {
      buffer.append(e).append(';');
      e = e.getCause();
    }
    return buffer.toString();
  }

  private void gatewayHandler(Config config) {
    info(String.format("%s gateway config %s", getTimestamp(), isoConvert(config.timestamp)));
  }

  private void configHandler(Config config) {
    try {
      info("Config handler");
      File configOut = new File(OUT_DIR, "config.json");
      try {
        OBJECT_MAPPER.writeValue(configOut, config);
        debug("New config:\n" + OBJECT_MAPPER.writeValueAsString(config));
      } catch (Exception e) {
        throw new RuntimeException("While writing config " + configOut.getPath(), e);
      }
      processConfigUpdate(config);
      configLatch.countDown();
      publisherConfigLog("apply", null);
    } catch (Exception e) {
      publisherConfigLog("apply", e);
      trace(stackTraceString(e));
    }
    publishStateMessage();
  }

  private void processConfigUpdate(Config config) {
    final int actualInterval;
    if (config != null) {
      deviceConfig = config;
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
    DiscoveryConfig discoveryConfig = discovery == null ? new DiscoveryConfig() : discovery;
    if (deviceState.discovery == null) {
      deviceState.discovery = new DiscoveryState();
    }
    updateDiscoveryEnumeration(discoveryConfig.enumeration);
    updateDiscoveryScan(discoveryConfig.families);
  }

  private void updateDiscoveryEnumeration(FamilyDiscoveryConfig enumeration) {
    if (enumeration == null) {
      return;
    }
    if (deviceState.discovery.enumeration == null) {
      deviceState.discovery.enumeration = new FamilyDiscoveryState();
      deviceState.discovery.enumeration.generation = DEVICE_START_TIME;
    }
    Date enumerationGeneration = enumeration.generation;
    if (enumerationGeneration == null
        || !enumerationGeneration.after(deviceState.discovery.enumeration.generation)) {
      return;
    }
    deviceState.discovery.enumeration = new FamilyDiscoveryState();
    deviceState.discovery.enumeration.generation = enumerationGeneration;
    info("Discovery enumeration at " + isoConvert(enumerationGeneration));
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.generation = enumerationGeneration;
    discoveryEvent.points = enumeratePoints(configuration.deviceId);
    publishDeviceMessage(discoveryEvent);
  }

  private Map<String, PointEnumerationEvent> enumeratePoints(String deviceId) {
    return allMetadata.get(deviceId).pointset.points.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, this::getPointEnumerationEvent));
  }

  private PointEnumerationEvent getPointEnumerationEvent(
      Map.Entry<String, PointPointsetModel> entry) {
    PointEnumerationEvent pointEnumerationEvent = new PointEnumerationEvent();
    PointPointsetModel model = entry.getValue();
    pointEnumerationEvent.writable = model.writable;
    pointEnumerationEvent.units = model.units;
    pointEnumerationEvent.ref = model.ref;
    return pointEnumerationEvent;
  }

  private void updateDiscoveryScan(HashMap<String, FamilyDiscoveryConfig> familiesRaw) {
    HashMap<String, FamilyDiscoveryConfig> families =
        familiesRaw == null ? new HashMap<>() : familiesRaw;
    if (deviceState.discovery.families == null) {
      deviceState.discovery.families = new HashMap<>();
    }

    deviceState.discovery.families.keySet().forEach(family -> {
      if (!families.containsKey(family)) {
        FamilyDiscoveryState familyDiscoveryState = deviceState.discovery.families.get(family);
        if (familyDiscoveryState.generation != null) {
          info("Clearing scheduled discovery family " + family);
          familyDiscoveryState.generation = null;
          familyDiscoveryState.active = null;
        }
      }
    });
    families.keySet().forEach(family -> {
      FamilyDiscoveryConfig familyDiscoveryConfig = families.get(family);
      Date configGeneration = familyDiscoveryConfig.generation;
      if (configGeneration == null) {
        deviceState.discovery.families.remove(family);
        return;
      }

      Date previousGeneration = getFamilyDiscoveryState(family).generation;
      Date baseGeneration = previousGeneration == null ? DEVICE_START_TIME : previousGeneration;
      final Date startGeneration;
      if (configGeneration.before(baseGeneration)) {
        int interval = getScanInterval(family);
        if (interval > 0) {
          long deltaSec = (baseGeneration.getTime() - configGeneration.getTime() + 999) / 1000;
          long intervals = (deltaSec + interval - 1) / interval;
          startGeneration = Date.from(
              configGeneration.toInstant().plusSeconds(intervals * interval));
        } else {
          return;
        }
      } else {
        startGeneration = configGeneration;
      }

      info("Discovery scan generation " + family + " is " + isoConvert(startGeneration));
      scheduleFuture(startGeneration, () -> checkDiscoveryScan(family, startGeneration));
    });

    if (deviceState.discovery.families.isEmpty()) {
      deviceState.discovery = null;
    }
  }

  private FamilyDiscoveryState getFamilyDiscoveryState(String family) {
    return deviceState.discovery.families.computeIfAbsent(
        family, key -> new FamilyDiscoveryState());
  }

  private long scheduleFuture(Date futureTime, Runnable futureTask) {
    long delay = futureTime.getTime() - new Date().getTime();
    executor.schedule(futureTask, delay, TimeUnit.MILLISECONDS);
    return delay;
  }

  private void checkDiscoveryScan(String family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
      if (familyDiscoveryState.generation == null
          || familyDiscoveryState.generation.before(scanGeneration)) {
        scheduleDiscoveryScan(family, scanGeneration);
      }
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  private void scheduleDiscoveryScan(String family, Date scanGeneration) {
    info("Discovery scan starting " + family + " as " + isoConvert(scanGeneration));
    Date stopTime = Date.from(Instant.now().plusSeconds(SCAN_DURATION_SEC));
    FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
    scheduleFuture(stopTime, () -> discoveryScanComplete(family, scanGeneration));
    familyDiscoveryState.generation = scanGeneration;
    familyDiscoveryState.active = true;
    publishStateMessage();
    Date sendTime = Date.from(Instant.now().plusSeconds(SCAN_DURATION_SEC / 2));
    scheduleFuture(sendTime, () -> sendDiscoveryEvent(family, scanGeneration));
  }

  private void sendDiscoveryEvent(String family, Date scanGeneration) {
    FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
    if (scanGeneration.equals(familyDiscoveryState.generation)
        && familyDiscoveryState.active) {
      AtomicInteger sentEvents = new AtomicInteger();
      allMetadata.forEach((deviceId, targetMetadata) -> {
        FamilyLocalnetModel familyLocalnetModel = getFamilyLocalnetModel(family, targetMetadata);
        if (familyLocalnetModel != null && familyLocalnetModel.id != null) {
          DiscoveryEvent discoveryEvent = new DiscoveryEvent();
          discoveryEvent.generation = scanGeneration;
          discoveryEvent.scan_family = family;
          discoveryEvent.families = targetMetadata.localnet.families.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, this::eventForTarget));
          discoveryEvent.families.computeIfAbsent("iot",
              key -> new FamilyDiscoveryEvent()).id = deviceId;
          if (isTrue(() -> deviceConfig.discovery.families.get(family).enumerate)) {
            discoveryEvent.points = enumeratePoints(deviceId);
          }
          publishDeviceMessage(discoveryEvent);
          sentEvents.incrementAndGet();
        }
      });
      info("Sent " + sentEvents.get() + " discovery events from " + family + " for "
          + scanGeneration);
    }
  }

  private boolean isTrue(Supplier<Boolean> target) {
    try {
      return target.get();
    } catch (Exception e) {
      return false;
    }
  }

  private FamilyDiscoveryEvent eventForTarget(Map.Entry<String, FamilyLocalnetModel> target) {
    FamilyDiscoveryEvent event = new FamilyDiscoveryEvent();
    event.id = target.getValue().id;
    return event;
  }

  private FamilyLocalnetModel getFamilyLocalnetModel(String family, Metadata targetMetadata) {
    try {
      return targetMetadata.localnet.families.get(family);
    } catch (Exception e) {
      return null;
    }
  }

  private void discoveryScanComplete(String family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
      if (scanGeneration.equals(familyDiscoveryState.generation)) {
        int interval = getScanInterval(family);
        if (interval > 0) {
          Date newGeneration = Date.from(scanGeneration.toInstant().plusSeconds(interval));
          scheduleFuture(newGeneration, () -> checkDiscoveryScan(family, newGeneration));
        } else {
          info("Discovery scan stopping " + family + " from " + isoConvert(scanGeneration));
          familyDiscoveryState.active = false;
          publishStateMessage();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan complete", e);
    }
  }

  private int getScanInterval(String family) {
    try {
      return deviceConfig.discovery.families.get(family).scan_interval_sec;
    } catch (Exception e) {
      return 0;
    }
  }

  private String stackTraceString(Throwable e) {
    OutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(outputStream)) {
      e.printStackTrace(ps);
    }
    return outputStream.toString();
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
      if (timestamp == null) {
        return "null";
      }
      String dateString = OBJECT_MAPPER.writeValueAsString(timestamp);
      // Strip off the leading and trailing quotes from the JSON string-as-string representation.
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  private void updatePointsetConfig(PointsetConfig pointsetConfig) {
    PointsetConfig useConfig = pointsetConfig != null ? pointsetConfig : new PointsetConfig();
    Map<String, PointPointsetConfig> points =
        useConfig.points != null ? useConfig.points : new HashMap<>();
    allPoints.forEach(point ->
        updatePointConfig(point, points.get(point.getName())));
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
    if ((++deviceMessageCount) % MESSAGE_REPORT_INTERVAL == 0) {
      info(String.format("%s sending test message #%d", getTimestamp(), deviceMessageCount));
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
    systemEvent.logentries.add(report);
    publishDeviceMessage(systemEvent);
  }

  private void publishStateMessage() {
    long delay = lastStateTimeMs + STATE_THROTTLE_MS - System.currentTimeMillis();
    if (delay > 0) {
      warn(String.format("defer state update %d", delay));
      markStateDirty(delay);
      return;
    }
    deviceState.timestamp = new Date();
    info(String.format("update state %s last_config %s", isoConvert(deviceState.timestamp),
        isoConvert(deviceState.system.last_config)));
    try {
      debug("State update:\n" + OBJECT_MAPPER.writeValueAsString(deviceState));
    } catch (Exception e) {
      throw new RuntimeException("While converting new device state", e);
    }
    stateDirty.set(false);
    // TODO: Make this block until the callback is actually called.
    lastStateTimeMs = System.currentTimeMillis() + STATE_THROTTLE_MS;
    publishDeviceMessage(deviceState, () -> {
      lastStateTimeMs = System.currentTimeMillis();
    });
  }

  private void publishDeviceMessage(Object message) {
    publishDeviceMessage(message, null);
  }

  private void publishDeviceMessage(Object message, Runnable callback) {
    if (mqttPublisher == null) {
      warn("Ignoring publish message b/c connection is shutdown");
      return;
    }
    String topic = MESSAGE_TOPIC_MAP.get(message.getClass());
    if (topic == null) {
      error("Unknown message class " + message.getClass());
      return;
    }
    augmentDeviceMessage(message);
    mqttPublisher.publish(configuration.deviceId, topic, message, callback);

    String fileName = topic.replace("/", "_") + ".json";
    File stateOut = new File(OUT_DIR, fileName);
    try {
      OBJECT_MAPPER.writeValue(stateOut, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + stateOut.getAbsolutePath(), e);
    }
  }

  private void augmentDeviceMessage(Object message) {
    try {
      Field version = message.getClass().getField("version");
      assert version.get(message) == null;
      version.set(message, UDMI_VERSION);
      Field timestamp = message.getClass().getField("timestamp");
      assert timestamp.get(message) == null;
      timestamp.set(message, new Date());
    } catch (Exception e) {
      throw new RuntimeException("While augmenting device message", e);
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
    } catch (Exception e) {
      mqttPublisher = null;
      localLog("Error publishing log message: " + e, Level.ERROR, timestamp);
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

  private void trace(String message) {
    cloudLog(message, Level.TRACE);
  }

  private void debug(String message) {
    cloudLog(message, Level.DEBUG);
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
    String longMessage = message + ": " + e.getMessage();
    cloudLog(longMessage, Level.ERROR);
    trace(stackTraceString(e));
  }

  static class ExtraPointsetEvent extends PointsetEvent {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }
}
