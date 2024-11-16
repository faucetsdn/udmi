package com.google.bos.udmi.service.pod;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.instantNow;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.time.Duration.between;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;

import com.google.bos.udmi.service.core.ComponentName;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.BasePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.IotAccess;
import udmi.schema.Level;
import udmi.schema.PodConfiguration;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase implements UdmiComponent {

  public static final String INITIAL_EXECUTION_CONTEXT = "xxxxxxxx";
  public static final Integer FUNCTIONS_VERSION_MIN = 15;
  public static final Integer FUNCTIONS_VERSION_MAX = 16;
  public static final String EMPTY_JSON = "{}";
  public static final String REFLECT_BASE = "UDMI-REFLECT";
  private static final ThreadLocal<String> executionContext = new ThreadLocal<>();
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)}");
  private static final Pattern MULTI_PATTERN = Pattern.compile("!\\{([,a-zA-Z_]+)}");
  private static final int JITTER_ADJ_MS = 1000; // Empirically determined to be good.
  public static final String ENABLED_KEY = "enabled";
  public static final String TRUE_OPTION = "true";
  protected static String reflectRegistry = REFLECT_BASE;
  private static BasePodConfiguration basePodConfig = new BasePodConfiguration();
  protected final PodConfiguration podConfiguration;
  protected final long periodicSec;
  protected final String containerId;
  private final ScheduledExecutorService scheduledExecutor;
  private final double failureRate;
  private final Instant executorGeneration;

  /**
   * Create a basic pod container.
   */
  public ContainerBase() {
    podConfiguration = null;
    failureRate = getPodFailureRate();
    periodicSec = 0;
    scheduledExecutor = null;
    containerId = getSimpleName();
    executorGeneration = null;
  }

  /**
   * Create an instance with specific parameters.
   */
  public ContainerBase(String useId, Integer executorSec, Date generation) {
    podConfiguration = null;
    failureRate = getPodFailureRate();
    periodicSec = ofNullable(executorSec).orElse(0);
    executorGeneration = ifNotNullGet(generation, Date::toInstant);
    scheduledExecutor = ifTrueGet(periodicSec > 0, Executors::newSingleThreadScheduledExecutor);
    containerId = ifNotNullGet(useId, id -> id, getSimpleName());
  }

  /**
   * Construct a new instance given a configuration file. Only used once for the pod itself.
   */
  public ContainerBase(PodConfiguration config) {
    podConfiguration = config;
    basePodConfig = ofNullable(podConfiguration.base).orElseGet(BasePodConfiguration::new);
    failureRate = getPodFailureRate();
    reflectRegistry = getReflectRegistry();
    info("Configured with reflect registry " + reflectRegistry);
    ifTrueThen(failureRate > 0, () -> warn("Random failure rate configured at " + failureRate));
    periodicSec = 0;
    scheduledExecutor = null;
    containerId = getSimpleName();
    executorGeneration = null;
  }

  public ContainerBase(EndpointConfiguration configuration) {
    this(configuration.name, configuration.periodic_sec, configuration.generation);
  }

  /**
   * Get the component name taken from a class annotation.
   */
  public static String getName(Class<?> clazz) {
    try {
      return requireNonNull(clazz.getAnnotation(ComponentName.class),
          "no ComponentName annotation").value();
    } catch (Exception e) {
      throw new RuntimeException("While extracting component name for " + clazz.getSimpleName(), e);
    }
  }

  @TestOnly
  static void resetForTest() {
    basePodConfig = new BasePodConfiguration();
    reflectRegistry = null;
  }

  protected String getEnv(String group) {
    return System.getenv(group);
  }

  @NotNull
  protected String getPodNamespacePrefix() {
    return ofNullable(basePodConfig.udmi_prefix).map(this::variableSubstitution).orElse("");
  }

  protected synchronized String grabExecutionContext() {
    String previous = getExecutionContext();
    String context = format("%08x", (long) (Math.random() * 0x100000000L));
    setExecutionContext(context);
    return previous;
  }

  protected Set<String> multiSubstitution(String value) {
    String raw = variableSubstitution(value);
    if (raw == null) {
      return ImmutableSet.of();
    }
    Matcher matcher = MULTI_PATTERN.matcher(raw);
    if (!matcher.find()) {
      return ImmutableSet.of(raw);
    }
    String group = matcher.group(1);
    if (matcher.find()) {
      throw new RuntimeException(format("Multi multi-expansions not supported: %s", raw));
    }
    String[] parts = group.split(",");
    Set<String> expanded = Arrays.stream(parts).map(matcher::replaceFirst).collect(toSet());
    expanded.forEach(set -> debug("Expanded intermediate %s with '%s'", raw, set));
    return expanded;
  }

  protected void periodicTask() {
    if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
      debug("Shutting down unused scheduled executor");
      scheduledExecutor.shutdown();
      return;
    }
    throw new IllegalStateException("Unexpected periodic task execution");
  }

  protected void randomlyFail() {
    if (Math.random() < failureRate) {
      throw new IllegalStateException("Randomly induced failure");
    }
  }

  protected void scheduleIn(Duration duration, Runnable task) {
    scheduledExecutor.schedule(task, duration.getSeconds(), SECONDS);
  }

  protected String variableSubstitution(String value) {
    if (value == null) {
      return null;
    }
    return variableSubstitution(value, "unknown null value");
  }

  protected String variableSubstitution(String value, @NotNull String nullMessage) {
    requireNonNull(value, requireNonNull(nullMessage, "null message not defined"));
    Matcher matcher = VARIABLE_PATTERN.matcher(value);
    String out = matcher.replaceAll(this::environmentReplacer);
    ifNotTrueThen(value.equals(out), () -> debug("Replaced value %s with '%s'", value, out));
    return out;
  }

  private void alignWithGeneration() {
    // The initial delay will often be slightly off the intended time due to rounding errors.
    // Add in a quick/bounded delay to the start of the next second for dynamic alignment.
    // Mostly this is just to make the output timestamps look pretty, but has no functional impact.
    long initialDelay = initialDelaySec();
    long secondsToAdd = initialDelay < periodicSec / 2 ? initialDelay : 0;
    Duration duration = Duration.ofMillis(JITTER_ADJ_MS).plusSeconds(secondsToAdd);
    safeSleep(duration.minusNanos(Instant.now().getNano()).toMillis());
  }

  private String environmentReplacer(MatchResult match) {
    String replacement = ofNullable(getEnv(match.group(1))).orElse("");
    if (replacement.startsWith("!")) {
      return format("!{%s}", replacement.substring(1));
    }
    return replacement;
  }

  private String getExecutionContext() {
    if (executionContext.get() == null) {
      executionContext.set(INITIAL_EXECUTION_CONTEXT);
    }
    return executionContext.get();
  }

  protected void setExecutionContext(String newContext) {
    trace("Setting execution context %s", newContext);
    executionContext.set(newContext);
  }

  private double getPodFailureRate() {
    return ofNullable(basePodConfig.failure_rate).orElse(0.0);
  }

  @NotNull
  private String getReflectRegistry() {
    return getPodNamespacePrefix() + REFLECT_BASE;
  }

  @NotNull
  private String getSimpleName() {
    return getClass().getSimpleName();
  }

  private long initialDelaySec() {
    return ifNotNullGet(executorGeneration, generation ->
        floorMod(between(instantNow(), generation).getSeconds(), periodicSec), periodicSec);
  }

  private void periodicScheduler(long initialSec, long periodicSec) {
    notice("Scheduling %s execution, after %ss every %ss", containerId, initialSec, periodicSec);
    scheduledExecutor.scheduleAtFixedRate(this::periodicWrapper, initialSec, periodicSec, SECONDS);
  }

  private void periodicWrapper() {
    try {
      grabExecutionContext();
      ifNotNullThen(executorGeneration, this::alignWithGeneration);
      periodicTask();
    } catch (Exception e) {
      error("Exception executing periodic task: " + friendlyStackTrace(e));
      error("Task exception details: " + stackTraceString(e));
    }
  }

  @Override
  public void activate() {
    info("Activating");
    ifTrueThen(periodicSec > 0, () -> periodicScheduler(initialDelaySec(), periodicSec));
  }

  public void debug(String format, Object... args) {
    debug(format(format, args));
  }

  public void debug(String message) {
    output(Level.DEBUG, message);
  }

  public void error(String format, Object... args) {
    error(format(format, args));
  }

  public void error(String message) {
    output(Level.ERROR, message);
  }

  public void info(String format, Object... args) {
    info(format(format, args));
  }

  public void info(String message) {
    output(Level.INFO, message);
  }

  public void notice(String message) {
    output(Level.NOTICE, message);
  }

  public void notice(String message, Object... args) {
    notice(format(message, args));
  }

  @Override
  public void output(Level level, String message) {
    PrintStream printStream = level.value() >= Level.WARNING.value() ? System.err : System.out;
    printStream.printf("%s %s %s: %s %s%n", JsonUtil.currentIsoMs(), getExecutionContext(),
        level.name().charAt(0), getSimpleName(), message);
    printStream.flush();
  }

  @Override
  public void shutdown() {
    ifNotNullThen(scheduledExecutor, ExecutorService::shutdown);
  }

  public void trace(String message) {
    // TODO: Make this dynamic and/or structured logging.
  }

  public void trace(String format, Object... args) {
    trace(format(format, args));
  }

  public void warn(String message) {
    output(Level.WARNING, message);
  }

  public void warn(String format, Object... args) {
    warn(format(format, args));
  }

  protected Map<String, String> parseOptions(IotAccess iotAccess) {
    String options = variableSubstitution(iotAccess.options);
    if (options == null) {
      return ImmutableMap.of();
    }
    String[] parts = options.split(",");
    return Arrays.stream(parts).map(String::trim).map(option -> option.split("=", 2))
        .collect(Collectors.toMap(x -> x[0], x -> x.length > 1 ? x[1] : TRUE_OPTION));
  }
}
