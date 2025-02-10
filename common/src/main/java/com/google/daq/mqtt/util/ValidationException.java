package com.google.daq.mqtt.util;

import com.google.common.collect.ImmutableList;

/**
 * Exception during validation.
 */
public class ValidationException extends ValidationError {

  private final ImmutableList<ValidationException> causingExceptions;

  public ValidationException(
      String message, ImmutableList<ValidationException> causingExceptions) {
    super(message);
    this.causingExceptions = causingExceptions;
  }

  /**
   * Create a simple validation exception.
   *
   * @param message exception message
   */
  public ValidationException(String message) {
    this(message, ImmutableList.of());
  }

  /**
   * Get the exception that started it all.
   *
   * @return The triggering exception.
   */
  public ImmutableList<ValidationException> getCausingExceptions() {
    return causingExceptions;
  }

  /**
   * Get all the messages in this exception.
   *
   * @return List of the messages.
   */
  public ImmutableList<String> getAllMessages() {
    ImmutableList.Builder<String> messagesBuilder =
        ImmutableList.<String>builder().add(getMessage());
    for (ValidationException causingException : causingExceptions) {
      messagesBuilder.addAll(causingException.getAllMessages());
    }
    return messagesBuilder.build();
  }
}
