package com.google.daq.mqtt.util;

public class ValidationError extends RuntimeException {

  public ValidationError(String message) {
    super(message);
  }
}
