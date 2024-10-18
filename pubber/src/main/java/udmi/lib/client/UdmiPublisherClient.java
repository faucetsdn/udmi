package udmi.lib.client;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isGetTrue;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.GeneralUtils.toJsonFile;
import static com.google.udmi.util.GeneralUtils.toJsonString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.parseJson;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.lib.ManagerBase.WAIT_TIME_SEC;
import static udmi.lib.ManagerBase.updateStateHolder;
import static udmi.lib.MqttDevice.CONFIG_TOPIC;
import static udmi.lib.MqttDevice.ERRORS_TOPIC;
import static udmi.lib.MqttDevice.STATE_TOPIC;
import static udmi.lib.MqttPublisher.DEFAULT_CONFIG_WAIT_SEC;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.MessageDowngrader;
import com.google.udmi.util.SchemaVersion;
import com.google.udmi.util.SiteModel;
import java.io.File;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import udmi.lib.GatewayError;
import udmi.lib.ManagerHost;
import udmi.lib.MqttDevice;
import udmi.lib.MqttPublisher.FakeTopic;
import udmi.lib.MqttPublisher.InjectedMessage;
import udmi.lib.MqttPublisher.InjectedState;
import udmi.lib.client.PointsetManagerClient.ExtraPointsetEvent;
import udmi.lib.client.SystemManagerClient.ExtraSystemState;
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
import udmi.schema.Entry;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PointsetEvents;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.schema.State;
import udmi.schema.SystemEvents;
import udmi.schema.SystemState;

/**
 * UDMI publisher client.
 */
public interface UdmiPublisherClient extends ManagerHost {

  String DATA_URL_JSON_BASE64 = "data:application/json;base64,";

  String UDMI_VERSION = SchemaVersion.CURRENT.key();
  Date DEVICE_START_TIME = getRoundedStartTime();
  String BROKEN_VERSION = "1.4.";
  int STATE_THROTTLE_MS = 2000;
  int DEFAULT_REPORT_SEC = 10;
  String SYSTEM_CATEGORY_FORMAT = "system.%s.%s";
  ImmutableMap<Class<?>, String> MESSAGE_TOPIC_SUFFIX_MAP =
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
  Map<String, String> INVALID_REPLACEMENTS = ImmutableMap.of(
      "events/blobset", "\"\"",
      "events/discovery", "{}",
      "events/gateway", "{ \"testing\": \"This is prematurely terminated",
      "events/mapping", "{ NOT VALID JSON!"
  );
  List<String> INVALID_KEYS = new ArrayList<>(INVALID_REPLACEMENTS.keySet());
  String CORRUPT_STATE_MESSAGE = "!&*@(!*&@!";
  long INJECT_MESSAGE_DELAY_MS = 1000; // Delay to make sure testing is stable.
  int FORCED_STATE_TIME_MS = 10000;
  Duration SMOKE_CHECK_TIME = Duration.ofMinutes(5);
  String RAW_EVENT_TOPIC = "events";
  String SYSTEM_EVENT_TOPIC = "events/system";
  int MESSAGE_REPORT_INTERVAL = 10;

  State getDeviceState();

  Config getDeviceConfig();

  DeviceManagerClient getDeviceManager();

  MqttDevice getDeviceTarget();

  void setDeviceTarget(MqttDevice deviceTarget);

  boolean isGatewayDevice();
  
  static String getEventsSuffix(String suffixSuffix) {
    return MqttDevice.EVENTS_TOPIC + "/" + suffixSuffix;
  }

  /**
   * Retrieves the start time of the current second,
   * with milliseconds removed for precise comparison.
   */
  static Date getRoundedStartTime() {
    long timestamp = getNow().getTime();
    // Remove ms so that rounded conversions preserve equality.
    return new Date(timestamp - (timestamp % 1000));
  }

  /**
   * Acquires and validates blob data from a given URL encoded in Base64 format.
   */
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

  /**
   * Augments a given {@code message} object with the current timestamp and version information.
   *
   */
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

  default DevicePersistent newDevicePersistent() {
    return new DevicePersistent();
  }

  default void markStateDirty() {
    markStateDirty(0);
  }

  void markStateDirty(long delayMs);

  /**
   * Publishes a dirty state by resetting the internal state flag to clean.
   *
   */
  default void publishDirtyState() {
    if (getStateDirty().get()) {
      debug("Publishing dirty state block");
      markStateDirty(0);
    }
  }

  @Override
  default void update(Object update) {
    if (update == null) {
      publishSynchronousState();
      return;
    }
    updateStateHolder(getDeviceState(), update);
    markStateDirty();
    if (update instanceof SystemState) {
      ifTrueThen(getOptions().dupeState, this::sendPartialState);
    }
  }

  private void sendPartialState() {
    State dupeState = new State();
    dupeState.system = getDeviceState().system;
    dupeState.timestamp = getDeviceState().timestamp;
    dupeState.version = getDeviceState().version;
    publishStateMessage(dupeState);
  }

  private void sendDupeState() {
    State dupeState = new State();
    dupeState.system = getDeviceState().system;
    dupeState.timestamp = getDeviceState().timestamp;
    dupeState.version = getDeviceState().version;
    publishStateMessage(dupeState);
  }

  @Override
  default void publish(Object message) {
    publishDeviceMessage(message);
  }

  default void publish(String targetId, Object message) {
    publishDeviceMessage(targetId, message);
  }

  /**
   * Executes the provided {@code Runnable} and captures any exceptions that occur by
   * calling {@link #error(String, Throwable)} with the action name and the caught exception.
   *
   */
  default void captureExceptions(String action, Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      error(action, e);
    }
  }

  /**
   * Disconnects the MQTT device target if it is not null. Closes and shuts down the MQTT publisher,
   * then sets the device target to null.
   */
  default void disconnectMqtt() {
    if (getDeviceTarget() != null) {
      captureExceptions("closing mqtt publisher", () -> getDeviceTarget().close());
      captureExceptions("shutting down mqtt publisher executor",
          () -> getDeviceTarget().shutdown());
      setDeviceTarget(null);
    }
  }

  /**
   * Registers the necessary message handlers for device configuration and error handling based on
   * whether the device is a gateway or a proxy device.
   */
  default void registerMessageHandlers() {
    getDeviceTarget().registerHandler(CONFIG_TOPIC, this::configHandler, Config.class);
    String gatewayId = getGatewayId(getDeviceId(), getConfig());
    if (isGatewayDevice()) {
      // In this case, this is the gateway so register the appropriate error handler directly.
      getDeviceTarget().registerHandler(ERRORS_TOPIC, this::errorHandler, GatewayError.class);
    } else if (gatewayId != null) {
      // In this case, this is a proxy device with a gateway, so register handlers accordingly.
      MqttDevice gatewayTarget = new MqttDevice(gatewayId, getDeviceTarget());
      gatewayTarget.registerHandler(CONFIG_TOPIC, this::gatewayHandler, Config.class);
      gatewayTarget.registerHandler(ERRORS_TOPIC, this::errorHandler, GatewayError.class);
    }
  }


  default MqttDevice getMqttDevice(String proxyId) {
    return new MqttDevice(proxyId, getDeviceTarget());
  }

  /**
   * Connects to the device target and initializes configuration latch.
   */
  default void connect() {
    try {
      warn("Creating new config latch for " + getDeviceId());
      setConfigLatch(new CountDownLatch(1));
      getDeviceTarget().connect();
      info("Connection complete.");
      setWorkingEndpoint(toJsonString(getConfig().endpoint));
    } catch (Exception e) {
      throw new RuntimeException("Connection error", e);
    }
  }

  void setWorkingEndpoint(String jsonString);

  void setConfigLatch(CountDownLatch countDownLatch);

  default void publisherConfigLog(String phase, Exception e, String targetId) {
    publisherHandler("config", phase, e, targetId);
  }

  /**
   * Handles the reception of a message with an optional error.
   *
   * @param type The type of the message being received.
   * @param phase A string representing the current processing phase of the message.
   * @param cause An optional Throwable that represents the error causing the failure,
   *             or null if there is no error.
   * @param targetId The ID of the target to which the log message should be published.
   */
  default void publisherHandler(String type, String phase, Throwable cause, String targetId) {
    if (cause != null) {
      error("Error receiving message " + type, cause);
      if (isTrue(getConfig().options.barfConfig)) {
        error("Restarting system because of restart-on-error configuration setting");
        getDeviceManager().systemLifecycle(SystemMode.RESTART);
      }
    }
    String usePhase = isTrue(getOptions().badCategory) ? "apply" : phase;
    String category = format(SYSTEM_CATEGORY_FORMAT, type, usePhase);
    Entry report = entryFromException(category, cause);
    getDeviceManager().localLog(report);
    publishLogMessage(report, targetId);
    ifTrueThen(getDeviceId().equals(targetId), () -> registerSystemStatus(report));
  }

  void error(String s);

  default void registerSystemStatus(Entry report) {
    getDeviceState().system.status = report;
    markStateDirty();
  }

  /**
   * Issue a state update in response to a received config message. This will optionally add a
   * synthetic delay in so that testing infrastructure can test that related sequence tests handle
   * this case appropriately.
   */
  default void publishConfigStateUpdate() {
    if (isTrue(getConfig().options.configStateDelay)) {
      delayNextStateUpdate();
    }
    publishAsynchronousState();
  }

  /**
   * Delays updating the next state by calculating a synthetic last state time that includes
   * an optional delay.
   */
  default void delayNextStateUpdate() {
    // Calculate a synthetic last state time that factors in the optional delay.
    long syntheticType = System.currentTimeMillis() - STATE_THROTTLE_MS + FORCED_STATE_TIME_MS;
    // And use the synthetic time iff it's later than the actual last state time.
    setLastStateTimeMs(Math.max(getLastStateTimeMs(), syntheticType));
  }

  void setLastStateTimeMs(long lastStateTimeMs);

  long getLastStateTimeMs();

  /**
   * Creates an {@link Entry} object from a given exception and category.
   *
   * @param category the category for the log entry
   * @param e the exception from which to create the log entry; can be null
   * @return a new {@link Entry} object based on the provided parameters
   */
  default Entry entryFromException(String category, Throwable e) {
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
      configPreprocess(getDeviceId(), config);
      debug(format("Config update %s%s", getDeviceId(), getDeviceManager().getTestingTag()),
          toJsonString(config));
      processConfigUpdate(config);
      if (getConfigLatch().getCount() > 0) {
        warn("Received config for config latch " + getDeviceId());
        getConfigLatch().countDown();
      }
      publisherConfigLog("apply", null, getDeviceId());
    } catch (Exception e) {
      publisherConfigLog("apply", e, getDeviceId());
    }
    publishConfigStateUpdate();
  }

  CountDownLatch getConfigLatch();

  private void gatewayHandler(Config gatewayConfig) {
    warn("Ignoring configuration for gateway " + getGatewayId(getDeviceId(), getConfig()));
  }

  private void errorHandler(GatewayError error) {
    warn(format("%s for %s: %s", error.error_type, error.device_id, error.description));
  }

  /**
   * Configures the preprocessing for a given target ID using the provided configuration message.
   *
   * @param targetId The unique identifier of the target to be configured.
   * @param configMsg The configuration message to be processed.
   */
  default void configPreprocess(String targetId, Config configMsg) {
    String gatewayId = getGatewayId(targetId, getConfig());
    String suffix = ifNotNullGet(gatewayId, x -> "_" + targetId, "");
    String deviceType = ifNotNullGet(gatewayId, x -> "Proxy", "Device");
    info(format("%s %s config handler", deviceType, targetId));
    File configOut = new File(getOutDir(), format("%s.json", traceTimestamp("config" + suffix)));
    toJsonFile(configOut, configMsg);
  }

  File getOutDir();

  private void processConfigUpdate(Config configMsg) {
    try {
      // Grab this to make state-after-config updates monolithic.
      getStateLock().lock();
    } catch (Exception e) {
      throw new RuntimeException("While acquiring state lock", e);
    }

    try {
      if (configMsg != null) {
        if (configMsg.system == null && isTrue(getConfig().options.barfConfig)) {
          error("Empty config system block and configured to restart on bad config!");
          getDeviceManager().systemLifecycle(SystemMode.RESTART);
        }
        GeneralUtils.copyFields(configMsg, getDeviceConfig(), true);
        info(format("%s received config %s", getTimestamp(), isoConvert(configMsg.timestamp)));
        getDeviceManager().updateConfig(configMsg);
        extractEndpointBlobConfig();
      } else {
        info(getTimestamp() + " defaulting empty config");
      }
      updateInterval(DEFAULT_REPORT_SEC);
    } finally {
      getStateLock().unlock();
    }
  }

  void updateInterval(Integer defaultReportSec);

  /**
   * Check smoky failure.
   */
  default void checkSmokyFailure() {
    if (isTrue(getConfig().options.smokeCheck)
        && Instant.now().minus(SMOKE_CHECK_TIME).isAfter(DEVICE_START_TIME.toInstant())) {
      error(format("Smoke check failed after %sm, terminating run.",
          SMOKE_CHECK_TIME.getSeconds() / 60));
      getDeviceManager().systemLifecycle(SystemMode.TERMINATE);
    }
  }


  /**
   * Deferred config actions.
   */
  default void deferredConfigActions() {
    if (!isConnected()) {
      return;
    }

    getDeviceManager().maybeRestartSystem();

    // Do redirect after restart system check, since this might take a long time.
    maybeRedirectEndpoint();
  }


  /**
   * For testing, if configured, send a slate of bad messages for testing by the message handling
   * infrastructure. Uses the sekrit REPLACE_MESSAGE_WITH field to sneak bad output into the pipe.
   * E.g., Will send a message with "{ INVALID JSON!" as a message payload. Inserts a delay before
   * each message sent to stabilize the output order for testing purposes.
   */
  default void sendEmptyMissingBadEvents() {
    if (!isTrue(getConfig().options.emptyMissing)) {
      return;
    }

    final int explicitPhases = 3;

    checkState(MESSAGE_REPORT_INTERVAL > explicitPhases + INVALID_REPLACEMENTS.size() + 1,
        "not enough space for hacky messages");
    int phase = (getDeviceUpdateCount() + MESSAGE_REPORT_INTERVAL / 2) % MESSAGE_REPORT_INTERVAL;

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

  /**
   * Maybe tweak state.
   */
  default void maybeTweakState() {
    if (!isTrue(getOptions().tweakState)) {
      return;
    }
    int phase = getDeviceUpdateCount() % 2;
    String randomValue = format("%04x", System.currentTimeMillis() % 0xffff);
    if (phase == 0) {
      catchToNull(() -> getDeviceState().system.software.put("random", randomValue));
    } else if (phase == 1) {
      ifNotNullThen(getDeviceState().pointset, state -> state.state_etag = randomValue);
    }
  }

  Lock getStateLock();

  // TODO: Consider refactoring this to either return or change an instance variable, not both.
  /**
   * Extracts the endpoint configuration blob from the device configuration.
   *
   * @return The extracted {@link EndpointConfiguration}
   */
  default EndpointConfiguration extractEndpointBlobConfig() {
    setExtractedEndpoint(null);
    if (getDeviceConfig().blobset == null) {
      return null;
    }
    try {
      String iotConfig = extractConfigBlob(IOT_ENDPOINT_CONFIG.value());
      setExtractedEndpoint(fromJsonString(iotConfig, EndpointConfiguration.class));
      if (getExtractedEndpoint() != null) {
        if (getDeviceConfig().blobset.blobs.containsKey(IOT_ENDPOINT_CONFIG.value())) {
          BlobBlobsetConfig config = getDeviceConfig()
              .blobset.blobs.get(IOT_ENDPOINT_CONFIG.value());
          getExtractedEndpoint().generation = config.generation;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While extracting endpoint blob config", e);
    }
    return getExtractedEndpoint();
  }

  EndpointConfiguration getExtractedEndpoint();

  void setExtractedEndpoint(EndpointConfiguration endpointConfiguration);

  private void removeBlobsetBlobState(SystemBlobsets blobId) {
    if (getDeviceState().blobset == null) {
      return;
    }

    if (getDeviceState().blobset.blobs.remove(blobId.value()) == null) {
      return;
    }

    if (getDeviceState().blobset.blobs.isEmpty()) {
      getDeviceState().blobset = null;
    }

    markStateDirty();
  }

  /**
   * Attempts to redirect the endpoint based on configuration settings and handles redirection
   * logic.
   */
  default void maybeRedirectEndpoint() {
    String redirectRegistry = getConfig().options.redirectRegistry;
    String currentSignature = toJsonString(getConfig().endpoint);
    String extractedSignature =
        redirectRegistry == null ? toJsonString(getExtractedEndpoint())
            : redirectedEndpoint(redirectRegistry);

    if (extractedSignature == null) {
      setAttemptedEndpoint(null);
      removeBlobsetBlobState(IOT_ENDPOINT_CONFIG);
      return;
    }

    BlobBlobsetState endpointState = ensureBlobsetState(IOT_ENDPOINT_CONFIG);

    if (extractedSignature.equals(currentSignature)
        || extractedSignature.equals(getAttemptedEndpoint())) {
      return; // No need to redirect anything!
    }

    if (getExtractedEndpoint() != null) {
      if (!Objects.equals(endpointState.generation, getExtractedEndpoint().generation)) {
        notice("Starting new endpoint generation");
        endpointState.phase = null;
        endpointState.status = null;
        endpointState.generation = getExtractedEndpoint().generation;
      }

      if (getExtractedEndpoint().error != null) {
        setAttemptedEndpoint(extractedSignature);
        endpointState.phase = BlobPhase.FINAL;
        Exception applyError = new RuntimeException(getExtractedEndpoint().error);
        endpointState.status = exceptionStatus(applyError, Category.BLOBSET_BLOB_APPLY);
        publishSynchronousState();
        return;
      }
    }

    info("New config blob endpoint detected:\n" + stringify(parseJson(extractedSignature)));

    try {
      setAttemptedEndpoint(extractedSignature);
      endpointState.phase = BlobPhase.APPLY;
      publishSynchronousState();
      resetConnection(extractedSignature);
      persistEndpoint(getExtractedEndpoint());
      endpointState.phase = BlobPhase.FINAL;
      markStateDirty();
    } catch (Exception e) {
      try {
        error("Reconfigure failed, attempting connection to last working endpoint", e);
        endpointState.phase = BlobPhase.FINAL;
        endpointState.status = exceptionStatus(e, Category.BLOBSET_BLOB_APPLY);
        resetConnection(getWorkingEndpoint());
        publishAsynchronousState();
        notice("Endpoint connection restored to last working endpoint");
      } catch (Exception e2) {
        throw new RuntimeException("While restoring working endpoint", e2);
      }
      error("While redirecting connection endpoint", e);
    }
  }

  String getWorkingEndpoint();

  void setAttemptedEndpoint(String s);

  String getAttemptedEndpoint();

  default void notice(String message) {
    cloudLog(message, Level.NOTICE);
  }

  private String redirectedEndpoint(String redirectRegistry) {
    try {
      EndpointConfiguration endpoint = deepCopy(getConfig().endpoint);
      endpoint.client_id = getClientId(redirectRegistry);
      return toJsonString(endpoint);
    } catch (Exception e) {
      throw new RuntimeException("While getting redirected endpoint", e);
    }
  }

  /**
   * Creates an {@link Entry} object with error details from the given exception and category.
   *
   */
  default Entry exceptionStatus(Exception e, String category) {
    Entry entry = new Entry();
    entry.message = e.getMessage();
    entry.detail = stackTraceString(e);
    entry.category = category;
    entry.level = Level.ERROR.value();
    entry.timestamp = getNow();
    return entry;
  }

  /**
   * Ensures the {@code blobset} and its {@code blobs} map are initialized in the device state.
   *
   */
  default BlobBlobsetState ensureBlobsetState(SystemBlobsets iotEndpointConfig) {
    getDeviceState().blobset = ofNullable(getDeviceState().blobset).orElseGet(BlobsetState::new);
    getDeviceState().blobset.blobs = ofNullable(getDeviceState().blobset.blobs)
        .orElseGet(HashMap::new);
    return getDeviceState().blobset.blobs.computeIfAbsent(iotEndpointConfig.value(),
        key -> new BlobBlobsetState());
  }

  default String getClientId(String forRegistry) {
    String cloudRegion = SiteModel.parseClientId(getConfig().endpoint.client_id).cloudRegion;
    return SiteModel.getClientId(getConfig().iotProject, cloudRegion, forRegistry, getDeviceId());
  }

  /**
   * Extracts the configuration blob with the specified name, if it exists and is in the final
   * phase.
   *
   */
  default String extractConfigBlob(String blobName) {
    // TODO: Refactor to get any blob meta parameters.
    try {
      if (getDeviceConfig() == null || getDeviceConfig().blobset == null
          || getDeviceConfig().blobset.blobs == null) {
        return null;
      }
      BlobBlobsetConfig blobBlobsetConfig = getDeviceConfig().blobset.blobs.get(blobName);
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

  default void publishLogMessage(Entry logEntry, String targetId) {
    getDeviceManager().publishLogMessage(logEntry, targetId);
  }

  /**
   * Publishes the current state asynchronously, deferring if necessary to ensure that the state
   * update does not occur too frequently.
   *
   */
  default void publishAsynchronousState() {
    if (getStateLock().tryLock()) {
      try {
        long soonestAllowedStateUpdate = getLastStateTimeMs() + STATE_THROTTLE_MS;
        long delay = soonestAllowedStateUpdate - System.currentTimeMillis();
        debug(format("State update defer %dms", delay));
        if (delay > 0) {
          markStateDirty(delay);
        } else {
          publishStateMessage();
        }
      } finally {
        getStateLock().unlock();
      }
    } else {
      markStateDirty(-1);
    }
  }

  /**
   * Publishes the current state synchronously.
   * This method locks the state before publishing it to ensure thread safety,
   * and handles exceptions by wrapping them in a RuntimeException.
   */
  default void publishSynchronousState() {
    try {
      getStateLock().lock();
      publishStateMessage();
    } catch (Exception e) {
      throw new RuntimeException("While sending synchronous state", e);
    } finally {
      getStateLock().unlock();
    }
  }

  default boolean publisherActive() {
    return getDeviceTarget() != null && getDeviceTarget().isActive();
  }

  /**
   * Publishes the current device state as a message to the publisher if the publisher is active.
   * If the publisher is not active, it marks the state as dirty and returns without publishing.
   */
  default void publishStateMessage() {
    if (!publisherActive()) {
      markStateDirty(-1);
      return;
    }
    getStateDirty().set(false);
    getDeviceState().timestamp = getNow();
    info(format("Update state %s last_config %s", isoConvert(getDeviceState().timestamp),
        isoConvert(getDeviceState().system.last_config)));
    publishStateMessage(isTrue(getOptions().badState) ? getDeviceState().system : getDeviceState());
  }

  /**
   * Publishes the given state message to a designated location.
  *
   * @param stateToSend The state object to be published.
   */
  default void publishStateMessage(Object stateToSend) {
    try {
      getStateLock().lock();
      publishStateMessageRaw(stateToSend);
    } finally {
      getStateLock().unlock();
    }
  }

  AtomicBoolean getStateDirty();

  /**
   * Publishes the current device state to a remote service.
   *
   * @param stateToSend The current device state to be published.
   */
  default void publishStateMessageRaw(Object stateToSend) {
    if (getConfigLatch() == null || getConfigLatch().getCount() > 0) {
      warn("Dropping state update until config received...");
      return;
    }

    long delay = getLastStateTimeMs() + STATE_THROTTLE_MS - System.currentTimeMillis();
    if (delay > 0) {
      warn(format("State update delay %dms", delay));
      safeSleep(delay);
    }

    setLastStateTimeMs(System.currentTimeMillis());
    CountDownLatch latch = new CountDownLatch(1);

    try {
      debug(format("State update %s%s", getDeviceId(), getDeviceManager().getTestingTag()),
          toJsonString(stateToSend));
    } catch (Exception e) {
      throw new RuntimeException("While converting new device state", e);
    }

    publishDeviceMessage(getDeviceId(), stateToSend, () -> {
      setLastStateTimeMs(System.currentTimeMillis());
      latch.countDown();
    });
    try {
      if (shouldSendState() && !latch.await(WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException("Timeout waiting for state send");
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While waiting for %s state send latch", getDeviceId()), e);
    }
  }

  default boolean shouldSendState() {
    return !isGetTrue(() -> getConfig().options.noState);
  }

  default void publishDeviceMessage(Object message) {
    publishDeviceMessage(getDeviceId(), message);
  }

  private void publishDeviceMessage(String targetId, Object message) {
    publishDeviceMessage(targetId, message, null);
  }

  /**
   * Publishes a device message to the appropriate topic and handles squelching of state updates
   * if configured.
   */
  default void publishDeviceMessage(String targetId, Object message, Runnable callback) {
    if (getDeviceTarget() == null) {
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

    if (getDeviceTarget() == null) {
      error("publisher not active");
      return;
    }

    if (isTrue(getOptions().noFolder) && topicSuffix.equals(SYSTEM_EVENT_TOPIC)) {
      topicSuffix = RAW_EVENT_TOPIC;
    }

    augmentDeviceMessage(message, getNow(), isTrue(getOptions().badVersion));
    Object downgraded = downgradeMessage(message);
    getDeviceTarget().publish(targetId, topicSuffix, downgraded, callback);
    String messageBase = topicSuffix.replace("/", "_");
    String gatewayId = getGatewayId(targetId, getConfig());
    String suffix = ifNotNullGet(gatewayId, x -> "_" + targetId, "");
    File messageOut = new File(getOutDir(), format("%s.json",
        traceTimestamp(messageBase + suffix)));

    try {
      toJsonFile(messageOut, downgraded);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + messageOut.getAbsolutePath(), e);
    }
  }

  private Object downgradeMessage(Object message) {
    MessageDowngrader messageDowngrader = new MessageDowngrader(SubType.STATE.value(), message);
    return ifNotNullGet(getTargetSchema(), messageDowngrader::downgrade, message);
  }

  SchemaVersion getTargetSchema();

  default void cloudLog(String message, Level level) {
    cloudLog(message, level, null);
  }

  /**
   * Cloud log.
   */
  default void cloudLog(String message, Level level, String detail) {
    if (getDeviceManager() != null) {
      getDeviceManager().cloudLog(message, level, detail);
    } else {
      String detailPostfix = detail == null ? "" : ":\n" + detail;
      String logMessage = format("%s%s", message, detailPostfix);
      getLogMap().get(level).accept(logMessage);
    }
  }

  Map<Level, Consumer<String>> getLogMap();

  /**
   * Initializes the system by calling {@link #initializeDevice()} and {@link #initializeMqtt()}.
   *
   * @throws RuntimeException if initialization fails due to an unrecoverable error.
   */
  default void initialize() {
    try {
      initializeDevice();
      initializeMqtt();
    } catch (Exception e) {
      shutdown();
      throw new RuntimeException("While initializing main UDMI publisher class", e);
    }
  }

  default void debug(String message, String detail) {
    cloudLog(message, Level.DEBUG, detail);
  }

  void initializeDevice();

  void initializeMqtt();

  void initializePersistentStore();

  void writePersistentStore();

  void startConnection(Function<String, Boolean> connectionDone);

  /**
   * Flushes the dirty state by publishing an asynchronous state change.
   */
  default void flushDirtyState() {
    if (getStateDirty().get()) {
      publishAsynchronousState();
    }
  }

  byte[] ensureKeyBytes();

  void publisherException(Exception toReport);

  void persistEndpoint(EndpointConfiguration endpoint);

  void resetConnection(String targetEndpoint);

  String traceTimestamp(String messageBase);

  /**
   * Configures a wait time for the configuration latch and waits until it is acquired.
   *
   * @throws RuntimeException if the configuration latch is not acquired within the
   *         specified or default wait time.
   */
  default void configLatchWait() {
    try {
      int waitTimeSec = ofNullable(getConfig().endpoint.config_sync_sec)
          .orElse(DEFAULT_CONFIG_WAIT_SEC);
      int useWaitTime = waitTimeSec == 0 ? DEFAULT_CONFIG_WAIT_SEC : waitTimeSec;
      warn(format("Start waiting %ds for config latch for %s", useWaitTime, getDeviceId()));
      if (useWaitTime > 0 && !getConfigLatch().await(useWaitTime, TimeUnit.SECONDS)) {
        throw new RuntimeException("Config latch timeout");
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While waiting for %s config latch", getDeviceId()), e);
    }
  }

  void shutdown();

  PubberOptions getOptions();

  PubberConfiguration getConfig();

  String getDeviceId();

  boolean isConnected();

  int getDeviceUpdateCount();
}
