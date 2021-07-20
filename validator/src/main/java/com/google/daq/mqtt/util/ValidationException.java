package com.google.daq.mqtt.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.stream.StreamSupport;

public class ValidationException extends RuntimeException {

  private final ImmutableList<ValidationException> causingExceptions;

  private ValidationException(
      String message, ImmutableList<ValidationException> causingExceptions) {
    super(message);
    this.causingExceptions = causingExceptions;
  }

  private ValidationException(String message) {
    this(message, ImmutableList.of());
  }

  public static ValidationException fromProcessingReport(ProcessingReport report) {
    Preconditions.checkArgument(!report.isSuccess(), "Report must not be successful");
    ImmutableList<ValidationException> causingExceptions =
        StreamSupport.stream(report.spliterator(), false)
            .filter(
                processingMessage -> processingMessage.getLogLevel().compareTo(LogLevel.ERROR) >= 0)
            .map(processingMessage -> new ValidationException(processingMessage.getMessage()))
            .collect(toImmutableList());
    return new ValidationException(
        String.format("%d schema violations found", causingExceptions.size()), causingExceptions);
  }

  public ImmutableList<ValidationException> getCausingExceptions() {
    return causingExceptions;
  }

  public ImmutableList<String> getAllMessages() {
    ImmutableList.Builder<String> messagesBuilder =
        ImmutableList.<String>builder().add(getMessage());
    for (ValidationException causingException : causingExceptions) {
      messagesBuilder.addAll(causingException.getAllMessages());
    }
    return messagesBuilder.build();
  }
}
