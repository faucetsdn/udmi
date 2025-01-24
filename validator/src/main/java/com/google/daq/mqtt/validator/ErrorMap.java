package com.google.daq.mqtt.validator;

import java.util.HashMap;

/**
 * Simple class for a keyed map of exceptions.
 */
public class ErrorMap extends HashMap<String, Exception> {

  private final String description;

  public ErrorMap(String description) {
    super();
    this.description = description;
  }

  /**
   * Throw an exception if there's something in the map.
   */
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