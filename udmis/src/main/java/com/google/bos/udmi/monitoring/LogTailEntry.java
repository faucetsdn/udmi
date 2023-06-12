package com.google.bos.udmi.monitoring;

import com.google.cloud.logging.Severity;
import java.time.Instant;

/**
 * A data class to hold one or more equivalent log tail entries that occurred at or after one
 * timestamp, up to another point with another timestamp (i.e. a window). The window is defined by
 * the user of this data class, not this class.
 * TODO(any): Consider replacing with a Java record class.
 */
public class LogTailEntry {

  Instant timestamp;
  String methodName;
  String serviceName;
  String resourceName;
  int statusCode;
  String statusMessage;
  Severity severity;
  int value = 1;

  /**
   * Returns true if equal, false if not.
   *
   * @param other LogTailEntry instance to compare equality with.
   * @return Boolean.
   */
  public boolean equals(LogTailEntry other) {
    return other.timestamp.equals(timestamp)
        && other.methodName.equals(methodName)
        && other.serviceName.equals(serviceName)
        && other.resourceName.equals(resourceName)
        && other.statusCode == statusCode
        && other.statusMessage.equals(statusMessage)
        && other.severity.equals(severity)
        && other.value == this.value;
  }

}
