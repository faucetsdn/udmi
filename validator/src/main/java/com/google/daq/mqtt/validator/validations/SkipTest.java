package com.google.daq.mqtt.validator.validations;

public class SkipTest extends RuntimeException {

  public SkipTest(String reason) {
    super(reason);
  }
}
