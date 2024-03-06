package com.google.bos.udmi.service.pod;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

import com.google.bos.udmi.service.core.ComponentName;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.BasePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Level;
import udmi.schema.PodConfiguration;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase implements ContainerProvider {

  public static final String INITIAL_EXECUTION_CONTEXT = "xxxxxxxx";
  public static final Integer FUNCTIONS_VERSION_MIN = 11;
  public static final Integer FUNCTIONS_VERSION_MAX = 11;
  public static final String EMPTY_JSON = "{}";
  public static final String REFLECT_BASE = "UDMI-REFLECT";
  private static final ThreadLocal<String> executionContext = new ThreadLocal<>();
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)}");
  private static final Pattern MULTI_PATTERN = Pattern.compile("!\\{([,a-zA-Z_]+)}");
  protected static String reflectRegistry = REFLECT_BASE;
  private static BasePodConfiguration basePodConfig = new BasePodConfiguration();
  protected final PodConfiguration podConfiguration;
  protected final int periodicSec;
  private final ScheduledExecutorService scheduledExecutor;
  private final double failureRate;
  protected final String containerId;

  /**
   * Create a basic pod container.
   */
  public ContainerBase() {
    podConfiguration = null;
    failureRate = getPodFailureRate();
    periodicSec = 0;
    scheduledExecutor = null;
    containerId = getSimpleName();
  }

  /**
   * Create an instance with specific parameters.
   */
  public ContainerBase(int executorSec, String useId) {
    podConfiguration = null;
    failureRate = getPodFailureRate();
    periodicSec = executorSec;
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
  }

  public ContainerBase(EndpointConfiguration configuration) {
    this(ofNullable(configuration.periodic_sec).orElse(0), configuration.name);
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

  private void periodicTaskWrapper() {
    try {
      periodicTask();;
    } catch (Exception e) {
      error("Exception executing periodic task: " + friendlyStackTrace(e));
    }
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

  protected String variableSubstitution(String value, @NotNull String nullMessage) {
    requireNonNull(value, requireNonNull(nullMessage, "null message not defined"));
    Matcher matcher = VARIABLE_PATTERN.matcher(value);
    String out = matcher.replaceAll(this::environmentReplacer);
    ifNotTrueThen(value.equals(out), () -> debug("Replaced value %s with '%s'", value, out));
    return out;
  }

  protected String variableSubstitution(String value) {
    if (value == null) {
      return null;
    }
    return variableSubstitution(value, "unknown null value");
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

  @Override
  public void activate() {
    info("Activating");
    ifTrueThen(periodicSec > 0, () -> {
      notice("Scheduling periodic task %s execution every %ss", containerId, periodicSec);
      scheduledExecutor.scheduleAtFixedRate(this::periodicTaskWrapper, periodicSec, periodicSec,
          TimeUnit.SECONDS);
    });
  }

  protected void scheduleIn(Duration duration, Runnable task) {
    scheduledExecutor.schedule(task, duration.getSeconds(), TimeUnit.SECONDS);
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
    printStream.printf("%s %s %s: %s %s%n", JsonUtil.isoConvert(), getExecutionContext(),
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
}
