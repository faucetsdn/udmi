package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchOrElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.CleanDateFormat;
import java.io.File;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Metrics;
import udmi.schema.Operation;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberConfiguration;
import udmi.schema.StateSystemHardware;
import udmi.schema.StateSystemOperation;
import udmi.schema.SystemConfig;
import udmi.schema.SystemEvent;
import udmi.schema.SystemState;

/**
 * Support manager for system stuff.
 */
public class SystemManager extends ManagerBase {

  public static final String PUBBER_LOG_CATEGORY = "device.log";
  public static final String PUBBER_LOG = "pubber.log";
  private static final long BYTES_PER_MEGABYTE = 1024 * 1024;
  private static final String DEFAULT_MAKE = "bos";
  private static final String DEFAULT_MODEL = "pubber";
  private static final String DEFAULT_SOFTWARE_KEY = "firmware";
  private static final String DEFAULT_SOFTWARE_VALUE = "v1";
  private static final Date DEVICE_START_TIME = Pubber.deviceStartTime;
  private static final Map<SystemMode, Integer> EXIT_CODE_MAP = ImmutableMap.of(
      SystemMode.SHUTDOWN, 0, // Indicates expected clean shutdown (success).
      SystemMode.RESTART, 192, // Indicate process to be explicitly restarted.
      SystemMode.TERMINATE, 193); // Indicates expected shutdown (failure code).
  private static final Integer UNKNOWN_MODE_EXIT_CODE = -1;
  private static final Logger LOG = Pubber.LOG;
  private static final Map<Level, Consumer<String>> LOG_MAP =
      ImmutableMap.<Level, Consumer<String>>builder()
          .put(Level.TRACE, LOG::info) // TODO: Make debug/trace programmatically visible.
          .put(Level.DEBUG, LOG::info)
          .put(Level.INFO, LOG::info)
          .put(Level.NOTICE, LOG::info)
          .put(Level.WARNING, LOG::warn)
          .put(Level.ERROR, LOG::error)
          .build();
  private static final PrintStream logPrintWriter;

  static {
    File outDir = new File(Pubber.PUBBER_OUT);
    try {
      outDir.mkdirs();
      logPrintWriter = new PrintStream(new File(outDir, PUBBER_LOG));
      logPrintWriter.println("Pubber log started at " + getTimestamp());
    } catch (Exception e) {
      throw new RuntimeException("While initializing out dir " + outDir.getAbsolutePath(), e);
    }
  }

  private final List<Entry> logentries = new ArrayList<>();
  private final SystemState systemState;
  private final ManagerHost host;
  private int systemEventCount;
  private SystemConfig systemConfig;
  private boolean publishingLog;

  /**
   * New instance.
   */
  public SystemManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    this.host = host;

    info("Device start time is " + getTimestamp(DEVICE_START_TIME));

    systemState = new SystemState();
    systemState.operation = new StateSystemOperation();

    if (!isTrue(options.noLastStart)) {
      systemState.operation.last_start = DEVICE_START_TIME;
    }

    systemState.hardware = isTrue(options.noHardware) ? null : new StateSystemHardware();

    systemState.operation.operational = true;
    systemState.operation.mode = SystemMode.INITIAL;
    systemState.serial_no = configuration.serialNo;
    systemState.last_config = new Date(0);

    updateState();
  }

  @Override
  public void shutdown() {
    super.shutdown();
    logPrintWriter.close();
  }

  private void setHardwareSoftware(Metadata metadata) {

    systemState.hardware.make = catchOrElse(
        () -> metadata.system.hardware.make, () -> DEFAULT_MAKE);

    systemState.hardware.model = catchOrElse(
        () -> metadata.system.hardware.model, () -> DEFAULT_MODEL);

    systemState.software = new HashMap<>();
    Map<String, String> metadataSoftware = catchToNull(() -> metadata.system.software);
    if (metadataSoftware == null) {
      systemState.software.put(DEFAULT_SOFTWARE_KEY, DEFAULT_SOFTWARE_VALUE);
    } else {
      systemState.software = metadataSoftware;
    }

    if (options.softwareFirmwareValue != null) {
      systemState.software.put("firmware", options.softwareFirmwareValue);
    }
  }

  private SystemEvent getSystemEvent() {
    SystemEvent systemEvent = new SystemEvent();
    systemEvent.last_config = systemState.last_config;
    return systemEvent;
  }

  void maybeRestartSystem() {
    SystemConfig system = ofNullable(systemConfig).orElseGet(SystemConfig::new);
    Operation operation = ofNullable(system.operation).orElseGet(Operation::new);
    SystemMode configMode = operation.mode;
    SystemMode stateMode = systemState.operation.mode;

    if (SystemMode.ACTIVE.equals(stateMode)
        && SystemMode.RESTART.equals(configMode)) {
      error("System mode requesting device restart");
      systemLifecycle(SystemMode.RESTART);
    }

    if (SystemMode.ACTIVE.equals(configMode)) {
      systemState.operation.mode = SystemMode.ACTIVE;
      updateState();
    }

    Date configLastStart = operation.last_start;
    if (configLastStart != null) {
      if (DEVICE_START_TIME.before(configLastStart)) {
        error(format("Device start time %s before last config start %s, terminating.",
            getTimestamp(DEVICE_START_TIME), getTimestamp(configLastStart)));
        systemLifecycle(SystemMode.TERMINATE);
      } else if (isTrue(options.smokeCheck)
          && CleanDateFormat.dateEquals(DEVICE_START_TIME, configLastStart)) {
        error(format("Device start time %s matches, smoke check indicating success!",
            getTimestamp(configLastStart)));
        systemLifecycle(SystemMode.SHUTDOWN);
      }
    }
  }

  private void updateState() {
    host.update(systemState);
  }

  private void sendSystemEvent() {
    SystemEvent systemEvent = getSystemEvent();
    systemEvent.metrics = new Metrics();
    Runtime runtime = Runtime.getRuntime();
    systemEvent.metrics.mem_free_mb = (double) runtime.freeMemory() / BYTES_PER_MEGABYTE;
    systemEvent.metrics.mem_total_mb = (double) runtime.totalMemory() / BYTES_PER_MEGABYTE;
    systemEvent.metrics.store_total_mb = Double.NaN;
    systemEvent.event_count = systemEventCount++;
    systemEvent.logentries = ImmutableList.copyOf(logentries);
    logentries.clear();
    host.publish(systemEvent);
  }

  @Override
  protected void periodicUpdate() {
    sendSystemEvent();
  }

  void systemLifecycle(SystemMode mode) {
    systemState.operation.mode = mode;
    try {
      host.update(host);
    } catch (Exception e) {
      error("Squashing error publishing state while shutting down", e);
    }
    int exitCode = EXIT_CODE_MAP.getOrDefault(mode, UNKNOWN_MODE_EXIT_CODE);
    error("Stopping system with extreme prejudice, restart " + mode + " with code " + exitCode);
    new RuntimeException("TAP exit").printStackTrace();;
    System.exit(exitCode);
  }

  public void setMetadata(Metadata metadata) {
    setHardwareSoftware(metadata);
  }

  public void setPersistentData(DevicePersistent persistentData) {
    systemState.operation.restart_count = persistentData.restart_count;
  }

  void updateConfig(SystemConfig system, Date timestamp) {
    systemConfig = system;
    systemState.last_config = timestamp;
    updateInterval(ifNotNullGet(system, config -> config.metrics_rate_sec));
    updateState();
  }

  void publishLogMessage(Entry report) {
    if (shouldLogLevel(report.level)) {
      logentries.add(report);
    }
  }

  private boolean shouldLogLevel(int level) {
    if (options.fixedLogLevel != null) {
      return level >= options.fixedLogLevel;
    }

    Integer minLoglevel = ifNotNullGet(systemConfig, config -> systemConfig.min_loglevel);
    return level >= requireNonNullElse(minLoglevel, Level.INFO.value());
  }

  void cloudLog(String message, Level level, String detail) {
    String timestamp = getTimestamp();
    localLog(message, level, timestamp, detail);

    if (publishingLog) {
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

  String getTestingTag() {
    SystemConfig config = systemConfig;
    return config == null || config.testing == null
        || config.testing.sequence_name == null ? ""
        : format(" (%s)", config.testing.sequence_name);
  }

  void localLog(Entry entry) {
    String message = format("Log %s%s %s %s %s%s", Level.fromValue(entry.level).name(),
        shouldLogLevel(entry.level) ? "" : "*",
        entry.category, entry.message, getTimestamp(entry.timestamp), getTestingTag());
    localLog(message, Level.fromValue(entry.level), getTimestamp(entry.timestamp), null);
  }

  static void localLog(String message, Level level, String timestamp, String detail) {
    String detailPostfix = detail == null ? "" : ":\n" + detail;
    String logMessage = format("%s %s%s", timestamp, message, detailPostfix);
    LOG_MAP.get(level).accept(logMessage);
    try {
      logPrintWriter.println(logMessage);
      logPrintWriter.flush();
    } catch (Exception e) {
      throw new RuntimeException("While writing log output file", e);
    }
  }

  /**
   * Log a message.
   */
  public void pubberLogMessage(String logMessage, Level level, String timestamp, String detail) {
    Entry logEntry = new Entry();
    logEntry.category = PUBBER_LOG_CATEGORY;
    logEntry.level = level.value();
    logEntry.timestamp = Date.from(Instant.parse(timestamp));
    logEntry.message = logMessage;
    logEntry.detail = detail;
    publishLogMessage(logEntry);
  }
}
