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


}
