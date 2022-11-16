package com.google.daq.mqtt.validator;

import java.util.Map;

public class MessageParseException extends RuntimeException {

  final String source;
  final Map<String, String> attributes;
  final Exception exception;

  public MessageParseException(String source, Map<String, String> attributes,
      Exception exception) {
    this.source = source;
    this.attributes = attributes;
    this.exception = exception;
  }
}
