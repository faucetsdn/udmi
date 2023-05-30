package com.google.bos.udmi.monitoring;

import com.google.cloud.logging.Severity;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.Monitoring;
import udmi.schema.MonitoringMetric;


/*
 * All the LogTail items currently processing.
 * Hold the log entries in buckets for upstream processing in chunks.
 */
public class LogTimeSeries extends TreeMap<Long, LinkedList<LogTailEntry>> {

  final int LOG_ENTRIES_BUCKET_SECONDS = 10;
  final int LOG_ENTRIES_PER_SUBMIT = 100;
  private final Object timeSeriesLock = new Object();
  int numberOfLogs = 0;

  private void emitMetrics(LogTailOutput output) throws IOException {
    synchronized (timeSeriesLock) {
      for (long k : keySet()) {
        for (LogTailEntry log : this.get(k)) {
          Monitoring monitoring = new Monitoring();
          monitoring.metric = new MonitoringMetric();
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
      if (log.severity.equals(Severity.ERROR) && logs.get(i).serviceName.equals(log.severity.toString())) {
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

    if (log.severity.equals(Severity.ERROR)) {
      metric.severity = MonitoringMetric.Severity.ERROR;
    } else {
      metric.severity = MonitoringMetric.Severity.NONE;
    }

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

  public boolean add(LogTailEntry log) {
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
    return true;
  }

  @Override
  public void clear() {
    synchronized (timeSeriesLock) {
      super.clear();
      numberOfLogs = 0;
    }
  }

  public void maybeEmitMetrics(LogTailOutput output) throws IOException {
    if (shouldSubmit()) {
      emitMetrics(output);
      clear();
    }
  }
}

