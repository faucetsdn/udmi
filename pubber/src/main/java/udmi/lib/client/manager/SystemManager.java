package udmi.lib.client.manager;

import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import udmi.lib.client.host.ProxyHost;
import udmi.lib.client.host.PublisherHost;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Metrics;
import udmi.schema.Operation;
import udmi.schema.Operation.SystemMode;
import udmi.schema.StateSystemHardware;
import udmi.schema.StateSystemOperation;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvents;
import udmi.schema.SystemState;

/**
 * System client.
 */
public interface SystemManager extends SubBlockManager {

  String DEFAULT_SOFTWARE_KEY = "firmware";
  String UDMI_PUBLISHER_LOG_CATEGORY = "device.log";
  long BYTES_PER_MEGABYTE = 1024 * 1024;
  Integer UNKNOWN_MODE_EXIT_CODE = -1;
  Map<SystemMode, Integer> EXIT_CODE_MAP = ImmutableMap.of(
      SystemMode.SHUTDOWN, 0, // Indicates expected clean shutdown (success).
      SystemMode.RESTART, 192, // Indicate process to be explicitly restarted.
      SystemMode.TERMINATE, 193); // Indicates expected shutdown (failure code).

  /**
   * Initialize system state.
   */
  default void initialize(ManagerHost host, String serialNo) {
    ExtraSystemState state = getSystemState();
    state.operation = new StateSystemOperation();
    state.operation.operational = true;
    state.operation.mode = SystemMode.INITIAL;
    state.operation.last_start = getRoundedStartTime();
    state.hardware = new StateSystemHardware();
    state.last_config = new Date(0);

    if (host instanceof PublisherHost publisher) {
      publisher.initializeLogger();
      info(format("Device start time is %s", isoConvert(getStartTime())));
    }

    ifTrueThen(host instanceof PublisherHost, () -> state.serial_no = serialNo);
    ifTrueThen(isNoLastStart(), () -> state.operation.last_start = null);
    ifTrueThen(isNoHardware(), () -> state.hardware = null);
    ifNotNullThen(getExtraField(), value -> state.extraField = value);
    updateState();
  }

  /**
   * Builds a map of logger consumers.
   */
  static Function<Logger, Map<Level, Consumer<String>>> getLogMap() {
    return (logger) -> ImmutableMap.<Level, Consumer<String>>builder()
        .put(Level.TRACE, logger::info) // TODO: Make debug/trace programmatically visible.
        .put(Level.DEBUG, logger::info)
        .put(Level.INFO, logger::info)
        .put(Level.NOTICE, logger::info)
        .put(Level.WARNING, logger::warn)
        .put(Level.ERROR, logger::error)
        .build();
  }

  List<Entry> getLogs();

  /**
   * Get log entries.
   */
  default List<Entry> getLogEntries() {
    List<Entry> logs = getLogs();
    synchronized (logs) {
      if (isNoLog()) {
        return null;
      }
      List<Entry> entries = ImmutableList.copyOf(logs);
      logs.clear();
      return entries;
    }
  }

  /**
   * Publish log message.
   */
  default void publishLogMessage(Entry report) {
    List<Entry> logs = getLogs();
    synchronized (logs) {
      if (shouldLogLevel(report.level)) {
        ifTrueThen(isBadLevel(), () -> report.level = 0);
        logs.add(report);
      }
    }
  }

  AtomicBoolean getPublishLock();

  SystemConfig getSystemConfig();

  void systemLifecycle(SystemMode mode);

  /**
   * Device local log.
   */
  default void localLog(String message, Level level, String timestamp, String detail) {
    String detailPostfix = detail == null ? "" : ":\n" + detail;
    String logMessage = format("%s (%s) -> %s%s", timestamp, getDeviceId(), message, detailPostfix);
    SystemManager.getLogMap().apply(PublisherHost.LOG).get(level).accept(logMessage);
    try {
      PrintStream stream;
      if (getHost() instanceof PublisherHost publisher) {
        stream = publisher.getLogPrintWriter();
      } else if (getHost() instanceof ProxyHost proxyHost) {
        stream = proxyHost.getPublisherHost().getLogPrintWriter();
      } else {
        throw new RuntimeException("While writing log output file: Unknown host");
      }
      stream.println(logMessage);
      stream.flush();
    } catch (Exception e) {
      throw new RuntimeException("While writing log output file", e);
    }
  }

  /**
   * Local log.
   */
  default void localLog(Entry entry) {
    String message = format("Log %s%s %s %s %s%s", Level.fromValue(entry.level).name(),
        shouldLogLevel(entry.level) ? "" : "*",
        entry.category, entry.message, isoConvert(entry.timestamp), getTestingTag());
    localLog(message, Level.fromValue(entry.level), isoConvert(entry.timestamp), null);
  }

  /**
   * Retrieves the hardware and software from metadata.
   *
   */
  default void setHardwareSoftware(Metadata metadata) {
    ExtraSystemState state = getSystemState();
    state.hardware.make = catchToNull(() -> metadata.system.hardware.make);
    state.hardware.model = catchToNull(() -> metadata.system.hardware.model);
    state.software = catchToNull(() -> metadata.system.software);
    ifNotNullThen(getSoftwareFirmwareValue(), value ->  {
      ifNullThen(state.software, () -> state.software = new HashMap<>());
      state.software.put(DEFAULT_SOFTWARE_KEY, value);
    });
  }

  ExtraSystemState getSystemState();

  /**
   * Retrieves the system events.
   *
   * @return the system events.
   */
  default SystemEvents getSystemEvent() {
    SystemEvents systemEvent = new SystemEvents();
    systemEvent.last_config = getSystemState().last_config;
    return systemEvent;
  }

  /**
   * Checks the current system configuration and state to determine if a restart is necessary or
   * if the system should be terminated.
   */
  default void maybeRestartSystem() {
    SystemConfig system = ofNullable(getSystemConfig()).orElseGet(SystemConfig::new);
    Operation operation = ofNullable(system.operation).orElseGet(Operation::new);
    SystemMode configMode = operation.mode;
    SystemMode stateMode = getSystemState().operation.mode;

    if (SystemMode.ACTIVE.equals(stateMode) && SystemMode.RESTART.equals(configMode)) {
      error("System mode requesting device restart");
      systemLifecycle(SystemMode.RESTART);
      return;
    }

    if (SystemMode.ACTIVE.equals(configMode)) {
      getSystemState().operation.mode = SystemMode.ACTIVE;
      updateState();
    }

    Date configLastStart = operation.last_start;
    Date startTime = getStartTime();
    if (configLastStart != null && startTime != null && startTime.before(configLastStart)) {
      error(format("Device start time %s before last config start %s, terminating.",
          isoConvert(getStartTime()), isoConvert(configLastStart)));
      systemLifecycle(SystemMode.TERMINATE);
    }

    if (configLastStart != null && isSmokeCheck() && dateEquals(getStartTime(), configLastStart)) {
      error(format("Device start time %s matches, smoke check indicating success!",
          isoConvert(configLastStart)));
      systemLifecycle(SystemMode.SHUTDOWN);
    }
  }

  default Date getStartTime() {
    return catchToNull(() -> getSystemState().operation.last_start);
  }

  default void updateState() {
    getHost().update(getSystemState());
  }

  /**
   * Send a system event.
   *
   */
  default void sendSystemEvent() {
    SystemEvents systemEvent = getSystemEvent();
    systemEvent.metrics = new Metrics();
    if (!(getHost() instanceof ProxyHost)) {
      Runtime runtime = Runtime.getRuntime();
      systemEvent.metrics.mem_free_mb = (double) runtime.freeMemory() / BYTES_PER_MEGABYTE;
      systemEvent.metrics.mem_total_mb = (double) runtime.totalMemory() / BYTES_PER_MEGABYTE;
      systemEvent.metrics.store_total_mb = Double.NaN;
    }
    systemEvent.event_no = incrementEventCount();
    systemEvent.logentries = getLogEntries();
    getHost().publish(systemEvent);
  }

  default void setMetadata(Metadata metadata) {
    setHardwareSoftware(metadata);
  }

  default void setPersistentData(DevicePersistent persistentData) {
    getSystemState().operation.restart_count = persistentData.restart_count;
  }

  /**
   * Updates the system configuration with a new SystemConfig object and timestamps.
   *
   */
  default void updateConfig(SystemConfig system, Date timestamp) {
    Integer oldBase = catchToNull(() -> getSystemConfig().testing.config_base);
    Integer newBase = catchToNull(() -> system.testing.config_base);
    if (oldBase != null && oldBase.equals(newBase)
        && !stringify(getSystemConfig()).equals(stringify(system))) {
      error(format("Panic! Duplicate config_base detected: %s", oldBase));
      System.exit(-22);
    }
    setSystemConfig(system);
    updateInterval(ifNotNullGet(system, config -> config.metrics_rate_sec));
    getSystemState().last_config = ifNotTrueGet(isNoLastConfig(), () -> timestamp);
    updateState();
  }

  void setSystemConfig(SystemConfig system);

  /**
   * Check if we should log at the level provided.
   */
  default boolean shouldLogLevel(int level) {
    Integer fixedLogLevel = getFixedLogLevel();
    if (fixedLogLevel != null) {
      return level >= fixedLogLevel;
    }
    Integer minLoglevel = ifNotNullGet(getSystemConfig(), config -> config.min_loglevel);
    return level >= requireNonNullElse(minLoglevel, Level.INFO.value());
  }

  /**
   * Logs a message with specified level and detail. If publishing is enabled,
   * it will publish the log message using the `udmiPublisherLogMessage` method.
   */
  default void cloudLog(String message, Level level, String detail) {
    String timestamp = getTimestamp();
    localLog(message, level, timestamp, detail);
    AtomicBoolean publishLock = getPublishLock();
    if (publishLock.compareAndSet(false, true)) {
      try {
        publishLog(message, level, timestamp, detail);
      } catch (Exception e) {
        localLog(format("Error publishing log message: %s", e), Level.ERROR, timestamp, null);
      } finally {
        publishLock.set(false);
      }
    }
  }

  /**
   * Get a testing tag.
   */
  default String getTestingTag() {
    SystemConfig config = getSystemConfig();
    return config == null || config.testing == null || config.testing.sequence_name == null
        ? ""
        : format(" (%s)", config.testing.sequence_name);
  }

  /**
   * Log a message.
   */
  default void publishLog(String message, Level level, String timestamp, String detail) {
    Entry logEntry = new Entry();
    logEntry.category = UDMI_PUBLISHER_LOG_CATEGORY;
    logEntry.level = level.value();
    logEntry.timestamp = Date.from(Instant.parse(timestamp));
    logEntry.message = message;
    logEntry.detail = detail;
    publishLogMessage(logEntry);
  }

  default void setStatus(Entry report) {
    getSystemState().status = report;
    updateState();
  }

  /**
   * Retrieves the start time of the current second,
   * with milliseconds removed for precise comparison.
   */
  default Date getRoundedStartTime() {
    long timestamp = getNow().getTime();
    // Remove ms so that rounded conversions preserve equality.
    return new Date(timestamp - (timestamp % 1000));
  }

  /**
   * Extra system state with extra field.
   */
  class ExtraSystemState extends SystemState {

    public String extraField;
  }

  void stop();

  /**
   * Shutdown.
   */
  default void shutdown() {
    if (getHost() instanceof PublisherHost publisher) {
      publisher.shutdownLogger();
    }
  }

  default void periodicUpdate() {
    sendSystemEvent();
  }

  void error(String message);

  default boolean isSmokeCheck() {
    return false;
  }

  default boolean isNoLog() {
    return false;
  }

  default boolean isBadLevel() {
    return false;
  }

  default boolean isNoLastConfig() {
    return false;
  }

  default boolean isNoLastStart() {
    return false;
  }

  default boolean isNoHardware() {
    return false;
  }

  default String getExtraField() {
    return null;
  }

  default Integer getFixedLogLevel() {
    return null;
  }

  default String getSoftwareFirmwareValue() {
    return null;
  }
}
