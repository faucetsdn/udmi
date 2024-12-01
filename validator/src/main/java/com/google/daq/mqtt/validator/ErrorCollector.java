package com.google.daq.mqtt.validator;

public interface ErrorCollector {

  void addError(Exception error, String category, String detail);
}
