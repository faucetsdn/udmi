package com.google.bos.udmi.monitoring;

import com.google.cloud.logging.Severity;
import java.time.Instant;

public class LogTailEntry {

  Instant timestamp;
  String methodName;
  String serviceName;
  String resourceName;
  int statusCode;
  String statusMessage;
  Severity severity;
  int value = 1;

  public boolean equals(LogTailEntry other) {
    return other.timestamp.equals(timestamp) &&
        other.methodName.equals(methodName) &&
        other.serviceName.equals(serviceName) &&
        other.resourceName.equals(resourceName) &&
        other.statusCode == statusCode &&
        other.statusMessage.equals(statusMessage) &&
        other.severity.equals(severity) &&
        other.value == this.value;
  }

}
