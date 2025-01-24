package daq.pubber.impl.host;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToFalse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.fromJsonFile;
import static com.google.udmi.util.GeneralUtils.getFileBytes;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.optionsString;
import static com.google.udmi.util.GeneralUtils.toJsonFile;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static udmi.schema.EndpointConfiguration.Protocol.MQTT;

import com.google.udmi.util.CertManager;
import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberFeatures;
import daq.pubber.impl.PubberManager;
import daq.pubber.impl.manager.PubberDeviceManager;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import udmi.lib.base.MqttDevice;
import udmi.lib.client.host.PublisherHost;
import udmi.lib.client.manager.DeviceManager;
import udmi.schema.DevicePersistent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.util.SchemaVersion;

/**
 * IoT Core UDMI Device Emulator.
 */
public class PubberPublisherHost extends PubberManager implements PublisherHost {

  private static final int CONNECT_RETRIES = 10;

  private PubberDeviceManager deviceManager;
  private SiteModel siteModel;

  /**
   * Start an instance from a configuration file.
   *
   * @param configPath Path to configuration file.
   */
  public PubberPublisherHost(String configPath) {
    super(null, loadConfiguration(configPath));
    outDir = createOutDir(null);
  }

  /**
   * Start an instance from explicit args.
   *
   * @param iotProject GCP project
   * @param sitePath   Path to site_model
   * @param deviceId   Device ID to emulate
   * @param serialNo   Serial number of the device
   */
  public PubberPublisherHost(String iotProject, String sitePath, String deviceId, String serialNo) {
    super(null, makeExplicitConfiguration(iotProject, sitePath, deviceId, serialNo));
    outDir = createOutDir(serialNo);
  }

  @Override
  public void initialize() {
    EndpointConfiguration.Protocol protocol = requireNonNullElse(
            ifNotNullGet(config.endpoint, endpoint -> endpoint.protocol), MQTT);
    checkArgument(MQTT.equals(protocol), "Protocol mismatch");
    PublisherHost.super.initialize();
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
            format("Device ID %s not found in site model", config.deviceId));
      }
      Metadata metadata = siteModel.getMetadata(config.deviceId);
      processDeviceMetadata(metadata);
      deviceManager.setSiteModel(siteModel);
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
    checkState(persistentData == null, "Persistent data already loaded");
    File persistentStore = getPersistentStore(siteModel, deviceId);

    if (isTrue(config.options.noPersist)) {
      info(format("Resetting persistent store %s", persistentStore.getAbsolutePath()));
      persistentData = newDevicePersistent();
    } else {
      info(format("Initializing from persistent store %s", persistentStore.getAbsolutePath()));
      persistentData = persistentStore.exists()
          ? fromJsonFile(persistentStore, DevicePersistent.class)
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
      error("Neither configuration nor persistent data supplies endpoint configuration");
    }

    writePersistentStore();
  }

  @Override
  public void writePersistentStore() {
    checkState(persistentData != null, "Persistent data not defined");
    toJsonFile(getPersistentStore(siteModel, deviceId), persistentData);
    warn(format("Updating persistent store:\n%s", stringify(persistentData)));
    deviceManager.setPersistentData(persistentData);
  }

  private void processDeviceMetadata(Metadata metadata) {
    if (metadata instanceof SiteModel.MetadataException metadataException) {
      throw new RuntimeException(
          format("While processing metadata file %s", metadataException.file),
          metadataException.exception);
    }
    targetSchema = ifNotNullGet(metadata.system.device_version, SchemaVersion::fromKey);
    ifNotNullThen(targetSchema,
        version -> warn(format("Emulating UDMI version %s", version.key())));

    ifNullThen(config.serialNo,
        () -> config.serialNo = catchToNull(() -> metadata.system.serial_no));
    ifNullThen(config.gatewayId,
        () -> config.gatewayId = catchToNull(() -> metadata.gateway.gateway_id));

    config.algorithm = config.gatewayId == null
        ? catchToNull(() -> metadata.cloud.auth_type.value())
        : catchToNull(() -> siteModel.getAuthType(config.gatewayId).value());

    info(format("Configured with auth_type %s", config.algorithm));
    isGatewayDevice = catchToFalse(() -> metadata.gateway.proxy_ids != null);
    deviceManager.setMetadata(metadata);
  }

  @Override
  public void startConnection() {
    String nonce = String.valueOf(System.currentTimeMillis());
    warn(format("Starting connection %s with %d", nonce, retriesCount.get()));
    try {
      while (retriesCount.getAndIncrement() < CONNECT_RETRIES) {
        if (attemptConnection()) {
          return;
        }
      }
      throw new RuntimeException("Failed connection attempt after retries");
    } catch (Exception e) {
      throw new RuntimeException("While attempting to start connection", e);
    } finally {
      warn(format("Ending connection %s with %d", nonce, retriesCount.get()));
    }
  }

  private boolean attemptConnection() {
    try {
      deviceManager.stop();
      super.stop();
      disconnectMqtt();
      initializeMqtt();
      registerMessageHandlers();
      connect();
      configLatchWait();
      deviceManager.activate();
      return true;
    } catch (Exception e) {
      error("While waiting for connection start", e);
    }
    error(format("Attempt #%s failed", retriesCount.get()));
    safeSleep(RESTART_DELAY_MS);
    return false;
  }

  @Override
  public void initializeMqtt() {
    checkNotNull(config.deviceId, "Configuration deviceId not defined");
    if (siteModel != null && config.keyFile != null) {
      config.keyFile = siteModel.getDeviceKeyFile(config.deviceId);
    }
    ensureKeyBytes();
    checkState(deviceTarget == null, "MQTT target already defined");
    EndpointConfiguration endpoint = config.endpoint;
    endpoint.gatewayId = ofNullable(config.gatewayId).orElse(config.deviceId);
    endpoint.deviceId = config.deviceId;
    endpoint.noConfigAck = options.noConfigAck;
    endpoint.keyBytes = config.keyBytes;
    endpoint.algorithm = config.algorithm;
    augmentEndpoint(endpoint);
    String keyPassword = siteModel.getDevicePassword(config.deviceId);
    debug(format("Extracted device password from %s", siteModel.getDeviceKeyFile(config.deviceId)));
    String targetDeviceId = getTargetDeviceId(siteModel, config.deviceId);
    CertManager certManager = new CertManager(new File(siteModel.getReflectorDir(), "ca.crt"),
            siteModel.getDeviceDir(targetDeviceId), endpoint.transport, keyPassword, this::info);
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
  public void ensureKeyBytes() {
    if (config.keyBytes == null) {
      checkNotNull(config.keyFile, "Configuration keyFile not defined");
      info(format("Loading device key bytes from %s", config.keyFile));
      config.keyBytes = getFileBytes(config.keyFile);
      config.keyFile = null;
    }
  }

  @Override
  public synchronized void reconnect() {
    while (retriesCount.getAndIncrement() < CONNECT_RETRIES) {
      if (attemptConnection()) {
        return;
      }
    }
    error("Connection retry failed, giving up.");
    deviceManager.systemLifecycle(Operation.SystemMode.TERMINATE);
  }

  @Override
  public String getLogPath() {
    return LOG_PATH;
  }

  public SiteModel getSiteModel() {
    return siteModel;
  }

  // <editor-fold desc="TODO move this part to the library">
  private final Map<String, AtomicInteger> messageCounts = new ConcurrentHashMap<>();
  private final AtomicInteger retriesCount = new AtomicInteger(0);
  private final ReentrantLock stateLock = new ReentrantLock();

  private CountDownLatch configLatch;
  private MqttDevice deviceTarget;
  private long lastStateTimeMs;
  private String workingEndpoint;
  private String attemptedEndpoint;
  private EndpointConfiguration extractedEndpoint;
  private SchemaVersion targetSchema;
  private int deviceUpdateCount = -1;
  private boolean isGatewayDevice;
  private PrintStream logPrintWriter;

  public DevicePersistent persistentData;

  @Override
  public void periodicSchedule(int sec, Runnable runnable) {
    schedulePeriodic(sec, runnable);
  }

  @Override
  public void schedule(long ms, Runnable runnable) {
    executor.schedule(runnable, ms, TimeUnit.MILLISECONDS);
  }

  @Override
  public void periodicUpdate() {
    PublisherHost.super.periodicUpdate();
  }

  @Override
  public void shutdown() {
    PublisherHost.super.shutdown(super::shutdown);
  }

  @Override
  public void debug(String message) {
    PublisherHost.super.debug(message);
  }

  @Override
  public void info(String message) {
    PublisherHost.super.info(message);
  }

  @Override
  public void warn(String message) {
    PublisherHost.super.warn(message);
  }

  @Override
  public void error(String message) {
    PublisherHost.super.error(message);
  }

  @Override
  public void error(String message, Throwable e) {
    PublisherHost.super.error(message, e);
  }

  @Override
  public Map<String, AtomicInteger> getMessageCounts() {
    return messageCounts;
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
    return lastStateTimeMs;
  }

  @Override
  public CountDownLatch getConfigLatch() {
    return configLatch;
  }

  @Override
  public File getOutDir() {
    return outDir;
  }

  @Override
  public Lock getStateLock() {
    return stateLock;
  }

  @Override
  public EndpointConfiguration getExtractedEndpoint() {
    return extractedEndpoint;
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
    return attemptedEndpoint;
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
  public void setDeviceTarget(MqttDevice targetDevice) {
    this.deviceTarget = targetDevice;
  }

  @Override
  public boolean isGatewayDevice() {
    return isGatewayDevice;
  }

  @Override
  public void setWorkingEndpoint(String workingEndpoint) {
    this.workingEndpoint = workingEndpoint;
  }

  @Override
  public void setConfigLatch(CountDownLatch configLatch) {
    this.configLatch = configLatch;
  }

  @Override
  public int getDeviceUpdateCount() {
    return deviceUpdateCount;
  }

  @Override
  public void incrementDeviceUpdateCount() {
    deviceUpdateCount++;
  }

  @Override
  public PrintStream getLogPrintWriter() {
    return logPrintWriter;
  }

  @Override
  public void setLogPrintWriter(PrintStream logPrintWriter) {
    this.logPrintWriter = logPrintWriter;
  }

  @Override
  public String getIotProject() {
    return config.iotProject;
  }

  @Override
  public EndpointConfiguration getEndpoint() {
    return config.endpoint;
  }

  @Override
  public void setEndpoint(EndpointConfiguration endpoint) {
    config.endpoint = endpoint;
  }

  @Override
  public AtomicInteger getRetriesCount() {
    return retriesCount;
  }

  @Override
  public DevicePersistent getPersistentData() {
    return persistentData;
  }
  // </editor-fold>
}
