package com.google.bos.udmi.service.pod;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.core.ComponentName;
import com.google.common.base.Preconditions;
import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.BasePodConfiguration;
import udmi.schema.Level;
import udmi.schema.PodConfiguration;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase {

  public static final String INITIAL_EXECUTION_CONTEXT = "xxxxxxxx";
  public static final Integer FUNCTIONS_VERSION_MIN = 9;
  public static final Integer FUNCTIONS_VERSION_MAX = 10;
  public static final String EMPTY_JSON = "{}";
  public static final String REFLECT_BASE = "UDMI-REFLECT";
  private static final ThreadLocal<String> executionContext = new ThreadLocal<>();
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)\\}");
  private static BasePodConfiguration basePodConfig = new BasePodConfiguration();
  protected static String reflectRegistry = REFLECT_BASE;
  protected final PodConfiguration podConfiguration;

  /**
   * Create a basic pod container.
   */
  public ContainerBase() {
    podConfiguration = null;
  }

  /**
   * Construct a new instance given a configuration file. Only used once for the pod itself.
   *
   * @param config pod configuration
   */
  public ContainerBase(PodConfiguration config) {
    podConfiguration = config;
    basePodConfig = ofNullable(podConfiguration.base).orElseGet(BasePodConfiguration::new);
    reflectRegistry = getReflectRegistry();
    info("Configured with reflect registry " + reflectRegistry);
  }

  private static String environmentReplacer(MatchResult match) {
    return ofNullable(System.getenv(match.group(1))).orElse("");
  }

  @TestOnly
  static void resetForTest() {
    basePodConfig = null;
    reflectRegistry = null;
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

  protected synchronized String grabExecutionContext() {
    String previous = getExecutionContext();
    String context = format("%08x", (long) (Math.random() * 0x100000000L));
    setExecutionContext(context);
    return previous;
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
    String out = matcher.replaceAll(ContainerBase::environmentReplacer);
    ifNotTrueThen(value.equals(out), () -> debug("Replaced value %s with '%s'", value, out));
    return out;
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

  @NotNull
  private String getReflectRegistry() {
    return ofNullable(basePodConfig.udmi_prefix).map(this::variableSubstitution).orElse("")
        + REFLECT_BASE;
  }

  @NotNull
  private String getSimpleName() {
    return getClass().getSimpleName();
  }

  private void output(Level level, String message) {
    PrintStream printStream = level.value() >= Level.WARNING.value() ? System.err : System.out;
    printStream.printf("%s %s %s: %s %s%n", JsonUtil.getTimestamp(), getExecutionContext(),
        level.name().charAt(0), getSimpleName(), message);
    printStream.flush();
  }

  public void activate() {
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

  public void shutdown() {
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
