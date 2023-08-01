package com.google.bos.udmi.service.pod;

import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.core.ComponentName;
import com.google.bos.udmi.service.core.ProcessorBase;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.MissingArgumentException;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Level;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase {

  public static final String INITIAL_EXECUTION_CONTEXT = "xxxxxxxx";
  private static final ThreadLocal<String> executionContext = new ThreadLocal<>();
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)\\}");

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

  protected String grabExecutionContext() {
    String previous = getExecutionContext();
    String context = format("%08x", Objects.hash(this, Long.toString(System.currentTimeMillis())));
    setExecutionContext(context);
    return previous;
  }

  protected String variableSubstitution(String value, String nullMessage) {
    requireNonNull(value, nullMessage);
    Matcher matcher = VARIABLE_PATTERN.matcher(value);
    String out = matcher.replaceAll(ContainerBase::environmentReplacer);
    ifNotTrueThen(value.equals(out), () -> debug("Replaced value %s with %s", value, out));
    return out;
  }

  private static String environmentReplacer(MatchResult match) {
    String group = match.group(1);
    String value = System.getenv(group);
    if (value == null) {
      throw new IllegalArgumentException("Missing definition for env " + group);
    }
    return value;
  }

  private String getExecutionContext() {
    if (executionContext.get() == null) {
      executionContext.set(INITIAL_EXECUTION_CONTEXT);
    }
    return executionContext.get();
  }

  protected void setExecutionContext(String newContext) {
    debug("Setting execution context %s", newContext);
    executionContext.set(newContext);
  }

  @NotNull
  private String getSimpleName() {
    return getClass().getSimpleName();
  }

  private void output(Level level, String message) {
    PrintStream printStream = level.value() >= Level.WARNING.value() ? System.err : System.out;
    printStream.printf("%s %s %s: %s %s%n", getExecutionContext(), JsonUtil.getTimestamp(),
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

  public void trace(String message, Object... args) {
    trace(format(message, args));
  }

  public void warn(String message) {
    output(Level.WARNING, message);
  }

  public void warn(String format, Object... args) {
    warn(format(format, args));
  }
}
