package com.google.bos.udmi.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.audit.AuditLog;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Severity;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.Monitoring;
import udmi.schema.MonitoringMetric;

public class LogTail {

  private String projectId;
  private LogTimeSeries logsTimeSeries;
  private String LOG_FILTER =
      "(resource.labels.function_name=\"udmi_target\") OR " +
          "(resource.labels.function_name=\"udmi_state\") OR " +
          "(resource.labels.function_name=\"udmi_config\") OR " +
          "(severity=ERROR AND protoPayload.serviceName=\"cloudiot.googleapis.com\")";

  public LogTail(String projectId) {
    this.projectId = projectId;
    this.logsTimeSeries = new LogTimeSeries();
  }

  public static void main(String[] args) {
    String projectId = "essential-keep-197822";
    LogTail lt = new LogTail(projectId);
    lt.tailLogs();
  }

  private LogEntryServerStream getCloudLogStream(String filter) {
    LoggingOptions options = LoggingOptions.getDefaultInstance();
    Logging logging = options.getService();
    LogEntryServerStream stream;
    if (filter == null) {
      stream = logging.tailLogEntries();
    } else {
      stream = logging.tailLogEntries(TailOption.filter(filter));
    }
    return stream;
  }

  /*
   * Top level processor of incoming GCP log entries.
   */
  private void processLogEntry(LogEntry log) {
    if (log.getSeverity().equals(Severity.ERROR)) {
      processLogEntryError(log);
    } else {
      String function_name = log.getResource().getLabels().getOrDefault("function_name", null);
      if (function_name.equals("udmi_target")) {
      } else if (function_name.equals("udmi_state")) {
      } else if (function_name.equals("udmi_config")) {
      }
    }
  }

  /*
   * Top level processor of incoming GCP log entries of ERROR type.
   */
  private void processLogEntryError(LogEntry log) {
    // Be careful in here. Unwrapping this is a careful sequence.
    Payload.ProtoPayload payload = log.getPayload();
    if (!payload.getData().getTypeUrl().equals("type.googleapis.com/google.cloud.audit.AuditLog")) {
      System.err.println("Not AuditLog");
      return;
    }
    try {
      AuditLog auditLog = AuditLog.parseFrom(payload.getData().getValue().toByteArray());
      LogTailEntry entry = new LogTailEntry();
      entry.timestamp = log.getInstantTimestamp();
      entry.methodName = auditLog.getMethodName();
      entry.serviceName = auditLog.getServiceName();
      entry.resourceName = auditLog.getResourceName();
      entry.statusCode = auditLog.getStatus().getCode();
      entry.statusMessage = auditLog.getStatus().getMessage();
      entry.severity = log.getSeverity();
      logsTimeSeries.add(entry);
      logsTimeSeries.maybeEmitMetrics();
    } catch (InvalidProtocolBufferException e) {
      System.err.println(e.toString());
    } catch (IOException e) {
      System.err.println(e.toString());
    }
  }

  private void processLogStream(LogEntryServerStream stream) {
    for (LogEntry log : stream) {
      processLogEntry(log);
    }
  }

  public void tailLogs() {
    LogEntryServerStream stream = getCloudLogStream(LOG_FILTER);
    processLogStream(stream);
  }

  /*
   * Fields from GCP Logging stored in a neutral class.
   */
  protected class LogTailEntry {

    Instant timestamp;
    String methodName;
    String serviceName;
    String resourceName;
    int statusCode;
    String statusMessage;
    Severity severity;
    int value = 1;
  }

  /*
   * Linked list of LogTailEntry.
   */
  protected class LogTailEntryList extends LinkedList<LogTailEntry> {

  }

  /*
   * All the LogTail items currently processing.
   * Hold the log entries in buckets for upstream processing in chunks.
   */
  protected class LogTimeSeries extends TreeMap<Long, LogTailEntryList> {

    int numberOfLogs = 0;
    int LOG_ENTRIES_BUCKET_SECONDS = 10;
    int LOG_ENTRIES_PER_SUBMIT = 100;

    private void emitJsonTimeSeries(PrintStream output) throws JsonProcessingException {
      ObjectMapper objectMapper = new ObjectMapper();
      for (long k : keySet()) {
        for (LogTailEntry log : this.get(k)) {
          Monitoring monitoring = new Monitoring();
          monitoring.metric = new MonitoringMetric();
          loadMetricFields(monitoring.metric, log);
          output.println(objectMapper.writeValueAsString(monitoring));
        }
      }
    }

    private int findSameLog(long k, LogTailEntry log) {
      // TODO: Revise when output storage requirements are known.
      LogTailEntryList logs = this.get(k);
      for (int i = 0; i < logs.size(); i++) {
        if (log.severity.equals(Severity.ERROR) && logs.get(i).serviceName.equals(log.severity)) {
          if (logs.get(i).statusMessage.equals(log.statusMessage)) {
            return i;
          }
        }
      }
      return -1;
    }

    /*
     * Generate the bucket key for a LogTailEntry.
     */
    private long keyFor(LogTailEntry log) {
      long t = log.timestamp.toEpochMilli();
      t = t - (t % (LOG_ENTRIES_BUCKET_SECONDS * 1000));
      return t;
    }

    private void loadMetricFields(MonitoringMetric metric, LogTailEntry log) {
      metric.timestamp = Date.from(log.timestamp);
      metric.severity = log.severity.toString();

      if (!log.resourceName.isEmpty()) {
        loadMetricFieldsFromResourceName(metric, log);
      }

      if (!log.statusMessage.isEmpty()) {
        metric.status_message = log.statusMessage.toString();
        if (log.severity.toString().equals("ERROR")) {
          Pattern pattern = Pattern.compile("Device [`'](\\d+)[`'] is ");
          Matcher matcher = pattern.matcher(log.statusMessage);
          if (matcher.find()) {
            metric.device_num = matcher.group(1);
          }
        }
      }
    }

    private void loadMetricFieldsFromResourceName(MonitoringMetric metric, LogTailEntry log) {
      // Example:
      // projects/essential-keep-197822/locations/us-central1/registries/UDMS-REFLECT/devices/IN-HYD-SAR2
      Pattern pattern = Pattern.compile(
          "projects/(?<project>[^/]+)/locations/(?<location>[^/]+)/registries/(?<registry>[^/]+)/devices/(?<device>[^/]+)");
      Matcher matcher = pattern.matcher(log.resourceName);
      if (matcher.find()) {
        metric.project = matcher.group("project");
        metric.location = matcher.group("location");
        metric.registry = matcher.group("registry");
        metric.device_id = matcher.group("device");
      } else {
        metric.device_id = log.resourceName.toString();
      }
    }

    private boolean shouldSubmit() {
      return (this.numberOfLogs > LOG_ENTRIES_PER_SUBMIT);
    }

    public synchronized boolean add(LogTailEntry log) {
      // TODO: Again, based on output requirements, update performance and/or grouping.
      long k = keyFor(log);
      LogTailEntryList reInsert = this.getOrDefault(k, new LogTailEntryList());
      if (reInsert.size() > 0) {
        int i = findSameLog(k, log);
        if (i >= 0) {
          reInsert.get(i).value++;
        } else {
          reInsert.add(log);
        }
      } else {
        reInsert.add(log);
      }
      this.put(k, reInsert);
      numberOfLogs++;
      return true;
    }

    @Override
    public void clear() {
      super.clear();
      numberOfLogs = 0;
    }

    public void maybeEmitMetrics() throws IOException {
      if (shouldSubmit()) {
        emitJsonTimeSeries(System.out);
        clear();
      }
    }
  }


}