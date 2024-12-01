package com.google.daq.mqtt.validator;

/**
 * Simple interface for collecting errors of various kinds.
 */
public interface ErrorCollector {

  void addError(Exception error, String category, String detail);
}
