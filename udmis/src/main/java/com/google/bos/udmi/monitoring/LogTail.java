package com.google.bos.udmi.monitoring;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.audit.AuditLog;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Severity;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Read the tail of GCP logs and generate metrics.
 */
public class LogTail extends LogTailBase {

  protected static final String LOG_FILTER =
      "(resource.labels.function_name=\"udmi_target\") OR "
          + "(resource.labels.function_name=\"udmi_state\") OR "
          + "(resource.labels.function_name=\"udmi_config\") OR "
          + "(severity=ERROR AND protoPayload.serviceName=\"cloudiot.googleapis.com\")";
  protected boolean outputJson = false;
  protected LogTailOutput output;
  protected LogTimeSeries logTimeSeries;
  private String projectName;
  private String keyFilename;

  /**
   * Constructor for LogTail.
   * TODO: Build an options class when >N arguments are passed.
   */
  public LogTail(String projectName, String keyFilename) {
    this.projectName = projectName;
    this.keyFilename = keyFilename;
    this.logTimeSeries = new LogTimeSeries();
    this.outputJson = true;
  }

  /**
   * Main function.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) throws ParseException {
    CommandLine commandLine = parseArgs(args);
    if (commandLine == null) {
      throw new RuntimeException("Exiting");
    }
    String projectId = commandLine.getOptionValue("p");
    String keyFilename = commandLine.getOptionValue("k");
    LogTail logTail = new LogTail(projectId, keyFilename);
    logTail.tailLogs();
  }

  /**
   * Additional main function used during testing.
   *
   * @param args Command line arguments.
   * @param logTail Instantiated LogTail object.
   */
  public static void mainUnderTest(String[] args, LogTail logTail) throws ParseException {
    CommandLine commandLine = parseArgs(args);
    if (commandLine == null) {
      throw new RuntimeException("Exiting");
    }
    logTail.tailLogs();
  }

  private static CommandLine parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption("h", "help", false, "Print usage info.");
    options.addOption("p", "project", true, "Cloud project name (required)");
    options.addOption("k", "key-file", true, "File containing cloud application credentials");

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

    if (!commandLine.hasOption("k")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("Required: key file", options);
      return (null);
    }

    return commandLine;
  }

  protected AuditLog getAuditLogParseFrom(byte[] bytes) throws InvalidProtocolBufferException {
    return AuditLog.parseFrom(bytes);
  }

  protected InputStream getCredentialsStream(String filename) throws IllegalArgumentException {
    final FileInputStream fileInputStream;
    try {
      fileInputStream = new FileInputStream(filename);
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException(e);
    }
    return fileInputStream;
  }

  protected LogEntryServerStream getCloudLogStream(String filter) {
    Logging logging = getLoggingService(projectName, getCredentialsStream(keyFilename));
    LogEntryServerStream stream;
    if (filter == null) {
      stream = logging.tailLogEntries(TailOption.project(projectName));
    } else {
      stream = logging.tailLogEntries(TailOption.project(projectName), TailOption.filter(filter));
    }
    return stream;
  }

  protected LoggingOptions.Builder getLoggingOptionsBuilder() {
    return LoggingOptions.newBuilder();
  }

  protected ServiceAccountCredentials getServiceAccountCredentialsFromStream(
      InputStream inputStream) throws IOException {
    return ServiceAccountCredentials.fromStream(inputStream);
  }

  protected Logging getLoggingService(String projectName, InputStream credentialsStream) {
    try {
      LoggingOptions loggingOptions = getLoggingOptionsBuilder()
          .setCredentials(getServiceAccountCredentialsFromStream(credentialsStream))
          .setProjectId(projectName)
          .build();
      return loggingOptions.getService();
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
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
      AuditLog auditLog = getAuditLogParseFrom(payload.getData().getValue().toByteArray());
      LogTailEntry entry = new LogTailEntry();
      entry.timestamp = log.getInstantTimestamp();
      entry.methodName = auditLog.getMethodName();
      entry.serviceName = auditLog.getServiceName();
      entry.resourceName = auditLog.getResourceName();
      entry.statusCode = auditLog.getStatus().getCode();
      entry.statusMessage = auditLog.getStatus().getMessage();
      entry.severity = log.getSeverity();
      logTimeSeries.add(entry);
      logTimeSeries.maybeEmitMetrics(output);
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
   */
  public void tailLogs() {
    LogEntryServerStream stream = getCloudLogStream(LOG_FILTER);
    if (outputJson) {
      this.output = new LogTailJsonOutput();
    }
    processLogStream(stream);
  }
}