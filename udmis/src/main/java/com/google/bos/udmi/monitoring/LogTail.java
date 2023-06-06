package com.google.bos.udmi.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.cloud.audit.AuditLog;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Severity;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.udmi.util.JsonUtil;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import udmi.schema.Monitoring;
import udmi.schema.MonitoringMetric;

/**
 * Read the tail of GCP logs and generate metrics.
 *
 */
public class LogTail extends LogTailBase {

  protected static final String LOG_FILTER =
      "(resource.labels.function_name=\"udmi_target\") OR "
          + "(resource.labels.function_name=\"udmi_state\") OR "
          + "(resource.labels.function_name=\"udmi_config\") OR "
          + "(severity=ERROR AND protoPayload.serviceName=\"cloudiot.googleapis.com\")";
  protected boolean outputJson = false;
  protected LogTailOutput output;
  private String projectName;
  private LogTimeSeries logsTimeSeries;

  /**
   * Constructor for LogTail.
   */
  public LogTail(String projectName) {
    this.projectName = projectName;
    this.logsTimeSeries = new LogTimeSeries();
    this.outputJson = true;
  }

  /**
   * Main function.

   * @param args Command line arguments.
   *
   */
  public static void main(String[] args) throws ParseException {
    CommandLine commandLine = parseArgs(args);
    if (commandLine == null) {
      throw new RuntimeException("Exiting");
    }
    String projectId = commandLine.getOptionValue("p"); // TODO:(rm)NOTES: "essential-keep-197822"
    LogTail logTail = new LogTail(projectId);
    logTail.tailLogs();
  }

  /**
   * Additional main function used during testing.

   * @param args Command line arguments.
   * @param logTail Instantiated LogTail object.
   */
  public static void mainUnderTest(String[] args, LogTail logTail) throws ParseException {
    CommandLine commandLine = parseArgs(args);
    if (commandLine == null) {
      throw new RuntimeException("Exiting");
    }
    String projectId = commandLine.getOptionValue("p"); // TODO:(rm)NOTES: "essential-keep-197822"
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
      return (null);
    }

    if (!commandLine.hasOption("p")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("Required: project name", options);
      return (null);
    }

    return commandLine;
  }

  protected LogEntryServerStream getCloudLogStream(String filter) {
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
  protected void processLogEntry(LogEntry log) {
    if (log.getSeverity().equals(Severity.ERROR)) {
      processLogEntryError(log);
    } else {
      String functionName = log.getResource().getLabels().getOrDefault("function_name", null);
      if (functionName.equals("udmi_target")) {
        processLogEntryUdmiTarget(log);
      } else if (functionName.equals("udmi_state")) {
        processLogEntryUdmiState(log);
      } else if (functionName.equals("udmi_config")) {
        processLogEntryUdmiConfig(log);
      }
    }
  }

  /*
   * Top level processor of incoming GCP log entries of ERROR type.
   */
  protected void processLogEntryError(LogEntry log) {
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

  protected void processLogEntryUdmiConfig(LogEntry log) {
    Payload.StringPayload payload = log.getPayload();
    String data = payload.getData().toString();
    debug("udmi_config: %s", data);
  }

  protected void processLogEntryUdmiState(LogEntry log) {
    Payload.StringPayload payload = log.getPayload();
    String data = payload.getData().toString();
    debug("udmi_state: %s", data);

  }

  protected void processLogEntryUdmiTarget(LogEntry log) {

    Payload.StringPayload payload = log.getPayload();
    String data = payload.getData().toString();
    debug("udmi_target: %s", data);

  }

  protected void processLogStream(LogEntryServerStream stream) {
    for (LogEntry log : stream) {
      processLogEntry(log);
    }
  }

  /**
   * Begin tailing logs from GCP.
   *
   */
  public void tailLogs() {
    LogEntryServerStream stream = getCloudLogStream(LOG_FILTER);
    if (outputJson) {
      this.output = new LogTailJsonOutput();
    }
    processLogStream(stream);
  }
}