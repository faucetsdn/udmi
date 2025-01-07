package daq.pubber;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToFalse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.fromJsonFile;
import static com.google.udmi.util.GeneralUtils.fromJsonFileStrict;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.getFileBytes;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.optionsString;
import static com.google.udmi.util.GeneralUtils.setClockSkew;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.GeneralUtils.toJsonFile;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static udmi.schema.EndpointConfiguration.Protocol.MQTT;

import com.google.udmi.util.CertManager;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import daq.pubber.PubberPubSubClient.Bundle;
import java.io.File;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.lib.base.MqttDevice;
import udmi.lib.client.DeviceManager;
import udmi.lib.client.SystemManager;
import udmi.lib.intf.FamilyProvider;
import udmi.schema.Config;
import udmi.schema.DevicePersistent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.util.SchemaVersion;

/**
 * IoT Core UDMI Device Emulator.
 */
public class Pubber extends PubberManager implements PubberUdmiPublisher {

  public static final String PUBBER_OUT = "pubber/out";
  public static final String PERSISTENT_STORE_FILE = "persistent_data.json";
  public static final String PERSISTENT_TMP_FORMAT = "/tmp/pubber_%s_" + PERSISTENT_STORE_FILE;
  public static final String CA_CRT = "ca.crt";

  public static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  private static final String HOSTNAME = System.getenv("HOSTNAME");

  private static final String PUBSUB_SITE = "PubSub";

  private static final Map<String, AtomicInteger> MESSAGE_COUNTS = new ConcurrentHashMap<>();
  private static final int CONNECT_RETRIES = 10;
  private static final AtomicInteger retriesRemaining = new AtomicInteger(CONNECT_RETRIES);
  private static final long RESTART_DELAY_MS = 1000;

  private static final Duration CLOCK_SKEW = Duration.ofMinutes(30);
  private static final int STATE_SPAM_SEC = 5; // Expected config-state response time.

  private final File outDir;
  private final ReentrantLock stateLock = new ReentrantLock();
  public PrintStream logPrintWriter;
  protected DevicePersistent persistentData;
  private CountDownLatch configLatch;
  private MqttDevice deviceTarget;
  private long lastStateTimeMs;
  private PubberPubSubClient pubSubClient;
  private Function<String, Boolean> connectionDone;
  private String workingEndpoint;
  private String attemptedEndpoint;
  private EndpointConfiguration extractedEndpoint;
  private SiteModel siteModel;
  private SchemaVersion targetSchema;
  private int deviceUpdateCount = -1;
  private PubberDeviceManager deviceManager;
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
      pubSubClient = new PubberPubSubClient(iotProject, deviceId);
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

  /**
   * Start a pubber instance with command line args.
   *
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

  @Override
  public FamilyProvider getLocalnetProvider(String family) {
    return deviceManager.getLocalnetProvider(family);
  }

  @Override
  public void initializeDevice() {
    deviceManager = new PubberDeviceManager(this, config);

    if (config.sitePath != null) {
      PubberFeatures.writeFeatureFile(config.sitePath, deviceManager);
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

    PubberFeatures.setFeatureSwap(config.options.featureEnableSwap);
    initializePersistentStore();

    info(format("Starting pubber %s, serial %s, mac %s, gateway %s, options %s",
        config.deviceId, config.serialNo, config.macAddr,
        config.gatewayId, optionsString(config.options)));

    markStateDirty();
  }

  @Override
  public void initializePersistentStore() {
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

  @Override
  public void writePersistentStore() {
    checkState(persistentData != null, "persistent data not defined");
    toJsonFile(getPersistentStore(), persistentData);
    warn("Updating persistent store:\n" + stringify(persistentData));
    deviceManager.setPersistentData(persistentData);
  }

  private File getPersistentStore() {
    return siteModel == null ? new File(format(PERSISTENT_TMP_FORMAT, deviceId)) :
        new File(siteModel.getDeviceWorkingDir(deviceId), PERSISTENT_STORE_FILE);
  }

  @Override
  public void markStateDirty(long delayMs) {
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

        return;
      } catch (Exception e) {
        error("Error pulling swarm message", e);
        safeSleep(INITIAL_THRESHOLD_SEC);
      }
    }
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata instanceof MetadataException metadataException) {
      throw new RuntimeException("While processing metadata file " + metadataException.file,
          metadataException.exception);
    }
    targetSchema = ifNotNullGet(metadata.system.device_version, SchemaVersion::fromKey);
    ifNotNullThen(targetSchema, version -> warn("Emulating UDMI version " + version.key()));

    if (config.serialNo == null) {
      config.serialNo = catchToNull(() -> metadata.system.serial_no);
    }
    if (config.gatewayId == null) {
      config.gatewayId = catchToNull(() -> metadata.gateway.gateway_id);
    }

    config.algorithm = config.gatewayId == null
        ? catchToNull(() -> metadata.cloud.auth_type.value())
        : catchToNull(() -> siteModel.getAuthType(config.gatewayId).value());

    info("Configured with auth_type " + config.algorithm);

    isGatewayDevice = catchToFalse(() -> metadata.gateway.proxy_ids != null);

    deviceManager.setMetadata(metadata);
  }

  @Override
  public synchronized void periodicUpdate() {
    try {
      deviceUpdateCount++;
      checkSmokyFailure();
      deferredConfigActions();
      sendEmptyMissingBadEvents();
      maybeTweakState();
      flushDirtyState();
    } catch (Exception e) {
      error("Fatal error during execution", e);
      resetConnection(getWorkingEndpoint());
    }
  }

  @Override
  public void startConnection(Function<String, Boolean> connectionDone) {
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
      super.stop();
      if (deviceTarget == null || !deviceTarget.isActive()) {
        warn("Mqtt publisher not active");
      }
      disconnectMqtt();
      initializeMqtt();
      registerMessageHandlers();
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

  @Override
  public void shutdown() {
    warn("Initiating device shutdown");

    if (deviceState.system != null && deviceState.system.operation != null) {
      deviceState.system.operation.mode = SystemMode.SHUTDOWN;
    }

    if (isConnected()) {
      captureExceptions("Publishing shutdown state", this::publishSynchronousState);
    }
    isConnected = false;
    ifNotNullThen(deviceManager, dm -> captureExceptions("Device manager shutdown", dm::shutdown));
    captureExceptions("Pubber sender shutdown", super::shutdown);
    captureExceptions("Disconnecting mqtt", this::disconnectMqtt);
  }

  @Override
  public PubberConfiguration getConfig() {
    return config;
  }

  @Override
  public void initializeMqtt() {
    checkNotNull(config.deviceId, "configuration deviceId not defined");
    if (siteModel != null && config.keyFile != null) {
      config.keyFile = siteModel.getDeviceKeyFile(config.deviceId);
    }
    ensureKeyBytes();
    checkState(deviceTarget == null, "mqttPublisher already defined");
    EndpointConfiguration endpoint = config.endpoint;
    endpoint.gatewayId = ofNullable(config.gatewayId).orElse(config.deviceId);
    endpoint.deviceId = config.deviceId;
    endpoint.noConfigAck = options.noConfigAck;
    endpoint.keyBytes = config.keyBytes;
    endpoint.algorithm = config.algorithm;
    augmentEndpoint(endpoint);
    String keyPassword = siteModel.getDevicePassword(config.deviceId);
    debug("Extracted device password from " + siteModel.getDeviceKeyFile(config.deviceId));
    String targetDeviceId = getTargetDeviceId(siteModel, config.deviceId);
    CertManager certManager = new CertManager(new File(siteModel.getReflectorDir(), CA_CRT),
        siteModel.getDeviceDir(targetDeviceId), endpoint.transport, keyPassword,
        this::info);
    deviceTarget = new MqttDevice(endpoint, this::publisherException, certManager);
    publishDirtyState();
  }

  protected void augmentEndpoint(EndpointConfiguration endpoint) {
  }

  private String getTargetDeviceId(SiteModel siteModel, String deviceId) {
    Metadata metadata = siteModel.getMetadata(deviceId);
    return ofNullable(catchToNull(() -> metadata.gateway.gateway_id)).orElse(deviceId);
  }

  @Override
  public byte[] ensureKeyBytes() {
    if (config.keyBytes == null) {
      checkNotNull(config.keyFile, "configuration keyFile not defined");
      info("Loading device key bytes from " + config.keyFile);
      config.keyBytes = getFileBytes(config.keyFile);
      config.keyFile = null;
    }
    return (byte[]) config.keyBytes;
  }

  @Override
  public synchronized void reconnect() {
    while (retriesRemaining.getAndDecrement() > 0) {
      if (attemptConnection()) {
        return;
      }
    }
    error("Connection retry failed, giving up.");
    deviceManager.systemLifecycle(SystemMode.TERMINATE);
  }

  @Override
  public void persistEndpoint(EndpointConfiguration endpoint) {
    notice("Persisting connection endpoint");
    persistentData.endpoint = endpoint;
    writePersistentStore();
  }

  @Override
  public synchronized void resetConnection(String targetEndpoint) {
    try {
      config.endpoint = fromJsonString(targetEndpoint,
          EndpointConfiguration.class);
      retriesRemaining.set(CONNECT_RETRIES);
      startConnection(connectionDone);
    } catch (Exception e) {
      throw new RuntimeException("While resetting connection", e);
    }
  }

  @Override
  public String traceTimestamp(String messageBase) {
    int serial = MESSAGE_COUNTS.computeIfAbsent(messageBase, key -> new AtomicInteger())
        .incrementAndGet();
    String timestamp = getTimestamp().replace("Z", format(".%03dZ", serial));
    return messageBase + (isTrue(config.options.messageTrace) ? ("_" + timestamp) : "");
  }

  private void trace(String message) {
    cloudLog(message, Level.TRACE);
  }

  @Override
  public void debug(String message) {
    cloudLog(message, Level.DEBUG);
  }

  @Override
  public void info(String message) {
    cloudLog(message, Level.INFO);
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


  @Override
  public AtomicBoolean getStateDirty() {
    return stateDirty;
  }

  @Override
  public SchemaVersion getTargetSchema() {
    return targetSchema;
  }

  @Override
  public void setLastStateTimeMs(long lastStateTimeMs) {
    this.lastStateTimeMs = lastStateTimeMs;
  }

  @Override
  public long getLastStateTimeMs() {
    return this.lastStateTimeMs;
  }

  @Override
  public CountDownLatch getConfigLatch() {
    return this.configLatch;
  }

  @Override
  public File getOutDir() {
    return this.outDir;
  }

  @Override
  public PubberOptions getOptions() {
    return options;
  }

  @Override
  public Lock getStateLock() {
    return stateLock;
  }

  @Override
  public EndpointConfiguration getExtractedEndpoint() {
    return this.extractedEndpoint;
  }

  @Override
  public void setExtractedEndpoint(EndpointConfiguration endpointConfiguration) {
    this.extractedEndpoint = endpointConfiguration;
  }

  @Override
  public String getWorkingEndpoint() {
    return workingEndpoint;
  }

  @Override
  public void setAttemptedEndpoint(String attemptedEndpoint) {
    this.attemptedEndpoint = attemptedEndpoint;
  }

  @Override
  public String getAttemptedEndpoint() {
    return this.attemptedEndpoint;
  }

  @Override
  public Config getDeviceConfig() {
    return deviceConfig;
  }

  @Override
  public DeviceManager getDeviceManager() {
    return deviceManager;
  }

  @Override
  public MqttDevice getDeviceTarget() {
    return deviceTarget;
  }

  @Override
  public void setDeviceTarget(MqttDevice deviceTarget) {
    this.deviceTarget = deviceTarget;
  }

  @Override
  public boolean isGatewayDevice() {
    return isGatewayDevice;
  }

  @Override
  public void setWorkingEndpoint(String jsonString) {
    this.workingEndpoint = jsonString;
  }

  @Override
  public void setConfigLatch(CountDownLatch countDownLatch) {
    this.configLatch = countDownLatch;
  }

  @Override
  public boolean isConnected() {
    return isConnected;
  }

  @Override
  public int getDeviceUpdateCount() {
    return deviceUpdateCount;
  }

  @Override
  public Map<Level, Consumer<String>> getLogMap() {
    return SystemManager.getLogMap().apply(LOG);
  }

  public SiteModel getSiteModel() {
    return siteModel;
  }
}
