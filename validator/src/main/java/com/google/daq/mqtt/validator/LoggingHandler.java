package com.google.daq.mqtt.validator;

import static java.lang.String.format;

import java.io.PrintStream;
import java.util.function.BiConsumer;
import udmi.schema.Level;

class LoggingHandler {

  private final BiConsumer<Level, String> outputLogger;

  public LoggingHandler() {
    this(LoggingHandler::systemLogger);
  }

  public LoggingHandler(BiConsumer<Level, String> logger) {
    outputLogger = logger;
  }

  private static void systemLogger(Level level, String message) {
    if (level.value() >= Level.DEBUG.value()) {
      PrintStream printStream = level.value() >= Level.WARNING.value() ? System.err : System.out;
      printStream.println(message);
    }
  }

  void debug(String message) {
    outputLogger.accept(Level.DEBUG, message);
  }

  void debug(String format, Object... args) {
    debug(format(format, args));
  }

  void info(String message) {
    outputLogger.accept(Level.INFO, message);
  }

  void info(String format, Object... args) {
    info(format(format, args));
  }

  void warn(String message) {
    outputLogger.accept(Level.WARNING, message);
  }

  void error(String message) {
    outputLogger.accept(Level.ERROR, message);
  }

  void error(String format, Object... args) {
    error(format(format, args));
  }

  void trace(String message) {
    outputLogger.accept(Level.TRACE, message);
  }

  void trace(String format, Object... args) {
    trace(format(format, args));
  }
}
