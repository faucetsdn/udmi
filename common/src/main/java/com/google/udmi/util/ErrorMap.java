package com.google.udmi.util;

import java.util.HashMap;
import java.util.Map;

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

  public class ErrorMapException extends RuntimeException {

    ErrorMapException() {
      super(description);
    }

    public Map<String, Exception> getMap() {
      return ErrorMap.this;
    }
  }
}