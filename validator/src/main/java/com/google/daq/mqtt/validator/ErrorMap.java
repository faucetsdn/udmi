package com.google.daq.mqtt.validator;

import java.util.HashMap;

public class ErrorMap extends HashMap<String, Exception> {

  private final String description;

  public ErrorMap(String description) {
    super();
    this.description = description;
  }

  public void throwIfNotEmpty() {
    if (!isEmpty()) {
      throw asException();
    }
  }

  public ErrorMapException asException() {
    return new ErrorMapException();
  }

  class ErrorMapException extends RuntimeException {

  }
}