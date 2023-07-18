package com.google.bos.udmi.service.pod;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public abstract class ContainerBase {

  @NotNull
  protected String getSimpleName() {
    return getClass().getSimpleName();
  }

  public void activate() {
  }

  public void debug(String format, Object... args) {
    System.err.println(getSimpleName() + " D: " + format(format, args));
  }

  public void debug(String message) {
    System.err.println(getSimpleName() + " D: " + message);
  }

  public void error(String message) {
    System.err.println(getSimpleName() + " E: " + message);
  }

  public void info(String message) {
    System.err.println(getSimpleName() + " I: " + message);
  }

  public void shutdown() {
  }
}
