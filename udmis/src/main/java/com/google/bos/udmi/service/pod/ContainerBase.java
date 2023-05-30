package com.google.bos.udmi.service.pod;

import static java.lang.String.format;

import org.jetbrains.annotations.NotNull;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 * TODO: Implement facilities for other loggers, including structured-to-cloud.
 */
public class ContainerBase {

  @NotNull
  protected String getSimpleName() {
    return getClass().getSimpleName();
  }

  public void debug(String message) {
    System.err.println(getSimpleName() + " D: " + message);
  }

  public void debug(String format, Object... args) {
    System.err.println(getSimpleName() + " D: " + format(format, args));
  }

  public void error(String message) {
    System.err.println(getSimpleName() + " E: " + message);
  }

  public void info(String message) {
    System.err.println(getSimpleName() + " I: " + message);
  }
}
