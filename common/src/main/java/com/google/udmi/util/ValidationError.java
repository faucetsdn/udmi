package com.google.udmi.util;

public class ValidationError extends RuntimeException {

  public ValidationError(String message) {
    super(message);
  }
}
