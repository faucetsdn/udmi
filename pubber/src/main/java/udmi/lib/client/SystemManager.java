package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.catchOrElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.CleanDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Metrics;
import udmi.schema.Operation;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberOptions;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvents;
import udmi.schema.SystemState;

/**
 * System client.
 */
public interface SystemManager extends Manager {

  String UDMI_PUBLISHER_LOG_CATEGORY = "device.log";

  long BYTES_PER_MEGABYTE = 1024 * 1024;
  String DEFAULT_MAKE = "bos";
  String DEFAULT_MODEL = "pubber";
  String DEFAULT_SOFTWARE_KEY = "firmware";
  String DEFAULT_SOFTWARE_VALUE = "v1";

  Map<SystemMode, Integer> EXIT_CODE_MAP = ImmutableMap.of(
      SystemMode.SHUTDOWN, 0, // Indicates expected clean shutdown (success).
      SystemMode.RESTART, 192, // Indicate process to be explicitly restarted.
      SystemMode.TERMINATE, 193); // Indicates expected shutdown (failure code).
  Integer UNKNOWN_MODE_EXIT_CODE = -1;

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

  List<Entry> getLogentries();

  boolean getPublishingLog();

  int getSystemEventCount();

  int incrementSystemEventCount();

  SystemConfig getSystemConfig();


  void systemLifecycle(SystemMode mode);

  void localLog(String message, Level trace, String timestamp, String detail);

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

    getSystemState().hardware.make = catchOrElse(
        () -> metadata.system.hardware.make, () -> DEFAULT_MAKE);

    getSystemState().hardware.model = catchOrElse(
        () -> metadata.system.hardware.model, () -> DEFAULT_MODEL);

    getSystemState().software = new HashMap<>();
    Map<String, String> metadataSoftware = catchToNull(() -> metadata.system.software);
    if (metadataSoftware == null) {
      getSystemState().software.put(DEFAULT_SOFTWARE_KEY, DEFAULT_SOFTWARE_VALUE);
    } else {
      getSystemState().software = metadataSoftware;
    }

    if (getOptions().softwareFirmwareValue != null) {
      getSystemState().software.put("firmware", getOptions().softwareFirmwareValue);
    }
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

    if (SystemMode.ACTIVE.equals(stateMode)
        && SystemMode.RESTART.equals(configMode)) {
      error("System mode requesting device restart");
      systemLifecycle(SystemMode.RESTART);
    }

    if (SystemMode.ACTIVE.equals(configMode)) {
      getSystemState().operation.mode = SystemMode.ACTIVE;
      updateState();
    }

    Date configLastStart = operation.last_start;
    if (configLastStart != null) {
      if (getDeviceStartTime().before(configLastStart)) {
        error(format("Device start time %s before last config start %s, terminating.",
            isoConvert(getDeviceStartTime()), isoConvert(configLastStart)));
        systemLifecycle(SystemMode.TERMINATE);
      } else if (isTrue(getOptions().smokeCheck)
          && CleanDateFormat.dateEquals(getDeviceStartTime(), configLastStart)) {
        error(format("Device start time %s matches, smoke check indicating success!",
            isoConvert(configLastStart)));
        systemLifecycle(SystemMode.SHUTDOWN);
      }
    }
  }


  Date getDeviceStartTime();

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
    Runtime runtime = Runtime.getRuntime();
    systemEvent.metrics.mem_free_mb = (double) runtime.freeMemory() / BYTES_PER_MEGABYTE;
    systemEvent.metrics.mem_total_mb = (double) runtime.totalMemory() / BYTES_PER_MEGABYTE;
    systemEvent.metrics.store_total_mb = Double.NaN;
    systemEvent.event_count = incrementSystemEventCount();
    ifNotTrueThen(getOptions().noLog,
        () -> systemEvent.logentries = ImmutableList.copyOf(getLogentries()));
    getLogentries().clear();
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
      error("Panic! Duplicate config_base detected: " + oldBase);
      System.exit(-22);
    }

    setSystemConfig(system);
    getSystemState().last_config = ifNotTrueGet(getOptions().noLastConfig, () -> timestamp);
    updateInterval(ifNotNullGet(system, config -> config.metrics_rate_sec));
    updateState();
  }

  void setSystemConfig(SystemConfig system);

  /**
   * Publish log message.
   *
   */
  default void publishLogMessage(Entry report) {
    if (shouldLogLevel(report.level)) {
      ifTrueThen(getOptions().badLevel, () -> report.level = 0);
      getLogentries().add(report);
    }
  }

  /**
   * Check if we should log at the level provided.
   */
  default boolean shouldLogLevel(int level) {
    if (getOptions().fixedLogLevel != null) {
      return level >= getOptions().fixedLogLevel;
    }

    Integer minLoglevel = ifNotNullGet(getSystemConfig(), config -> getSystemConfig().min_loglevel);
    return level >= requireNonNullElse(minLoglevel, Level.INFO.value());
  }

  /**
   * Logs a message with specified level and detail. If publishing is enabled,
   * it will publish the log message using the `udmiPublisherLogMessage` method.
   *
   */
  default void cloudLog(String message, Level level, String detail) {
    String timestamp = getTimestamp();
    localLog(message, level, timestamp, detail);

    if (getPublishingLog()) {
      return;
    }

    try {
      setPublishingLog(true);
      udmiPublisherLogMessage(message, level, timestamp, detail);
    } catch (Exception e) {
      localLog("Error publishing log message: " + e, Level.ERROR, timestamp, null);
    } finally {
      setPublishingLog(false);
    }
  }

  void setPublishingLog(boolean b);

  /**
   * Get a testing tag.
   */
  default String getTestingTag() {
    SystemConfig config = getSystemConfig();
    return config == null || config.testing == null
        || config.testing.sequence_name == null ? ""
        : format(" (%s)", config.testing.sequence_name);
  }

  /**
   * Log a message.
   */
  default void udmiPublisherLogMessage(String logMessage, Level level, String timestamp,
      String detail) {
    Entry logEntry = new Entry();
    logEntry.category = UDMI_PUBLISHER_LOG_CATEGORY;
    logEntry.level = level.value();
    logEntry.timestamp = Date.from(Instant.parse(timestamp));
    logEntry.message = logMessage;
    logEntry.detail = detail;
    publishLogMessage(logEntry);
  }

  /**
   * Extra system state with extra field.
   */
  class ExtraSystemState extends SystemState {

    public String extraField;
  }

  void stop();

  void shutdown();

  void error(String message);

  PubberOptions getOptions();

}
