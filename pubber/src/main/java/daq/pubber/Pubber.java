package daq.pubber;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToFalse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.fromJsonFile;
import static com.google.udmi.util.GeneralUtils.fromJsonFileStrict;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.getFileBytes;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isGetTrue;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.optionsString;
import static com.google.udmi.util.GeneralUtils.setClockSkew;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.GeneralUtils.toJsonFile;
import static com.google.udmi.util.GeneralUtils.toJsonString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.parseJson;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static daq.pubber.MqttDevice.CONFIG_TOPIC;
import static daq.pubber.MqttDevice.ERRORS_TOPIC;
import static daq.pubber.MqttDevice.STATE_TOPIC;
import static daq.pubber.MqttPublisher.DEFAULT_CONFIG_WAIT_SEC;
import static daq.pubber.SystemManager.LOG_MAP;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;
import static udmi.schema.EndpointConfiguration.Protocol.MQTT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.udmi.util.CertManager;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.MessageDowngrader;
import com.google.udmi.util.SchemaVersion;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import daq.pubber.MqttPublisher.FakeTopic;
import daq.pubber.MqttPublisher.InjectedMessage;
import daq.pubber.MqttPublisher.InjectedState;
import daq.pubber.MqttPublisher.PublisherException;
import daq.pubber.PointsetManager.ExtraPointsetEvent;
import daq.pubber.PubSubClient.Bundle;
import daq.pubber.SystemManager.ExtraSystemState;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobBlobsetState;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.BlobsetState;
import udmi.schema.Category;
import udmi.schema.Config;
import udmi.schema.DevicePersistent;
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Entry;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PointsetEvents;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.schema.State;
import udmi.schema.SystemEvents;
import udmi.schema.SystemState;

/**
 * IoT Core UDMI Device Emulator.
 */
public class Pubber extends ManagerBase implements ManagerHost {

  public static final String PUBBER_OUT = "pubber/out";
  public static final String PERSISTENT_STORE_FILE = "persistent_data.json";
  public static final String PERSISTENT_TMP_FORMAT = "/tmp/pubber_%s_" + PERSISTENT_STORE_FILE;
  public static final String DATA_URL_JSON_BASE64 = "data:application/json;base64,";
  public static final String CA_CRT = "ca.crt";
  static final String UDMI_VERSION = SchemaVersion.CURRENT.key();
  static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  static final Date DEVICE_START_TIME = getRoundedStartTime();
  static final int MESSAGE_REPORT_INTERVAL = 10;
  private static final String BROKEN_VERSION = "1.4.";
  private static final String HOSTNAME = System.getenv("HOSTNAME");
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String PUBSUB_SITE = "PubSub";
  private static final int DEFAULT_REPORT_SEC = 10;
  private static final String SYSTEM_CATEGORY_FORMAT = "system.%s.%s";
  private static final ImmutableMap<Class<?>, String> MESSAGE_TOPIC_SUFFIX_MAP =
      new Builder<Class<?>, String>()
          .put(State.class, STATE_TOPIC)
          .put(ExtraSystemState.class, STATE_TOPIC) // Used for badState option
          .put(SystemEvents.class, getEventsSuffix("system"))
          .put(PointsetEvents.class, getEventsSuffix("pointset"))
          .put(ExtraPointsetEvent.class, getEventsSuffix("pointset"))
          .put(InjectedMessage.class, getEventsSuffix("system"))
          .put(FakeTopic.class, getEventsSuffix("racoon"))
          .put(InjectedState.class, STATE_TOPIC)
          .put(DiscoveryEvents.class, getEventsSuffix("discovery"))
          .build();
  private static final Map<String, String> INVALID_REPLACEMENTS = ImmutableMap.of(
      "events/blobset", "\"\"",
      "events/discovery", "{}",
      "events/gateway", "{ \"testing\": \"This is prematurely terminated",
      "events/mapping", "{ NOT VALID JSON!"
  );
  public static final List<String> INVALID_KEYS = new ArrayList<>(INVALID_REPLACEMENTS.keySet());
  private static final Map<String, AtomicInteger> MESSAGE_COUNTS = new HashMap<>();
  private static final int CONNECT_RETRIES = 10;
  private static final AtomicInteger retriesRemaining = new AtomicInteger(CONNECT_RETRIES);
  private static final long RESTART_DELAY_MS = 1000;
  private static final String CORRUPT_STATE_MESSAGE = "!&*@(!*&@!";
  private static final long INJECT_MESSAGE_DELAY_MS = 1000; // Delay to make sure testing is stable.
  private static final int FORCED_STATE_TIME_MS = 10000;
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(30);
  private static final Duration SMOKE_CHECK_TIME = Duration.ofMinutes(5);
  private static final int STATE_SPAM_SEC = 5; // Expected config-state response time.
  private static final String SYSTEM_EVENT_TOPIC = "events/system";
  private static final String RAW_EVENT_TOPIC = "events";
  private final File outDir;
  private final ReentrantLock stateLock = new ReentrantLock();
  public PrintStream logPrintWriter;
  protected DevicePersistent persistentData;
  private CountDownLatch configLatch;
  private MqttDevice deviceTarget;
  private long lastStateTimeMs;
  private PubSubClient pubSubClient;
  private Function<String, Boolean> connectionDone;
  private String workingEndpoint;
  private String attemptedEndpoint;
  private EndpointConfiguration extractedEndpoint;
  private SiteModel siteModel;
  private SchemaVersion targetSchema;
  private int deviceUpdateCount = -1;
  private DeviceManager deviceManager;
  private boolean isConnected;
  private boolean isGatewayDevice;

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public Pubber(String configPath) {
    super(null, loadConfiguration(configPath));
    setClockSkew(isTrue(options.skewClock) ? CLOCK_SKEW : Duration.ZERO);
    Protocol protocol = requireNonNullElse(
        ifNotNullGet(config.endpoint, endpoint -> endpoint.protocol), MQTT);
    checkArgument(MQTT.equals(protocol), "protocol mismatch");
    outDir = new File(PUBBER_OUT);
    ifTrueThen(options.spamState, () -> schedulePeriodic(STATE_SPAM_SEC, this::markStateDirty));
  }

  /**
   * Start an instance from explicit args.
   *
   * @param iotProject GCP project
   * @param sitePath   Path to site_model
   * @param deviceId   Device ID to emulate
   * @param serialNo   Serial number of the device
   */
  public Pubber(String iotProject, String sitePath, String deviceId, String serialNo) {
    super(null, makeExplicitConfiguration(iotProject, sitePath, deviceId, serialNo));
    outDir = new File(PUBBER_OUT + "/" + serialNo);
    if (!outDir.exists()) {
      checkState(outDir.mkdirs(), "could not make out dir " + outDir.getAbsolutePath());
    }
    if (PUBSUB_SITE.equals(sitePath)) {
      pubSubClient = new PubSubClient(iotProject, deviceId);
    }
  }

  private static PubberConfiguration loadConfiguration(String configPath) {
    File configFile = new File(configPath);
    try {
      return sanitizeConfiguration(fromJsonFileStrict(configFile, PubberConfiguration.class));
    } catch (Exception e) {
      throw new RuntimeException("While configuring from " + configFile.getAbsolutePath(), e);
    }
  }

  private static PubberConfiguration makeExplicitConfiguration(String iotProject, String sitePath,
      String deviceId, String serialNo) {
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.iotProject = iotProject;
    configuration.sitePath = sitePath;
    configuration.deviceId = deviceId;
    configuration.serialNo = serialNo;
    configuration.options = new PubberOptions();
    return configuration;
  }

  private static String getEventsSuffix(String suffixSuffix) {
    return MqttDevice.EVENTS_TOPIC + "/" + suffixSuffix;
  }

  private static Date getRoundedStartTime() {
    long timestamp = getNow().getTime();
    // Remove ms so that rounded conversions preserve equality.
    return new Date(timestamp - (timestamp % 1000));
  }

  /**
   * Start a pubber instance with command line args.
   *
   * @param args The usual
   */
  public static void main(String[] args) {
    try {
      boolean swarm = args.length > 1 && PUBSUB_SITE.equals(args[1]);
      if (swarm) {
        swarmPubber(args);
      } else {
        singularPubber(args);
      }
      LOG.info("Done with main");
    } catch (Exception e) {
      LOG.error("Exception starting pubber: " + friendlyStackTrace(e));
      e.printStackTrace();
      System.exit(-1);
    }
  }

  static Pubber singularPubber(String[] args) {
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
        LOG.info(format("Connection closed/finished for %s", deviceId));
        return true;
      });
    } catch (Exception e) {
      if (pubber != null) {
        pubber.shutdown();
      }
      throw new RuntimeException("While starting singular pubber", e);
    }
    return pubber;
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
    LOG.info(format("Starting %d pubber instances", instances));
    for (int instance = 0; instance < instances; instance++) {
      String serialNo = format("%s-%d", HOSTNAME, (instance + 1));
      startFeedListener(projectId, siteName, feedName, serialNo);
    }
    LOG.info(format("Started all %d pubber instances", instances));
  }

  private static void startFeedListener(String projectId, String siteName, String feedName,
      String serialNo) {
    Pubber pubber = new Pubber(projectId, siteName, feedName, serialNo);
    try {
      LOG.info("Starting feed listener " + serialNo);
      pubber.initialize();
      pubber.startConnection(deviceId -> {
        LOG.error("Connection terminated, restarting listener");
        startFeedListener(projectId, siteName, feedName, serialNo);
        return false;
      });
      pubber.shutdown();
    } catch (Exception e) {
      LOG.error("Exception starting instance " + serialNo, e);
      pubber.shutdown();
      startFeedListener(projectId, siteName, feedName, serialNo);
    }
  }

  private static PubberConfiguration sanitizeConfiguration(PubberConfiguration configuration) {
    if (configuration.options == null) {
      configuration.options = new PubberOptions();
    }
    return configuration;
  }

  static String acquireBlobData(String url, String sha256) {
    if (!url.startsWith(DATA_URL_JSON_BASE64)) {
      throw new RuntimeException("URL encoding not supported: " + url);
    }
    byte[] dataBytes = Base64.getDecoder().decode(url.substring(DATA_URL_JSON_BASE64.length()));
    String dataSha256 = GeneralUtils.sha256(dataBytes);
    if (!dataSha256.equals(sha256)) {
      throw new RuntimeException("Blob data hash mismatch");
    }
    return new String(dataBytes);
  }

  static void augmentDeviceMessage(Object message, Date now, boolean useBadVersion) {
    try {
      Field version = message.getClass().getField("version");
      version.set(message, useBadVersion ? BROKEN_VERSION : UDMI_VERSION);
      Field timestamp = message.getClass().getField("timestamp");
      timestamp.set(message, now);
    } catch (Throwable e) {
      throw new RuntimeException("While augmenting device message", e);
    }
  }

  static String getGatewayId(String targetId, PubberConfiguration configuration) {
    return ofNullable(configuration.gatewayId).orElse(
        targetId.equals(configuration.deviceId) ? null : configuration.deviceId);
  }

  @Override
  public FamilyProvider getLocalnetProvider(String family) {
    return deviceManager.getLocalnetProvider(family);
  }

  private void initializeDevice() {
    deviceManager = new DeviceManager(this, config);

    if (config.sitePath != null) {
      SupportedFeatures.writeFeatureFile(config.sitePath, deviceManager);
      siteModel = new SiteModel(config.sitePath);
      siteModel.initialize();
      if (config.endpoint == null) {
        config.endpoint = siteModel.makeEndpointConfig(config.iotProject, deviceId);
      }
      if (!siteModel.allDeviceIds().contains(config.deviceId)) {
        throw new IllegalArgumentException(
            "Device ID " + config.deviceId + " not found in site model");
      }
      Metadata metadata = siteModel.getMetadata(config.deviceId);
      processDeviceMetadata(metadata);
      deviceManager.setSiteModel(siteModel);
    } else if (pubSubClient != null) {
      pullDeviceMessage();
    }

    SupportedFeatures.setFeatureSwap(config.options.featureEnableSwap);
    initializePersistentStore();

    info(format("Starting pubber %s, serial %s, mac %s, gateway %s, options %s",
        config.deviceId, config.serialNo, config.macAddr,
        config.gatewayId, optionsString(config.options)));

    markStateDirty();
  }

  protected DevicePersistent newDevicePersistent() {
    return new DevicePersistent();
  }

  protected void initializePersistentStore() {
    checkState(persistentData == null, "persistent data already loaded");
    File persistentStore = getPersistentStore();

    if (isTrue(config.options.noPersist)) {
      info("Resetting persistent store " + persistentStore.getAbsolutePath());
      persistentData = newDevicePersistent();
    } else {
      info("Initializing from persistent store " + persistentStore.getAbsolutePath());
      persistentData =
          persistentStore.exists() ? fromJsonFile(persistentStore, DevicePersistent.class)
              : newDevicePersistent();
    }

    persistentData.restart_count = requireNonNullElse(persistentData.restart_count, 0) + 1;

    // If the persistentData contains endpoint configuration, prioritize using that.
    // Otherwise, use the endpoint configuration that came from the Pubber config file on start.
    if (persistentData.endpoint != null) {
      info("Loading endpoint from persistent data");
      config.endpoint = persistentData.endpoint;
    } else if (config.endpoint != null) {
      info("Loading endpoint into persistent data from configuration");
      persistentData.endpoint = config.endpoint;
    } else {
      error(
          "Neither configuration nor persistent data supplies endpoint configuration");
    }

    writePersistentStore();
  }

  private void writePersistentStore() {
    checkState(persistentData != null, "persistent data not defined");
    toJsonFile(getPersistentStore(), persistentData);
    warn("Updating persistent store:\n" + stringify(persistentData));
    deviceManager.setPersistentData(persistentData);
  }

  private File getPersistentStore() {
    return siteModel == null ? new File(format(PERSISTENT_TMP_FORMAT, deviceId)) :
        new File(siteModel.getDeviceWorkingDir(deviceId), PERSISTENT_STORE_FILE);
  }

  private void markStateDirty(Runnable action) {
    action.run();
    markStateDirty();
  }

  private void markStateDirty() {
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

  private void publishDirtyState() {
    if (stateDirty.get()) {
      debug("Publishing dirty state block");
      markStateDirty(0);
    }
  }

  @Override
  public void update(Object update) {
    if (update == null) {
      publishSynchronousState();
      return;
    }
    updateStateHolder(deviceState, update);
    markStateDirty();
    if (update instanceof SystemState) {
      ifTrueThen(options.dupeState, this::sendPartialState);
    }
  }

  private void sendPartialState() {
    State dupeState = new State();
    dupeState.system = deviceState.system;
    dupeState.timestamp = deviceState.timestamp;
    dupeState.version = deviceState.version;
    publishStateMessage(dupeState);
  }

  @Override
  public void publish(Object message) {
    publishDeviceMessage(message);
  }

  public void publish(String targetId, Object message) {
    publishDeviceMessage(targetId, message);
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
        SwarmMessage swarm = fromJsonString(pull.body, SwarmMessage.class);
        processSwarmConfig(swarm, attributes);
        return;
      } catch (Exception e) {
        error("Error pulling swarm message", e);
        safeSleep(WAIT_TIME_SEC);
      }
    }
  }

  private void processSwarmConfig(SwarmMessage swarm, Envelope attributes) {
    config.deviceId = checkNotNull(attributes.deviceId, "deviceId");
    config.keyBytes = Base64.getDecoder()
        .decode(checkNotNull(swarm.key_base64, "key_base64"));
    config.endpoint = SiteModel.makeEndpointConfig(attributes);
    processDeviceMetadata(checkNotNull(swarm.device_metadata, "device_metadata"));
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata instanceof MetadataException metadataException) {
      throw new RuntimeException("While processing metadata file " + metadataException.file,
          metadataException.exception);
    }
    targetSchema = ifNotNullGet(metadata.system.device_version, SchemaVersion::fromKey);
    ifNotNullThen(targetSchema, version -> warn("Emulating UDMI version " + version.key()));

    config.serialNo = catchToNull(() -> metadata.system.serial_no);

    config.gatewayId = catchToNull(() -> metadata.gateway.gateway_id);

    config.algorithm = config.gatewayId == null
        ? catchToNull(() -> metadata.cloud.auth_type.value())
        : catchToNull(() -> siteModel.getAuthType(config.gatewayId).value());

    info("Configured with auth_type " + config.algorithm);

    isGatewayDevice = catchToFalse(() -> metadata.gateway.proxy_ids != null);

    deviceManager.setMetadata(metadata);
  }

  @Override
  public void periodicUpdate() {
    try {
      deviceUpdateCount++;
      checkSmokyFailure();
      deferredConfigActions();
      sendEmptyMissingBadEvents();
      maybeTweakState();
      flushDirtyState();
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  private void checkSmokyFailure() {
    if (isTrue(config.options.smokeCheck)
        && Instant.now().minus(SMOKE_CHECK_TIME).isAfter(DEVICE_START_TIME.toInstant())) {
      error(format("Smoke check failed after %sm, terminating run.",
          SMOKE_CHECK_TIME.getSeconds() / 60));
      deviceManager.systemLifecycle(SystemMode.TERMINATE);
    }
  }

  /**
   * For testing, if configured, send a slate of bad messages for testing by the message handling
   * infrastructure. Uses the sekrit REPLACE_MESSAGE_WITH field to sneak bad output into the pipe.
   * E.g., Will send a message with "{ INVALID JSON!" as a message payload. Inserts a delay before
   * each message sent to stabilize the output order for testing purposes.
   */
  private void sendEmptyMissingBadEvents() {
    if (!isTrue(config.options.emptyMissing)) {
      return;
    }

    final int explicitPhases = 3;

    checkState(MESSAGE_REPORT_INTERVAL > explicitPhases + INVALID_REPLACEMENTS.size() + 1,
        "not enough space for hacky messages");
    int phase = (deviceUpdateCount + MESSAGE_REPORT_INTERVAL / 2) % MESSAGE_REPORT_INTERVAL;

    safeSleep(INJECT_MESSAGE_DELAY_MS);

    if (phase == 0) {
      flushDirtyState();
      InjectedState invalidState = new InjectedState();
      invalidState.REPLACE_MESSAGE_WITH = CORRUPT_STATE_MESSAGE;
      warn("Sending badly formatted state as per configuration");
      publishStateMessage(invalidState);
    } else if (phase == 1) {
      InjectedMessage invalidEvent = new InjectedMessage();
      invalidEvent.field = "bunny";
      warn("Sending badly formatted message with extra field");
      publishDeviceMessage(invalidEvent);
    } else if (phase == 2) {
      FakeTopic invalidTopic = new FakeTopic();
      warn("Sending badly formatted message with fake topic");
      publishDeviceMessage(invalidTopic);
    } else if (phase < INVALID_REPLACEMENTS.size() + explicitPhases) {
      String key = INVALID_KEYS.get(phase - explicitPhases);
      InjectedMessage replacedEvent = new InjectedMessage();
      replacedEvent.REPLACE_TOPIC_WITH = key;
      replacedEvent.REPLACE_MESSAGE_WITH = INVALID_REPLACEMENTS.get(key);
      warn("Sending badly formatted message of type " + key);
      publishDeviceMessage(replacedEvent);
    }
    safeSleep(INJECT_MESSAGE_DELAY_MS);
  }

  private void maybeTweakState() {
    if (!isTrue(options.tweakState)) {
      return;
    }
    int phase = deviceUpdateCount % 2;
    String randomValue = format("%04x", System.currentTimeMillis() % 0xffff);
    if (phase == 0) {
      catchToNull(() -> deviceState.system.software.put("random", randomValue));
    } else if (phase == 1) {
      ifNotNullThen(deviceState.pointset, state -> state.state_etag = randomValue);
    }
  }

  private void deferredConfigActions() {
    if (!isConnected) {
      return;
    }

    deviceManager.maybeRestartSystem();

    // Do redirect after restart system check, since this might take a long time.
    maybeRedirectEndpoint();
  }

  private void flushDirtyState() {
    if (stateDirty.get()) {
      publishAsynchronousState();
    }
  }

  private void captureExceptions(String action, Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      error(action, e);
    }
  }

  protected void startConnection(Function<String, Boolean> connectionDone) {
    String nonce = String.valueOf(System.currentTimeMillis());
    warn(format("Starting connection %s with %d", nonce, retriesRemaining.get()));
    try {
      this.connectionDone = connectionDone;
      while (retriesRemaining.getAndDecrement() > 0) {
        if (attemptConnection()) {
          return;
        }
      }
      throw new RuntimeException("Failed connection attempt after retries");
    } catch (Exception e) {
      throw new RuntimeException("While attempting to start connection", e);
    } finally {
      warn(format("Ending connection %s with %d", nonce, retriesRemaining.get()));
    }
  }

  private boolean attemptConnection() {
    try {
      isConnected = false;
      deviceManager.stop();
      if (deviceTarget == null) {
        throw new RuntimeException("Mqtt publisher not initialized");
      }
      connect();
      configLatchWait();
      isConnected = true;
      deviceManager.activate();
      return true;
    } catch (Exception e) {
      error("While waiting for connection start", e);
    }
    error("Attempt failed, retries remaining: " + retriesRemaining.get());
    safeSleep(RESTART_DELAY_MS);
    return false;
  }

  private void configLatchWait() {
    try {
      int waitTimeSec = ofNullable(config.endpoint.config_sync_sec)
          .orElse(DEFAULT_CONFIG_WAIT_SEC);
      int useWaitTime = waitTimeSec == 0 ? DEFAULT_CONFIG_WAIT_SEC : waitTimeSec;
      warn(format("Start waiting %ds for config latch for %s", useWaitTime, deviceId));
      if (useWaitTime > 0 && !configLatch.await(useWaitTime, TimeUnit.SECONDS)) {
        throw new RuntimeException("Config latch timeout");
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While waiting for %s config latch", deviceId), e);
    }
  }

  protected void initialize() {
    try {
      initializeDevice();
      initializeMqtt();
    } catch (Exception e) {
      shutdown();
      throw new RuntimeException("While initializing main pubber class", e);
    }
  }

  @Override
  public void shutdown() {
    warn("Initiating device shutdown");

    if (deviceState.system != null && deviceState.system.operation != null) {
      deviceState.system.operation.mode = SystemMode.SHUTDOWN;
    }

    super.shutdown();
    ifNotNullThen(deviceManager, dm -> captureExceptions("device manager shutdown", dm::shutdown));
    captureExceptions("publishing shutdown state", this::publishSynchronousState);
    captureExceptions("disconnecting mqtt", this::disconnectMqtt);
  }

  private void disconnectMqtt() {
    if (deviceTarget != null) {
      captureExceptions("closing mqtt publisher", deviceTarget::close);
      captureExceptions("shutting down mqtt publisher executor", deviceTarget::shutdown);
      deviceTarget = null;
    }
  }

  private void initializeMqtt() {
    checkNotNull(config.deviceId, "configuration deviceId not defined");
    if (siteModel != null && config.keyFile != null) {
      config.keyFile = siteModel.getDeviceKeyFile(config.deviceId);
    }
    ensureKeyBytes();
    checkState(deviceTarget == null, "mqttPublisher already defined");
    String keyPassword = siteModel.getDevicePassword(config.deviceId);
    String targetDeviceId = getTargetDeviceId(siteModel, config.deviceId);
    CertManager certManager = new CertManager(new File(siteModel.getReflectorDir(), CA_CRT),
        siteModel.getDeviceDir(targetDeviceId), config.endpoint.transport, keyPassword,
        this::info);
    deviceTarget = new MqttDevice(config, this::publisherException, certManager);
    registerMessageHandlers();
    publishDirtyState();
  }

  private String getTargetDeviceId(SiteModel siteModel, String deviceId) {
    Metadata metadata = siteModel.getMetadata(deviceId);
    return ofNullable(catchToNull(() -> metadata.gateway.gateway_id)).orElse(deviceId);
  }

  private void registerMessageHandlers() {
    deviceTarget.registerHandler(CONFIG_TOPIC, this::configHandler, Config.class);
    String gatewayId = getGatewayId(deviceId, config);
    if (isGatewayDevice) {
      // In this case, this is the gateway so register the appropriate error handler directly.
      deviceTarget.registerHandler(ERRORS_TOPIC, this::errorHandler, GatewayError.class);
    } else if (gatewayId != null) {
      // In this case, this is a proxy device with a gateway, so register handlers accordingly.
      MqttDevice gatewayTarget = new MqttDevice(gatewayId, deviceTarget);
      gatewayTarget.registerHandler(CONFIG_TOPIC, this::gatewayHandler, Config.class);
      gatewayTarget.registerHandler(ERRORS_TOPIC, this::errorHandler, GatewayError.class);
    }
  }

  public MqttDevice getMqttDevice(String proxyId) {
    return new MqttDevice(proxyId, deviceTarget);
  }

  private byte[] ensureKeyBytes() {
    if (config.keyBytes == null) {
      checkNotNull(config.keyFile, "configuration keyFile not defined");
      info("Loading device key bytes from " + config.keyFile);
      config.keyBytes = getFileBytes(config.keyFile);
      config.keyFile = null;
    }
    return (byte[]) config.keyBytes;
  }

  private void connect() {
    try {
      warn("Creating new config latch for " + deviceId);
      configLatch = new CountDownLatch(1);
      deviceTarget.connect();
      info("Connection complete.");
      workingEndpoint = toJsonString(config.endpoint);
    } catch (Exception e) {
      throw new RuntimeException("Connection error", e);
    }
  }

  public void publisherConfigLog(String phase, Exception e, String targetId) {
    publisherHandler("config", phase, e, targetId);
  }

  private void publisherException(Exception toReport) {
    if (toReport instanceof PublisherException report) {
      publisherHandler(report.type, report.phase, report.getCause(), report.deviceId);
    } else if (toReport instanceof ConnectionClosedException) {
      error("Connection closed, attempting reconnect...");
      while (retriesRemaining.getAndDecrement() > 0) {
        if (attemptConnection()) {
          return;
        }
      }
      error("Connection retry failed, giving up.");
      deviceManager.systemLifecycle(SystemMode.TERMINATE);
    } else {
      error("Unknown exception type " + toReport.getClass(), toReport);
    }
  }

  private void publisherHandler(String type, String phase, Throwable cause, String targetId) {
    if (cause != null) {
      error("Error receiving message " + type, cause);
      if (isTrue(config.options.barfConfig)) {
        error("Restarting system because of restart-on-error configuration setting");
        deviceManager.systemLifecycle(SystemMode.RESTART);
      }
    }
    String usePhase = isTrue(options.badCategory) ? "apply" : phase;
    String category = format(SYSTEM_CATEGORY_FORMAT, type, usePhase);
    Entry report = entryFromException(category, cause);
    deviceManager.localLog(report);
    publishLogMessage(report, targetId);
    ifTrueThen(deviceId.equals(targetId), () -> registerSystemStatus(report));
  }

  private void registerSystemStatus(Entry report) {
    deviceState.system.status = report;
    markStateDirty();
  }

  /**
   * Issue a state update in response to a received config message. This will optionally add a
   * synthetic delay in so that testing infrastructure can test that related sequence tests handle
   * this case appropriately.
   */
  private void publishConfigStateUpdate() {
    if (isTrue(config.options.configStateDelay)) {
      delayNextStateUpdate();
    }
    publishAsynchronousState();
  }

  private void delayNextStateUpdate() {
    // Calculate a synthetic last state time that factors in the optional delay.
    long syntheticType = System.currentTimeMillis() - STATE_THROTTLE_MS + FORCED_STATE_TIME_MS;
    // And use the synthetic time iff it's later than the actual last state time.
    lastStateTimeMs = Math.max(lastStateTimeMs, syntheticType);
  }

  private Entry entryFromException(String category, Throwable e) {
    boolean success = e == null;
    Entry entry = new Entry();
    entry.category = category;
    entry.timestamp = getNow();
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

  private void configHandler(Config config) {
    try {
      configPreprocess(deviceId, config);
      debug(format("Config update %s%s", deviceId, deviceManager.getTestingTag()),
          toJsonString(config));
      processConfigUpdate(config);
      if (configLatch.getCount() > 0) {
        warn("Received config for config latch " + deviceId);
        configLatch.countDown();
      }
      publisherConfigLog("apply", null, deviceId);
    } catch (Exception e) {
      publisherConfigLog("apply", e, deviceId);
    }
    publishConfigStateUpdate();
  }

  private void gatewayHandler(Config gatewayConfig) {
    warn("Ignoring configuration for gateway " + getGatewayId(deviceId, config));
  }

  private void errorHandler(GatewayError error) {
    warn(format("%s for %s: %s", error.error_type, error.device_id, error.description));
  }

  void configPreprocess(String targetId, Config configMsg) {
    String gatewayId = getGatewayId(targetId, config);
    String suffix = ifNotNullGet(gatewayId, x -> "_" + targetId, "");
    String deviceType = ifNotNullGet(gatewayId, x -> "Proxy", "Device");
    info(format("%s %s config handler", deviceType, targetId));
    File configOut = new File(outDir, format("%s.json", traceTimestamp("config" + suffix)));
    toJsonFile(configOut, configMsg);
  }

  private void processConfigUpdate(Config configMsg) {
    try {
      // Grab this to make state-after-config updates monolithic.
      stateLock.lock();
    } catch (Exception e) {
      throw new RuntimeException("While acquiring state lock", e);
    }

    try {
      if (configMsg != null) {
        if (configMsg.system == null && isTrue(config.options.barfConfig)) {
          error("Empty config system block and configured to restart on bad config!");
          deviceManager.systemLifecycle(SystemMode.RESTART);
        }
        GeneralUtils.copyFields(configMsg, deviceConfig, true);
        info(format("%s received config %s", getTimestamp(), isoConvert(configMsg.timestamp)));
        deviceManager.updateConfig(configMsg);
        extractEndpointBlobConfig();
      } else {
        info(getTimestamp() + " defaulting empty config");
      }
      updateInterval(DEFAULT_REPORT_SEC);
    } finally {
      stateLock.unlock();
    }
  }

  // TODO: Consider refactoring this to either return or change an instance variable, not both.
  EndpointConfiguration extractEndpointBlobConfig() {
    extractedEndpoint = null;
    if (deviceConfig.blobset == null) {
      return null;
    }
    try {
      String iotConfig = extractConfigBlob(IOT_ENDPOINT_CONFIG.value());
      extractedEndpoint = fromJsonString(iotConfig, EndpointConfiguration.class);
      if (extractedEndpoint != null) {
        if (deviceConfig.blobset.blobs.containsKey(IOT_ENDPOINT_CONFIG.value())) {
          BlobBlobsetConfig config = deviceConfig.blobset.blobs.get(IOT_ENDPOINT_CONFIG.value());
          extractedEndpoint.generation = config.generation;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While extracting endpoint blob config", e);
    }
    return extractedEndpoint;
  }

  private void removeBlobsetBlobState(SystemBlobsets blobId) {
    if (deviceState.blobset == null) {
      return;
    }

    if (deviceState.blobset.blobs.remove(blobId.value()) == null) {
      return;
    }

    if (deviceState.blobset.blobs.isEmpty()) {
      deviceState.blobset = null;
    }

    markStateDirty();
  }

  void maybeRedirectEndpoint() {
    String redirectRegistry = config.options.redirectRegistry;
    String currentSignature = toJsonString(config.endpoint);
    String extractedSignature =
        redirectRegistry == null ? toJsonString(extractedEndpoint)
            : redirectedEndpoint(redirectRegistry);

    if (extractedSignature == null) {
      attemptedEndpoint = null;
      removeBlobsetBlobState(IOT_ENDPOINT_CONFIG);
      return;
    }

    BlobBlobsetState endpointState = ensureBlobsetState(IOT_ENDPOINT_CONFIG);

    if (extractedSignature.equals(currentSignature)
        || extractedSignature.equals(attemptedEndpoint)) {
      return; // No need to redirect anything!
    }

    if (extractedEndpoint != null) {
      if (!Objects.equals(endpointState.generation, extractedEndpoint.generation)) {
        notice("Starting new endpoint generation");
        endpointState.phase = null;
        endpointState.status = null;
        endpointState.generation = extractedEndpoint.generation;
      }

      if (extractedEndpoint.error != null) {
        attemptedEndpoint = extractedSignature;
        endpointState.phase = BlobPhase.FINAL;
        Exception applyError = new RuntimeException(extractedEndpoint.error);
        endpointState.status = exceptionStatus(applyError, Category.BLOBSET_BLOB_APPLY);
        publishSynchronousState();
        return;
      }
    }

    info("New config blob endpoint detected:\n" + stringify(parseJson(extractedSignature)));

    try {
      attemptedEndpoint = extractedSignature;
      endpointState.phase = BlobPhase.APPLY;
      publishSynchronousState();
      resetConnection(extractedSignature);
      persistEndpoint(extractedEndpoint);
      endpointState.phase = BlobPhase.FINAL;
      markStateDirty();
    } catch (Exception e) {
      try {
        error("Reconfigure failed, attempting connection to last working endpoint", e);
        endpointState.phase = BlobPhase.FINAL;
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

  private void persistEndpoint(EndpointConfiguration endpoint) {
    notice("Persisting connection endpoint");
    persistentData.endpoint = endpoint;
    writePersistentStore();
  }

  private String redirectedEndpoint(String redirectRegistry) {
    try {
      EndpointConfiguration endpoint = deepCopy(config.endpoint);
      endpoint.client_id = getClientId(redirectRegistry);
      return toJsonString(endpoint);
    } catch (Exception e) {
      throw new RuntimeException("While getting redirected endpoint", e);
    }
  }

  private void resetConnection(String targetEndpoint) {
    try {
      config.endpoint = fromJsonString(targetEndpoint,
          EndpointConfiguration.class);
      disconnectMqtt();
      initializeMqtt();
      retriesRemaining.set(CONNECT_RETRIES);
      startConnection(connectionDone);
    } catch (Exception e) {
      throw new RuntimeException("While resetting connection", e);
    }
  }

  private Entry exceptionStatus(Exception e, String category) {
    Entry entry = new Entry();
    entry.message = e.getMessage();
    entry.detail = stackTraceString(e);
    entry.category = category;
    entry.level = Level.ERROR.value();
    entry.timestamp = getNow();
    return entry;
  }

  private BlobBlobsetState ensureBlobsetState(SystemBlobsets iotEndpointConfig) {
    deviceState.blobset = ofNullable(deviceState.blobset).orElseGet(BlobsetState::new);
    deviceState.blobset.blobs = ofNullable(deviceState.blobset.blobs).orElseGet(HashMap::new);
    return deviceState.blobset.blobs.computeIfAbsent(iotEndpointConfig.value(),
        key -> new BlobBlobsetState());
  }

  private String getClientId(String forRegistry) {
    String cloudRegion = SiteModel.parseClientId(config.endpoint.client_id).cloudRegion;
    return SiteModel.getClientId(config.iotProject, cloudRegion, forRegistry, deviceId);
  }

  private String extractConfigBlob(String blobName) {
    // TODO: Refactor to get any blob meta parameters.
    try {
      if (deviceConfig == null || deviceConfig.blobset == null
          || deviceConfig.blobset.blobs == null) {
        return null;
      }
      BlobBlobsetConfig blobBlobsetConfig = deviceConfig.blobset.blobs.get(blobName);
      if (blobBlobsetConfig != null && BlobPhase.FINAL.equals(blobBlobsetConfig.phase)) {
        return acquireBlobData(blobBlobsetConfig.url, blobBlobsetConfig.sha256);
      }
      return null;
    } catch (Exception e) {
      EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
      endpointConfiguration.error = e.toString();
      return stringify(endpointConfiguration);
    }
  }

  private void publishLogMessage(Entry logEntry, String targetId) {
    deviceManager.publishLogMessage(logEntry, targetId);
  }

  private void publishAsynchronousState() {
    if (stateLock.tryLock()) {
      try {
        long soonestAllowedStateUpdate = lastStateTimeMs + STATE_THROTTLE_MS;
        long delay = soonestAllowedStateUpdate - System.currentTimeMillis();
        debug(format("State update defer %dms", delay));
        if (delay > 0) {
          markStateDirty(delay);
        } else {
          publishStateMessage();
        }
      } finally {
        stateLock.unlock();
      }
    } else {
      markStateDirty(-1);
    }
  }

  void publishSynchronousState() {
    try {
      stateLock.lock();
      publishStateMessage();
    } catch (Exception e) {
      throw new RuntimeException("While sending synchronous state", e);
    } finally {
      stateLock.unlock();
    }
  }

  boolean publisherActive() {
    return deviceTarget != null && deviceTarget.isActive();
  }

  private void publishStateMessage() {
    if (!publisherActive()) {
      markStateDirty(-1);
      return;
    }
    stateDirty.set(false);
    deviceState.timestamp = getNow();
    info(format("Update state %s last_config %s", isoConvert(deviceState.timestamp),
        isoConvert(deviceState.system.last_config)));
    publishStateMessage(isTrue(options.badState) ? deviceState.system : deviceState);
  }

  private void publishStateMessage(Object stateToSend) {
    try {
      stateLock.lock();
      publishStateMessageRaw(stateToSend);
    } finally {
      stateLock.unlock();
    }
  }

  private void publishStateMessageRaw(Object stateToSend) {
    if (configLatch == null || configLatch.getCount() > 0) {
      warn("Dropping state update until config received...");
      return;
    }

    long delay = lastStateTimeMs + STATE_THROTTLE_MS - System.currentTimeMillis();
    if (delay > 0) {
      warn(format("State update delay %dms", delay));
      safeSleep(delay);
    }

    lastStateTimeMs = System.currentTimeMillis();
    CountDownLatch latch = new CountDownLatch(1);

    try {
      debug(format("State update %s%s", deviceId, deviceManager.getTestingTag()),
          toJsonString(stateToSend));
    } catch (Exception e) {
      throw new RuntimeException("While converting new device state", e);
    }

    publishDeviceMessage(deviceId, stateToSend, () -> {
      lastStateTimeMs = System.currentTimeMillis();
      latch.countDown();
    });
    try {
      if (shouldSendState() && !latch.await(WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException("Timeout waiting for state send");
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While waiting for %s state send latch", deviceId), e);
    }
  }

  private boolean shouldSendState() {
    return !isGetTrue(() -> config.options.noState);
  }

  private void publishDeviceMessage(Object message) {
    publishDeviceMessage(deviceId, message);
  }

  private void publishDeviceMessage(String targetId, Object message) {
    publishDeviceMessage(targetId, message, null);
  }

  private void publishDeviceMessage(String targetId, Object message, Runnable callback) {
    if (deviceTarget == null) {
      error("publisher not active");
      return;
    }

    String topicSuffix = MESSAGE_TOPIC_SUFFIX_MAP.get(message.getClass());
    if (topicSuffix == null) {
      error("Unknown message class " + message.getClass());
      return;
    }

    if (!shouldSendState() && topicSuffix.equals(STATE_TOPIC)) {
      warn("Squelching state update as per configuration");
      return;
    }

    if (isTrue(options.noFolder) && topicSuffix.equals(SYSTEM_EVENT_TOPIC)) {
      topicSuffix = RAW_EVENT_TOPIC;
    }

    augmentDeviceMessage(message, getNow(), isTrue(options.badVersion));
    Object downgraded = downgradeMessage(message);
    deviceTarget.publish(targetId, topicSuffix, downgraded, callback);
    String messageBase = topicSuffix.replace("/", "_");
    String gatewayId = getGatewayId(targetId, config);
    String suffix = ifNotNullGet(gatewayId, x -> "_" + targetId, "");
    File messageOut = new File(outDir, format("%s.json", traceTimestamp(messageBase + suffix)));
    try {
      toJsonFile(messageOut, downgraded);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + messageOut.getAbsolutePath(), e);
    }
  }

  private Object downgradeMessage(Object message) {
    MessageDowngrader messageDowngrader = new MessageDowngrader(SubType.STATE.value(), message);
    return ifNotNullGet(targetSchema, messageDowngrader::downgrade, message);
  }

  private String traceTimestamp(String messageBase) {
    int serial = MESSAGE_COUNTS.computeIfAbsent(messageBase, key -> new AtomicInteger())
        .incrementAndGet();
    String timestamp = getTimestamp().replace("Z", format(".%03dZ", serial));
    return messageBase + (isTrue(config.options.messageTrace) ? ("_" + timestamp) : "");
  }

  private void cloudLog(String message, Level level) {
    cloudLog(message, level, null);
  }

  private void cloudLog(String message, Level level, String detail) {
    if (deviceManager != null) {
      deviceManager.cloudLog(message, level, detail);
    } else {
      String detailPostfix = detail == null ? "" : ":\n" + detail;
      String logMessage = format("%s%s", message, detailPostfix);
      LOG_MAP.get(level).accept(logMessage);
    }
  }

  private void trace(String message) {
    cloudLog(message, Level.TRACE);
  }

  @Override
  public void debug(String message) {
    cloudLog(message, Level.DEBUG);
  }

  private void debug(String message, String detail) {
    cloudLog(message, Level.DEBUG, detail);
  }

  @Override
  public void info(String message) {
    cloudLog(message, Level.INFO);
  }

  private void notice(String message) {
    cloudLog(message, Level.NOTICE);
  }

  @Override
  public void warn(String message) {
    cloudLog(message, Level.WARNING);
  }

  @Override
  public void error(String message) {
    cloudLog(message, Level.ERROR);
  }

  @Override
  public void error(String message, Throwable e) {
    if (e == null) {
      error(message);
      return;
    }
    String longMessage = message + ": " + e.getMessage();
    cloudLog(longMessage, Level.ERROR);
    deviceManager.localLog(message, Level.TRACE, getTimestamp(), stackTraceString(e));
  }

  public Metadata getMetadata(String id) {
    return siteModel.getMetadata(id);
  }
}
