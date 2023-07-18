package com.google.bos.udmi.service.pod;

import static java.lang.String.format;

import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Level;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase {

  @NotNull
  private String getSimpleName() {
    return getClass().getSimpleName();
  }

  public void activate() {
  }

  private void output(Level level, String message) {
    PrintStream printStream = level.value() >= Level.WARNING.value() ? System.err : System.out;
    printStream.printf("%s %s %s: %s %s%n", getThreadId(), JsonUtil.getTimestamp(),
        level.name().charAt(0), getSimpleName(), message);
    printStream.flush();
  }

  private String getThreadId() {
    return format("%08x", Thread.currentThread().hashCode());
  }

  public void debug(String format, Object... args) {
    debug(format(format, args));
  }

  public void debug(String message) {
    output(Level.DEBUG, message);
  }

  public void notice(String message) {
    output(Level.NOTICE, message);
  }

  public void warn(String message) {
    output(Level.WARNING, message);
  }

  public void error(String message) {
    output(Level.ERROR, message);
  }

  public void info(String message) {
    output(Level.INFO, message);
  }

  public void trace(String message) {
    // TODO: Make this dynamic and/or structured logging.
  }

  public void trace(String message, Object... args) {
    trace(format(message, args));
  }

  public void shutdown() {
  }
}
