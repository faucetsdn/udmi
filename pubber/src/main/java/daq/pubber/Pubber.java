package daq.pubber;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.fromJsonFile;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isGetTrue;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.optionsString;
import static com.google.udmi.util.GeneralUtils.setClockSkew;
import static com.google.udmi.util.GeneralUtils.toJsonFile;
import static com.google.udmi.util.GeneralUtils.toJsonString;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static daq.pubber.MqttDevice.CONFIG_TOPIC;
import static daq.pubber.MqttDevice.ERRORS_TOPIC;
import static daq.pubber.MqttDevice.STATE_TOPIC;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;
import static udmi.schema.EndpointConfiguration.Protocol.MQTT;

import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MessageDowngrader;
import com.google.udmi.util.SchemaVersion;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import daq.pubber.MqttPublisher.InjectedMessage;
import daq.pubber.MqttPublisher.InjectedState;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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
import udmi.schema.DevicePersistent;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Entry;
import udmi.schema.Enumerate;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryEvent;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.Level;
import udmi.schema.LocalnetState;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PointEnumerationEvent;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.schema.State;
import udmi.schema.SystemEvent;
import udmi.schema.SystemState;

/**
 * IoT Core UDMI Device Emulator.
 */
public class Pubber extends ManagerBase implements ManagerHost {

  public static final int SCAN_DURATION_SEC = 10;
  public static final String PUBBER_OUT = "pubber/out";
  public static final String PERSISTENT_STORE_FILE = "persistent_data.json";
  public static final String PERSISTENT_TMP_FORMAT = "/tmp/pubber_%s_" + PERSISTENT_STORE_FILE;
  public static final String DATA_URL_JSON_BASE64 = "data:application/json;base64,";
  static final String UDMI_VERSION = SchemaVersion.CURRENT.key();
  static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  static final Date deviceStartTime = getRoundedStartTime();
  static final int MESSAGE_REPORT_INTERVAL = 10;
  private static final String BROKEN_VERSION = "1.4.";
  private static final String HOSTNAME = System.getenv("HOSTNAME");
  private static final int STATE_THROTTLE_MS = 2000;
  private static final String PUBSUB_SITE = "PubSub";
  private static final int DEFAULT_REPORT_SEC = 10;
  private static final String SYSTEM_CATEGORY_FORMAT = "system.%s.%s";
  private static final ImmutableMap<Class<?>, String> MESSAGE_TOPIC_SUFFIX_MAP =
      new ImmutableMap.Builder<Class<?>, String>()
          .put(State.class, MqttDevice.STATE_TOPIC)
          .put(SystemEvent.class, getEventsSuffix("system"))
          .put(PointsetEvent.class, getEventsSuffix("pointset"))
          .put(ExtraPointsetEvent.class, getEventsSuffix("pointset"))
          .put(InjectedMessage.class, getEventsSuffix("invalid"))
          .put(InjectedState.class, MqttDevice.STATE_TOPIC)
          .put(DiscoveryEvent.class, getEventsSuffix("discovery"))
          .build();
  private static final Map<String, String> INVALID_REPLACEMENTS = ImmutableMap.of(
      "events/blobset", "\"\"",
      "events/discovery", "{}",
      "events/mapping", "{ NOT VALID JSON!"
  );
  public static final List<String> INVALID_KEYS = new ArrayList<>(INVALID_REPLACEMENTS.keySet());
  private static final Map<String, AtomicInteger> MESSAGE_COUNTS = new HashMap<>();
  private static final int CONNECT_RETRIES = 10;
  private static final AtomicInteger retriesRemaining = new AtomicInteger(CONNECT_RETRIES);
  private static final long RESTART_DELAY_MS = 1000;
  private static final String CORRUPT_STATE_MESSAGE = "!&*@(!*&@!";
  private static final long INJECT_MESSAGE_DELAY_MS = 2000; // Delay to make sure testing is stable.
  private static final int FORCED_STATE_TIME_MS = 10000;
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(30);
  private static final Duration SMOKE_CHECK_TIME = Duration.ofMinutes(5);
  static PubberConfiguration configuration;
  final State deviceState = new State();
  final Config deviceConfig = new Config();
  private final File outDir;
  private final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  private final CountDownLatch configLatch = new CountDownLatch(1);
  private final AtomicBoolean stateDirty = new AtomicBoolean();
  private final ReentrantLock stateLock = new ReentrantLock();
  private final String deviceId;
  protected DevicePersistent persistentData;
  private MqttDevice deviceTarget;
  private long lastStateTimeMs;
  private PubSubClient pubSubClient;
  private Function<String, Boolean> connectionDone;
  private String workingEndpoint;
  private String attemptedEndpoint;
  private EndpointConfiguration extractedEndpoint;
  private SiteModel siteModel;
  private MqttDevice gatewayTarget;
  private SchemaVersion targetSchema;
  private int deviceUpdateCount = -1;
  private DeviceManager deviceManager;
  private Map<String, ProxyDevice> proxyDevices = new HashMap<>();

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public Pubber(String configPath) {
    super(null, loadConfiguration(configPath));
    setClockSkew(isTrue(options.skewClock) ? CLOCK_SKEW : Duration.ZERO);
    Protocol protocol = requireNonNullElse(
        ifNotNullGet(configuration.endpoint, endpoint -> endpoint.protocol), MQTT);
    checkArgument(MQTT.equals(protocol), "protocol mismatch");
    deviceId = requireNonNull(configuration.deviceId, "device id not defined");
    outDir = new File(PUBBER_OUT);
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
    this.deviceId = deviceId;
    outDir = new File(PUBBER_OUT + "/" + serialNo);
    if (PUBSUB_SITE.equals(sitePath)) {
      pubSubClient = new PubSubClient(iotProject, deviceId);
    }
  }

  private static PubberConfiguration loadConfiguration(String configPath) {
    File configFile = new File(configPath);
    try {
      configuration = sanitizeConfiguration(fromJsonFile(configFile, PubberConfiguration.class));
      return configuration;
    } catch (Exception e) {
      throw new RuntimeException("While configuring from " + configFile.getAbsolutePath(), e);
    }
  }

  private static PubberConfiguration makeExplicitConfiguration(String iotProject, String sitePath,
      String deviceId, String serialNo) {
    configuration = new PubberConfiguration();
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
   * @throws Exception When something is wrong...
   */
  public static void main(String[] args) throws Exception {
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

  private void initializeDevice() {
    ifNotNullThen(configuration.sitePath, SupportedFeatures::writeFeatureFile);
    SupportedFeatures.setFeatureSwap(configuration.options.featureEnableSwap);

    deviceManager = new DeviceManager(this, configuration);

    if (configuration.sitePath != null) {
      siteModel = new SiteModel(configuration.sitePath);
      siteModel.initialize();
      if (configuration.endpoint == null) {
        configuration.endpoint = siteModel.makeEndpointConfig(configuration.iotProject, deviceId);
      }
      if (!siteModel.allDeviceIds().contains(configuration.deviceId)) {
        throw new IllegalArgumentException(
            "Device ID " + configuration.deviceId + " not found in site model");
      }
      Metadata metadata = siteModel.getMetadata(configuration.deviceId);
      processDeviceMetadata(metadata);
      ifNotNullThen(metadata.gateway, g -> createProxyDevices(g.proxy_ids));
    } else if (pubSubClient != null) {
      pullDeviceMessage();
    }

    initializePersistentStore();

    info(format("Starting pubber %s, serial %s, mac %s, gateway %s, options %s",
        configuration.deviceId, configuration.serialNo, configuration.macAddr,
        configuration.gatewayId, optionsString(configuration.options)));

    markStateDirty();
  }

  private void createProxyDevices(List<String> proxyIds) {
    if (proxyIds == null) {
      proxyDevices = new HashMap<>();
      return;
    }
    String firstId = proxyIds.stream().sorted().findFirst().orElseThrow();
    String noProxyId = ifTrueGet(isTrue(options.noProxy), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device " + noProxyId)));
    proxyDevices = proxyIds.stream().filter(not(id -> id.equals(noProxyId)))
        .collect(toMap(k -> k, v -> new ProxyDevice(this, siteModel, v)));
  }

  protected DevicePersistent newDevicePersistent() {
    return new DevicePersistent();
  }

  protected void initializePersistentStore() {
    checkState(persistentData == null, "persistent data already loaded");
    File persistentStore = getPersistentStore();

    if (isTrue(configuration.options.noPersist)) {
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
      configuration.endpoint = persistentData.endpoint;
    } else if (configuration.endpoint != null) {
      info("Loading endpoint into persistent data from configuration");
      persistentData.endpoint = configuration.endpoint;
    } else {
      error(
          "Neither configuration nor persistent data supplies endpoint configuration");
    }

    writePersistentStore();
  }

  private void writePersistentStore() {
    checkState(persistentData != null, "persistent data not defined");
    toJsonFile(getPersistentStore(), persistentData);
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
    requireNonNull(update, "null update message");
    boolean markerClass = update instanceof Class<?>;
    final Object checkValue = markerClass ? null : update;
    final Object checkTarget;
    try {
      checkTarget = markerClass ? ((Class<?>) update).getConstructor().newInstance() : update;
    } catch (Exception e) {
      throw new RuntimeException("Could not create marker instance of class " + update.getClass());
    }
    if (checkTarget == this) {
      publishSynchronousState();
    } else if (checkTarget instanceof SystemState) {
      deviceState.system = (SystemState) checkValue;
    } else if (checkTarget instanceof PointsetState) {
      deviceState.pointset = (PointsetState) checkValue;
    } else if (checkTarget instanceof LocalnetState) {
      deviceState.localnet = (LocalnetState) checkValue;
    } else {
      throw new RuntimeException(
          "Unrecognized update type " + checkTarget.getClass().getSimpleName());
    }
    markStateDirty();
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
    configuration.deviceId = checkNotNull(attributes.deviceId, "deviceId");
    configuration.keyBytes = Base64.getDecoder()
        .decode(checkNotNull(swarm.key_base64, "key_base64"));
    configuration.endpoint = SiteModel.makeEndpointConfig(attributes);
    processDeviceMetadata(
        checkNotNull(swarm.device_metadata, "device_metadata"));
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata instanceof MetadataException metadataException) {
      throw new RuntimeException("While processing metadata file " + metadataException.file,
          metadataException.exception);
    }
    targetSchema = ifNotNullGet(metadata.device_version, SchemaVersion::fromKey);
    ifNotNullThen(targetSchema, version -> warn("Emulating UDMI version " + version.key()));

    if (metadata.cloud != null) {
      configuration.algorithm = catchToNull(() -> metadata.cloud.auth_type.value());
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

    info("Configured with auth_type " + configuration.algorithm);

    deviceManager.setMetadata(metadata);
  }

  @Override
  public void periodicUpdate() {
    try {
      deviceUpdateCount++;
      checkSmokyFailure();
      deferredConfigActions();
      sendEmptyMissingBadEvents();
      flushDirtyState();
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  private void checkSmokyFailure() {
    if (isTrue(configuration.options.smokeCheck)
        && Instant.now().minus(SMOKE_CHECK_TIME).isAfter(deviceStartTime.toInstant())) {
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
    int phase = deviceUpdateCount % MESSAGE_REPORT_INTERVAL;
    if (!isTrue(configuration.options.emptyMissing)
        || (phase >= INVALID_REPLACEMENTS.size() + 2)) {
      return;
    }

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
      warn("Sending badly formatted message type");
      publishDeviceMessage(invalidEvent);
    } else {
      String key = INVALID_KEYS.get(phase - 2);
      InjectedMessage replacedEvent = new InjectedMessage();
      replacedEvent.REPLACE_TOPIC_WITH = key;
      replacedEvent.REPLACE_MESSAGE_WITH = INVALID_REPLACEMENTS.get(key);
      warn("Sending badly formatted message of type " + key);
      publishDeviceMessage(replacedEvent);
    }
    safeSleep(INJECT_MESSAGE_DELAY_MS);
  }

  private void deferredConfigActions() {
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
    }
  }

  private boolean attemptConnection() {
    try {
      if (deviceTarget == null) {
        throw new RuntimeException("Mqtt publisher not initialized");
      }
      connect();
      deviceTarget.startupLatchWait(configLatch, "initial config sync");
      return true;
    } catch (Exception e) {
      error("While waiting for connection start", e);
    }
    error("Attempt failed, retries remaining: " + retriesRemaining.get());
    safeSleep(RESTART_DELAY_MS);
    return false;
  }

  protected void initialize() {
    try {
      initializeDevice();
      initializeMqtt();
      proxyDevices.values().forEach(this::initializeProxyDevice);
    } catch (Exception e) {
      shutdown();
      throw new RuntimeException("While initializing main pubber class", e);
    }
  }

  private void initializeProxyDevice(ProxyDevice proxyDevice) {
    deviceTarget.connect(proxyDevice.deviceId);
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
      deviceTarget = null;
    }
  }

  private void initializeMqtt() {
    checkNotNull(configuration.deviceId, "configuration deviceId not defined");
    if (siteModel != null && configuration.keyFile != null) {
      configuration.keyFile = siteModel.getDeviceKeyFile(configuration.deviceId);
    }
    checkState(deviceTarget == null, "mqttPublisher already defined");
    ensureKeyBytes();
    deviceTarget = new MqttDevice(configuration, this::publisherException);
    ifTrueThen(isGateway(), () -> registerGatewayHandlers(deviceId), this::registerDeviceHandlers);
    String gatewayId = getGatewayId(deviceId, configuration);
    ifNotNullThen(gatewayId, this::registerGatewayHandlers);
    proxyDevices.values().forEach(ProxyDevice::initialize);
    publishDirtyState();
  }

  private boolean isGateway() {
    return !proxyDevices.isEmpty();
  }

  private void registerDeviceHandlers() {
    deviceTarget.registerHandler(CONFIG_TOPIC, this::configHandler, Config.class);
  }

  private void registerGatewayHandlers(String gatewayId) {
    gatewayTarget = getMqttDevice(gatewayId);
    gatewayTarget.registerHandler(CONFIG_TOPIC, this::configHandler, Config.class);
    gatewayTarget.registerHandler(ERRORS_TOPIC, this::errorHandler, GatewayError.class);
  }

  public MqttDevice getMqttDevice(String proxyId) {
    return new MqttDevice(proxyId, deviceTarget);
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
      deviceTarget.connect();
      info("Connection complete.");
      workingEndpoint = toJsonString(configuration.endpoint);
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
      while (retriesRemaining.getAndDecrement() > 0) {
        error("TAP2");
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

  private void publisherHandler(String type, String phase, Throwable cause) {
    if (cause != null) {
      error("Error receiving message " + type, cause);
      if (isTrue(configuration.options.barfConfig)) {
        error("Restarting system because of restart-on-error configuration setting");
        deviceManager.systemLifecycle(SystemMode.RESTART);
      }
    }
    String usePhase = isTrue(options.badCategory) ? "apply" : phase;
    String category = format(SYSTEM_CATEGORY_FORMAT, type, usePhase);
    Entry report = entryFromException(category, cause);
    deviceManager.localLog(report);
    publishLogMessage(report);
    registerSystemStatus(report);
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
    if (isTrue(configuration.options.configStateDelay)) {
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
      debug(format("Config update%s", deviceManager.getTestingTag()), toJsonString(config));
      processConfigUpdate(config);
      configLatch.countDown();
      publisherConfigLog("apply", null);
    } catch (Exception e) {
      publisherConfigLog("apply", e);
    }
    publishConfigStateUpdate();
  }

  void configPreprocess(String targetId, Config config) {
    info(format("Device %s config handler", deviceId));
    String gatewayId = getGatewayId(targetId, configuration);
    String suffix = ifNotNullGet(gatewayId, x -> "_" + targetId, "");
    File configOut = new File(outDir, format("%s%s.json", traceTimestamp("config"), suffix));
    toJsonFile(configOut, config);
  }

  private void processConfigUpdate(Config config) {
    try {
      // Grab this to make state-after-config updates monolithic.
      stateLock.lock();
    } catch (Exception e) {
      throw new RuntimeException("While acquiting state lock", e);
    }

    try {
      if (config != null) {
        if (config.system == null && isTrue(configuration.options.barfConfig)) {
          error("Empty config system block and configured to restart on bad config!");
          deviceManager.systemLifecycle(SystemMode.RESTART);
        }
        GeneralUtils.copyFields(config, deviceConfig, true);
        info(format("%s received config %s", getTimestamp(), isoConvert(config.timestamp)));
        deviceManager.updateConfig(config);
        updateDiscoveryConfig(config.discovery);
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
    deviceState.blobset.blobs.remove(blobId.value());
    if (deviceState.blobset.blobs.isEmpty()) {
      deviceState.blobset = null;
    }
    markStateDirty();
  }

  void maybeRedirectEndpoint() {
    String redirectRegistry = configuration.options.redirectRegistry;
    String currentSignature = toJsonString(configuration.endpoint);
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

    info("New config blob endpoint detected");

    try {
      attemptedEndpoint = extractedSignature;
      endpointState.phase = BlobPhase.APPLY;
      publishSynchronousState();
      resetConnection(extractedSignature);
      persistEndpoint(extractedEndpoint);
      endpointState.phase = BlobPhase.FINAL;
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
      EndpointConfiguration endpoint = deepCopy(configuration.endpoint);
      endpoint.client_id = getClientId(redirectRegistry);
      return toJsonString(endpoint);
    } catch (Exception e) {
      throw new RuntimeException("While getting redirected endpoint");
    }
  }

  private void resetConnection(String targetEndpoint) {
    try {
      configuration.endpoint = fromJsonString(targetEndpoint,
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
    String cloudRegion = SiteModel.parseClientId(configuration.endpoint.client_id).cloudRegion;
    return SiteModel.getClientId(configuration.iotProject, cloudRegion, forRegistry, deviceId);
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
      return JsonUtil.stringify(endpointConfiguration);
    }
  }

  private void updateDiscoveryConfig(DiscoveryConfig config) {
    if (config == null) {
      deviceState.discovery = null;
      return;
    }
    if (deviceState.discovery == null) {
      deviceState.discovery = new DiscoveryState();
    }
    updateDiscoveryEnumeration(config);
    updateDiscoveryScan(config.families);
  }

  private void updateDiscoveryEnumeration(DiscoveryConfig config) {
    Date enumerationGeneration = config.generation;
    if (enumerationGeneration == null) {
      deviceState.discovery.generation = null;
      return;
    }
    if (deviceState.discovery.generation != null
        && !enumerationGeneration.after(deviceState.discovery.generation)) {
      return;
    }
    deviceState.discovery.generation = enumerationGeneration;
    info("Discovery enumeration at " + isoConvert(enumerationGeneration));
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.generation = enumerationGeneration;
    Enumerate enumerate = config.enumerate;
    discoveryEvent.uniqs = ifTrue(enumerate.uniqs, () -> enumeratePoints(configuration.deviceId));
    discoveryEvent.features = ifTrue(enumerate.features, SupportedFeatures::getFeatures);
    discoveryEvent.families = ifTrue(enumerate.families, () -> deviceManager.enumerateFamilies());
    publishDeviceMessage(discoveryEvent);
  }

  private <T> T ifTrue(Boolean condition, Supplier<T> supplier) {
    return isGetTrue(() -> condition) ? supplier.get() : null;
  }

  private Map<String, PointEnumerationEvent> enumeratePoints(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points.entrySet().stream().collect(
        Collectors.toMap(this::getPointUniqKey, this::getPointEnumerationEvent));
  }

  private String getPointUniqKey(Map.Entry<String, PointPointsetModel> entry) {
    return format("%08x", entry.getKey().hashCode());
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
      Date baseGeneration = previousGeneration == null ? deviceStartTime : previousGeneration;
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
      siteModel.forEachMetadata((deviceId, targetMetadata) -> {
        FamilyLocalnetModel familyLocalnetModel = getFamilyLocalnetModel(family, targetMetadata);
        if (familyLocalnetModel != null && familyLocalnetModel.addr != null) {
          DiscoveryEvent discoveryEvent = new DiscoveryEvent();
          discoveryEvent.generation = scanGeneration;
          discoveryEvent.scan_family = family;
          discoveryEvent.scan_addr = deviceId;
          discoveryEvent.families = targetMetadata.localnet.families.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, this::eventForTarget));
          discoveryEvent.families.computeIfAbsent("iot",
              key -> new FamilyDiscoveryEvent()).addr = deviceId;
          if (isGetTrue(() -> deviceConfig.discovery.families.get(family).enumerate)) {
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

  private FamilyDiscoveryEvent eventForTarget(Map.Entry<String, FamilyLocalnetModel> target) {
    FamilyDiscoveryEvent event = new FamilyDiscoveryEvent();
    event.addr = target.getValue().addr;
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
    return isoConvert(getNow());
  }

  private Date isoConvert(String timestamp) {
    try {
      String wrappedString = "\"" + timestamp + "\"";
      return fromJsonString(wrappedString, Date.class);
    } catch (Exception e) {
      throw new RuntimeException("Creating date", e);
    }
  }

  private String isoConvert(Date timestamp) {
    try {
      if (timestamp == null) {
        return "null";
      }
      String dateString = toJsonString(timestamp);
      // Strip off the leading and trailing quotes from the JSON string-as-string representation.
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  private void errorHandler(GatewayError error) {
    info(format("%s for %s: %s", error.error_type, error.device_id, error.description));
  }

  private byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  private void publishLogMessage(Entry logEntry) {
    deviceManager.publishLogMessage(logEntry);
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
    info(format("update state %s last_config %s", isoConvert(deviceState.timestamp),
        isoConvert(deviceState.system.last_config)));
    publishStateMessage(deviceState);
  }

  private void publishStateMessage(Object stateToSend) {
    if (configLatch.getCount() > 0) {
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
      debug(format("State update%s", deviceManager.getTestingTag()),
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
      throw new RuntimeException("While waiting for state send latch", e);
    }
  }

  private boolean shouldSendState() {
    return !isGetTrue(() -> configuration.options.noState);
  }

  private void publishDeviceMessage(Object message) {
    publishDeviceMessage(deviceId, message);
  }

  private void publishDeviceMessage(String targetId, Object message) {
    publishDeviceMessage(targetId, message, null);
  }

  private void publishDeviceMessage(String targetId, Object message, Runnable callback) {
    String topicSuffix = MESSAGE_TOPIC_SUFFIX_MAP.get(message.getClass());
    if (topicSuffix == null) {
      error("Unknown message class " + message.getClass());
      return;
    }

    if (!shouldSendState() && topicSuffix.equals(STATE_TOPIC)) {
      info("Squelching state update as per configuration");
      return;
    }

    if (deviceTarget == null) {
      error("publisher not active");
      return;
    }

    augmentDeviceMessage(message, getNow(), isTrue(options.badVersion));
    Object downgraded = downgradeMessage(message);
    deviceTarget.publish(targetId, topicSuffix, downgraded, callback);
    String messageBase = topicSuffix.replace("/", "_");
    String fileName = traceTimestamp(messageBase) + ".json";
    File messageOut = new File(outDir, fileName);
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
    return messageBase + (isTrue(configuration.options.messageTrace) ? ("_" + timestamp) : "");
  }

  private void cloudLog(String message, Level level) {
    cloudLog(message, level, null);
  }

  private void cloudLog(String message, Level level, String detail) {
    if (deviceManager != null) {
      deviceManager.cloudLog(message, level, detail);
    } else {
      SystemManager.localLog(message, level, getTimestamp(), detail);
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

  static class ExtraPointsetEvent extends PointsetEvent {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }
}
