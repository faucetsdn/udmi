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
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.ParseException;
import udmi.schema.Monitoring;
import udmi.schema.MonitoringMetric;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;

public class LogTail extends LogTailBase {

  private String projectName;
  private LogTimeSeries logsTimeSeries;
  private String LOG_FILTER =
      "(resource.labels.function_name=\"udmi_target\") OR " +
          "(resource.labels.function_name=\"udmi_state\") OR " +
          "(resource.labels.function_name=\"udmi_config\") OR " +
          "(severity=ERROR AND protoPayload.serviceName=\"cloudiot.googleapis.com\")";
  private boolean outputJson = false;
  private LogTailOutput output;

  public LogTail(String projectName) {
    this.projectName = projectName;
    this.logsTimeSeries = new LogTimeSeries();
    this.outputJson = true;
  }

  public static void main(String[] args) throws ParseException {
    CommandLine commandLine = parseArgs(args);
    String projectId = commandLine.getOptionValue("p"); // TODO:(rm)NOTES: "essential-keep-197822"
    LogTail logTail = new LogTail(projectId);
    logTail.tailLogs();
  }

  private static CommandLine parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption("h", "help", false, "Print usage info.");
    options.addOption("p", "project", true, "Cloud project name (required)");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    if (commandLine.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("Example", options);
      System.exit(0);
    }

    if (!commandLine.hasOption("p")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("Required: project name", options);
      System.exit(1);
    }

    return commandLine;
  }

  private LogEntryServerStream getCloudLogStream(String filter) {
    LoggingOptions options = LoggingOptions.getDefaultInstance();
    Logging logging = options.getService();
    LogEntryServerStream stream;
    if (filter == null) {
      stream = logging.tailLogEntries(TailOption.project(projectName));
    } else {
      stream = logging.tailLogEntries(TailOption.project(projectName), TailOption.filter(filter));
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
    // Be careful in here. Unwrapping the audit payload is a careful sequence.
    Payload.ProtoPayload payload = log.getPayload();
    if (!payload.getData().getTypeUrl().equals("type.googleapis.com/google.cloud.audit.AuditLog")) {
      error("Log payload is not AuditLog");
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
      logsTimeSeries.maybeEmitMetrics(output);
    } catch (InvalidProtocolBufferException e) {
      error("Log payload AuditLog deserialize error: " + e.toString());
    } catch (IOException e) {
      error(e.toString());
    }
  }

  private void processLogStream(LogEntryServerStream stream) {
    for (LogEntry log : stream) {
      processLogEntry(log);
    }
  }

  public void tailLogs() {
    LogEntryServerStream stream = getCloudLogStream(LOG_FILTER);
    if (outputJson) {
      this.output = new LogTailJsonOutput();
    }
    processLogStream(stream);
  }
}