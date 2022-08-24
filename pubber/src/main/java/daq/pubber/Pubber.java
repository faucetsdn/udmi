package daq.pubber;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;
import static udmi.schema.EndpointConfiguration.Protocol.MQTT;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.SiteModel;
import daq.pubber.MqttPublisher.ClientInfo;
import daq.pubber.MqttPublisher.PublisherException;
import daq.pubber.PubSubClient.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobBlobsetState;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.BlobsetState;
import udmi.schema.Category;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Config;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Entry;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryEvent;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointEnumerationEvent;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.schema.State;
import udmi.schema.SystemConfig;
import udmi.schema.SystemConfig.SystemMode;
import udmi.schema.SystemEvent;
import udmi.schema.SystemHardware;
import udmi.schema.SystemState;

/**
 * IoT Core UDMI Device Emulator.
 */
public class Pubber {

  public static final int SCAN_DURATION_SEC = 10;
  public static final String PUBBER_OUT = "pubber/out";
  private static final String UDMI_VERSION = "1.3.14";
  private static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String HOSTNAME = System.getenv("HOSTNAME");
  private static final String CONFIG_TOPIC = "config";
  private static final String ERROR_TOPIC = "errors";
  private static final int MIN_REPORT_MS = 200;
  private static final int DEFAULT_REPORT_SEC = 10;
  private static final int CONFIG_WAIT_TIME_SEC = 10;
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String PUBSUB_SITE = "PubSub";
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
  private static final Map<Level, Consumer<String>> LOG_MAP =
      ImmutableMap.<Level, Consumer<String>>builder()
          .put(Level.TRACE, LOG::info) // TODO: Make debug/trace programmatically visible.
          .put(Level.DEBUG, LOG::info)
          .put(Level.INFO, LOG::info)
          .put(Level.NOTICE, LOG::info)
          .put(Level.WARNING, LOG::warn)
          .put(Level.ERROR, LOG::error)
          .build();
  private static final Map<String, PointPointsetModel> DEFAULT_POINTS = ImmutableMap.of(
      "recalcitrant_angle", makePointPointsetModel(true, 50, 50, "Celsius"),
      "faulty_finding", makePointPointsetModel(true, 40, 0, "deg"),
      "superimposition_reading", makePointPointsetModel(false)
  );
  private static final Date DEVICE_START_TIME = getRoundedStartTime();
  private static final int RESTART_EXIT_CODE = 192; // After exit, wrapper script should restart.
  private static final int SHUTDOWN_EXIT_CODE = 193; // After exit, do not restart.
  private static final Map<String, AtomicInteger> MESSAGE_COUNTS = new HashMap<>();
  private static final int CONNECT_RETRIES = 10;
  private static final AtomicInteger retriesRemaining = new AtomicInteger(CONNECT_RETRIES);
  private static final long RESTART_DELAY_MS = 1000;
  private final File outDir;
  private final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  private final PubberConfiguration configuration;
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_SEC * 1000);
  private final CountDownLatch configLatch = new CountDownLatch(1);
  private final State deviceState = new State();
  private final ExtraPointsetEvent devicePoints = new ExtraPointsetEvent();
  private final Set<AbstractPoint> allPoints = new HashSet<>();
  private final AtomicBoolean stateDirty = new AtomicBoolean();
  private final Semaphore stateLock = new Semaphore(1);
  private final String projectId;
  private final String deviceId;
  private Config deviceConfig = new Config();
  private int deviceMessageCount = -1;
  private MqttPublisher mqttPublisher;
  private ScheduledFuture<?> periodicSender;
  private long lastStateTimeMs;
  private PubSubClient pubSubClient;
  private Function<String, Boolean> connectionDone;
  private boolean publishingLog;
  private String appliedEndpoint;
  private String workingEndpoint;
  private String attemptedEndpoint;
  private EndpointConfiguration extractedEndpoint;
  private SiteModel siteModel;
  private PrintStream logPrintWriter;

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public Pubber(String configPath) {
    File configFile = new File(configPath);
    try {
      configuration = sanitizeConfiguration(
          OBJECT_MAPPER.readValue(configFile, PubberConfiguration.class));
      checkArgument(MQTT.equals(configuration.endpoint.protocol), "protocol mismatch");
      ClientInfo clientInfo = MqttPublisher.parseClientId(configuration.endpoint.client_id);
      projectId = clientInfo.projectId;
      deviceId = clientInfo.deviceId;
      outDir = new File(PUBBER_OUT);
    } catch (Exception e) {
      throw new RuntimeException("While configuring instance from " + configFile.getAbsolutePath(),
          e);
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
    this.projectId = projectId;
    this.deviceId = deviceId;
    outDir = new File(PUBBER_OUT + "/" + serialNo);
    configuration = sanitizeConfiguration(new PubberConfiguration());
    configuration.deviceId = deviceId;
    configuration.serialNo = serialNo;
    if (PUBSUB_SITE.equals(sitePath)) {
      pubSubClient = new PubSubClient(projectId, deviceId);
    } else {
      configuration.sitePath = sitePath;
    }
  }

  private static Date getRoundedStartTime() {
    long timestamp = new Date().getTime();
    // Remove ms so that rounded conversions preserve equality.
    return new Date(timestamp - (timestamp % 1000));
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
    Pubber pubber = null;
    try {
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
        LOG.info(String.format("Connection closed/finished for %s", deviceId));
        return true;
      });
    } catch (Exception e) {
      new RuntimeException("While starting singular pubber", e).printStackTrace();
      if (pubber != null) {
        pubber.terminate();
      }
    }
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
        LOG.error("Connection terminated, restarting listener");
        startFeedListener(projectId, siteName, feedName, serialNo);
        return false;
      });
    } catch (Exception e) {
      LOG.error("Exception starting instance " + serialNo, e);
      startFeedListener(projectId, siteName, feedName, serialNo);
    }
  }

  private static PubberConfiguration sanitizeConfiguration(PubberConfiguration configuration) {
    if (configuration.options == null) {
      configuration.options = new PubberOptions();
    }
    return configuration;
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

  private void initializeDevice() {
    deviceState.system = new SystemState();
    deviceState.system.hardware = new SystemHardware();
    deviceState.system.last_start = DEVICE_START_TIME;
    deviceState.pointset = new PointsetState();
    deviceState.pointset.points = new HashMap<>();
    devicePoints.points = new HashMap<>();

    if (configuration.sitePath != null) {
      siteModel = new SiteModel(configuration.sitePath);
      siteModel.initialize(projectId, deviceId);
      configuration.endpoint = siteModel.getEndpointConfig();
      processDeviceMetadata(siteModel.getMetadata(configuration.deviceId));
    } else if (pubSubClient != null) {
      pullDeviceMessage();
    }

    info(String.format("Starting pubber %s, serial %s, mac %s, gateway %s, options %s",
        configuration.deviceId, configuration.serialNo, configuration.macAddr,
        configuration.gatewayId, GeneralUtils.optionsString(configuration.options)));

    deviceState.system.operational = true;
    deviceState.system.mode = SystemMode.INITIAL;
    deviceState.system.serial_no = configuration.serialNo;
    deviceState.system.hardware.make = "BOS";
    deviceState.system.hardware.model = "pubber";
    deviceState.system.software = new HashMap<>();
    deviceState.system.software.put("firmware", "v1");
    deviceState.system.last_config = new Date(0);

    // Pubber runtime options
    if (configuration.options.extraField != null) {
      devicePoints.extraField = configuration.options.extraField;
    }

    if (configuration.options.extraPoint != null) {
      addPoint(makePoint(configuration.options.extraPoint,
          makePointPointsetModel(true, 50, 50, "Celsius")));
    }

    if (configuration.options.noHardware != null && configuration.options.noHardware) {
      deviceState.system.hardware = null;
    }

    markStateDirty(0);
  }

  private void markStateDirty(long delayMs) {
    stateDirty.set(true);
    if (delayMs >= 0) {
      try {
        executor.schedule(this::flushDirtyState, delayMs, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        System.err.println("Rejecting state publish after " + delayMs + " " + e);
      }
    }
  }

  private void pullDeviceMessage() {
    while (true) {
      try {
        info("Waiting for swarm configuration");
        Envelope attributes = new Envelope();
        Bundle pull = pubSubClient.pull();
        attributes.subFolder = SubFolder.valueOf(pull.attributes.get("subFolder"));
        if (!SubFolder.SWARM.equals(attributes.subFolder)) {
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
        safeSleep(CONFIG_WAIT_TIME_SEC);
      }
    }
  }

  private void safeSleep(long durationMs) {
    try {
      Thread.sleep(durationMs);
    } catch (InterruptedException e) {
      throw new RuntimeException("Error sleeping", e);
    }
  }

  private void processSwarmConfig(SwarmMessage swarm, Envelope attributes) {
    configuration.deviceId = checkNotNull(attributes.deviceId, "deviceId");
    configuration.keyBytes = Base64.getDecoder()
        .decode(checkNotNull(swarm.key_base64, "key_base64"));
    configuration.endpoint = SiteModel.makeEndpointConfig(attributes);
    processDeviceMetadata(
        checkNotNull(swarm.device_metadata, "device_metadata"));
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata.cloud != null) {
      configuration.algorithm = metadata.cloud.auth_type.value();
      info("Configuring with key type " + configuration.algorithm);
    }

    if (metadata.gateway != null) {
      configuration.gatewayId = metadata.gateway.gateway_id;
      if (configuration.gatewayId != null) {
        Auth_type authType = siteModel.getAuthType(configuration.gatewayId);
        if (authType != null) {
          configuration.algorithm = authType.value();
        }
      }
    }

    Map<String, PointPointsetModel> points =
        metadata.pointset == null ? DEFAULT_POINTS : metadata.pointset.points;

    if (configuration.options.missingPoint != null) {
      if (points.containsKey(configuration.options.missingPoint)) {
        points.remove(configuration.options.missingPoint);
      } else {
        throw new RuntimeException("missingPoint not in pointset");
      }
    }

    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  private synchronized void maybeRestartExecutor(int intervalMs) {
    if (periodicSender == null || intervalMs != messageDelayMs.get()) {
      cancelPeriodicSend();
      messageDelayMs.set(intervalMs);
      startPeriodicSend();
    }
  }

  private synchronized void startPeriodicSend() {
    Preconditions.checkState(periodicSender == null);
    int delay = messageDelayMs.get();
    info("Starting executor with send message delay " + delay);
    periodicSender = executor.scheduleAtFixedRate(this::sendMessages, delay, delay,
        TimeUnit.MILLISECONDS);
  }

  private synchronized void cancelPeriodicSend() {
    if (periodicSender != null) {
      try {
        periodicSender.cancel(false);
      } catch (Exception e) {
        throw new RuntimeException("While cancelling executor", e);
      } finally {
        periodicSender = null;
      }
    }
  }

  private void sendMessages() {
    try {
      updatePoints();
      sendDeviceMessage();
      deferredConfigActions();
      flushDirtyState();
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  private void deferredConfigActions() {
    maybeRedirectEndpoint();
    maybeRestartSystem();
  }

  private void maybeRestartSystem() {
    SystemConfig systemConfig = deviceConfig.system;
    if (systemConfig == null) {
      return;
    }
    if (SystemMode.ACTIVE.equals(deviceState.system.mode)
        && SystemMode.RESTART.equals(systemConfig.mode)) {
      restartSystem(true);
    }
    if (SystemMode.ACTIVE.equals(systemConfig.mode)) {
      deviceState.system.mode = SystemMode.ACTIVE;
    }
    if (systemConfig.last_start != null && DEVICE_START_TIME.before(systemConfig.last_start)) {
      System.err.printf("Device start time %s before last config start %s, terminating.",
          isoConvert(DEVICE_START_TIME), isoConvert(systemConfig.last_start));
      restartSystem(false);
    }
  }

  private void restartSystem(boolean restart) {
    deviceState.system.mode = restart ? SystemMode.RESTART : SystemMode.SHUTDOWN;
    publishSynchronousState();
    error("Stopping system with extreme prejudice, restart " + restart);
    System.exit(restart ? RESTART_EXIT_CODE : SHUTDOWN_EXIT_CODE);
  }

  private void flushDirtyState() {
    if (stateDirty.get()) {
      publishAsynchronousState();
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

  private void captureExceptions(String action, Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      error(action, e);
    }
  }

  private void terminate() {
    warn("Terminating");
    deviceState.system.mode = SystemMode.SHUTDOWN;
    captureExceptions("publishing shutdown state", this::publishSynchronousState);
    stop();
    captureExceptions("executor flush", this::stopExecutor);
  }

  private void stopExecutor() {
    try {
      cancelPeriodicSend();
      executor.shutdown();
      if (!executor.awaitTermination(CONFIG_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException("Failed to shutdown scheduled tasks");
      }
    } catch (Exception e) {
      throw new RuntimeException("While stopping executor", e);
    }
  }

  private void startConnection(Function<String, Boolean> connectionDone) {
    try {
      this.connectionDone = connectionDone;
      while (retriesRemaining.getAndDecrement() > 0) {
        if (attemptConnection()) {
          return;
        }
      }
      throw new RuntimeException("Failed connection attempt after retries");
    } catch (Exception e) {
      stop();
      throw new RuntimeException("While attempting to start connection", e);
    }
  }

  private boolean attemptConnection() {
    try {
      if (mqttPublisher == null) {
        throw new RuntimeException("Mqtt publisher not initialized");
      }
      connect();
      if (configLatch.await(CONFIG_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        return true;
      }
      error("Configuration sync failed after " + CONFIG_WAIT_TIME_SEC);
    } catch (Exception e) {
      error("While waiting for connection start", e);
    }
    error("Attempt failed, retries remaining: " + retriesRemaining.get());
    safeSleep(RESTART_DELAY_MS);
    return false;
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
    try {
      initializeDevice();

      try {
        outDir.mkdirs();
        File logOut = new File(outDir, traceTimestamp("pubber") + ".log");
        logPrintWriter = new PrintStream(logOut);
        logPrintWriter.println("Pubber log started at " + getTimestamp());
      } catch (Exception e) {
        throw new RuntimeException("While initializing out dir " + outDir.getAbsolutePath(), e);
      }

      initializeMqtt();
    } catch (Exception e) {
      terminate();
      throw new RuntimeException("While initializing main pubber class", e);
    }
  }

  private void stop() {
    captureExceptions("disconnecting mqtt", this::disconnectMqtt);
    captureExceptions("closing log", this::closeLogWriter);
    captureExceptions("stopping periodic send", this::cancelPeriodicSend);
  }

  private void closeLogWriter() {
    if (logPrintWriter != null) {
      logPrintWriter.close();
      logPrintWriter = null;
    }
  }

  private void disconnectMqtt() {
    if (mqttPublisher != null) {
      captureExceptions("closing mqtt publisher", mqttPublisher::close);
      mqttPublisher = null;
    }
  }

  private void initializeMqtt() {
    checkNotNull(configuration.deviceId, "configuration deviceId not defined");
    if (siteModel != null && configuration.keyFile != null) {
      configuration.keyFile = siteModel.getDeviceKeyFile(configuration.deviceId);
    }
    Preconditions.checkState(mqttPublisher == null, "mqttPublisher already defined");
    ensureKeyBytes();
    appliedEndpoint = toJson(configuration.endpoint);
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

  private String toJson(Object target) {
    try {
      if (target == null) {
        return null;
      }
      return OBJECT_MAPPER.writeValueAsString(target);
    } catch (Exception e) {
      throw new RuntimeException("While converting object to string", e);
    }
  }

  private void ensureKeyBytes() {
    if (configuration.keyBytes != null) {
      return;
    }
    checkNotNull(configuration.keyFile, "configuration keyFile not defined");
    info("Loading device key bytes from " + configuration.keyFile);
    configuration.keyBytes = getFileBytes(configuration.keyFile);
    configuration.keyFile = null;
  }

  private void connect() {
    try {
      mqttPublisher.connect(configuration.deviceId);
      info("Connection complete.");
      workingEndpoint = toJson(configuration.endpoint);
    } catch (Exception e) {
      throw new RuntimeException("Connection error", e);
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
      error("Connection closed, attempting reconnect...");
      if (connectionDone != null) {
        while (retriesRemaining.getAndDecrement() > 0) {
          if (attemptConnection()) {
            return;
          }
        }
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
    Entry report = entryFromException(category, cause);
    localLog(report);
    publishLogMessage(report);
    // TODO: Replace this with a heap so only the highest-priority status is reported.
    deviceState.system.status = shouldLogLevel(report.level) ? report : null;
    publishAsynchronousState();
    if (cause != null && configLatch.getCount() > 0) {
      configLatch.countDown();
      warn("Released startup latch because reported error");
    }
  }

  private boolean shouldLogLevel(int level) {
    Integer minLoglevel = deviceConfig.system == null ? null : deviceConfig.system.min_loglevel;
    return level >= (minLoglevel == null ? Level.INFO.value() : minLoglevel);
  }

  private Entry entryFromException(String category, Throwable e) {
    boolean success = e == null;
    Entry entry = new Entry();
    entry.category = category;
    entry.timestamp = new Date();
    entry.message = success ? "success"
        : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    entry.detail = success ? null : exceptionDetail(e);
    Level successLevel = Category.LEVEL.computeIfAbsent(category, key -> Level.INFO);
    entry.level = (success ? successLevel : Level.ERROR).value();
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
      File configOut = new File(outDir, traceTimestamp("config") + ".json");
      try {
        OBJECT_MAPPER.writeValue(configOut, config);
        debug(String.format("Config update%s", getTestingTag(config)), toJson(config));
      } catch (Exception e) {
        throw new RuntimeException("While writing config " + configOut.getPath(), e);
      }
      processConfigUpdate(config);
      configLatch.countDown();
      publisherConfigLog("apply", null);
    } catch (Exception e) {
      publisherConfigLog("apply", e);
    }
    publishAsynchronousState();
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
      extractEndpointBlobConfig();
    } else {
      info(getTimestamp() + " defaulting empty config");
      actualInterval = DEFAULT_REPORT_SEC * 1000;
    }
    maybeRestartExecutor(actualInterval);
  }

  private void extractEndpointBlobConfig() {
    if (deviceConfig.blobset == null) {
      deviceState.blobset = null;
      return;
    }
    try {
      String iotConfig = extractConfigBlob(IOT_ENDPOINT_CONFIG.value());
      if (iotConfig == null) {
        removeBlobsetBlobState(IOT_ENDPOINT_CONFIG);
        return;
      }
      extractedEndpoint = OBJECT_MAPPER.readValue(iotConfig, EndpointConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException("While extracting endpoint blob config", e);
    }
  }

  private void removeBlobsetBlobState(SystemBlobsets blobId) {
    if (deviceState.blobset == null) {
      return;
    }
    deviceState.blobset.blobs.remove(blobId.value());
  }

  private void maybeRedirectEndpoint() {
    String redirectRegistry = configuration.options.redirectRegistry;
    String currentSignature = toJson(configuration.endpoint);
    String extractedSignature =
        redirectRegistry == null ? toJson(extractedEndpoint) : redirectedEndpoint(redirectRegistry);

    if (extractedSignature == null || extractedSignature.equals(
        currentSignature) || extractedSignature.equals(attemptedEndpoint)) {
      return; // No need to redirect anything!
    }

    info("New config blob endpoint detected");
    BlobBlobsetState endpointState = ensureBlobsetState(IOT_ENDPOINT_CONFIG);

    try {
      endpointState.phase = BlobPhase.APPLY;
      endpointState.status = null;
      publishSynchronousState();
      attemptedEndpoint = extractedSignature;
      resetConnection(extractedSignature);
      endpointState.phase = BlobPhase.FINAL;
      appliedEndpoint = null;
    } catch (Exception e) {
      try {
        error("Reconfigure failed, attempting connection to last working endpoint", e);
        endpointState.status = exceptionStatus(e, Category.BLOBSET_BLOB_APPLY);
        resetConnection(workingEndpoint);
        publishAsynchronousState();
        notice("Endpoint connection restored to last working endpoint");
      } catch (Exception e2) {
        throw new RuntimeException("While restoring working endpoint", e2);
      }
      error("While redirecting connection endpoint", e);
    }
  }

  private String redirectedEndpoint(String redirectRegistry) {
    try {
      EndpointConfiguration endpoint = OBJECT_MAPPER.readValue(toJson(configuration.endpoint),
          EndpointConfiguration.class);
      endpoint.client_id = getClientId(redirectRegistry);
      return toJson(endpoint);
    } catch (Exception e) {
      throw new RuntimeException("While getting redirected endpoint");
    }
  }

  private void resetConnection(String targetEndpoint) {
    try {
      configuration.endpoint = OBJECT_MAPPER.readValue(targetEndpoint,
          EndpointConfiguration.class);
      disconnectMqtt();
      initializeMqtt();
      retriesRemaining.set(CONNECT_RETRIES);
      startConnection(connectionDone);
    } catch (Exception e) {
      stop();
      throw new RuntimeException("While resetting connection", e);
    }
  }

  private Entry exceptionStatus(Exception e, String category) {
    Entry entry = new Entry();
    entry.message = e.getMessage();
    entry.detail = stackTraceString(e);
    entry.category = category;
    entry.level = Level.ERROR.value();
    return entry;
  }

  private BlobBlobsetState ensureBlobsetState(SystemBlobsets iotEndpointConfig) {
    deviceState.blobset = ofNullable(deviceState.blobset).orElseGet(BlobsetState::new);
    deviceState.blobset.blobs = ofNullable(deviceState.blobset.blobs).orElseGet(HashMap::new);
    return deviceState.blobset.blobs.computeIfAbsent(iotEndpointConfig.value(),
        key -> new BlobBlobsetState());
  }

  private String getClientId(String forRegistry) {
    String cloudRegion = MqttPublisher.parseClientId(configuration.endpoint.client_id).cloudRegion;
    return MqttPublisher.getClientId(projectId, cloudRegion, forRegistry, deviceId);
  }

  private String extractConfigBlob(String blobName) {
    try {
      if (deviceConfig == null || deviceConfig.blobset == null
          || deviceConfig.blobset.blobs == null) {
        return null;
      }
      BlobBlobsetConfig blobBlobsetConfig = deviceConfig.blobset.blobs.get(blobName);
      if (blobBlobsetConfig != null && BlobPhase.FINAL.equals(blobBlobsetConfig.phase)
          && blobBlobsetConfig.base64 != null) {
        return new String(Base64.getDecoder().decode(blobBlobsetConfig.base64));
      }
      return null;
    } catch (Exception e) {
      throw new RuntimeException("While extracting config blob " + blobName, e);
    }
  }

  private void updateDiscoveryConfig(DiscoveryConfig discovery) {
    DiscoveryConfig discoveryConfig = discovery == null ? new DiscoveryConfig() : discovery;
    if (deviceState.discovery == null) {
      deviceState.discovery = new DiscoveryState();
    }
    updateDiscoveryEnumeration(discoveryConfig.enumeration);
    updateDiscoveryScan(discoveryConfig.families);
    if (deviceState.discovery.families == null && deviceState.discovery.enumeration == null) {
      deviceState.discovery = null;
    }
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
    discoveryEvent.uniqs = enumeratePoints(configuration.deviceId);
    publishDeviceMessage(discoveryEvent);
  }

  private Map<String, PointEnumerationEvent> enumeratePoints(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points.entrySet().stream().collect(
        Collectors.toMap(this::getPointUniqKey, this::getPointEnumerationEvent));
  }

  private String getPointUniqKey(Map.Entry<String, PointPointsetModel> entry) {
    return String.format("%08x", entry.getKey().hashCode());
  }

  private PointEnumerationEvent getPointEnumerationEvent(
      Map.Entry<String, PointPointsetModel> entry) {
    PointEnumerationEvent pointEnumerationEvent = new PointEnumerationEvent();
    PointPointsetModel model = entry.getValue();
    pointEnumerationEvent.writable = model.writable;
    pointEnumerationEvent.units = model.units;
    pointEnumerationEvent.ref = model.ref;
    pointEnumerationEvent.name = entry.getKey();
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
      deviceState.discovery.families = null;
    }
  }

  private FamilyDiscoveryState getFamilyDiscoveryState(String family) {
    return deviceState.discovery.families.computeIfAbsent(
        family, key -> new FamilyDiscoveryState());
  }

  private long scheduleFuture(Date futureTime, Runnable futureTask) {
    if (executor.isShutdown() || executor.isTerminated()) {
      throw new RuntimeException("Executor shutdown/terminated, not scheduling");
    }
    long delay = futureTime.getTime() - new Date().getTime();
    debug(String.format("Scheduling future in %dms", delay));
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
    publishAsynchronousState();
    Date sendTime = Date.from(Instant.now().plusSeconds(SCAN_DURATION_SEC / 2));
    scheduleFuture(sendTime, () -> sendDiscoveryEvent(family, scanGeneration));
  }

  private void sendDiscoveryEvent(String family, Date scanGeneration) {
    FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
    if (scanGeneration.equals(familyDiscoveryState.generation)
        && familyDiscoveryState.active) {
      AtomicInteger sentEvents = new AtomicInteger();
      siteModel.forEachDevice((deviceId, targetMetadata) -> {
        FamilyLocalnetModel familyLocalnetModel = getFamilyLocalnetModel(family, targetMetadata);
        if (familyLocalnetModel != null && familyLocalnetModel.id != null) {
          DiscoveryEvent discoveryEvent = new DiscoveryEvent();
          discoveryEvent.generation = scanGeneration;
          discoveryEvent.scan_family = family;
          discoveryEvent.scan_id = deviceId;
          discoveryEvent.families = targetMetadata.localnet.families.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, this::eventForTarget));
          discoveryEvent.families.computeIfAbsent("iot",
              key -> new FamilyDiscoveryEvent()).id = deviceId;
          if (isTrue(() -> deviceConfig.discovery.families.get(family).enumerate)) {
            discoveryEvent.uniqs = enumeratePoints(deviceId);
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
          publishAsynchronousState();
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
      String dateString = toJson(timestamp);
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

  private void pubberLogMessage(String logMessage, Level level, String timestamp,
      String detail) {
    Entry logEntry = new Entry();
    logEntry.category = "pubber";
    logEntry.level = level.value();
    logEntry.timestamp = isoConvert(timestamp);
    logEntry.message = logMessage;
    logEntry.detail = detail;
    publishLogMessage(logEntry);
  }

  private void publishLogMessage(Entry report) {
    if (shouldLogLevel(report.level)) {
      SystemEvent systemEvent = new SystemEvent();
      systemEvent.logentries.add(report);
      publishDeviceMessage(systemEvent);
    }
  }

  private void publishAsynchronousState() {
    if (stateLock.tryAcquire()) {
      try {
        long delay = lastStateTimeMs + STATE_THROTTLE_MS - System.currentTimeMillis();
        warn(String.format("State update defer %dms", delay));
        if (delay > 0) {
          markStateDirty(delay);
        } else {
          publishStateRaw();
        }
      } finally {
        stateLock.release();
      }
    } else {
      markStateDirty(-1);
    }
  }

  private void publishSynchronousState() {
    try {
      stateLock.acquire();
      publishStateRaw();
    } catch (Exception e) {
      throw new RuntimeException("While sending synchronous state", e);
    } finally {
      stateLock.release();
    }
  }

  private void publishStateRaw() {
    long delay = lastStateTimeMs + STATE_THROTTLE_MS - System.currentTimeMillis();
    if (delay > 0) {
      warn(String.format("State update delay %dms", delay));
      safeSleep(delay);
    }

    CountDownLatch latch = new CountDownLatch(1);
    publishStateMessage(latch);

    try {
      info("Waiting for synchronous state send...");
      if (!latch.await(CONFIG_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        error("Timeout waiting for synchronous state send");
      }
    } catch (Exception e) {
      error("Exception while waiting for synchronous state send", e);
    }
  }

  private void publishStateMessage(CountDownLatch latch) {
    deviceState.timestamp = new Date();
    info(String.format("update state %s last_config %s", isoConvert(deviceState.timestamp),
        isoConvert(deviceState.system.last_config)));
    try {
      debug(String.format("State update%s", getTestingTag(deviceConfig)), toJson(deviceState));
    } catch (Exception e) {
      throw new RuntimeException("While converting new device state", e);
    }
    stateDirty.set(false);
    lastStateTimeMs = System.currentTimeMillis();
    CountDownLatch useLatch = latch == null ? new CountDownLatch(1) : latch;
    publishDeviceMessage(deviceState, () -> {
      lastStateTimeMs = System.currentTimeMillis();
      useLatch.countDown();
    });
    try {
      if (!useLatch.await(CONFIG_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException("Timeout waiting for state send");
      }
    } catch (Exception e) {
      throw new RuntimeException("While waiting for state send latch", e);
    }
  }

  private void publishDeviceMessage(Object message) {
    publishDeviceMessage(message, null);
  }

  private void publishDeviceMessage(Object message, Runnable callback) {
    String topic = MESSAGE_TOPIC_MAP.get(message.getClass());
    if (topic == null) {
      error("Unknown message class " + message.getClass());
      return;
    }

    augmentDeviceMessage(message);
    mqttPublisher.publish(configuration.deviceId, topic, message, callback);
    String messageBase = topic.replace("/", "_");
    String fileName = traceTimestamp(messageBase) + ".json";
    File messageOut = new File(outDir, fileName);
    try {
      OBJECT_MAPPER.writeValue(messageOut, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + messageOut.getAbsolutePath(), e);
    }
  }

  private String traceTimestamp(String messageBase) {
    int serial = MESSAGE_COUNTS.computeIfAbsent(messageBase, key -> new AtomicInteger())
        .incrementAndGet();
    String timestamp = getTimestamp().replace("Z", String.format(".%03dZ", serial));
    return messageBase + (TRUE.equals(configuration.options.messageTrace) ? ("_" + timestamp) : "");
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

  private boolean publisherActive() {
    return mqttPublisher != null && mqttPublisher.isActive();
  }

  private void cloudLog(String message, Level level) {
    cloudLog(message, level, null);
  }

  private void cloudLog(String message, Level level, String detail) {
    String timestamp = getTimestamp();
    localLog(message, level, timestamp, detail);

    if (publishingLog || !publisherActive()) {
      return;
    }

    try {
      publishingLog = true;
      pubberLogMessage(message, level, timestamp, detail);
    } catch (Exception e) {
      localLog("Error publishing log message: " + e, Level.ERROR, timestamp, null);
    } finally {
      publishingLog = false;
    }
  }

  private String getTestingTag(Config config) {
    return config.system == null || config.system.testing == null
        || config.system.testing.sequence_name == null ? ""
        : String.format(" (%s)", config.system.testing.sequence_name);
  }

  private void localLog(Entry entry) {
    String message = String.format("Entry %s %s %s %s%s", Level.fromValue(entry.level).name(),
        entry.category, entry.message, isoConvert(entry.timestamp), getTestingTag(deviceConfig));
    localLog(message, Level.fromValue(entry.level), isoConvert(entry.timestamp), null);
  }

  private void localLog(String message, Level level, String timestamp, String detail) {
    String detailPostfix = detail == null ? "" : ":\n" + detail;
    String logMessage = String.format("%s %s%s", timestamp, message, detailPostfix);
    LOG_MAP.get(level).accept(logMessage);
    try {
      if (logPrintWriter != null) {
        logPrintWriter.println(logMessage);
        logPrintWriter.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException("While writing log output file", e);
    }
  }

  private void trace(String message) {
    cloudLog(message, Level.TRACE);
  }

  private void debug(String message) {
    cloudLog(message, Level.DEBUG);
  }

  private void debug(String message, String detail) {
    cloudLog(message, Level.DEBUG, detail);
  }

  private void info(String message) {
    cloudLog(message, Level.INFO);
  }

  private void notice(String message) {
    cloudLog(message, Level.NOTICE);
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
  }

  static class ExtraPointsetEvent extends PointsetEvent {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }
}
