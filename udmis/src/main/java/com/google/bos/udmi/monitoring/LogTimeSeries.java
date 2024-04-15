package com.google.bos.udmi.monitoring;

import com.google.cloud.logging.Severity;
import com.google.logging.type.LogSeverity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Monitoring;
import udmi.schema.MonitoringMetric;


/**
 * All the LogTail items currently processing.
 * Hold the log entries in buckets for upstream processing in chunks.
 */
public class LogTimeSeries extends TreeMap<Long, LinkedList<LogTailEntry>> {
  private static final int LOG_ENTRIES_BUCKET_SECONDS = 10;
  private static final int LOG_ENTRIES_PER_SUBMIT = 100;
  private static final Object timeSeriesLock = new Object();
  int numberOfLogs = 0;

  private void emitMetrics(LogTailOutput output) throws IOException {
    synchronized (timeSeriesLock) {
      for (long k : keySet()) {
        for (LogTailEntry log : this.get(k)) {
          Monitoring monitoring = new Monitoring();
          monitoring.metric = new MonitoringMetric();
          monitoring.metric.system = new udmi.schema.SystemEvents();
          monitoring.metric.system.logentries = new ArrayList<udmi.schema.Entry>();
          // TODO: Refactor the contents of Envelope to put the relevant fields somewhere not
          // associated with a Message body, which is how it is used elsewhere.
          monitoring.metric.envelope = new Envelope();
          monitoring.metric.envelope.subFolder = SubFolder.MONITORING;
          loadMetricFields(monitoring.metric, log);
          output.emitMetric(monitoring);
        }
      }
    }
  }

  private int findSameLog(long k, LogTailEntry log) {
    // TODO: Revise when output storage requirements are known.
    LinkedList<LogTailEntry> logs = this.get(k);
    for (int i = 0; i < logs.size(); i++) {
      if (log.severity.equals(Severity.ERROR) && logs.get(i).serviceName.equals(
          log.severity.toString())) {
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

  private void loadMetricFields(MonitoringMetric metric, LogTailEntry log) throws IOException {
    metric.system.timestamp = Date.from(log.timestamp);
    metric.system.version = "1.4.1"; // TODO: Where to get this value

    udmi.schema.Entry entry = new udmi.schema.Entry();
    entry.level = severityToLogSeverity(log.severity).getNumber();
    entry.message = log.statusMessage;

    if (!log.resourceName.isEmpty()) {
      loadMetricFieldsFromResourceName(metric, log);
    }

    if (!log.statusMessage.isEmpty()) {
      entry.message = log.statusMessage;
      if (log.severity.toString().equals("ERROR")) {
        Pattern pattern = Pattern.compile("Device [`'](\\d+)[`'] is ");
        Matcher matcher = pattern.matcher(log.statusMessage);
        if (matcher.find()) {
          metric.envelope.deviceNumId = matcher.group(1);
        }
      }
    }

    metric.system.logentries.add(entry);
  }

  private void loadMetricFieldsFromResourceName(MonitoringMetric metric, LogTailEntry log) {
    // Example:
    // projects/essential-keep-197822/locations/us-central1/...
    // registries/UDMI-REFLECT/devices/IN-HYD-SAR2
    Pattern pattern = Pattern.compile(
        "projects/(?<project>[^/]+)/locations/(?<location>[^/]+)"
            + "/registries/(?<registry>[^/]+)/devices/(?<device>[^/]+)");
    Matcher matcher = pattern.matcher(log.resourceName);
    if (matcher.find()) {
      metric.envelope.projectId = matcher.group("project");
      metric.envelope.deviceRegistryLocation = matcher.group("location");
      metric.envelope.deviceRegistryId = matcher.group("registry");
      metric.envelope.deviceId = matcher.group("device");
    } // else do something?
  }

  private LogSeverity severityToLogSeverity(Severity severity) throws IOException {
    // Subtle: These are two different types with different enum value ranges. Translate manually.
    switch (severity) {
      case ERROR:
        return LogSeverity.ERROR;
      case DEBUG:
        return LogSeverity.DEBUG;
      case INFO:
        return LogSeverity.INFO;
      case ALERT:
        return LogSeverity.ALERT;
      case CRITICAL:
        return LogSeverity.CRITICAL;
      case WARNING:
        return LogSeverity.WARNING;
      case EMERGENCY:
        return LogSeverity.EMERGENCY;
      case NOTICE:
        return LogSeverity.NOTICE;
      case DEFAULT:
        return LogSeverity.DEFAULT;
      case NONE:
      default:
        throw new IOException("Unknown com.google.cloud.logging.Severity: " + severity);
    }
  }

  private boolean shouldEmitNow() {
    return (this.numberOfLogs > LOG_ENTRIES_PER_SUBMIT);
  }

  /**
   * Add one LogTailEntry to the time series.
   *
   * @param log LogTailEntry object to add.
   */
  public void add(LogTailEntry log) {
    // TODO: Again, based on output requirements, update performance and/or grouping.
    synchronized (timeSeriesLock) {
      long k = keyFor(log);
      LinkedList<LogTailEntry> reInsert = this.getOrDefault(k, new LinkedList<LogTailEntry>());
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
    }
  }

  @Override
  public void clear() {
    synchronized (timeSeriesLock) {
      super.clear();
      numberOfLogs = 0;
    }
  }

  /**
   * Per the requirements/limitations of both the internal time series structure,
   * and the properties of the LogTailOutput,
   * maybe emit the metrics currently in the time series. This may or may not produce output.
   *
   * @param output LogTailOutput instance to send output to.
   * @throws IOException If output fails.
   */
  public void maybeEmitMetrics(LogTailOutput output) throws IOException {
    if (shouldEmitNow()) {
      emitMetrics(output);
      clear();
    }
  }
}

