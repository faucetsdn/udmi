package com.google.daq.mqtt.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.google.api.client.util.Strings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.stream.StreamSupport;

/**
 * Exception during validation.
 */
public class ValidationException extends RuntimeException {

  private final ImmutableList<ValidationException> causingExceptions;

  private ValidationException(
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
   * From an external processing report.
   *
   * @param report Report to convert
   * @return Converted exception.
   */
  public static ValidationException fromProcessingReport(ProcessingReport report) {
    Preconditions.checkArgument(!report.isSuccess(), "Report must not be successful");
    ImmutableList<ValidationException> causingExceptions =
        StreamSupport.stream(report.spliterator(), false)
            .filter(
                processingMessage -> processingMessage.getLogLevel().compareTo(LogLevel.ERROR) >= 0)
            .map(ValidationException::convertMessage).collect(toImmutableList());
    return new ValidationException(
        String.format("%d schema violations found", causingExceptions.size()), causingExceptions);
  }

  private static ValidationException convertMessage(ProcessingMessage processingMessage) {
    String pointer = processingMessage.asJson().get("instance").get("pointer").asText();
    String prefix = Strings.isNullOrEmpty(pointer) ? "" : (pointer + ": ");
    return new ValidationException(prefix + processingMessage.getMessage());
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
